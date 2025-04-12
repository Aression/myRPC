package client.netty.handler;

import common.message.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;

public class NettyClientHandler extends SimpleChannelInboundHandler<RpcResponse> {
    private static final AttributeKey<RpcResponse> RESPONSE_KEY = AttributeKey.valueOf("RPCResponse");

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, RpcResponse rpcResponse) throws Exception {
        System.out.println("收到服务端响应: " + rpcResponse);
        // 设置响应
        channelHandlerContext.channel().attr(RESPONSE_KEY).set(rpcResponse);
        // 关闭通道
        channelHandlerContext.channel().close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.out.println("客户端处理响应时出错: " + cause.getMessage());
        cause.printStackTrace();
        ctx.close();
    }
}
