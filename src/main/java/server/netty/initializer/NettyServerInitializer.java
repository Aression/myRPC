package server.netty.initializer;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import lombok.AllArgsConstructor;
import server.netty.handler.NettyServerHandler;
import server.provider.ServiceProvider;

@AllArgsConstructor
public class NettyServerInitializer extends ChannelInitializer<SocketChannel> {
    private ServiceProvider serviceProvider;
    @Override
    protected void initChannel(SocketChannel ch) {
        /*
        Channel：通信基本单元
        Pipeline：用于处理消息的责任链，包含一系列Handler，负责不同操作

         */
        ChannelPipeline pipeline = ch.pipeline();
        // 通过设计消息格式解决沾包问题
        pipeline.addLast(
                new LengthFieldBasedFrameDecoder(
                        Integer.MAX_VALUE, //最大帧长度
                        0,4,//长度字段的起始位置和长度
                        0,4// 去掉长度字段后，实际数据的偏移量
                )
        );
        // 计算当前待发送消息长度，写入前四个字节
        pipeline.addLast(new LengthFieldPrepender(4));
        // 通过java自带序列化方式编码对象
        pipeline.addLast(new ObjectEncoder());
        // 解码器，通过接收到的类名解析出相应java类并将字节流转到对应对象
        pipeline.addLast(new ObjectDecoder(s -> Class.forName(s)));
        // 业务逻辑处理器
        pipeline.addLast(new NettyServerHandler(serviceProvider));
    }
}
