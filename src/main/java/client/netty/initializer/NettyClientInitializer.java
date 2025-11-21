package client.netty.initializer;

import client.netty.handler.NettyClientHandler;
import common.Decoder;
import common.Encoder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

public class NettyClientInitializer extends ChannelInitializer<SocketChannel> {

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        // 使用 LengthFieldBasedFrameDecoder 处理粘包/拆包
        // maxFrameLength: 1MB
        // lengthFieldOffset: 4 (消息类型2字节 + 序列化类型2字节)
        // lengthFieldLength: 4 (数据长度4字节)
        // lengthAdjustment: 0
        // initialBytesToStrip: 0 (保留所有头部信息给Decoder处理)
        pipeline.addLast(new io.netty.handler.codec.LengthFieldBasedFrameDecoder(1024 * 1024, 4, 4, 0, 0));

        // 使用自定义编解码器
        // 编解码器内部通过规约写入流的顺序构建协议体
        pipeline.addLast(new Decoder());
        pipeline.addLast(new Encoder(2)); // protobuf
        // 业务逻辑处理
        pipeline.addLast(new NettyClientHandler());
    }
}
