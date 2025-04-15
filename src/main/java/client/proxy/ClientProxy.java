package client.proxy;

import client.retry.GuavaRetry;
import client.rpcClient.RpcClient;
import client.rpcClient.impl.NettyRpcClient;
import client.rpcClient.impl.SimpleSocketRpcClient;
import client.serviceCenter.ServiceCenter;
import client.serviceCenter.ZKServiceCenter;
import client.serviceCenter.balance.LoadBalanceFactory;
import common.message.RpcRequest;
import common.message.RpcResponse;
import common.result.Result;
import lombok.AllArgsConstructor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import com.alibaba.fastjson.JSON;

/*
 * JDK动态代理类，通过工厂模式接收调用的服务对象，通过invoke方法实现代理功能
 * 注意这里实现的InovationHandler，所以是动态代理
 */
@AllArgsConstructor
public class ClientProxy implements InvocationHandler {
    private RpcClient rpcClient;

    public ClientProxy(){
        rpcClient = new NettyRpcClient();
    }
    
    public ClientProxy(LoadBalanceFactory.BalanceType balanceType){
        rpcClient = new NettyRpcClient(balanceType);
    }
    
    // JDK动态代理，每一次代理对象调用方法，会经过此方法增强（反射获取request对象，发送到服务端）
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 构建请求
        RpcRequest request = RpcRequest.builder()
                .interfaceName(method.getDeclaringClass().getName())
                .methodName(method.getName())
                .params(args)
                .paramsType(method.getParameterTypes())
                .timestamp(System.currentTimeMillis())
                .build();
        
        // 为白名单服务发送带重试请求；否则直接发送不可重试请求
        RpcResponse response;
        if(rpcClient.checkRetry(request.getInterfaceName())){
            response = new GuavaRetry().sendServiceWithRetry(request, rpcClient);
        }else{
            response = rpcClient.sendRequest(request);
        }
        
        // 处理结果
        if (response == null) {
            System.out.println("服务调用失败，返回空响应");
            return null;
        }
        
        // 记录服务器信息到客户端日志，便于测试统计
        if (response.getServerAddress() != null && !response.getServerAddress().isEmpty()) {
            System.out.println("请求路由到服务器: " + response.getServerAddress());
        }
        
        // 获取方法返回类型
        Type returnType = method.getGenericReturnType();
        
        // 检查返回类型是否为Result的参数化类型
        if (returnType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) returnType;
            Type rawType = parameterizedType.getRawType();
            
            if (rawType == Result.class) {
                // 获取Result的泛型参数
                Type[] typeArguments = parameterizedType.getActualTypeArguments();
                if (typeArguments.length > 0 && typeArguments[0] instanceof Class) {
                    // 获取泛型数据类型
                    Class<?> dataClass = (Class<?>) typeArguments[0];
                    
                    // 如果响应对象本身就是Result类型
                    if (response.getData() instanceof Result) {
                        return response.getData();
                    }
                    
                    // 否则需要将服务返回的数据封装到Result中
                    Object data = response.getData();
                    
                    // 类型不匹配时尝试转换
                    if (data != null && !dataClass.isInstance(data)) {
                        // 尝试使用JSON方式转换
                        String jsonStr = JSON.toJSONString(data);
                        data = JSON.parseObject(jsonStr, dataClass);
                    }
                    
                    // 创建并返回Result对象
                    if (response.getCode() == 200) {
                        Result<Object> result = Result.success(data, response.getMessage() != null ? response.getMessage() : "操作成功");
                        // 如果有服务器地址信息，将其保存到消息前缀
                        if (response.getServerAddress() != null && !response.getServerAddress().isEmpty()) {
                            result.setMessage("[" + response.getServerAddress() + "] " + result.getMessage());
                        }
                        return result;
                    } else {
                        Result<Object> result = Result.fail(response.getCode(), response.getMessage());
                        // 如果有服务器地址信息，将其保存到消息前缀
                        if (response.getServerAddress() != null && !response.getServerAddress().isEmpty()) {
                            result.setMessage("[" + response.getServerAddress() + "] " + result.getMessage());
                        }
                        return result;
                    }
                }
            }
        }
        
        // 对于非Result类型的返回，直接返回响应数据
        return response.getData();
    }
    
    // 获取代理对象
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> clazz) {
        return (T) Proxy.newProxyInstance(
                clazz.getClassLoader(),
                new Class[]{clazz},
                this
        );
    }
}
