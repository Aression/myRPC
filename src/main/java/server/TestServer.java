package server;

import common.service.UserService;
import common.service.impl.UserServiceImpl;
import server.provider.ServiceProvider;
import server.server.RpcServer;
import server.server.impl.NettyRPCServer;

public class TestServer {
    public static void main(String[] args) {
        //实例化服务
        UserService userService = new UserServiceImpl();

        //在服务提供者中上线服务并注册
        ServiceProvider serviceProvider = new ServiceProvider("127.0.0.1",9999);
        serviceProvider.provideServiceInterface(userService);

        //实例化服务端并启动
        RpcServer rpcServer = new NettyRPCServer(serviceProvider);
        rpcServer.start(9999);
    }
}
