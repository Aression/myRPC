package common.service;

import java.util.concurrent.CompletableFuture;

/**
 * Echo服务接口 - 异步版本
 * 所有方法返回CompletableFuture以支持纯异步调用
 */
public interface EchoService {
    /**
     * 异步Echo方法
     * 
     * @param message 要回显的消息
     * @return CompletableFuture包装的回显结果
     */
    CompletableFuture<String> echo(String message);
}
