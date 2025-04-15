package server.server.impl;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.netty.initializer.NettyServerInitializer;
import server.provider.ServiceProvider;
import server.server.RpcServer;

/**
 * NettyRPCServer 是基于 Netty 实现的 RPC 服务器。
 * 它负责启动一个 Netty 服务器，监听指定端口，并处理客户端的 RPC 请求。
 */
public class NettyRPCServer implements RpcServer {
    private static final Logger logger = LoggerFactory.getLogger(NettyRPCServer.class);
    private final ServiceProvider serviceProvider;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private int port;

    public NettyRPCServer(ServiceProvider serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

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
        bossGroup = new NioEventLoopGroup(); // 创建 bossGroup
        workerGroup = new NioEventLoopGroup(); // 创建 workGroup
        try {
            // 创建 ServerBootstrap 实例，用于配置和启动服务器
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup) // 设置 bossGroup 和 workGroup
                    .channel(NioServerSocketChannel.class) // 使用 NIO 模式
                    .childHandler(new NettyServerInitializer(serviceProvider))
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            // 绑定端口并启动服务器
            logger.info("Netty服务端已启用");
            ChannelFuture channelFuture = serverBootstrap.bind(port).sync();
            logger.info("服务器已启动，监听端口: {}", port);

            // 等待服务器 Channel 关闭（阻塞当前线程，直到服务器关闭）
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            // 处理中断异常
            logger.error("服务器启动失败: {}", e.getMessage(), e);
        } finally {
            // 优雅关闭线程组，释放资源
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
            logger.info("服务器已关闭");
        }
    }

    /**
     * 停止服务器，优雅关闭所有资源。
     */
    @Override
    public void stop() {
        try {
            // 关闭所有 EventLoopGroup
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
                logger.info("已关闭 bossGroup");
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully(); 
                logger.info("已关闭 workerGroup");
            }

            // 等待线程组完全终止
            if (bossGroup != null) {
                bossGroup.terminationFuture().sync();
            }
            if (workerGroup != null) {
                workerGroup.terminationFuture().sync();
            }
            
            logger.info("服务器已完全停止");
        } catch (InterruptedException e) {
            logger.error("关闭服务器时发生错误: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }

    public int getPort() {
        return port;
    }
}
