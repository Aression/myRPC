package client.proxy;

import client.proxy.breaker.Breaker;
import client.proxy.breaker.BreakerProvider;
import client.retry.GuavaRetry;
import client.rpcClient.RpcClient;
import com.alibaba.fastjson.JSON;
import common.message.RpcRequest;
import common.message.RpcResponse;
import common.result.Result;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 使用 LambdaMetafactory 优化性能的 RPC 代理工厂
 */
public class LambdaMetafactoryProxyFactory {

    private static final Logger logger = LoggerFactory.getLogger(LambdaMetafactoryProxyFactory.class);

    private final RpcClient rpcClient;
    private final BreakerProvider breakerProvider;
    private final GuavaRetry guavaRetry = new GuavaRetry();

    // 缓存每个方法对应的快速调用器
    private final Map<Method, Function<Object[], Object>> methodCache = new ConcurrentHashMap<>();

    // 构造函数使用依赖注入，更加灵活
    public LambdaMetafactoryProxyFactory(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
        this.breakerProvider = new BreakerProvider();
    }

    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> interfaceClass) {
        // 创建一个 InvocationHandler，它的核心职责是为方法创建并缓存 Lambda 执行器
        InvocationHandler handler = (proxy, method, args) -> {
            // 对 Object 的默认方法（如 toString, hashCode）直接执行，不走 RPC
            if (Object.class.equals(method.getDeclaringClass())) {
                return method.invoke(this, args);
            }
            // 从缓存获取执行器，如果不存在则创建
            return methodCache.computeIfAbsent(method, this::createMethodExecutor)
                              .apply(args);
        };

        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class[]{interfaceClass},
                handler
        );
    }

    /**
     * 为指定方法创建一个高性能的执行器
     */
    private Function<Object[], Object> createMethodExecutor(Method method) {
        try {
            // 找到我们实际要执行的业务逻辑方法，即 handleRpcCall
            MethodHandle handleRpcCallHandle = MethodHandles.lookup()
                    .findVirtual(LambdaMetafactoryProxyFactory.class, "handleRpcCall", MethodType.methodType(Object.class, Method.class, Object[].class));

            // 将 this（当前工厂实例）和 method（被代理的方法）作为参数绑定到 MethodHandle 上
            MethodHandle boundHandle = handleRpcCallHandle.bindTo(this).bindTo(method);

            // 使用 LambdaMetafactory 创建一个实现了 Function<Object[], Object> 接口的 lambda 实例
            // 这个 lambda 实例在调用 apply(args) 时，会直接调用我们绑定好的 boundHandle.invoke(args)
            return (Function<Object[], Object>) java.lang.invoke.LambdaMetafactory.metafactory(
                    MethodHandles.lookup(),
                    "apply", // 要实现的方法名 (Function.apply)
                    MethodType.methodType(Function.class), // 工厂签名
                    MethodType.methodType(Object.class, Object.class), // 实际方法签名 (Object apply(Object))
                    boundHandle, // 目标 MethodHandle
                    MethodType.methodType(Object.class, Object[].class) // 捕获后的方法签名
            ).getTarget().invokeExact();

        } catch (Throwable e) {
            throw new RuntimeException("Failed to create method executor for " + method.getName(), e);
        }
    }
    
    /**
     * 统一的 RPC 调用处理逻辑，这个方法将被 MethodHandle 指向
     * @param method 被调用的接口方法
     * @param args 方法参数
     * @return RPC 调用结果
     */
    public Object handleRpcCall(Method method, Object[] args) {
        // --- 这部分逻辑就是你原来 invoke 方法的核心 ---
        long startTime = System.currentTimeMillis();
        RpcRequest request = RpcRequest.builder()
                .interfaceName(method.getDeclaringClass().getName())
                .methodName(method.getName())
                .params(args)
                .paramsType(method.getParameterTypes())
                .timestamp(System.currentTimeMillis())
                .build();
        
        Breaker breaker = breakerProvider.getBreaker(method.getName());
        if (!breaker.allowRequest()) {
            logger.warn("熔断器触发，方法 {} 被拒绝", method.getName());
            return createFallbackResult(method.getGenericReturnType(), 429, "服务熔断中，请稍后重试");
        }

        RpcResponse response;
        try {
            if (rpcClient.checkRetry(request.getInterfaceName())) {
                response = guavaRetry.sendServiceWithRetry(request, rpcClient);
            } else {
                response = rpcClient.sendRequest(request);
            }
        } catch (Exception e) {
            // 记录异常并触发熔断
            logger.error("RPC call failed for method {}", method.getName(), e);
            breaker.recordFailure();
            return createFallbackResult(method.getGenericReturnType(), 500, "RPC 调用异常: " + e.getMessage());
        }

        if (response == null) {
            logger.warn("RPC服务调用失败，返回空响应");
            breaker.recordFailure();
            return createFallbackResult(method.getGenericReturnType(), 500, "服务返回空响应");
        }

        updateBreakerStatus(breaker, response.getCode());
        long elapsedTime = System.currentTimeMillis() - startTime;
        logger.info("方法 {} 执行耗时: {}ms, 状态码: {}", method.getName(), elapsedTime, response.getCode());
        
        return processResponse(method.getGenericReturnType(), response);
    }
    
    // --- 以下是辅助方法，从原来的 invoke 方法中提取和优化 ---

    private void updateBreakerStatus(Breaker breaker, int statusCode) {
        if (statusCode >= 500 || statusCode == 429) { // 仅对服务端错误和熔断请求计为失败
            breaker.recordFailure();
        } else {
            breaker.recordSuccess(); // 其他（包括4xx客户端错误）都算作网络成功
        }
    }

    private Object processResponse(Type returnType, RpcResponse response) {
        if (returnType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) returnType;
            if (parameterizedType.getRawType() == Result.class) {
                Type actualTypeArgument = parameterizedType.getActualTypeArguments()[0];
                Object data = response.getData();

                // 优化：如果 data 类型已经匹配，直接使用，避免不必要的 JSON 转换
                if (data != null && actualTypeArgument instanceof Class) {
                    Class<?> dataClass = (Class<?>) actualTypeArgument;
                    if (!dataClass.isInstance(data)) {
                        // 类型不匹配时再尝试转换
                        String jsonStr = JSON.toJSONString(data);
                        data = JSON.parseObject(jsonStr, dataClass);
                    }
                }
                
                if (response.getCode() == 200) {
                    return Result.success(data, response.getMessage());
                } else {
                    return Result.fail(response.getCode(), response.getMessage());
                }
            }
        }
        // 对于非 Result 类型的返回，直接返回数据
        return response.getData();
    }
    
    /**
     * 当请求被熔断或发生异常时，根据方法的返回类型创建一个优雅的失败/降级结果
     */
    private Object createFallbackResult(Type returnType, int code, String message) {
        if (returnType instanceof ParameterizedType && ((ParameterizedType) returnType).getRawType() == Result.class) {
            return Result.fail(code, message);
        }
        // 如果返回类型不是 Result<T>，无法创建有意义的降级结果，只能返回 null 或抛出异常
        // 在微服务实践中，推荐所有可能失败的服务调用都返回统一的 Result<T> 封装
        logger.warn("无法为非Result类型的返回创建降级结果，将返回null。方法返回类型: {}", returnType.getTypeName());
        return null; 
    }
}
