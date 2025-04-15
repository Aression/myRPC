package client.proxy.breaker;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.*;

import lombok.AllArgsConstructor;

enum BreakerState{
    CLOSED, OPEN, HALF_OPEN
}

@AllArgsConstructor
public class Breaker {
    private static final Logger logger = LoggerFactory.getLogger(Breaker.class);
    
    // 失败次数阈值
    private final int failureThreshold;

    // 半开->关闭的成功次数比例
    private final double half2OpenSuccessRate;
    
    // 恢复时间
    private final long retryTimePeriod;

    public Breaker(int failureThreshold, double half2OpenSuccessRate,long retryTimePeriod) {
        this.failureThreshold = failureThreshold;
        this.half2OpenSuccessRate = half2OpenSuccessRate;
        this.retryTimePeriod = retryTimePeriod;
    }
    
    // 默认为关闭状态，允许请求通过
    private BreakerState state = BreakerState.CLOSED; 
    private BreakerState getCurrentState(){
        return state;
    }
    
    // 上一次失败时间
    private long lastFailureTime = 0;

    // 设置原子性的计数变量
    private AtomicInteger failureCount = new AtomicInteger(0);
    private AtomicInteger successCount = new AtomicInteger(0);
    private AtomicInteger requestCount = new AtomicInteger(0);
    
    // 重置计数器
    private void resetCounts() {
        failureCount.set(0);
        successCount.set(0);
        requestCount.set(0);
    }

    // 判断是否允许请求通过熔断器
    public synchronized boolean allowRequest(){
        long curTime = System.currentTimeMillis();
        switch (state) {
            case OPEN: 
                if(curTime-lastFailureTime>retryTimePeriod){
                    // 熔断器处于开启状态时，如果超过时间则进入半开状态
                    state = BreakerState.HALF_OPEN;
                    resetCounts();
                    return true;
                }
                logger.warn("熔断器生效");
                return false;
            case HALF_OPEN:
                requestCount.incrementAndGet();
                return true;
            case CLOSED:
                return true;
            default:
                return true;
        }
    }

    // 成功和失败只能在请求完成之后才获得，所以这里独立出来
    public synchronized void recordSuccess(){
        if(state==BreakerState.HALF_OPEN){
            // 更新计数
            successCount.incrementAndGet();
            
            // 更新状态
            if(successCount.get()>=half2OpenSuccessRate*requestCount.get()){
                state = BreakerState.CLOSED;
                resetCounts();
            }else resetCounts();
        }
    }
    public synchronized void recordFailure(){
        // 更新计数
        failureCount.incrementAndGet();
        lastFailureTime = System.currentTimeMillis();
        logger.warn("熔断器记录一次失败次数");

        // 更新状态
        if(state==BreakerState.HALF_OPEN){
            state = BreakerState.OPEN;
        }else if(failureCount.get()>=failureThreshold){
            state = BreakerState.OPEN;
        }
    }
}
