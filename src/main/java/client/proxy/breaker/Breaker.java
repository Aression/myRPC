package client.proxy.breaker;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.*;
import lombok.AllArgsConstructor;

enum BreakerState {
    CLOSED, OPEN, HALF_OPEN
}

@AllArgsConstructor
public class Breaker {
    private static final Logger logger = LoggerFactory.getLogger(Breaker.class);

    // 失败次数阈值
    private final int failureThreshold;
    // 半开->关闭的成功次数比例 (例如 0.8 表示 80% 成功率)
    private final double half2OpenSuccessRate;
    // 恢复时间 (毫秒)
    private final long retryTimePeriod;

    public Breaker(int failureThreshold, double half2OpenSuccessRate, long retryTimePeriod) {
        this.failureThreshold = failureThreshold;
        this.half2OpenSuccessRate = half2OpenSuccessRate;
        this.retryTimePeriod = retryTimePeriod;
        logger.info("[Breaker Init] 阈值:{}, 恢复成功率:{}, 重试间隔:{}ms",
                failureThreshold, half2OpenSuccessRate, retryTimePeriod);
    }

    private final AtomicReference<BreakerState> state = new AtomicReference<>(BreakerState.CLOSED);

    // 上一次失败时间
    private volatile long lastFailureTime = 0;

    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger requestCount = new AtomicInteger(0);

    private void resetCounts() {
        failureCount.set(0);
        successCount.set(0);
        requestCount.set(0);
        // logger.debug("计数器已重置");
    }

    public BreakerState getCurrentState() {
        return state.get();
    }

    /**
     * 判断是否允许请求通过
     */
    public boolean allowRequest() {
        long curTime = System.currentTimeMillis();
        BreakerState currentState = state.get();

        switch (currentState) {
            case OPEN:
                long timePassed = curTime - lastFailureTime;
                if (timePassed > retryTimePeriod) {
                    // 尝试进入半开状态
                    if (state.compareAndSet(BreakerState.OPEN, BreakerState.HALF_OPEN)) {
                        resetCounts();
                        // 必须在这里自增，因为当前线程即将发起“探测”请求
                        requestCount.incrementAndGet();
                        logger.info("[State Change] OPEN -> HALF_OPEN (经过 {}ms, 超过重试周期 {}ms)，发起探测请求", timePassed,
                                retryTimePeriod);
                        return true;
                    } else {
                        // CAS失败说明有其他线程已经切到了 HALF_OPEN
                        // 重新检查状态，确保计数器逻辑正确
                        return allowRequest();
                    }
                }
                // logger.debug("[Blocked] 熔断器开启中，剩余等待时间: {}ms", retryTimePeriod - timePassed);
                return false;

            case HALF_OPEN:
                // 半开状态，记录请求数，用于计算成功率
                requestCount.incrementAndGet();
                logger.debug("[Half-Open] 允许探测请求通过");
                return true;

            case CLOSED:
                // 正常状态直接通过
                return true;

            default:
                return true;
        }
    }

    /**
     * 记录成功
     */
    public void recordSuccess() {
        // 只有半开状态才需要特定逻辑去判断是否关闭熔断器
        // 正常状态下单纯的成功不需要额外处理（除非你想做滑动窗口统计，但当前逻辑是基于累积计数的）
        if (state.get() == BreakerState.HALF_OPEN) {
            int sCount = successCount.incrementAndGet();
            int rCount = requestCount.get();

            double currentRate = (double) sCount / rCount;
            logger.info("[Check Recovery] HALF_OPEN 状态: 成功 {} / 总请求 {} (当前成功率: {}, 目标: {})",
                    sCount, rCount, String.format("%.2f", currentRate), half2OpenSuccessRate);

            // 只有总请求数大于0且成功率达标时
            if (rCount > 0 && currentRate >= half2OpenSuccessRate) {
                if (state.compareAndSet(BreakerState.HALF_OPEN, BreakerState.CLOSED)) {
                    resetCounts();
                    logger.info("[State Change] HALF_OPEN -> CLOSED (服务恢复)");
                }
            }
        }
    }

    /**
     * 记录失败
     */
    public void recordFailure() {
        lastFailureTime = System.currentTimeMillis();

        BreakerState currentState = state.get();

        if (currentState == BreakerState.HALF_OPEN) {
            // 半开状态下一旦失败，通常策略是立即重新打开
            if (state.compareAndSet(BreakerState.HALF_OPEN, BreakerState.OPEN)) {
                logger.warn("[State Change] HALF_OPEN -> OPEN (探测请求失败)");
            }
        } else if (currentState == BreakerState.CLOSED) {
            int currentFailures = failureCount.incrementAndGet();
            // logger.warn("[Failure Count] 当前连续失败次数: {} (阈值: {})", currentFailures,
            // failureThreshold);

            if (currentFailures >= failureThreshold) {
                if (state.compareAndSet(BreakerState.CLOSED, BreakerState.OPEN)) {
                    logger.error("[State Change] CLOSED -> OPEN (失败次数达到阈值)");
                }
            }
        }
    }

    /**
     * 负载均衡检查用
     */
    public boolean isAvailable() {
        BreakerState currentState = state.get();
        if (currentState == BreakerState.CLOSED || currentState == BreakerState.HALF_OPEN) {
            return true;
        }
        if (currentState == BreakerState.OPEN) {
            return System.currentTimeMillis() - lastFailureTime > retryTimePeriod;
        }
        return true;
    }
}