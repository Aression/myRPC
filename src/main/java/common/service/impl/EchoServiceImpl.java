package common.service.impl;

import common.service.EchoService;

import java.util.concurrent.CompletableFuture;

import common.service.FastService;

/**
 * Echo服务实现 - 异步版本
 */
@FastService
public class EchoServiceImpl implements EchoService {
    @Override
    public CompletableFuture<String> echo(String message) {
        // 服务端实现是同步的，使用completedFuture包装同步结果
        return CompletableFuture.completedFuture(message);
    }
}
