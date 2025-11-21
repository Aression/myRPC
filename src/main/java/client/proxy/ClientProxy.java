package client.proxy;

import client.retry.GuavaRetry;
import client.rpcClient.RpcClient;
import common.message.RpcRequest;
import common.message.RpcResponse;
import common.result.Result;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * JDK动态代理类，负责拦截接口方法调用并转发给 RpcClient（纯异步模式）
 * 特性：
 * - 仅支持异步调用，所有接口方法必须返回 CompletableFuture<T>
 * - 资源复用：共享 RpcClient 实例，避免创建多个 Netty 线程池
 * - 泛型自动转换：支持复杂类型的自动转换
 */
public class ClientProxy implements InvocationHandler {
    private static final Logger logger = LoggerFactory.getLogger(ClientProxy.class);

    private final RpcClient rpcClient;
    @SuppressWarnings("unused") // 保留用于未来的异步重试功能
    private final GuavaRetry retryStrategy;

    // 专用线程池用于异步处理响应，避免阻塞 Netty EventLoop
    private static final java.util.concurrent.ExecutorService responseExecutor;

    static {
        int cores = Runtime.getRuntime().availableProcessors();
        responseExecutor = new java.util.concurrent.ThreadPoolExecutor(
                cores * 2,
                cores * 4,
                60L,
                java.util.concurrent.TimeUnit.SECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(5000),
                r -> new Thread(r, "ClientResponseProcessor-" + System.nanoTime()),
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
    }

    // 建议使用此构造函数，传入共享的 rpcClient 实例，避免每个 Proxy 创建独立的 Netty 线程池
    public ClientProxy(RpcClient rpcClient, GuavaRetry retryStrategy) {
        this.rpcClient = rpcClient;
        this.retryStrategy = retryStrategy;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 1. 排除 Object 类的方法（如 toString, equals, hashCode）
        if (Object.class.equals(method.getDeclaringClass())) {
            return method.invoke(this, args);
        }

        long startTime = System.currentTimeMillis();

        // 2. 构建 RPC 请求对象
        RpcRequest request = buildRpcRequest(method, args);

        // 3. 仅支持异步调用 (返回值必须为 CompletableFuture)
        if (!CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
            throw new UnsupportedOperationException(
                    "ClientProxy 仅支持异步调用，方法返回类型必须为 CompletableFuture<T>: "
                            + method.getDeclaringClass().getName() + "." + method.getName());
        }

        // 4. 执行异步调用
        common.util.PerformanceTracker.startTracking(request.getRequestId());
        common.util.PerformanceTracker.record(request.getRequestId(), "proxy_build_end");

        // 使用体thenApplyAsync 在专用线程池执行，避免阻塞 Netty EventLoop
        return rpcClient.sendRequestAsync(request).thenApplyAsync(response -> {
            common.util.PerformanceTracker.record(request.getRequestId(), "response_process_start");
            try {
                // 异步回调中处理类型转换（获取 Future 的泛型参数类型）
                Type futureGenericType = getFutureGenericType(method);
                return processResponse(response, futureGenericType);
            } catch (Exception e) {
                logger.error("异步响应处理异常", e);
                throw new RuntimeException(e);
            } finally {
                common.util.PerformanceTracker.record(request.getRequestId(), "response_process_end");
                logPerformance(method, startTime);
            }
        }, responseExecutor); // 指定专用线程池
    }

    /**
     * 构建请求对象
     */
    private RpcRequest buildRpcRequest(Method method, Object[] args) {
        return RpcRequest.builder()
                .interfaceName(method.getDeclaringClass().getName())
                .methodName(method.getName())
                .params(args)
                .paramsType(method.getParameterTypes())
                .timestamp(System.currentTimeMillis())
                .requestId(UUID.randomUUID().toString())
                .build();
    }

    /**
     * 处理 RPC 响应并转换类型
     */
    private Object processResponse(RpcResponse response, Type returnType) {
        if (response == null) {
            throw new RuntimeException("RPC服务调用失败，无响应");
        }

        if (response.getCode() != 200) {
            if (isReturnTypeResult(returnType)) {
                return Result.fail(response.getCode(), response.getMessage());
            }
            throw new RuntimeException("RPC调用错误: " + response.getMessage());
        }

        Object data = response.getData();

        if (returnType == void.class || returnType == Void.class) {
            return null;
        }

        if (data == null) {
            return null;
        }

        if (isReturnTypeResult(returnType)) {
            return convertToResult(data, returnType, response.getMessage());
        }

        return convertDataToType(data, returnType);
    }

    private Object convertToResult(Object data, Type returnType, String msg) {
        if (returnType instanceof ParameterizedType) {
            Type[] typeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
            if (typeArguments.length > 0) {
                Type realDataType = typeArguments[0];
                Object convertedData = convertDataToType(data, realDataType);
                return Result.success(convertedData, msg != null ? msg : "操作成功");
            }
        }
        return Result.success(data, msg);
    }

    private Object convertDataToType(Object data, Type targetType) {
        if (data == null)
            return null;

        if (targetType instanceof Class<?>) {
            Class<?> targetClass = (Class<?>) targetType;
            if (targetClass.isInstance(data)) {
                return data;
            }
        }

        try {
            String jsonString = JSON.toJSONString(data);
            return JSON.parseObject(jsonString, targetType);
        } catch (Exception e) {
            logger.error("类型转换失败: source={} target={}", data.getClass().getName(), targetType.getTypeName(), e);
            throw new RuntimeException("RPC数据类型转换异常", e);
        }
    }

    private boolean isReturnTypeResult(Type type) {
        if (type instanceof ParameterizedType) {
            return ((ParameterizedType) type).getRawType() == Result.class;
        }
        return type == Result.class;
    }

    private Type getFutureGenericType(Method method) {
        Type returnType = method.getGenericReturnType();
        if (returnType instanceof ParameterizedType) {
            Type[] args = ((ParameterizedType) returnType).getActualTypeArguments();
            if (args.length > 0) {
                return args[0];
            }
        }
        return Object.class;
    }

    private void logPerformance(Method method, long startTime) {
        long elapsedTime = System.currentTimeMillis() - startTime;
        if (elapsedTime > 1000 || logger.isDebugEnabled()) {
            logger.info("RPC Call [{}.{}] completed in {}ms",
                    method.getDeclaringClass().getSimpleName(), method.getName(), elapsedTime);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> clazz) {
        return (T) Proxy.newProxyInstance(
                clazz.getClassLoader(),
                new Class[] { clazz },
                this);
    }
}
