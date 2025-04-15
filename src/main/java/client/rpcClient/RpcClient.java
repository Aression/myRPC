package client.rpcClient;

import common.message.RpcRequest;
import common.message.RpcResponse;

public interface RpcClient {
    boolean checkRetry(String serviceName);
    RpcResponse sendRequest(RpcRequest request);
}
