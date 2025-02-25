package server.server.impl;

import lombok.AllArgsConstructor;
import server.provider.ServiceProvider;
import server.server.RpcServer;
import server.server.work.WorkThread;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

@AllArgsConstructor
public class SimpleRPCServer implements RpcServer {
    private ServiceProvider serviceProvider;

    @Override
    public void start(int port) {
        try{
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("服务端Socket已开启...");
            while(true){
                Socket socket = serverSocket.accept();// 侦听端口获取连接
                new Thread(
                        new WorkThread(socket, serviceProvider)
                ).start();// 创建一个新的工作线程执行处理
            }
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        //停止服务端
    }
}
