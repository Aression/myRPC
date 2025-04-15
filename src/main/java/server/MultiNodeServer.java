package server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import common.service.UserService;
import common.service.impl.UserServiceImpl;
import server.provider.ServiceProvider;
import server.server.RpcServer;
import server.server.impl.NettyRPCServer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MultiNodeServer {
    private static final Logger logger = LoggerFactory.getLogger(MultiNodeServer.class);
    // 服务节点端口列表
    private static final int[] PORTS = {9991, 9992, 9993, 9994, 9995};
    private final List<RpcServer> servers = new ArrayList<>();

    public void start() {
        logger.info("开始启动多个服务节点...");
        
        // 创建线程池管理服务节点
        ExecutorService executor = Executors.newFixedThreadPool(PORTS.length);

        try {
            // 启动每个服务节点
            for (int port : PORTS) {
                executor.submit(() -> {
                    try {
                        // 实例化服务
                        UserService userService = new UserServiceImpl();

                        // 在服务提供者中上线服务并注册
                        ServiceProvider serviceProvider = new ServiceProvider("127.0.0.1", port);
                        serviceProvider.provideServiceInterface(userService, true); // 已经为用户crud做了幂等性保护，所以直接上线成可重试服务

                        // 实例化服务端并启动
                        RpcServer server = new NettyRPCServer(serviceProvider);
                        servers.add(server);
                        server.start(port);

                        logger.info("服务节点已启动，监听端口: {}", port);
                    } catch (Exception e) {
                        logger.error("启动服务节点失败，端口: {}", port, e);
                    }
                });
            }

            // 等待所有服务节点启动
            while (servers.size() < PORTS.length) {
                Thread.sleep(100);
            }

            logger.info("\n所有服务节点已成功启动:");
            for (int i = 0; i < PORTS.length; i++) {
                logger.info("节点 {}: 127.0.0.1:{}", i + 1, PORTS[i]);
            }

            // 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("\n[关闭] 开始关闭所有服务节点...");
                int totalNodes = servers.size();
                int closedNodes = 0;
                
                // 按端口号顺序关闭节点
                for (int port : PORTS) {
                    for (RpcServer server : servers) {
                        if (server instanceof NettyRPCServer) {
                            NettyRPCServer nettyServer = (NettyRPCServer) server;
                            if (nettyServer.getPort() == port) {
                                try {
                                    logger.info("[关闭] 正在关闭端口 {} 的服务节点...", port);
                                    server.stop();
                                    closedNodes++;
                                    logger.info("[关闭] 端口 {} 的服务节点已关闭 ({}/{})", 
                                        port, closedNodes, totalNodes);
                                } catch (Exception e) {
                                    logger.error("[关闭] 关闭端口 {} 的服务节点时出错: {}", 
                                        port, e.getMessage());
                                }
                            }
                        }
                    }
                }
                
                // 关闭线程池
                try {
                    logger.info("[关闭] 正在关闭线程池...");
                    executor.shutdown();
                    if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                        logger.warn("[关闭] 线程池未能在指定时间内关闭，尝试强制关闭");
                        executor.shutdownNow();
                    }
                    logger.info("[关闭] 线程池已关闭");
                } catch (InterruptedException e) {
                    logger.error("[关闭] 关闭线程池时被中断: {}", e.getMessage());
                    Thread.currentThread().interrupt();
                }
                
                logger.info("[关闭] 所有服务节点已关闭");
            }));

            // 保持主线程运行
            while (true) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            logger.error("服务节点启动过程被中断: {}", e.getMessage(), e);
        } finally {
            executor.shutdown();
        }
    }

    public static void main(String[] args) {
        MultiNodeServer multiNodeServer = new MultiNodeServer();
        multiNodeServer.start();
    }
}
