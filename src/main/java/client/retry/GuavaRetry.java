package client.retry;

import com.github.rholder.retry.*;

import client.rpcClient.RpcClient;

// TODO：不清楚这里应该用protobuf编译的rpc类还是原始类？
import common.message.RpcRequest;
import common.message.RpcResponse;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class GuavaRetry {
    private RpcClient rpcClient;
    public RpcResponse sendServiceWithRetry(RpcRequest rpcRequest, RpcClient rpcClient){
        this.rpcClient = rpcClient;
        Retryer<RpcResponse> retryer = RetryerBuilder.<RpcResponse>newBuilder()
                //无论出现什么异常，都进行重试
                .retryIfException()
                //返回结果为5开头的连接错误时进行重试
                .retryIfResult(response -> response.getCode() >= 500 && response.getCode() < 600)
                //重试等待策略：等待 2s 后再进行重试
                .withWaitStrategy(WaitStrategies.fixedWait(2, TimeUnit.SECONDS))
                //重试停止策略：重试达到 3 次
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .withRetryListener(new RetryListener() {
                    @Override
                    public <V> void onRetry(Attempt<V> attempt) {
                        System.out.println("RetryListener: 第" + attempt.getAttemptNumber() + "次调用");
                    }
                })
                .build();
        try {
            return retryer.call(() -> rpcClient.sendRequest(rpcRequest));
        } catch (Exception e) {
            System.out.println("GuavaRetry: 所有重试策略均失败，请求失败");
            e.printStackTrace();
        }
        return RpcResponse.fail();
    }
}
