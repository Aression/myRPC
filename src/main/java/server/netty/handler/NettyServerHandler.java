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

@AllArgsConstructor
public class NettyServerHandler extends SimpleChannelInboundHandler<RpcRequest> {
    private static final Logger logger = LoggerFactory.getLogger(NettyServerHandler.class);
    private ServiceProvider serviceProvider;
    
    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, RpcRequest request) {
        try {
            // 设置链路追踪信息
            TraceInterceptor.serverBeforeHandle(request.getTraceId(), request.getSpanId());
            
            logger.info("收到客户端请求: {}", request);
            RpcResponse rpcResponse = getResponse(request);
            
            // 设置响应的链路追踪信息
            rpcResponse.setTraceId(TraceContext.getTraceId());
            rpcResponse.setSpanId(TraceContext.getSpanId());
            
            logger.info("返回响应: {}", rpcResponse);
            
            // 发送响应并添加监听器，确保响应发送完成后再关闭通道
            channelHandlerContext.writeAndFlush(rpcResponse).addListener(future -> {
                if (future.isSuccess()) {
                    logger.info("响应发送成功，准备关闭通道");
                    channelHandlerContext.close();
                } else {
                    logger.error("响应发送失败: {}", future.cause().getMessage());
                    channelHandlerContext.close();
                }
            });
        } finally {
            TraceInterceptor.serverAfterHandle();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("服务端处理请求时出错: {}", cause.getMessage(), cause);
        ctx.close();
    }

    private RpcResponse getResponse(RpcRequest rpcRequest){
        // 获取服务名
        String serviceName = rpcRequest.getInterfaceName();
        logger.info("处理服务请求: {}", serviceName);

        // 得到服务对应限流器
        RateLimit rateLimit = serviceProvider.getRateLimit(serviceName);
        if(!rateLimit.getToken()){
            // 获取令牌失败，进入限流状态
            logger.warn("服务"+serviceName+"限流器被触发");
            return RpcResponse.fail(429, "服务限流");
        }

        // 得到服务端相应实现类
        Object service = serviceProvider.getService(serviceName);
        Method method;
        try{
            // 获取方法对象
            method = service.getClass().getMethod(
                    rpcRequest.getMethodName(), rpcRequest.getParamsType()
            );
            logger.info("调用方法: {}", rpcRequest.getMethodName());
            //通过反射调用方法
            Object invoke = method.invoke(service, rpcRequest.getParams());
            logger.info("方法调用成功，返回结果: {}", invoke);
            
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
        }catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // 找不到请求的方法or方法无法访问or方法执行过程中出错
            logger.error("服务端执行方法时出错: {}", e.getMessage(), e);
            return RpcResponse.fail(500,"服务端执行方法时出错");
        }
    }
}
