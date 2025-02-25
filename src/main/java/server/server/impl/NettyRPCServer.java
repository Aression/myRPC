package server.server.impl;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.AllArgsConstructor;
import server.netty.initializer.NettyServerInitializer;
import server.provider.ServiceProvider;
import server.server.RpcServer;

/**
 * NettyRPCServer 是基于 Netty 实现的 RPC 服务器。
 * 它负责启动一个 Netty 服务器，监听指定端口，并处理客户端的 RPC 请求。
 */
@AllArgsConstructor // Lombok 注解，自动生成包含所有字段的构造函数
public class NettyRPCServer implements RpcServer {

    // 服务提供者，用于注册和查找 RPC 服务
    private ServiceProvider serviceProvider;

    /**
     * 启动 Netty 服务器，监听指定端口。
     *
     * @param port 服务器监听的端口号
     */
    @Override
    public void start(int port) {
        /*
         * Netty 使用两个线程组来分别处理连接和数据处理任务，以提升服务器的并发性能。
         * - bossGroup: 负责客户端连接的建立。它侦听指定端口，并为每个连接创建新的 Channel。
         *              通常线程数较少，因为连接建立是轻量级操作。
         * - workGroup: 负责客户端数据的读写。连接建立后，它处理网络读写、消息编解码和业务逻辑。
         *              通常线程数较多，以支持高并发。
         */
        NioEventLoopGroup bossGroup = new NioEventLoopGroup(); // 创建 bossGroup
        NioEventLoopGroup workGroup = new NioEventLoopGroup(); // 创建 workGroup
        System.out.println("Netty服务端已启用");

        try {
            // 创建 ServerBootstrap 实例，用于配置和启动服务器
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workGroup) // 设置 bossGroup 和 workGroup
                    .channel(NioServerSocketChannel.class) // 使用 NIO 模式
                    .childHandler(new NettyServerInitializer(serviceProvider)); // 设置 Channel 的初始化器

            // 绑定端口并启动服务器
            ChannelFuture channelFuture = serverBootstrap.bind(port).sync();
            System.out.println("服务器已启动，监听端口: " + port);

            // 等待服务器 Channel 关闭（阻塞当前线程，直到服务器关闭）
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            // 处理中断异常
            e.printStackTrace();
        } finally {
            // 优雅关闭线程组，释放资源
            bossGroup.shutdownGracefully();
            workGroup.shutdownGracefully();
            System.out.println("服务器已关闭");
        }
    }

    /**
     * 停止服务器（当前未实现具体逻辑）。
     */
    @Override
    public void stop() {
        // 当前未实现具体逻辑，可以根据需要添加关闭服务器的代码
        System.out.println("服务器停止方法被调用，但未实现具体逻辑");
    }
}
