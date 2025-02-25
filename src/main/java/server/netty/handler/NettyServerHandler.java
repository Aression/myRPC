package server.netty.handler;

import common.message.RpcRequest;
import common.message.RpcResponse;
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
        RpcResponse rpcResponse = getResponse(request);
        channelHandlerContext.writeAndFlush(rpcResponse);
        channelHandlerContext.close();
    }

    private RpcResponse getResponse(RpcRequest rpcRequest){
        // 获取服务名
        String interfaceName = rpcRequest.getInterfaceName();
        // 得到服务端相应实现类
        Object service = serviceProvider.getService(interfaceName);
        Method method;
        try{
            // 获取方法对象
            method = service.getClass().getMethod(
                    rpcRequest.getMethodName(), rpcRequest.getParamsType()
            );
            //通过反射调用方法
            Object invoke = method.invoke(service, rpcRequest.getParams());
            return RpcResponse.success(invoke);
        }catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // 找不到请求的方法or方法无法访问or方法执行过程中出错
            e.printStackTrace();
            System.out.println("服务端执行方法时出错");
            return RpcResponse.fail();
        }
    }
}
