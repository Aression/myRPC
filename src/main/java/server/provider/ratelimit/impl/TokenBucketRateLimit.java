package server.provider.ratelimit.impl;

import server.provider.ratelimit.RateLimit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 高性能令牌桶限流器
 */
public class TokenBucketRateLimit implements RateLimit {

    private final int waitTimeRate;     // 生成令牌间隔(ms)
    private final int bucketCapacity;   // 桶容量

    private final AtomicInteger curCapacity;    // 当前令牌数
    private final AtomicLong lastRefillTime;    // 最后补充时间

    public TokenBucketRateLimit() {
        this(100, 100);
    }
    
    public TokenBucketRateLimit(int rate, int capacity) {
        if (rate <= 0 || capacity <= 0) {
            throw new IllegalArgumentException("参数必须为正数");
        }
        this.waitTimeRate = rate;
        this.bucketCapacity = capacity;
        this.curCapacity = new AtomicInteger(capacity);
        this.lastRefillTime = new AtomicLong(System.currentTimeMillis());
    }

    @Override
    public boolean getToken() {
        // 先尝试补充令牌
        refillTokens();
        
        // CAS获取令牌
        int current;
        do {
            current = curCapacity.get();
            if (current <= 0) {
                return false;
            }
        } while (!curCapacity.compareAndSet(current, current - 1));
        
        return true;
    }

    private void refillTokens() {
        long currentTime = System.currentTimeMillis();
        long lastTime = lastRefillTime.get();
        long timeDiff = currentTime - lastTime;
        
        // 时间间隔不足以生成令牌
        if (timeDiff < waitTimeRate) {
            return;
        }
        
        // 计算应生成令牌数
        int tokensToAdd = (int) (timeDiff / waitTimeRate);
        if (tokensToAdd == 0) {
            return;
        }

        // CAS更新补充时间
        long newLastTime = lastTime + (long) tokensToAdd * waitTimeRate;
        if (lastRefillTime.compareAndSet(lastTime, newLastTime)) {
            // 成功获得补充权，更新令牌数
            addTokens(tokensToAdd);
        }
    }

    private void addTokens(int tokensToAdd) {
        int current, newTokens;
        do {
            current = curCapacity.get();
            newTokens = Math.min(bucketCapacity, current + tokensToAdd);
        } while (!curCapacity.compareAndSet(current, newTokens));
    }
}