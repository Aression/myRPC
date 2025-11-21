package server.provider.ratelimit.impl;

import server.provider.ratelimit.RateLimit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 TPS 配置的令牌桶限流器 (ns 单位)
 */
public class TokenBucketRateLimit implements RateLimit {

    // 1秒的纳秒数
    private static final long NANO_PER_SECOND = 1_000_000_000L;
    
    private final long waitTimeRate;    // 生成令牌间隔 (ns)
    private final int bucketCapacity;   // 桶容量

    private final AtomicInteger curCapacity;    // 当前令牌数
    private final AtomicLong lastRefillTime;    // 最后补充时间 (ns)

    /**
     * 默认构造函数，使用 10 TPS 的速率和 100 的容量。
     */
    public TokenBucketRateLimit() {
        this(10, 100);
    }
    
    /**
     * 构造函数
     * @param tps 每秒允许的事务数 (TPS)
     * @param capacity 令牌桶容量
     */
    public TokenBucketRateLimit(long tps, int capacity) {
        if (tps <= 0 || capacity <= 0) {
            throw new IllegalArgumentException("TPS 和容量参数必须为正数");
        }
        
        // waitTimeRate = NANO_PER_SECOND / tps
        this.waitTimeRate = NANO_PER_SECOND / tps;
        this.bucketCapacity = capacity;
        this.curCapacity = new AtomicInteger(capacity);
        this.lastRefillTime = new AtomicLong(System.nanoTime());
    }

    @Override
    public boolean getToken() {
        refillTokens();
        
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
        long currentTime = System.nanoTime();
        long lastTime = lastRefillTime.get();
        long timeDiff = currentTime - lastTime;
        
        if (timeDiff < waitTimeRate) {
            return;
        }
        
        // 计算应生成令牌数
        long tokensToAddLong = timeDiff / waitTimeRate;
        
        if (tokensToAddLong == 0) {
            return;
        }

        // 限制 tokensToAddLong 不超过 int 范围
        int tokensToAdd = (int) Math.min(tokensToAddLong, Integer.MAX_VALUE);
        
        // CAS更新补充时间：上一次补充时间 + (已用于生成令牌的总时间)
        long newLastTime = lastTime + tokensToAddLong * waitTimeRate;
        if (lastRefillTime.compareAndSet(lastTime, newLastTime)) {
            addTokens(tokensToAdd);
        }
    }

    private void addTokens(int tokensToAdd) {
        int current, newTokens;
        do {
            current = curCapacity.get();
            // 不超过桶容量
            newTokens = Math.min(bucketCapacity, current + tokensToAdd);
        } while (!curCapacity.compareAndSet(current, newTokens));
    }
}