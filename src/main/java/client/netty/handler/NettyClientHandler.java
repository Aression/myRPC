package client.netty.handler;

import common.message.RawMessage;
import common.message.RpcResponse;
import common.serializer.Serializer;
import common.serializer.SerializerFactory;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class NettyClientHandler extends SimpleChannelInboundHandler<Object> {
    private static final Logger logger = LoggerFactory.getLogger(NettyClientHandler.class);

    // 客户端业务线程池，用于处理响应的反序列化
    private static final ThreadPoolExecutor clientWorkerPool;

    static {
        int cores = Runtime.getRuntime().availableProcessors();
        clientWorkerPool = new ThreadPoolExecutor(
                cores * 2,
                cores * 4,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(5000),
                java.util.concurrent.Executors.defaultThreadFactory());

        // 简单的监控
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            if (clientWorkerPool.getQueue().size() > 500) {
                logger.warn("ClientWorkerPool High Load: [Active: {}, Queue: {}]",
                        clientWorkerPool.getActiveCount(), clientWorkerPool.getQueue().size());
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, Object msg) throws Exception {
        if (!(msg instanceof RawMessage)) {
            logger.warn("客户端收到非RawMessage消息: {}", msg.getClass());
            return;
        }

        RawMessage rawMessage = (RawMessage) msg;

        // 提交到线程池处理
        clientWorkerPool.execute(() -> {
            try {
                Serializer serializer = SerializerFactory.getSerializerByCode(rawMessage.getSerializerType());
                if (serializer == null) {
                    logger.error("不支持的序列化类型: {}", rawMessage.getSerializerType());
                    return;
                }

                Object deserialized = serializer.deserialize(rawMessage.getData(), rawMessage.getMessageType());
                if (deserialized instanceof RpcResponse) {
                    client.netty.UnprocessedRequests.complete((RpcResponse) deserialized);
                } else {
                    logger.warn("收到非RpcResponse消息: {}", deserialized.getClass());
                }
            } catch (Exception e) {
                logger.error("客户端反序列化异常", e);
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.warn("客户端处理响应时出错: " + cause.getMessage());
        cause.printStackTrace();
        ctx.close();
    }
}
