package server.server.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.provider.ServiceProvider;
import server.server.RpcServer;
import server.server.work.WorkThread;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class SimpleRPCServer implements RpcServer {
    private static final Logger logger = LoggerFactory.getLogger(SimpleRPCServer.class);
    private final ServiceProvider serviceProvider;

    public SimpleRPCServer(ServiceProvider serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    @Override
    public void start(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            logger.info("服务端Socket已开启...");
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(new WorkThread(socket, serviceProvider)).start();
            }
        } catch (IOException e) {
            logger.error("服务器启动失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void stop() {
        // 简单服务器不需要特殊处理
    }
}
