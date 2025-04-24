package client.proxy;

import client.proxy.breaker.Breaker;
import client.proxy.breaker.BreakerProvider;
import client.retry.GuavaRetry;
import client.rpcClient.RpcClient;
import client.rpcClient.impl.NettyRpcClient;
import client.serviceCenter.balance.LoadBalance;
import common.message.RpcRequest;
import common.message.RpcResponse;
import common.result.Result;
import common.util.HashUtil;
import lombok.AllArgsConstructor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;

import org.slf4j.*;

import com.alibaba.fastjson.JSON;

/*
 * JDK动态代理类，通过工厂模式接收调用的服务对象，通过invoke方法实现代理功能
 * 注意这里实现的InovationHandler，所以是动态代理
 */
@AllArgsConstructor
public class ClientProxy implements InvocationHandler {
    private static final Logger logger = LoggerFactory.getLogger(ClientProxy.class);

    private RpcClient rpcClient;
    private BreakerProvider breakerProvider;

    public ClientProxy(){
        rpcClient = new NettyRpcClient();
        breakerProvider = new BreakerProvider();
    }
    
    public ClientProxy(LoadBalance.BalanceType balanceType){
        rpcClient = new NettyRpcClient(balanceType);
        breakerProvider = new BreakerProvider();
    }

    // JDK动态代理，每一次代理对象调用方法，会经过此方法增强（反射获取request对象，发送到服务端）
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 构建请求并记录开始时间
        long startTime = System.currentTimeMillis();
        RpcRequest request = RpcRequest.builder()
                .interfaceName(method.getDeclaringClass().getName())
                .methodName(method.getName())
                .params(args)
                .paramsType(method.getParameterTypes())
                .timestamp(System.currentTimeMillis())
                .build();

        // 获取方法对应熔断器
        Breaker breaker = breakerProvider.getBreaker(method.getName());
        if (!breaker.allowRequest()) {
            logger.warn("熔断器触发，方法 {} 被拒绝", method.getName());
            return Result.fail(429, "服务熔断中，请稍后重试");
        }

        // 为白名单服务发送带重试请求；否则直接发送不可重试请求
        RpcResponse response;
        if(rpcClient.checkRetry(request.getInterfaceName())){
            response = new GuavaRetry().sendServiceWithRetry(request, rpcClient);
        }else{
            response = rpcClient.sendRequest(request);
        }
        
        // 处理结果
        if (response == null) {
            logger.warn("RPC服务调用失败，返回空响应");
            return null;
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
                    if(response.getCode()==200) return Result.success(data, response.getMessage() != null ? response.getMessage() : "操作成功");
                    else return Result.fail(response.getCode(), response.getMessage());
                }
            }
        }
        updateBreakerStatus(breaker, response.getCode());
        
        // 记录请求耗时
        long elapsedTime = System.currentTimeMillis() - startTime;
        logger.info("方法 {} 执行耗时: {}ms", method.getName(), elapsedTime);
        
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

    private void updateBreakerStatus(Breaker breaker, int statusCode) {
        if (statusCode / 100 == 5 || statusCode == 429) {
            breaker.recordFailure();
        } else if (statusCode / 100 == 2 || statusCode / 100 == 4) {
            breaker.recordSuccess();
        }
    }

    public String reportServiceStatus(){
        return rpcClient.reportServiceStatus();
    }
}
