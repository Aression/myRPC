package client.netty.handler;

import common.message.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;

public class NettyClientHandler extends SimpleChannelInboundHandler<RpcResponse> {

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, RpcResponse rpcResponse) throws Exception {
        // 为rpcResponse设置别名并绑定到当前属性中，允许后续逻辑通过channel获取response
        AttributeKey<RpcResponse> key = AttributeKey.valueOf("RPCResponse");
        channelHandlerContext.channel().attr(key).set(rpcResponse);
        channelHandlerContext.channel().close(); //接收到响应后主动关闭当前连接（短连接模式）
    }
}
