package server.provider.ratelimit;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import common.util.AppConfig;
import server.provider.ratelimit.factory.*;
import java.util.ArrayList;

/**
 * RateLimitProvider 是一个单例类，用于提供服务级别的限流器实例。
 * 它使用 SPI 机制加载 RateLimit 实现，并在没有找到时提供默认实现。
 * 通过 ConcurrentHashMap 的 computeIfAbsent 保证每个 serviceName 对应的限流器实例是唯一的。
 */
public enum RateLimitProvider {
    /**
     * 单例实例
     */
    INSTANCE;

    private static final Logger logger = LoggerFactory.getLogger(RateLimitProvider.class);

    // 使用 ConcurrentHashMap 保证对 ratelimitMap 的并发访问是线程安全的
    private final Map<String, RateLimit> ratelimitMap = new ConcurrentHashMap<>();

    /**
     * 获取指定服务名称的限流器实例。
     * 如果该服务名称的限流器尚未创建，则原子地创建并缓存它。
     *
     * @param serviceName 服务名称，用于区分不同的限流器。
     * @return 对应服务名称的 RateLimit 实例。
     */
    public RateLimit getRateLimiter(String serviceName) {
        return ratelimitMap.computeIfAbsent(serviceName, this::createRateLimiter);
    }

    // 缓存一次性初始化结果
    private volatile RateLimitFactory cachedFactory;
    private volatile int cachedTPS;
    private volatile int cachedCapacity;

    private synchronized void initIfNeeded() {
        if (cachedFactory != null) return;

        String implName = AppConfig.getString("rpc.ratelimit.impl", "configurable_token_bucket").toLowerCase();
        int tps = AppConfig.getInt("rpc.ratelimit.rate.tps", 10);
        int capacity = AppConfig.getInt("rpc.ratelimit.capacity", 100);

        ServiceLoader<RateLimitFactory> loader = ServiceLoader.load(RateLimitFactory.class);
        RateLimitFactory selected = null;
        java.util.List<String> discovered = new ArrayList<>();
        for (RateLimitFactory f : loader) {
            try {
                String name = f.getName();
                discovered.add(name);
                if (implName.equalsIgnoreCase(name)) {
                    selected = f;
                    break;
                }
            } catch (Throwable t) {
                // 忽略坏实现
            }
        }

        if (selected == null) {
            logger.warn("未通过 SPI 找到名称为 '{}' 的 RateLimitFactory，可用实现：{}，将回退到内置 TokenBucket。", implName, discovered);
            selected = new TokenBucketFactory();
        }

        this.cachedFactory = selected;
        this.cachedTPS = tps;
        this.cachedCapacity = capacity;
        logger.info("RateLimitFactory 已就绪：name='{}', rateMs={}, capacity={}", implName, cachedTPS, capacity);
    }

    private RateLimit createRateLimiter(String serviceName) {
        initIfNeeded();
        RateLimit rl = cachedFactory.create(cachedTPS, cachedCapacity);
        logger.info("成功为服务 '{}' 创建并缓存限流器实例：{}(tps={}, capacity={})", serviceName, rl.getClass().getName(), cachedTPS, cachedCapacity);
        return rl;
    }

    public void clearAllRateLimiters() {
        ratelimitMap.clear();
        logger.info("所有限流器实例已从缓存中清除。");
    }
}