package client.rpcClient.impl;

import client.netty.initializer.NettyClientInitializer;
import client.rpcClient.RpcClient;
import client.serviceCenter.ServiceCenter;
import client.serviceCenter.ZKServiceCenter;
import common.message.RpcRequest;
import common.message.RpcResponse;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import lombok.AllArgsConstructor;

import java.net.InetSocketAddress;


public class NettyRpcClient implements RpcClient {
    private String host;
    private int port;
    // Netty相关配置
    private static final Bootstrap bootstrap; // Netty用于启动客户端的对象，负责设置与服务器的连接配置
    /*
    Netty内置线程池，用于处理I/O操作。
    初始化为NioSocketChannel，基于非阻塞I/O(NIO)实现，适合C/S架构通信。
     */
    private static final EventLoopGroup eventLoopGroup;

    //服务中心
    private ServiceCenter serviceCenter;
    public NettyRpcClient(){
        this.serviceCenter=new ZKServiceCenter();
    }

    //Netty初始化
    static{
        eventLoopGroup = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.group(eventLoopGroup).channel(NioSocketChannel.class)
                .handler(new NettyClientInitializer());
    }

    @Override
    public RpcResponse sendRequest(RpcRequest request) {
        // 从zookeeper注册中心获取host和port
        InetSocketAddress addr = serviceCenter.serviceDiscovery(request.getInterfaceName());
        String host = addr.getHostName();
        int port = addr.getPort();

        try{
            // 创建一个channelFuture对象，从给定ip端口获取连接。sync表示方法堵塞直到获得连接。
            // channelFuture是异步操作的结果，表示确认连接过程完成（成功或失败）
            ChannelFuture channelFuture = bootstrap.connect(host,port).sync();
            // 从ChannelFuture获取连接单位
            Channel channel = channelFuture.channel();
            //发送数据
            channel.writeAndFlush(request);
            //堵塞直到获得结果
            channel.closeFuture().sync();

            /*
            获取特定名称下channel中的内容
            目前是堵塞获取，也可以通过添加监听器来进行异步获取
             */
            AttributeKey<RpcResponse> key = AttributeKey.valueOf("RPCResponse");
            RpcResponse rpcResponse = channel.attr(key).get();

            System.out.println("Netty Client Get Response:"+rpcResponse);
            return rpcResponse;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }
}
