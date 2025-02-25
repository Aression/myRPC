package client.proxy;

import client.IOClient;
import common.message.RpcRequest;
import common.message.RpcResponse;
import lombok.AllArgsConstructor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

@AllArgsConstructor
public class ClientProxy implements InvocationHandler {
    // 通过反射机制将传入参数service接口的class对象封装为一个request
    private String host;
    private int port;

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        //执行request封装，通过宣称的class信息构建一个request并与服务器通信获取回应，返回数据
        RpcRequest request = RpcRequest.builder()
                .interfaceName(method.getDeclaringClass().getName())
                .methodName(method.getName())
                .params(args)
                .paramsType(method.getParameterTypes()).build();
        RpcResponse response = IOClient.sendRequest(host,port,request);
        return response!=null?response.getData():null;
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
