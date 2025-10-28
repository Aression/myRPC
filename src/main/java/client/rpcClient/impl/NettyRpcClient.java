package client.rpcClient.impl;

import client.netty.initializer.NettyClientInitializer;
import client.rpcClient.RpcClient;
import client.serviceCenter.ServiceCenter;
import client.serviceCenter.ZKServiceCenter;
import client.serviceCenter.balance.LoadBalance;
import client.serviceCenter.balance.LoadBalanceFactory;
import common.message.RpcRequest;
import common.message.RpcResponse;
import common.context.RpcRequestContext;
import common.trace.TraceInterceptor;
import common.trace.TraceContext;

import org.slf4j.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class NettyRpcClient implements RpcClient {
    private static final Logger logger = LoggerFactory.getLogger(NettyRpcClient.class);
    private static final AttributeKey<RpcResponse> RESPONSE_KEY = AttributeKey.valueOf("RPCResponse");
    private static final Bootstrap bootstrap;
    private static final EventLoopGroup eventLoopGroup;
    private ServiceCenter serviceCenter;
    private static final int TIMEOUT = 5;

    public NettyRpcClient(){
        LoadBalance loadBalance = LoadBalanceFactory.getFromConfigOrDefault();
        this.serviceCenter = new ZKServiceCenter(loadBalance);
    }
    
    public NettyRpcClient(LoadBalance.BalanceType balanceType){
        LoadBalance loadBalance = LoadBalanceFactory.getLoadBalance(balanceType);
        this.serviceCenter = new ZKServiceCenter(loadBalance);
    }
    
    public NettyRpcClient(LoadBalance loadBalance){
        this.serviceCenter = new ZKServiceCenter(loadBalance);
    }

    public boolean checkRetry(String serviceName){
        return serviceCenter.checkRetry(serviceName);
    }

    static{
        eventLoopGroup = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new NettyClientInitializer());
    }

    @Override
    public RpcResponse sendRequest(RpcRequest request) {
        // 设置当前线程的请求上下文
        try {
            RpcRequestContext.setCurrentRequest(request);
            
            // 设置链路追踪信息
            TraceInterceptor.clientBeforeRequest();
            request.setTraceId(TraceContext.getTraceId());
            request.setSpanId(TraceContext.getSpanId());
            
            // 从zookeeper注册中心获取host和port，传入特征码
            InetSocketAddress addr = serviceCenter.serviceDiscovery(
                    request.getInterfaceName(), 
                    request.getFeatureCode());
                    
            if(addr == null) {
                logger.error("无法找到服务 {} 的可用节点", request.getInterfaceName());
                return RpcResponse.fail(404, "服务不可用");
            }

            String host = addr.getHostName();
            int port = addr.getPort();
            String serverAddress = host + ":" + port;
            logger.info("连接到服务节点: {}", serverAddress);

            Channel channel = null;
            try {
                // 连接服务端
                ChannelFuture channelFuture = bootstrap.connect(host, port).sync();
                channel = channelFuture.channel();
                
                // 发送请求
                channel.writeAndFlush(request);
                logger.debug("已发送请求: {}", request);
                
                // 等待响应，设置超时时间
                if (!channel.closeFuture().await(TIMEOUT, TimeUnit.SECONDS)) {
                    logger.warn("等待响应超时");
                    RpcResponse timeoutResponse = RpcResponse.fail(504, "请求超时");
                    return timeoutResponse;
                }
                
                // 获取响应
                RpcResponse response = channel.attr(RESPONSE_KEY).get();
                if(response == null) {
                    logger.error("未收到服务端响应");
                    RpcResponse errorResponse = RpcResponse.fail(500, "未收到响应");
                    return errorResponse;
                }
                
                // 在响应中设置服务器地址信息和链路追踪信息
                response.setTraceId(TraceContext.getTraceId());
                response.setSpanId(TraceContext.getSpanId());
                
                TraceInterceptor.clientAfterResponse();
                
                logger.debug("成功获取响应: {}", response);
                return response;
            } catch (InterruptedException e) {
                logger.error("连接服务失败: {}", e.getMessage(), e);
                RpcResponse errorResponse = RpcResponse.fail(500, "连接失败");
                errorResponse.setTraceId(TraceContext.getTraceId());
                errorResponse.setSpanId(TraceContext.getSpanId());
                return errorResponse;
            } finally {
                // 只关闭当前通道，不关闭事件循环组
                if (channel != null) {
                    channel.close();
                }
            }
        } finally {
            // 清除当前线程的请求上下文和链路追踪信息
            RpcRequestContext.clear();
            TraceContext.clear();
        }
    }

    @Override
    public String reportServiceStatus() {
        return serviceCenter.reportServiceDistribution();
    }
}
