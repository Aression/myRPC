package server.provider.ratelimit.impl;

import server.provider.ratelimit.RateLimit;

public class TokenBucketRateLimit implements RateLimit{
    private static int waitTimeRate; // 决定是否重新生成令牌的等待时间阈值
    private static int bucketCapacity; // 令牌桶容量上限
    private volatile int curCapacity; // 当前的令牌桶容量

    private volatile long lastAccessTime; // 最后一次访问时间

    // 添加无参构造函数，用于SPI机制加载
    public TokenBucketRateLimit() {
        // 默认参数
        TokenBucketRateLimit.waitTimeRate = 100;
        TokenBucketRateLimit.bucketCapacity = 100;
        this.curCapacity = bucketCapacity;
        this.lastAccessTime = System.currentTimeMillis();
    }

    public TokenBucketRateLimit(int rate, int capacity){
        TokenBucketRateLimit.waitTimeRate = rate;
        TokenBucketRateLimit.bucketCapacity = capacity;

        this.curCapacity = capacity;
        this.lastAccessTime = System.currentTimeMillis();
    }

    @Override
    public synchronized boolean getToken(){
        if(curCapacity>0){
            curCapacity--;
            return true; // 令牌桶有容量，拿走一个令牌并返回true
        }
        long currentTime = System.currentTimeMillis();
        if(currentTime - lastAccessTime>=waitTimeRate){
            // 令牌桶没有剩余令牌
            // 则根据距离上次请求过去的时间计算等待时间中生成的令牌数量，更新当前令牌数
            if((currentTime-lastAccessTime)/waitTimeRate>=2){
                curCapacity += (int)(currentTime-lastAccessTime)/waitTimeRate-1;
            }

            // 保持桶容量不超过上限
            if(curCapacity>bucketCapacity) curCapacity = bucketCapacity;
            
            // 更新最后一次访问时间并返回
            lastAccessTime = currentTime;
            return true;
        }
        return false; 
    }
}
