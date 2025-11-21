package performance;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import client.rpcClient.impl.NettyRpcClient;
import common.message.RpcRequest;
import common.service.EchoService;
import common.service.impl.EchoServiceImpl;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.provider.ServiceProvider;
import server.server.impl.NettyRPCServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Echo Loopback Auto-Tuning Performance Test
 * 自动探测 RPC 框架的极限 QPS
 */
public class EchoPerformanceTest {
    private static final Logger logger = LoggerFactory.getLogger(EchoPerformanceTest.class);

    // 抑制日志干扰
    static {
        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            loggerContext.getLogger("ROOT").setLevel(Level.ERROR);
            loggerContext.getLogger("performance").setLevel(Level.INFO);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Server Configuration
    private static final int SERVER_START_PORT = 10001;
    private static final int SERVER_NODE_COUNT = 5; // 5个节点分摊负载
    private static final String SERVER_HOST = "127.0.0.1";

    // Tuning Configuration
    private static final int TEST_DURATION_SECONDS = 10; // 每轮压测时长
    private static final int START_CONCURRENCY = 100; // 起始并发数
    private static final int STEP_CONCURRENCY = 100; // 每轮增加并发数
    private static final double ERROR_RATE_THRESHOLD = 0.02; // 错误率容忍度 (2%)

    @BeforeAll
    public static void setup() throws Exception {
        // 关闭框架自身的限流和熔断，以便测试极限物理性能
        System.setProperty("rpc.ratelimit.rate.tps", "99999999");
        System.setProperty("rpc.ratelimit.capacity", "99999999");
        System.setProperty("rpc.breaker.failureThreshold", "99999999");
        System.setProperty("serializer.type", "3"); // Kryo

        startServer();
    }

    /**
     * 核心测试方法：自动探测极限 QPS
     */
    @Test
    public void autoTuneMaxQPS() throws Exception {
        NettyRpcClient rpcClient = new NettyRpcClient();

        logger.info("============================================================");
        logger.info("开始自动探测极限 QPS (Auto-Tuning Mode)");
        logger.info("策略: 每轮增加 {} 并发，持续 {} 秒，直到性能衰退", STEP_CONCURRENCY, TEST_DURATION_SECONDS);
        logger.info("============================================================");

        // 预热
        logger.info("正在预热 JIT 编译器...");
        runAsyncPhase(rpcClient, 50, 5, false);
        Thread.sleep(1000);

        int currentConcurrency = START_CONCURRENCY;
        double maxQps = 0;
        int bestConcurrency = 0;

        // 记录每轮结果用于最终展示
        List<String> reportLines = new ArrayList<>();
        reportLines.add(String.format("%-12s | %-10s | %-10s | %-8s | %-8s | %s", "Concurrency", "QPS", "Avg Latency",
                "P99", "Err Rate", "Status"));

        while (true) {
            System.gc(); // 尝试每轮前清理内存，减少GC对本轮的影响
            Thread.sleep(1000); // 冷却时间

            logger.info(">>> 正在测试并发度: {}", currentConcurrency);

            // 运行一轮测试
            TestResult result = runAsyncPhase(rpcClient, currentConcurrency, TEST_DURATION_SECONDS, true);

            // 格式化输出当前轮次结果
            String status = "OK";
            boolean stop = false;

            // 1. 检查错误率
            double errorRate = (double) result.failCount / (result.successCount + result.failCount);
            if (errorRate > ERROR_RATE_THRESHOLD) {
                status = "FAIL (Err Rate)";
                stop = true;
            }

            // 2. 检查 QPS 是否下滑 (拐点判定)
            // 允许 5% 的波动，如果当前 QPS 小于历史最高 QPS 的 90%，认为系统已过载
            if (!stop && maxQps > 0 && result.qps < maxQps * 0.90) {
                status = "OVERLOAD (QPS Drop)";
                stop = true;
            }

            // 更新最大值
            if (result.qps > maxQps) {
                maxQps = result.qps;
                bestConcurrency = currentConcurrency;
                status = "NEW BEST";
            }

            String logLine = String.format("%-12d | %-10.2f | %-10.2f | %-8d | %-8.2f%% | %s",
                    currentConcurrency, result.qps, result.avgLatency, result.p99Latency, errorRate * 100, status);
            logger.info(logLine);
            reportLines.add(logLine);

            if (stop) {
                logger.warn("探测结束：{}", status);
                break;
            }

            // 增加并发度，进入下一轮
            currentConcurrency += STEP_CONCURRENCY;
        }

        // 最终报告
        System.out.println("\n\n");
        System.out.println("############################################################");
        System.out.println("#                 极限性能测试报告                         #");
        System.out.println("############################################################");
        System.out.println("峰值 QPS: " + String.format("%.2f", maxQps));
        System.out.println("最佳并发度: " + bestConcurrency);
        System.out.println("------------------------------------------------------------");
        for (String line : reportLines) {
            System.out.println(line);
        }
        System.out.println("############################################################");
    }

    /**
     * 执行单轮压测
     * 
     * @param concurrency     并发限制（Semaphore 大小）
     * @param durationSeconds 持续时间
     */
    private TestResult runAsyncPhase(NettyRpcClient client, int concurrency, int durationSeconds, boolean record)
            throws InterruptedException {
        Semaphore semaphore = new Semaphore(concurrency);
        LongAdder successCount = new LongAdder();
        LongAdder failCount = new LongAdder();
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>(); // 用于计算 P99

        long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        AtomicInteger activeThreads = new AtomicInteger(0);

        // 启动发压线程 (Loop until time is up)
        // 为了避免创建过多线程对象，我们创建一组 Worker 线程持续发送请求
        int workerCount = Math.min(concurrency, Runtime.getRuntime().availableProcessors() * 4);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CountDownLatch finishLatch = new CountDownLatch(workerCount);

        long startNano = System.nanoTime();

        for (int i = 0; i < workerCount; i++) {
            executor.submit(() -> {
                while (System.currentTimeMillis() < endTime) {
                    try {
                        semaphore.acquire(); // 流量控制核心

                        RpcRequest request = buildRequest();
                        long reqStart = System.nanoTime();

                        client.sendRequestAsync(request).whenComplete((response, ex) -> {
                            try {
                                long reqEnd = System.nanoTime();
                                if (record) {
                                    long latencyUs = (reqEnd - reqStart) / 1000;
                                    latencies.add(latencyUs);

                                    if (ex == null && response != null && response.getCode() == 200) {
                                        successCount.increment();
                                    } else {
                                        failCount.increment();
                                    }
                                }
                            } finally {
                                semaphore.release(); // 请求完成（无论成功失败），释放信号量
                            }
                        });

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        semaphore.release();
                    }
                }
                finishLatch.countDown();
            });
        }

        finishLatch.await();
        executor.shutdownNow();

        // 等待所有在此期间发出的异步请求回调回来（稍微等一下，防止统计丢失）
        // 实际测试中，为了精确 QPS，我们只计算在此时间窗口内发起的。
        // 此处简单处理，直接计算。
        long totalTimeNs = System.nanoTime() - startNano;

        return new TestResult(
                successCount.sum(),
                failCount.sum(),
                totalTimeNs,
                latencies);
    }

    private static RpcRequest buildRequest() {
        return RpcRequest.builder()
                .interfaceName(EchoService.class.getName())
                .methodName("echo")
                .params(new Object[] { "hello" })
                .paramsType(new Class[] { String.class })
                .requestId(UUID.randomUUID().toString())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    // ----------------- 辅助类与方法 -----------------

    static class TestResult {
        long successCount;
        long failCount;
        double qps;
        double avgLatency;
        long p99Latency;

        public TestResult(long success, long fail, long totalTimeNs, ConcurrentLinkedQueue<Long> latencies) {
            this.successCount = success;
            this.failCount = fail;
            double totalTimeSec = totalTimeNs / 1_000_000_000.0;
            this.qps = (success + fail) / totalTimeSec;

            if (!latencies.isEmpty()) {
                List<Long> sorted = new ArrayList<>(latencies);
                Collections.sort(sorted);
                this.avgLatency = sorted.stream().mapToLong(Long::longValue).average().orElse(0);
                int p99Idx = (int) (sorted.size() * 0.99);
                if (p99Idx >= sorted.size())
                    p99Idx = sorted.size() - 1;
                this.p99Latency = sorted.get(p99Idx);
            }
        }
    }

    private static void startServer() throws Exception {
        // 简单的服务器启动逻辑 (与原版相同，省略具体日志以节省篇幅)
        ExecutorService serverExecutor = Executors.newFixedThreadPool(SERVER_NODE_COUNT);
        for (int i = 0; i < SERVER_NODE_COUNT; i++) {
            int port = SERVER_START_PORT + i;
            serverExecutor.submit(() -> {
                try {
                    ServiceProvider sp = new ServiceProvider(SERVER_HOST, port);
                    sp.provideServiceInterface(new EchoServiceImpl(), true);
                    new NettyRPCServer(sp).start(port);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        waitForServicesRegistered(SERVER_NODE_COUNT, 60);
    }

    private static void waitForServicesRegistered(int expectedNodeCount, int maxWaitSeconds) throws Exception {
        CuratorFramework zkClient = CuratorFrameworkFactory.newClient("127.0.0.1:2285",
                new ExponentialBackoffRetry(1000, 3));
        zkClient.start();
        String servicePath = "/MY_RPC/common.service.EchoService";
        long end = System.currentTimeMillis() + maxWaitSeconds * 1000L;
        while (System.currentTimeMillis() < end) {
            try {
                if (zkClient.checkExists().forPath(servicePath) != null &&
                        zkClient.getChildren().forPath(servicePath).size() >= expectedNodeCount)
                    return;
            } catch (Exception ignored) {
            }
            Thread.sleep(1000);
        }
    }
}