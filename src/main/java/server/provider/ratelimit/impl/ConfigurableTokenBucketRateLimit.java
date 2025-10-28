package server.provider.ratelimit.impl;

import server.provider.ratelimit.RateLimit;
import common.util.AppConfig;

/**
 * 可配置的令牌桶限流器实现，支持通过系统属性调整参数：
 * -Dratelimit.rate.ms=10       令牌生成间隔（毫秒），默认 10ms（约100 QPS）
 * -Dratelimit.capacity=300     桶容量，默认 300
 */
public class ConfigurableTokenBucketRateLimit implements RateLimit {

    private final TokenBucketRateLimit delegate;

    public ConfigurableTokenBucketRateLimit() {
        // 优先应用配置文件，其次系统属性（兼容旧键）
        int rateMs = AppConfig.getInt("rpc.ratelimit.rate.ms", parseIntOrDefault(System.getProperty("ratelimit.rate.ms"), 10));
        int capacity = AppConfig.getInt("rpc.ratelimit.capacity", parseIntOrDefault(System.getProperty("ratelimit.capacity"), 300));
        if (rateMs <= 0) rateMs = 10;
        if (capacity <= 0) capacity = 300;
        this.delegate = new TokenBucketRateLimit(rateMs, capacity);
    }

    @Override
    public boolean getToken() {
        return delegate.getToken();
    }

    private static int parseIntOrDefault(String value, int defaultVal) {
        if (value == null || value.isEmpty()) return defaultVal;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}


