package server.provider.ratelimit.factory;

import server.provider.ratelimit.RateLimit;
import server.provider.ratelimit.impl.TokenBucketRateLimit;

public class TokenBucketFactory implements RateLimitFactory {
    @Override
    public String getName() {
        return "token_bucket";
    }

    @Override
    public RateLimit create(int rateMs, int capacity) {
        return new TokenBucketRateLimit(rateMs, capacity);
    }
}


