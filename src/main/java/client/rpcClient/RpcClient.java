package client.rpcClient;

import common.message.RpcRequest;
import common.message.RpcResponse;

import java.util.concurrent.CompletableFuture;

/**
 * RPC客户端接口 - 支持异步调用
 */
public interface RpcClient {
    /**
     * 检查服务是否支持重试
     */
    boolean checkRetry(String serviceName);

    /**
     * 同步发送RPC请求（已废弃，建议使用sendRequestAsync）
     * 
     * @deprecated 使用 sendRequestAsync 代替
     */
    @Deprecated
    RpcResponse sendRequest(RpcRequest request);

    /**
     * 异步发送RPC请求
     * 
     * @param request RPC请求
     * @return CompletableFuture包装的响应
     */
    CompletableFuture<RpcResponse> sendRequestAsync(RpcRequest request);

    /**
     * 报告服务状态
     */
    String reportServiceStatus();
}
