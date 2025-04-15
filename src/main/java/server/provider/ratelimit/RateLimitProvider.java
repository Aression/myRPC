package server.provider.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import server.provider.ratelimit.impl.TokenBucketRateLimit;

public class RateLimitProvider {
    private Map<String, RateLimit> ratelimitMap = new ConcurrentHashMap<>();
    public RateLimit getRateLimiter(String serviceName){
        if(!ratelimitMap.containsKey(serviceName)){
            RateLimit rateLimit = new TokenBucketRateLimit(100, 10);
            return rateLimit;
        }
        return ratelimitMap.get(serviceName);
    }
}
