package server.netty.handler;

import common.message.RpcRequest;
import common.message.RpcResponse;
import common.result.Result;
import common.trace.TraceContext;
import common.trace.TraceInterceptor;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.provider.ServiceProvider;
import server.provider.ratelimit.RateLimit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

@AllArgsConstructor
public class NettyServerHandler extends SimpleChannelInboundHandler<Object> {
    private static final Logger logger = LoggerFactory.getLogger(NettyServerHandler.class);
    private ServiceProvider serviceProvider;
    private java.util.concurrent.ThreadPoolExecutor threadPool;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        // 必须是 RawMessage
        if (!(msg instanceof common.message.RawMessage)) {
            logger.warn("服务端收到非RawMessage消息: {}", msg.getClass());
            return;
        }
        common.message.RawMessage rawMessage = (common.message.RawMessage) msg;

        // 1. 反序列化 RawMessage -> RpcRequest (这步必须先做，才能知道调用的哪个服务)
        // 为了避免阻塞IO线程，我们这里需要权衡。
        // 如果是FastService，我们假设反序列化也很快（通常是小包），或者我们只反序列化Header？
        // 目前协议没分Header/Body，所以必须反序列化整个包。
        // 考虑到 EchoService 这种场景，反序列化开销 < 上下文切换开销。
        // 所以我们先在 IO 线程尝试反序列化。

        try {
            common.serializer.Serializer serializer = common.serializer.SerializerFactory
                    .getSerializerByCode(rawMessage.getSerializerType());
            if (serializer == null) {
                throw new RuntimeException("不支持的序列化类型: " + rawMessage.getSerializerType());
            }

            Object deserialized = serializer.deserialize(rawMessage.getData(), rawMessage.getMessageType());
            if (!(deserialized instanceof RpcRequest)) {
                throw new RuntimeException("消息类型错误，期望RpcRequest，实际: " + deserialized.getClass());
            }
            RpcRequest request = (RpcRequest) deserialized;

            // 检查是否为 FastService
            String serviceName = request.getInterfaceName();
            Object service = serviceProvider.getService(serviceName);
            boolean isFastService = service != null
                    && service.getClass().isAnnotationPresent(common.service.FastService.class);

            if (isFastService) {
                // --- 快速路径：直接在 IO 线程执行 ---
                TraceInterceptor.serverBeforeHandle(request.getTraceId(), request.getSpanId());
                try {
                    getResponseAsync(request).thenAccept(response -> {
                        response.setRequestId(request.getRequestId());
                        response.setTraceId(TraceContext.getTraceId());
                        response.setSpanId(TraceContext.getSpanId());
                        sendResponse(ctx, response, serializer);
                    });
                } finally {
                    TraceInterceptor.serverAfterHandle();
                }
            } else {
                // --- 慢速路径：提交到业务线程池 ---
                CompletableFuture.runAsync(() -> {
                    TraceInterceptor.serverBeforeHandle(request.getTraceId(), request.getSpanId());
                    try {
                        getResponseAsync(request).thenAccept(response -> {
                            response.setRequestId(request.getRequestId());
                            response.setTraceId(TraceContext.getTraceId());
                            response.setSpanId(TraceContext.getSpanId());
                            sendResponse(ctx, response, serializer);
                        });
                    } finally {
                        TraceInterceptor.serverAfterHandle();
                    }
                }, threadPool);
            }

        } catch (Exception e) {
            logger.error("请求处理失败", e);
            RpcResponse errorResponse = RpcResponse.fail(500, "服务端处理异常: " + e.getMessage());
            ctx.writeAndFlush(errorResponse);
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, RpcResponse response,
            common.serializer.Serializer serializer) {
        try {
            byte[] responseBytes = serializer.serialize(response);
            common.message.RawMessage responseRaw = new common.message.RawMessage(
                    (short) common.message.MessageType.RESPONSE.getCode(),
                    (short) serializer.getType(),
                    responseBytes);
            ctx.writeAndFlush(responseRaw);
        } catch (Exception e) {
            logger.error("序列化响应失败", e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("服务端处理请求时出错: {}", cause.getMessage(), cause);
        ctx.close();
    }

    /**
     * 异步获取服务响应
     */
    private CompletableFuture<RpcResponse> getResponseAsync(RpcRequest rpcRequest) {
        // 获取服务名
        String serviceName = rpcRequest.getInterfaceName();

        // 得到服务对应限流器
        RateLimit rateLimit = serviceProvider.getRateLimit(serviceName);
        if (!rateLimit.getToken()) {
            logger.warn("服务" + serviceName + "限流器被触发");
            return CompletableFuture.completedFuture(RpcResponse.fail(429, "服务限流"));
        }

        // 得到服务端相应实现类
        Object service = serviceProvider.getService(serviceName);

        try {
            // 获取方法对象
            Method method = service.getClass().getMethod(
                    rpcRequest.getMethodName(), rpcRequest.getParamsType());

            // 通过 ServiceInvoker 调用方法 (替代反射)
            Object invoke = serviceProvider.getServiceInvoker().invoke(service, method, rpcRequest.getParams());

            // 如果返回值是 CompletableFuture，异步等待
            if (invoke instanceof CompletableFuture) {
                return ((CompletableFuture<?>) invoke).thenApply(result -> {
                    return buildSuccessResponse(result);
                }).exceptionally(ex -> {
                    logger.error("服务端执行异步方法时出错", ex);
                    return RpcResponse.fail(500, "服务端执行异步方法时出错: " + ex.getMessage());
                });
            }

            // 同步方法，直接返回
            return CompletableFuture.completedFuture(buildSuccessResponse(invoke));

        } catch (Exception e) {
            logger.error("服务端执行方法时出错: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(RpcResponse.fail(500, "服务端执行方法时出错: " + e.getMessage()));
        }
    }

    /**
     * 构建成功响应
     */
    private RpcResponse buildSuccessResponse(Object invoke) {
        // 如果返回值是Result类型，根据Result状态转换为RpcResponse
        if (invoke instanceof Result) {
            Result<?> result = (Result<?>) invoke;
            if (result.isSuccess()) {
                return RpcResponse.success(result.getData());
            } else {
                return RpcResponse.fail(result.getCode(), result.getMessage());
            }
        } else {
            return RpcResponse.success(invoke);
        }
    }
}
