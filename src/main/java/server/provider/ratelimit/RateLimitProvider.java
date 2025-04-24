package server.provider.ratelimit;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import server.provider.ratelimit.impl.TokenBucketRateLimit;

public class RateLimitProvider {
    private Map<String, RateLimit> ratelimitMap = new ConcurrentHashMap<>();
    public RateLimit getRateLimiter(String serviceName){
        if(!ratelimitMap.containsKey(serviceName)){
            // 使用SPI机制加载RateLimit实现
            ServiceLoader<RateLimit> serviceLoader = ServiceLoader.load(RateLimit.class);
            
            // 尝试获取第一个可用的限流实现
            RateLimit rateLimit = null;
            for (RateLimit impl : serviceLoader) {
                rateLimit = impl;
                break;
            }
            
            // 如果SPI没有找到实现，使用默认的TokenBucket实现
            if (rateLimit == null) {
                rateLimit = new TokenBucketRateLimit(100, 10);
            }
            
            // 缓存并返回限流器
            ratelimitMap.put(serviceName, rateLimit);
            return rateLimit;
        }
        return ratelimitMap.get(serviceName);
    }
}
