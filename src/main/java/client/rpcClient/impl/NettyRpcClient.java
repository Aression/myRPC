package client.rpcClient.impl;

import client.netty.UnprocessedRequests;
import client.netty.initializer.NettyClientInitializer;
import client.proxy.breaker.Breaker;
import client.proxy.breaker.BreakerProvider;
import client.rpcClient.RpcClient;
import client.serviceCenter.ServiceCenter;
import client.serviceCenter.ZKServiceCenter;
import client.serviceCenter.balance.LoadBalance;
import client.serviceCenter.balance.LoadBalanceFactory;
import common.context.RpcRequestContext;
import common.message.RpcRequest;
import common.message.RpcResponse;
import common.trace.TraceContext;
import common.trace.TraceInterceptor;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.*;

public class NettyRpcClient implements RpcClient, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(NettyRpcClient.class);

    private final ServiceCenter serviceCenter;
    private final EventLoopGroup eventLoopGroup;
    private final Bootstrap bootstrap;
    private final ConnectionManager connectionManager;

    // 可配置参数
    private final long readTimeout;
    private final TimeUnit timeUnit;

    // 共享调度器，用于处理超时（建议全局单例或静态管理，避免每个Client创建太多线程）
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(4, r -> {
        Thread t = new Thread(r, "rpc-client-scheduler");
        t.setDaemon(true);
        return t;
    });

    // ================= 构造函数优化 =================

    public NettyRpcClient() {
        this(LoadBalanceFactory.getFromConfigOrDefault());
    }

    public NettyRpcClient(LoadBalance loadBalance) {
        this(loadBalance, 5, TimeUnit.SECONDS);
    }

    public NettyRpcClient(LoadBalance loadBalance, long timeout, TimeUnit timeUnit) {
        this.serviceCenter = new ZKServiceCenter(loadBalance);
        this.readTimeout = timeout;
        this.timeUnit = timeUnit;

        // 初始化 Netty 资源 (不再是 static，支持多实例)
        // 线程数设置为 CPU 核数 * 2 或根据业务密集度调整
        this.eventLoopGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors() * 2);
        this.bootstrap = new Bootstrap();
        this.bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(io.netty.channel.ChannelOption.TCP_NODELAY, true)
                .option(io.netty.channel.ChannelOption.SO_KEEPALIVE, true)
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .handler(new NettyClientInitializer());

        this.connectionManager = new ConnectionManager(bootstrap);
    }

    @Override
    public boolean checkRetry(String serviceName) {
        return serviceCenter.checkRetry(serviceName);
    }

    // ================= 核心逻辑：纯异步实现 =================

    @Override
    public RpcResponse sendRequest(RpcRequest request) {
        // 为了兼容RpcClient接口，保留此方法但标记为不推荐使用
        // 建议直接使用 sendRequestAsync 方法
        throw new UnsupportedOperationException("此客户端仅支持异步调用，请使用 sendRequestAsync 方法");
    }

    /**
     * 异步发送 RPC 请求
     * 
     * @param request RPC 请求对象
     * @return CompletableFuture<RpcResponse> 异步响应
     */
    public CompletableFuture<RpcResponse> sendRequestAsync(RpcRequest request) {
        CompletableFuture<RpcResponse> resultFuture = new CompletableFuture<>();

        // 1. 链路追踪上下文设置
        TraceInterceptor.clientBeforeRequest();
        request.setTraceId(TraceContext.getTraceId());
        request.setSpanId(TraceContext.getSpanId());

        common.util.PerformanceTracker.record(request.getRequestId(), "client_send_start");

        try {
            // 2. 服务发现
            InetSocketAddress addr = serviceCenter.serviceDiscovery(
                    request.getInterfaceName(),
                    request.getFeatureCode());

            if (addr == null) {
                completeFail(resultFuture, 404, "服务未找到: " + request.getInterfaceName());
                return resultFuture;
            }

            // 3. 熔断检测
            Breaker breaker = BreakerProvider.getInstance().getBreaker(addr);
            if (!breaker.allowRequest()) {
                completeFail(resultFuture, 500, "服务节点熔断: " + addr);
                return resultFuture;
            }

            // 4. 获取连接 (从连接池)
            Channel channel;
            try {
                channel = connectionManager.getChannel(addr);
                common.util.PerformanceTracker.record(request.getRequestId(), "client_conn_acquired");
            } catch (Exception e) {
                breaker.recordFailure();
                logger.error("连接建立失败: {}", addr, e);
                completeFail(resultFuture, 500, "连接失败: " + e.getMessage());
                return resultFuture;
            }

            // 5. 注册待处理请求
            UnprocessedRequests.put(request.getRequestId(), resultFuture);

            // 6. 发送请求 (异步写)
            channel.writeAndFlush(request).addListener((ChannelFutureListener) future -> {
                if (!future.isSuccess()) {
                    UnprocessedRequests.fail(request.getRequestId(), new Exception("发送请求失败")); // 移除并标记失败
                    breaker.recordFailure();
                    logger.error("发送请求失败", future.cause());
                    completeFail(resultFuture, 500, "网络发送失败");
                } else {
                    common.util.PerformanceTracker.record(request.getRequestId(), "client_write_success");
                }
            });

            // 7. 异步结果处理的回调 (当 UnprocessedRequests 收到 Response 并 complete future 时触发)
            resultFuture.whenComplete((response, throwable) -> {
                common.util.PerformanceTracker.record(request.getRequestId(), "client_response_receive");
                if (throwable != null) {
                    // 异常情况通常是超时被外部触发
                    breaker.recordFailure();
                } else {
                    // 正常响应处理
                    handleResponseMetrics(response, breaker);
                    TraceInterceptor.clientAfterResponse();
                }
                RpcRequestContext.clear(); // 清理 ThreadLocal (注意：如果是异步回调，这里的clear可能清理的是Netty线程的TL，需谨慎)
            });

            // 8. 设置超时任务
            // 注意：这里使用 remove 机制，如果请求已经完成，scheduled task 执行时会由 UnprocessedRequests
            // 内部逻辑处理或被忽略
            SCHEDULER.schedule(() -> {
                if (!resultFuture.isDone()) {
                    logger.warn("异步请求超时监控触发: {}", request.getRequestId());
                    breaker.recordFailure();
                    // UnprocessedRequests.fail 会触发 resultFuture.completeExceptionally
                    UnprocessedRequests.fail(request.getRequestId(), new TimeoutException("Async Request Timeout"));
                }
            }, readTimeout, timeUnit);

        } catch (Exception e) {
            logger.error("发送异步请求流程异常", e);
            completeFail(resultFuture, 500, "客户端内部错误: " + e.getMessage());
        }

        return resultFuture;
    }

    // 辅助方法：统一处理失败
    private void completeFail(CompletableFuture<RpcResponse> future, int code, String msg) {
        future.complete(RpcResponse.fail(code, msg));
        TraceInterceptor.clientAfterResponse();
        RpcRequestContext.clear();
    }

    // 辅助方法：处理响应与熔断器状态
    private void handleResponseMetrics(RpcResponse response, Breaker breaker) {
        if (response == null)
            return;

        int code = response.getCode();
        if (code == 200) {
            breaker.recordSuccess();
        } else if (code >= 500 || code == 429) {
            breaker.recordFailure();
        } else {
            breaker.recordSuccess();
        }

        response.setTraceId(TraceContext.getTraceId());
        response.setSpanId(TraceContext.getSpanId());
    }

    @Override
    public String reportServiceStatus() {
        return serviceCenter.reportServiceDistribution();
    }

    /**
     * 优雅停机
     */
    @Override
    public void close() {
        if (eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully();
        }
        connectionManager.close();
        logger.info("NettyRpcClient stopped.");
    }

    // ================= 内部类：连接管理器 =================

    /**
     * 负责管理连接缓存，解决并发连接问题
     */
    private static class ConnectionManager {
        private final Bootstrap bootstrap;
        private final Map<String, Channel> channels = new ConcurrentHashMap<>();
        // 使用锁对象防止对同一Host的并发Connect
        private final Map<String, Object> connectLocks = new ConcurrentHashMap<>();

        public ConnectionManager(Bootstrap bootstrap) {
            this.bootstrap = bootstrap;
        }

        public Channel getChannel(InetSocketAddress address) throws InterruptedException {
            String key = address.toString();

            // 1. 快速检查
            Channel channel = channels.get(key);
            if (channel != null && channel.isActive()) {
                return channel;
            }

            // 2. 获取锁对象 (细粒度锁，只锁当前地址)
            Object lock = connectLocks.computeIfAbsent(key, k -> new Object());

            synchronized (lock) {
                // 3. 双重检查
                channel = channels.get(key);
                if (channel != null && channel.isActive()) {
                    return channel;
                }

                // 4. 建立连接 (同步等待，因为建立连接通常很快，且必须建立后才能发请求)
                // 也可以优化为返回 Future<Channel> 实现全异步，但增加了复杂度
                logger.debug("Connecting to {}", address);
                channel = bootstrap.connect(address).sync().channel();

                // 5. 添加关闭监听，自动移除失效连接
                channel.closeFuture().addListener(future -> {
                    logger.debug("Channel disconnected: {}", key);
                    channels.remove(key);
                    connectLocks.remove(key); // 清理锁对象
                });

                channels.put(key, channel);
            }
            return channel;
        }

        public void close() {
            for (Channel channel : channels.values()) {
                channel.close();
            }
            channels.clear();
            connectLocks.clear();
        }
    }
}