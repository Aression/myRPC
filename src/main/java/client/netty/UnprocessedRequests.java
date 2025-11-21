package client.netty;

import common.message.RpcResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class UnprocessedRequests {
    private static final Map<String, CompletableFuture<RpcResponse>> futureMap = new ConcurrentHashMap<>();

    public static void put(String requestId, CompletableFuture<RpcResponse> future) {
        futureMap.put(requestId, future);
    }

    public static void complete(RpcResponse rpcResponse) {
        CompletableFuture<RpcResponse> future = futureMap.remove(rpcResponse.getRequestId());
        if (future != null) {
            future.complete(rpcResponse);
        }
    }

    public static void fail(String requestId, Throwable t) {
        CompletableFuture<RpcResponse> future = futureMap.remove(requestId);
        if (future != null) {
            future.completeExceptionally(t);
        }
    }
}
