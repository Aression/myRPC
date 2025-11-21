package server.provider.ratelimit.impl;

import server.provider.ratelimit.RateLimit;
import common.util.AppConfig;

// SPI载入包装类
public class ConfigurableTokenBucketRateLimit implements RateLimit {

    private final TokenBucketRateLimit delegate;

    public ConfigurableTokenBucketRateLimit() {
        // 优先应用配置文件，其次系统属性（兼容旧键）
        int tps = AppConfig.getInt("rpc.ratelimit.rate.tps",parseIntOrDefault(System.getProperty("ratelimit.rate.tps"), 10));
        int capacity = AppConfig.getInt("rpc.ratelimit.capacity", parseIntOrDefault(System.getProperty("ratelimit.capacity"), 300));
        
        if (tps <= 0) tps = 10;
        if (capacity <= 0) capacity = 100;
        
        this.delegate = new TokenBucketRateLimit(tps, capacity);
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


