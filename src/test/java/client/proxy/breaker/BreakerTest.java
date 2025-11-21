package client.proxy.breaker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("熔断器测试")
class BreakerTest {

    private static final int FAILURE_THRESHOLD = 3;
    private static final double HALF_OPEN_SUCCESS_RATE_TO_CLOSE = 0.5;
    private static final long RETRY_TIME_PERIOD_MS = 1000; // 1秒

    @Nested
    @DisplayName("熔断器状态转换逻辑")
    class BreakerStateTransitions {

        private Breaker breaker;

        @BeforeEach
        void setUp() {
            breaker = new Breaker(FAILURE_THRESHOLD, HALF_OPEN_SUCCESS_RATE_TO_CLOSE, RETRY_TIME_PERIOD_MS);
        }

        @Test
        @DisplayName("初始状态应为关闭(CLOSED)，允许请求")
        void shouldBeClosedInitially() {
            assertTrue(breaker.allowRequest(), "初始状态应为CLOSED，并允许请求");
        }

        @Test
        @DisplayName("达到失败阈值后，状态应变为打开(OPEN)")
        void shouldOpenWhenFailureThresholdIsReached() {
            openTheBreaker();
            assertFalse(breaker.allowRequest(), "达到失败阈值后应变为OPEN，并拒绝请求");
        }

        @Test
        @DisplayName("打开(OPEN)状态超时后，应变为半开(HALF-OPEN)")
        void shouldBecomeHalfOpenAfterTimeout() throws InterruptedException {
            openTheBreaker();
            transitionToHalfOpen();
            assertTrue(breaker.allowRequest(), "OPEN状态超时后应变为HALF-OPEN，并允许试探性请求");
        }

        @Test
        @DisplayName("半开(HALF-OPEN)状态下，成功率达标后应变为关闭(CLOSED)")
        void shouldCloseFromHalfOpenOnSufficientSuccesses() throws InterruptedException {
            openTheBreaker();

            // 等待进入半开状态
            transitionToHalfOpen();
            assertTrue(breaker.allowRequest(), "应进入HALF-OPEN状态");

            // 在半开状态下，需要至少 FAILURE_THRESHOLD 次调用来决定状态
            // 成功率要求 0.5，因此需要 ceil(3 * 0.5) = 2 次成功
            breaker.recordSuccess(); // 成功1
            breaker.allowRequest();
            breaker.recordSuccess(); // 成功2
            breaker.allowRequest();
            breaker.recordFailure(); // 失败1 (总调用3次，成功率 2/3 > 0.5)

            // 此时熔断器应该已经关闭
            assertTrue(breaker.allowRequest(), "成功率达标后，熔断器应从HALF-OPEN变为CLOSED");
        }

        @Test
        @DisplayName("半开(HALF-OPEN)状态下，一次失败立即回到打开(OPEN)")
        void shouldReopenFromHalfOpenOnSingleFailure() throws InterruptedException {
            openTheBreaker();
            transitionToHalfOpen();

            // 确认进入半开状态
            assertTrue(breaker.allowRequest(), "应进入HALF-OPEN状态");

            // 在半开状态下记录一次失败
            breaker.recordFailure();

            // 应立即回到打开状态
            assertFalse(breaker.allowRequest(), "HALF-OPEN状态下发生失败，应立即回到OPEN状态");
        }

        /**
         * 辅助方法：触发失败，使熔断器打开
         */
        private void openTheBreaker() {
            for (int i = 0; i < FAILURE_THRESHOLD; i++) {
                breaker.recordFailure();
            }
        }

        /**
         * 辅助方法：等待超过重试时间，使熔断器从OPEN进入HALF-OPEN
         */
        private void transitionToHalfOpen() throws InterruptedException {
            // 额外增加100ms确保时间窗口已过
            Thread.sleep(RETRY_TIME_PERIOD_MS + 100);
        }
    }

    @Nested
    @DisplayName("熔断器提供者(BreakerProvider)")
    class BreakerProviderTest {

        private final BreakerProvider provider = BreakerProvider.getInstance();

        @Test
        @DisplayName("为同一服务节点应始终返回相同的熔断器实例")
        void shouldReturnSameInstanceForSameService() {
            InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 8080);
            Breaker breaker1 = provider.getBreaker(addr);
            Breaker breaker2 = provider.getBreaker(addr);
            assertSame(breaker1, breaker2, "同一服务节点应返回相同的熔断器实例");
        }

        @Test
        @DisplayName("为不同服务节点应返回不同的熔断器实例")
        void shouldReturnDifferentInstancesForDifferentServices() {
            Breaker breaker1 = provider.getBreaker(new InetSocketAddress("127.0.0.1", 8080));
            Breaker breaker2 = provider.getBreaker(new InetSocketAddress("127.0.0.1", 8081));
            assertNotSame(breaker1, breaker2, "不同服务节点应返回不同的熔断器实例");
        }

        @ParameterizedTest
        @ValueSource(ints = { 1, 20 }) // 1: 测试单服务并发, 20: 测试多服务并发
        @DisplayName("在并发访问时，每个服务节点应只创建一个熔断器实例")
        void shouldCreateOnlyOneInstancePerServiceUnderConcurrency(int serviceCount) {
            // 准备服务地址列表
            List<InetSocketAddress> addresses = IntStream.range(0, serviceCount)
                    .mapToObj(i -> new InetSocketAddress("127.0.0.1", 8000 + i))
                    .collect(Collectors.toList());

            // 用于存储每个服务获取到的所有熔断器实例
            ConcurrentHashMap<InetSocketAddress, Set<Breaker>> instancesPerService = new ConcurrentHashMap<>();

            // 使用并行流模拟高并发访问和操作
            IntStream.range(0, 5000).parallel().forEach(i -> {
                // 随机选择一个服务
                InetSocketAddress addr = addresses.get(ThreadLocalRandom.current().nextInt(serviceCount));

                // 从provider获取熔断器
                Breaker breaker = provider.getBreaker(addr);
                assertNotNull(breaker, "获取到的熔断器实例不应为null");

                // 记录获取到的实例
                instancesPerService.computeIfAbsent(addr, k -> ConcurrentHashMap.newKeySet()).add(breaker);

                // 模拟随机操作
                int action = ThreadLocalRandom.current().nextInt(3);
                if (action == 0) {
                    breaker.recordSuccess();
                } else if (action == 1) {
                    breaker.recordFailure();
                } else {
                    breaker.allowRequest();
                }
            });

            // 断言：检查每个服务是否都只创建了一个实例
            assertEquals(serviceCount, instancesPerService.size(), "应为所有请求的服务都创建了熔断器");
            instancesPerService.forEach((addr, instances) -> {
                assertEquals(1, instances.size(), "服务节点 '" + addr + "' 在并发下应只创建一个实例");
            });
        }
    }
}
