package server;

import common.service.UserService;
import common.service.impl.UserServiceImpl;
import server.provider.ServiceProvider;
import server.server.RpcServer;
import server.server.impl.NettyRPCServer;

public class TestServer {
    public static void main(String[] args) {
        //服务注册中心
        ServiceProvider serviceProvider = new ServiceProvider();

        //实例化服务并进行注册
        UserService userService = new UserServiceImpl();
        serviceProvider.provideServiceInterface(userService);

        //实例化服务端并启动
        RpcServer rpcServer = new NettyRPCServer(serviceProvider);
        rpcServer.start(9999);
    }
}
