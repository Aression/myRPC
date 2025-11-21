package server.provider.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import server.provider.ratelimit.impl.TokenBucketRateLimit;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RateLimit 令牌桶与 Provider 测试")
class RateLimitTest {

    // 定义基本限流器的期望速率 (TPS - Tokens Per Second)
    private static final int BASE_EXPECTED_TPS = 100;
    private static final int BASE_CAPACITY = 5;

    // 【新增】根据期望的 TPS 计算 TokenBucketRateLimit 内部的 waitTimeRate (ns)
    private static long calculateIntervalNanosFromTps(int tps) {
        if (tps <= 0) {
            throw new IllegalArgumentException("TPS must be positive.");
        }
        // 1,000,000,000 ns / tps
        return 1_000_000_000L / tps;
    }

    // 【删除】旧的 calculateIntervalMillisFromTps 方法不再需要

    @Nested
    @DisplayName("TokenBucketRateLimit 行为")
    class TokenBucketBehaviorTests {

        private RateLimit rateLimit;
        private long intervalNanos; // 对应 TokenBucketRateLimit 的 waitTimeRate (ns)

        @BeforeEach
        void setUp() {
            // 【修正】直接将期望的 TPS 传入构造函数
            rateLimit = new TokenBucketRateLimit(BASE_EXPECTED_TPS, BASE_CAPACITY);
            // 计算纳秒间隔用于 Thread.sleep 的时长估算
            intervalNanos = calculateIntervalNanosFromTps(BASE_EXPECTED_TPS);
        }

        @Test
        @DisplayName("初始状态应填满令牌桶")
        void shouldProvideInitialTokens() {
            for (int i = 0; i < BASE_CAPACITY; i++) {
                assertTrue(rateLimit.getToken(), "初始状态下应能获取令牌 (第 " + (i + 1) + " 个)");
            }
            assertFalse(rateLimit.getToken(), "用尽后应立即耗尽令牌");
        }

        @Test
        @DisplayName("定时生成的令牌应可获取")
        void shouldRegenerateTokenAfterInterval() throws InterruptedException {
            drainTokens(rateLimit);

            // 【修正】使用纳秒间隔计算 sleep 毫秒数
            long sleepMillis = TimeUnit.NANOSECONDS.toMillis(intervalNanos) + 5;
            // 如果 waitTimeRate 小于 1 毫秒 (即 TPS > 1000)，至少等待 6ms 确保调度
            if (sleepMillis <= 5)
                sleepMillis = 6;

            Thread.sleep(sleepMillis);

            assertTrue(rateLimit.getToken(), "等待后应能获取新令牌");
            assertFalse(rateLimit.getToken(), "未到下个周期应无法继续获取");
        }

        @Test
        @DisplayName("连续等待应生成多个令牌")
        void shouldRegenerateMultipleTokens() throws InterruptedException {
            drainTokens(rateLimit);
            int tokensToGenerate = 3;

            // 【修正】使用纳秒间隔计算 sleep 毫秒数
            long sleepNanos = intervalNanos * tokensToGenerate;
            long sleepMillis = TimeUnit.NANOSECONDS.toMillis(sleepNanos) + 5;
            if (sleepMillis <= 5)
                sleepMillis = tokensToGenerate + 5;

            Thread.sleep(sleepMillis);

            for (int i = 0; i < tokensToGenerate; i++) {
                assertTrue(rateLimit.getToken(), "应能获取第 " + (i + 1) + " 个新令牌");
            }
            assertFalse(rateLimit.getToken(), "新令牌已全部取完");
        }

        @Test
        @DisplayName("令牌桶容量应生效")
        void shouldRespectCapacityLimit() throws InterruptedException {
            drainTokens(rateLimit);

            // 【修正】使用纳秒间隔计算 sleep 毫秒数
            long sleepNanos = intervalNanos * (BASE_CAPACITY + 2);
            long sleepMillis = TimeUnit.NANOSECONDS.toMillis(sleepNanos) + 5;
            if (sleepMillis <= 5)
                sleepMillis = (BASE_CAPACITY + 2) + 5;

            Thread.sleep(sleepMillis);

            for (int i = 0; i < BASE_CAPACITY; i++) {
                assertTrue(rateLimit.getToken(), "应能获取容量内的令牌 (第 " + (i + 1) + " 个)");
            }
            assertFalse(rateLimit.getToken(), "容量外的令牌应被丢弃");
        }

        private void drainTokens(RateLimit rateLimit) {
            for (int i = 0; i < BASE_CAPACITY; i++) {
                rateLimit.getToken();
            }
        }
    }

    @Nested
    @DisplayName("RateLimitProvider 单例行为")
    class ProviderTests {
        @Test
        @DisplayName("同一服务应返回同一实例")
        void shouldReturnSameInstanceForSameService() {
            RateLimitProvider provider = RateLimitProvider.INSTANCE;
            RateLimit first = provider.getRateLimiter("testService");
            RateLimit second = provider.getRateLimiter("testService");
            assertSame(first, second, "同一服务名应复用同一限流器");
        }

        @Test
        @DisplayName("不同服务应返回不同实例")
        void shouldReturnDifferentInstanceForDifferentService() {
            RateLimitProvider provider = RateLimitProvider.INSTANCE;
            RateLimit one = provider.getRateLimiter("serviceA");
            RateLimit two = provider.getRateLimiter("serviceB");
            assertNotSame(one, two, "不同服务名应获得独立限流器");
        }
    }

}