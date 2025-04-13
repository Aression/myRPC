package server.netty.initializer;

import common.Decoder;
import common.Encoder;
import common.serializer.impl.JsonSerializer;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import lombok.AllArgsConstructor;
import server.netty.handler.NettyServerHandler;
import server.provider.ServiceProvider;

@AllArgsConstructor
public class NettyServerInitializer extends ChannelInitializer<SocketChannel> {
    private ServiceProvider serviceProvider;
    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        // 使用自定义编解码器
        // 编解码器内部通过规约写入流的顺序构建协议体
        pipeline.addLast(new Decoder());
        pipeline.addLast(new Encoder(2));// protobuf
        // 业务逻辑处理
        pipeline.addLast(new NettyServerHandler(serviceProvider));
    }
}
