package server.server.impl;

import server.provider.ServiceProvider;
import server.server.RpcServer;
import server.server.work.WorkThread;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPoolRPCServer implements RpcServer {
    private final ThreadPoolExecutor threadPool;
    private ServiceProvider serviceProvider;

    public ThreadPoolRPCServer(ServiceProvider serviceProvider) {
        // 定义线程池属性
        threadPool = new ThreadPoolExecutor(
                Runtime.getRuntime().availableProcessors(),
                1000,60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100)
        );
        this.serviceProvider = serviceProvider;
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
        System.out.println("多线程服务端已启动");
        try{
            ServerSocket serverSocket = new ServerSocket();
            while(true){
                Socket socket = serverSocket.accept();
                threadPool.execute(new WorkThread(socket, serviceProvider));
            }
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        // do noting
    }
}
