package server.provider.ratelimit.factory;

import server.provider.ratelimit.RateLimit;
import server.provider.ratelimit.impl.SynchronizedTokenBucketRateLimit;

public class SynchronizedTokenBucketFactory implements RateLimitFactory {
    @Override
    public String getName() {
        return "synchronized_token_bucket";
    }

    @Override
    public RateLimit create(int rateMs, int capacity) {
        return new SynchronizedTokenBucketRateLimit(rateMs, capacity);
    }
}


