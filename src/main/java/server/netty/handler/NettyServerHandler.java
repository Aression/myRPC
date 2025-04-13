package server.netty.handler;

import common.message.RpcRequest;
import common.message.RpcResponse;
import common.result.Result;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.AllArgsConstructor;
import server.provider.ServiceProvider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@AllArgsConstructor
public class NettyServerHandler extends SimpleChannelInboundHandler<RpcRequest> {
    private ServiceProvider serviceProvider;
    
    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, RpcRequest request) {
        System.out.println("收到客户端请求: " + request);
        RpcResponse rpcResponse = getResponse(request);
        System.out.println("返回响应: " + rpcResponse);
        
        // 发送响应并添加监听器，确保响应发送完成后再关闭通道
        channelHandlerContext.writeAndFlush(rpcResponse).addListener(future -> {
            if (future.isSuccess()) {
                System.out.println("响应发送成功，准备关闭通道");
                channelHandlerContext.close();
            } else {
                System.out.println("响应发送失败: " + future.cause().getMessage());
                channelHandlerContext.close();
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.out.println("服务端处理请求时出错: " + cause.getMessage());
        cause.printStackTrace();
        ctx.close();
    }

    private RpcResponse getResponse(RpcRequest rpcRequest){
        // 获取服务名
        String interfaceName = rpcRequest.getInterfaceName();
        System.out.println("处理服务请求: " + interfaceName);
        // 得到服务端相应实现类
        Object service = serviceProvider.getService(interfaceName);
        Method method;
        try{
            // 获取方法对象
            method = service.getClass().getMethod(
                    rpcRequest.getMethodName(), rpcRequest.getParamsType()
            );
            System.out.println("调用方法: " + rpcRequest.getMethodName());
            //通过反射调用方法
            Object invoke = method.invoke(service, rpcRequest.getParams());
            System.out.println("方法调用成功，返回结果: " + invoke);
            
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
            e.printStackTrace();
            System.out.println("服务端执行方法时出错: " + e.getMessage());
            return RpcResponse.fail(500,"服务端执行方法时出错");
        }
    }
}
