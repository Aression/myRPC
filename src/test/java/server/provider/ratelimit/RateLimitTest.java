package server.provider.ratelimit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import server.provider.ratelimit.impl.TokenBucketRateLimit;

import server.provider.ratelimit.impl.SynchronizedTokenBucketRateLimit; // 假设这是您之前的同步版本
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class RateLimitTest {
    private RateLimit rateLimit;
    private static final int RATE = 100; // 100ms生成一个令牌
    private static final int CAPACITY = 5; // 令牌桶容量
    
    @Before
    public void setUp() {
        // 创建令牌桶限流器，设置生成速率和容量
        rateLimit = new TokenBucketRateLimit(RATE, CAPACITY);
    }
    
    @Test
    public void testInitialCapacity() {
        // 测试初始状态下令牌桶应该是满的
        for (int i = 0; i < CAPACITY; i++) {
            Assert.assertTrue("初始状态下应能获取令牌", rateLimit.getToken());
        }
        
        // 令牌用完后应该无法获取
        Assert.assertFalse("令牌用完后应无法获取", rateLimit.getToken());
    }
    
    @Test
    public void testTokenRegeneration() throws InterruptedException {
        // 先消耗所有令牌
        for (int i = 0; i < CAPACITY; i++) {
            rateLimit.getToken();
        }
        
        // 等待足够时间生成新令牌
        Thread.sleep(RATE + 50); // 等待时间略大于生成速率
        
        // 应该能获取到新生成的令牌
        Assert.assertTrue("等待后应能获取新令牌", rateLimit.getToken());
        
        // 但是下一个令牌还没生成
        Assert.assertFalse("新令牌已用完", rateLimit.getToken());
    }
    
    @Test
    public void testMultipleTokenRegeneration() throws InterruptedException {
        // 先消耗所有令牌
        for (int i = 0; i < CAPACITY; i++) {
            rateLimit.getToken();
        }
        
        // 等待足够时间生成多个令牌
        int tokensToGenerate = 3;
        Thread.sleep(RATE * tokensToGenerate + 50);
        
        // 应该能获取到新生成的多个令牌
        for (int i = 0; i < tokensToGenerate; i++) {
            Assert.assertTrue("应能获取第" + (i+1) + "个新令牌", rateLimit.getToken());
        }
        
        // 但是下一个令牌还没生成
        Assert.assertFalse("新令牌已用完", rateLimit.getToken());
    }
    
    @Test
    public void testCapacityLimit() throws InterruptedException {
        // 先消耗所有令牌
        for (int i = 0; i < CAPACITY; i++) {
            rateLimit.getToken();
        }
        
        // 等待足够时间，使令牌桶能够重新填满
        Thread.sleep(RATE * (CAPACITY + 2)); // 等待时间足够生成比容量更多的令牌
        
        // 应该只能获取容量上限个令牌
        for (int i = 0; i < CAPACITY; i++) {
            Assert.assertTrue("应能获取第" + (i+1) + "个令牌", rateLimit.getToken());
        }
        
        // 超过容量后应该无法获取
        Assert.assertFalse("超过容量后应无法获取", rateLimit.getToken());
    }
    
    @Test
    public void testRateLimitProvider() {
        // 测试RateLimitProvider
        RateLimitProvider provider = RateLimitProvider.INSTANCE;
        
        // 获取同一服务的限流器应该是同一个实例
        String serviceName = "testService";
        RateLimit rateLimit1 = provider.getRateLimiter(serviceName);
        RateLimit rateLimit2 = provider.getRateLimiter(serviceName);
        Assert.assertSame("同一服务名应返回同一限流器实例", rateLimit1, rateLimit2);
        
        // 获取不同服务的限流器应该是不同实例
        RateLimit anotherRateLimit = provider.getRateLimiter("anotherService");
        Assert.assertNotSame("不同服务名应返回不同限流器实例", rateLimit1, anotherRateLimit);
    }

    /**
     * 测试1: 原始吞吐能力对比测试 (Burst Test)
     * 目的: 衡量限流器处理大量并发请求的原始速度。
     * 观察指标:
     *  - 总耗时: CAS版本应远小于Synchronized版本。
     *  - 系统总吞吐量: CAS版本应远高于Synchronized版本。
     */
    @Test
    public void throughputComparisonTest() throws InterruptedException {
        int threadCount = 100;
        int requestsPerThread = 1000;
        int rate = 1000; // 1000 tps
        int capacity = 1000;
        int waitTimeRate = 1000 / rate;

        System.out.println("### 测试1: 原始吞吐能力对比 ###");
        System.out.printf("并发线程: %d, 每个线程请求数: %d\n", threadCount, requestsPerThread);
        System.out.println("---------------------------------------------------------");

        RateLimit casRateLimit = new TokenBucketRateLimit(waitTimeRate, capacity);
        System.out.println(">>> 正在测试 [Atomic + CAS] 版本...");
        runThroughputTest(casRateLimit, threadCount, requestsPerThread);
        System.out.println("---------------------------------------------------------");

        RateLimit synchronizedRateLimit = new SynchronizedTokenBucketRateLimit(waitTimeRate, capacity);
        System.out.println(">>> 正在测试 [Synchronized] 版本...");
        runThroughputTest(synchronizedRateLimit, threadCount, requestsPerThread);
    }

    /**
     * 测试2: 限流速率准确性对比测试 (Fixed Duration Test)
     * 目的: 验证限流器是否在设定的时间窗口内准确地放行了请求。
     * 观察指标:
     *  - 测量TPS: CAS和Synchronized版本都应非常接近期望的TPS。
     */
    @Test
    public void rateAccuracyComparisonTest() throws InterruptedException {
        int threadCount = 100;
        int expectedTps = 1000; // 期望速率: 1000 TPS
        int capacity = 1000;
        int waitTimeRate = 1000 / expectedTps;
        int durationSeconds = 5; // 测试持续时间

        System.out.println("\n### 测试2: 限流速率准确性对比 ###");
        System.out.printf("并发线程: %d, 期望TPS: %d, 测试时长: %d秒\n", threadCount, expectedTps, durationSeconds);
        System.out.println("---------------------------------------------------------");

        RateLimit casRateLimit = new TokenBucketRateLimit(waitTimeRate, capacity);
        System.out.println(">>> 正在测试 [Atomic + CAS] 版本...");
        runRateAccuracyTest(casRateLimit, threadCount, expectedTps, durationSeconds);
        System.out.println("---------------------------------------------------------");
        
        RateLimit synchronizedRateLimit = new SynchronizedTokenBucketRateLimit(waitTimeRate, capacity);
        System.out.println(">>> 正在测试 [Synchronized] 版本...");
        runRateAccuracyTest(synchronizedRateLimit, threadCount, expectedTps, durationSeconds);
    }


    // --- 测试执行辅助方法 ---

    private void runThroughputTest(RateLimit rateLimit, int threadCount, int requestsPerThread) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    for (int j = 0; j < requestsPerThread; j++) {
                        if (rateLimit.getToken()) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endGate.countDown();
                }
            });
        }

        long startTime = System.nanoTime();
        startGate.countDown();
        endGate.await();
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        executor.shutdown();

        int totalRequests = threadCount * requestsPerThread;
        double totalThroughput = (durationMillis > 0) ? (double) totalRequests * 1000 / durationMillis : 0;
        
        System.out.printf("总耗时: %d ms\n", durationMillis);
        System.out.printf("成功请求: %d / %d\n", successCount.get(), totalRequests);
        System.out.printf("系统总吞吐量 (衡量方法执行速度): %.2f req/s\n", totalThroughput);
    }

    private void runRateAccuracyTest(RateLimit rateLimit, int threadCount, int expectedTps, int durationSeconds) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final AtomicLong successCount = new AtomicLong(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                // 循环直到线程被中断
                try {
                    // 检查中断标志
                    while (!Thread.currentThread().isInterrupted()) {
                        if (rateLimit.getToken()) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    // 通常线程在 I/O 操作时被中断会抛出异常，这里不需要处理
                }
            });
        }

        // 等待指定的测试时间
        Thread.sleep(durationSeconds * 1000L);

        // 强制停止所有线程。它会通过中断来停止正在运行的任务。
        executor.shutdownNow();
        if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
            System.err.println("线程池未能按时终止！");
        }

        long totalSuccess = successCount.get();
        double measuredTps = (double) totalSuccess / durationSeconds;

        System.out.printf("测试时长内总成功请求数: %d\n", totalSuccess);
        System.out.printf("测量TPS (实际通过速率): %.2f req/s (期望值: %d TPS)\n", measuredTps, expectedTps);

        // 获取限流器容量以进行更精确的断言
        // (这需要你在RateLimit接口和实现中添加一个getCapacity方法)
        // 假设容量就是expectedTps
        long capacity = expectedTps;
        
        // 断言：实际通过速率应该在期望值的合理范围内
        // 期望的成功数 = 初始容量 + (速率 * 时间)。我们给一个10%的浮动范围。
        long expectedTotalSuccess = capacity + (long)expectedTps * durationSeconds;
        double lowerBound = expectedTotalSuccess * 0.9;
        double upperBound = expectedTotalSuccess * 1.1;

        Assert.assertTrue(
                String.format("测量总成功数 %d 不在期望范围 [%.0f, %.0f] 内", totalSuccess, lowerBound, upperBound),
                totalSuccess >= lowerBound && totalSuccess <= upperBound
        );
    }
}