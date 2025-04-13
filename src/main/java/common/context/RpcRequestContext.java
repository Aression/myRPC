package common.context;

import common.message.RpcRequest;

/**
 * RPC请求上下文，用于在线程中保存当前处理的请求
 */
public class RpcRequestContext {
    private static final ThreadLocal<RpcRequest> REQUEST_THREAD_LOCAL = new ThreadLocal<>();
    
    /**
     * 设置当前线程的RPC请求
     * @param request 当前请求
     */
    public static void setCurrentRequest(RpcRequest request) {
        REQUEST_THREAD_LOCAL.set(request);
    }
    
    /**
     * 获取当前线程的RPC请求
     * @return 当前请求
     */
    public static RpcRequest getCurrentRequest() {
        return REQUEST_THREAD_LOCAL.get();
    }
    
    /**
     * 清除当前线程的RPC请求
     */
    public static void clear() {
        REQUEST_THREAD_LOCAL.remove();
    }
} 