package server.server.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.provider.ServiceProvider;
import server.server.RpcServer;
import server.server.work.WorkThread;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPoolRPCServer implements RpcServer {
    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolRPCServer.class);
    private final ExecutorService threadPool;
    private final ServiceProvider serviceProvider;

    public ThreadPoolRPCServer(ServiceProvider serviceProvider) {
        this.serviceProvider = serviceProvider;
        // 创建线程池
        threadPool = new ThreadPoolExecutor(
                Runtime.getRuntime().availableProcessors(),
                100,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100)
        );
    }

    public ThreadPoolRPCServer(
            ServiceProvider serviceProvider,
            int corePoolSize, int maxPoolSize, long keepAliveTime,
            TimeUnit unit,
            BlockingDeque<Runnable> workQueue
    ){
        threadPool = new ThreadPoolExecutor(
                corePoolSize, maxPoolSize, keepAliveTime, unit,
                workQueue
        );
        this.serviceProvider = serviceProvider;
    }

    @Override
    public void start(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            logger.info("多线程服务端已启动");
            while (true) {
                Socket socket = serverSocket.accept();
                threadPool.execute(new WorkThread(socket, serviceProvider));
            }
        } catch (IOException e) {
            logger.error("服务器启动失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void stop() {
        threadPool.shutdown();
    }
}
