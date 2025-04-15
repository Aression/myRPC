package client.rpcClient.impl;

import client.netty.initializer.NettyClientInitializer;
import client.rpcClient.RpcClient;
import client.serviceCenter.ServiceCenter;
import client.serviceCenter.ZKServiceCenter;
import client.serviceCenter.balance.LoadBalance;
import client.serviceCenter.balance.LoadBalanceFactory;
import common.message.RpcRequest;
import common.message.RpcResponse;
import common.context.RpcRequestContext;
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
        this(LoadBalanceFactory.BalanceType.CONSISTENCY_HASH);
    }
    
    public NettyRpcClient(LoadBalanceFactory.BalanceType balanceType){
        LoadBalance loadBalance = LoadBalanceFactory.getLoadBalance(balanceType);
        this.serviceCenter = new ZKServiceCenter(loadBalance);
    }
    
    public NettyRpcClient(LoadBalance loadBalance){
        this.serviceCenter = new ZKServiceCenter(loadBalance);
    }

    public boolean checkRetry(String serviceName){
        return serviceCenter.checkRetry(serviceName);
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
        // 设置当前线程的请求上下文
        try {
            RpcRequestContext.setCurrentRequest(request);
            
            // 从zookeeper注册中心获取host和port，传入特征码
            InetSocketAddress addr = serviceCenter.serviceDiscovery(
                    request.getInterfaceName(), 
                    request.getFeatureCode());
                    
            if(addr == null) {
                System.out.println("无法找到服务 " + request.getInterfaceName() + " 的可用节点");
                return RpcResponse.fail(404, "服务不可用");
            }

            String host = addr.getHostName();
            int port = addr.getPort();
            String serverAddress = host + ":" + port;
            System.out.println("连接到服务节点: " + serverAddress);

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
                    RpcResponse timeoutResponse = RpcResponse.fail(504, "请求超时");
                    timeoutResponse.setServerAddress(serverAddress);  // 添加服务器地址
                    return timeoutResponse;
                }
                
                // 获取响应
                RpcResponse response = channel.attr(RESPONSE_KEY).get();
                if(response == null) {
                    System.out.println("未收到服务端响应");
                    RpcResponse errorResponse = RpcResponse.fail(500, "未收到响应");
                    errorResponse.setServerAddress(serverAddress);  // 添加服务器地址
                    return errorResponse;
                }
                
                // 在响应中设置服务器地址信息
                response.setServerAddress(serverAddress);
                
                System.out.println("成功获取响应: " + response);
                return response;
            } catch (InterruptedException e) {
                System.out.println("连接服务失败: " + e.getMessage());
                RpcResponse errorResponse = RpcResponse.fail(500, "连接失败");
                errorResponse.setServerAddress(serverAddress);  // 添加服务器地址
                return errorResponse;
            } finally {
                // 只关闭当前通道，不关闭事件循环组
                if (channel != null) {
                    channel.close();
                }
            }
        } finally {
            // 清除当前线程的请求上下文
            RpcRequestContext.clear();
        }
    }
}
