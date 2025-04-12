package server;

import common.service.UserService;
import common.service.impl.UserServiceImpl;
import server.provider.ServiceProvider;
import server.server.RpcServer;
import server.server.impl.NettyRPCServer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MultiNodeServer {
    // 服务节点端口列表
    private static final int[] PORTS = {9991, 9992, 9993, 9994, 9995};

    public static void main(String[] args) {
        System.out.println("开始启动多个服务节点...");
        
        // 创建线程池管理服务节点
        ExecutorService executor = Executors.newFixedThreadPool(PORTS.length);
        List<RpcServer> servers = new ArrayList<>();

        try {
            // 启动每个服务节点
            for (int port : PORTS) {
                executor.submit(() -> {
                    try {
                        // 实例化服务
                        UserService userService = new UserServiceImpl();

                        // 在服务提供者中上线服务并注册
                        ServiceProvider serviceProvider = new ServiceProvider("127.0.0.1", port);
                        serviceProvider.provideServiceInterface(userService);

                        // 实例化服务端并启动
                        RpcServer rpcServer = new NettyRPCServer(serviceProvider);
                        servers.add(rpcServer);
                        rpcServer.start(port);

                        System.out.println("服务节点已启动，监听端口: " + port);
                    } catch (Exception e) {
                        System.err.println("启动服务节点失败，端口: " + port);
                        e.printStackTrace();
                    }
                });
            }

            // 等待所有服务节点启动
            while (servers.size() < PORTS.length) {
                Thread.sleep(100);
            }

            System.out.println("\n所有服务节点已成功启动:");
            for (int i = 0; i < PORTS.length; i++) {
                System.out.println("节点 " + (i + 1) + ": 127.0.0.1:" + PORTS[i]);
            }

            // 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n正在关闭所有服务节点...");
                for (RpcServer server : servers) {
                    try {
                        server.stop();
                    } catch (Exception e) {
                        System.err.println("关闭服务节点时出错");
                        e.printStackTrace();
                    }
                }
                executor.shutdown();
                System.out.println("所有服务节点已关闭");
            }));

            // 保持主线程运行
            while (true) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            System.err.println("服务节点启动过程被中断");
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }
    }
}
