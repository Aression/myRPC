package client.netty.initializer;

import client.netty.handler.NettyClientHandler;
import common.Decoder;
import common.Encoder;
import common.serializer.impl.JsonSerializer;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

public class NettyClientInitializer extends ChannelInitializer<SocketChannel> {

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        // 使用自定义编解码器
        // 编解码器内部通过规约写入流的顺序构建协议体
        pipeline.addLast(new Decoder());
        pipeline.addLast(new Encoder(0)); // 使用JSON序列化
        // 业务逻辑处理
        pipeline.addLast(new NettyClientHandler());
    }
}
