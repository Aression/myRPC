package client.proxy;

import client.rpcClient.RpcClient;
import client.rpcClient.impl.NettyRpcClient;
import client.rpcClient.impl.SimpleSocketRpcClient;
import common.message.RpcRequest;
import common.message.RpcResponse;
import lombok.AllArgsConstructor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

@AllArgsConstructor
public class ClientProxy implements InvocationHandler {
    private RpcClient rpcClient;
    public ClientProxy(String host,int port,int choose){
        switch (choose){
            case 0:
                rpcClient = new NettyRpcClient(host, port);
                break;
            case 1:
                rpcClient = new SimpleSocketRpcClient(host,port);
        }
    }
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        //执行request封装，通过宣称的class信息构建一个request并与服务器通信获取回应，返回数据
        RpcRequest request = RpcRequest.builder()
                .interfaceName(method.getDeclaringClass().getName())
                .methodName(method.getName())
                .params(args)
                .paramsType(method.getParameterTypes()).build();
        RpcResponse response= rpcClient.sendRequest(request);
        return response.getData();
    }

    //动态生成一个指定接口的代理对象
    public <T>T getProxy(Class<T> clazz){
        Object o = Proxy.newProxyInstance(
                clazz.getClassLoader(),
                new Class[]{clazz},
                this
        );
        return (T)o;
    }
}
