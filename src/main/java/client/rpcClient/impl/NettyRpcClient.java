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
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class NettyRpcClient implements RpcClient {
    private static final AttributeKey<RpcResponse> RESPONSE_KEY = AttributeKey.valueOf("RPCResponse");
    private static final Bootstrap bootstrap;
    private static final EventLoopGroup eventLoopGroup;
    private ServiceCenter serviceCenter;
    private static final int TIMEOUT = 5;

    public NettyRpcClient(){
        this.serviceCenter = new ZKServiceCenter();
    }

    static{
        eventLoopGroup = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new NettyClientInitializer());
    }

    @Override
    public RpcResponse sendRequest(RpcRequest request) {
        // 从zookeeper注册中心获取host和port
        InetSocketAddress addr = serviceCenter.serviceDiscovery(request.getInterfaceName());
        if(addr == null) {
            System.out.println("无法找到服务 " + request.getInterfaceName() + " 的可用节点");
            return RpcResponse.fail(404, "服务不可用");
        }

        String host = addr.getHostName();
        int port = addr.getPort();
        System.out.println("连接到服务节点: " + host + ":" + port);

        Channel channel = null;
        try {
            // 连接服务端
            ChannelFuture channelFuture = bootstrap.connect(host, port).sync();
            channel = channelFuture.channel();
            
            // 发送请求
            channel.writeAndFlush(request);
            System.out.println("已发送请求: " + request);
            
            // 等待响应，设置超时时间
            if (!channel.closeFuture().await(TIMEOUT, TimeUnit.SECONDS)) {
                System.out.println("等待响应超时");
                return RpcResponse.fail(504, "请求超时");
            }
            
            // 获取响应
            RpcResponse response = channel.attr(RESPONSE_KEY).get();
            if(response == null) {
                System.out.println("未收到服务端响应");
                return RpcResponse.fail(500, "未收到响应");
            }
            
            System.out.println("成功获取响应: " + response);
            return response;
        } catch (InterruptedException e) {
            System.out.println("连接服务失败: " + e.getMessage());
            return RpcResponse.fail(500, "连接失败");
        } finally {
            // 只关闭当前通道，不关闭事件循环组
            if (channel != null) {
                channel.close();
            }
        }
    }
}
