package server.provider.ratelimit.impl;

import server.provider.ratelimit.RateLimit;

/**
 * 修复了状态隔离和令牌生成逻辑的令牌桶限流器
 */
public class SynchronizedTokenBucketRateLimit implements RateLimit {
    
    private final int waitTimeRate;     // 生成一个令牌需要等待的时间（毫秒.ms），高并发1-10ms，一般服务50-100ms，低频服务200-1000ms
    private final int bucketCapacity;   // 令牌桶容量上限，一般设置为rate*10到rate*100

    private int curCapacity;            // 当前的令牌桶容量 (非 volatile，由 synchronized 保护)
    private long lastAccessTime;        // 最后一次处理令牌的时间 (非 volatile，由 synchronized 保护)

    // 添加无参构造函数，用于SPI机制加载 (使用默认值)
    public SynchronizedTokenBucketRateLimit() {
        // 默认参数: 100ms/token (10 tps), 容量 1000
        this(100, 1000);
    }

    public SynchronizedTokenBucketRateLimit(int rate, int capacity) {
        if (rate <= 0 || capacity <= 0) {
            throw new IllegalArgumentException("Rate and capacity must be positive.");
        }
        this.waitTimeRate = rate;
        this.bucketCapacity = capacity;

        // 初始时，桶是满的
        this.curCapacity = capacity;
        // 记录初始时间
        this.lastAccessTime = System.currentTimeMillis();
    }

    @Override
    public synchronized boolean getToken() {
        long currentTime = System.currentTimeMillis();
        long timeDiff = currentTime - lastAccessTime;

        // 只要时间流逝，就计算并补充令牌
        if (timeDiff > 0) {
            // 计算这段时间内应该生成的令牌数
            int tokensToAdd = (int) (timeDiff / waitTimeRate);
            
            if (tokensToAdd > 0) {
                // 更新当前令牌数，但不超过桶容量
                this.curCapacity = Math.min(this.curCapacity + tokensToAdd, this.bucketCapacity);
                // 更新最后访问时间，只减去已用于生成令牌的时间，以保留余数
                this.lastAccessTime += (long) tokensToAdd * this.waitTimeRate;
            }
        }
        
        // 尝试获取令牌
        if (this.curCapacity > 0) {
            this.curCapacity--;
            return true; // 令牌桶有令牌，拿走一个并返回true
        }
        
        return false; // 没有可用令牌
    }
}
