package common.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个服务为"快速服务"
 * 快速服务将在 Netty IO 线程中直接执行，不提交到业务线程池
 * 适用于：
 * 1. 非阻塞操作 (纯内存操作)
 * 2. 执行时间极短 (< 1ms)
 * 3. 返回 CompletableFuture 的异步非阻塞方法
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FastService {
}
