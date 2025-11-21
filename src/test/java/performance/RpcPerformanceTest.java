package performance;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import client.proxy.ClientProxy;
import client.retry.GuavaRetry;
import client.rpcClient.impl.NettyRpcClient;
import client.serviceCenter.balance.LoadBalance;
import common.pojo.User;
import common.result.Result;
import common.service.UserService;
import common.service.impl.UserServiceImpl;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.provider.ServiceProvider;
import server.server.impl.NettyRPCServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RPC Framework Performance Test
 * Measures QPS, Latency (Avg, P95, P99), and Success Rate.
 */
public class RpcPerformanceTest {
    private static final Logger logger = LoggerFactory.getLogger(RpcPerformanceTest.class);

    static {
        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            loggerContext.getLogger("server").setLevel(Level.WARN);
            loggerContext.getLogger("common").setLevel(Level.WARN);
            loggerContext.getLogger("performance").setLevel(Level.INFO);
            loggerContext.getLogger("client").setLevel(Level.WARN);
            loggerContext.getLogger("org.apache.curator").setLevel(Level.ERROR);
            loggerContext.getLogger("org.apache.zookeeper").setLevel(Level.ERROR);
        } catch (Exception e) {
            System.err.println("✗ 初始化配置失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Configuration
    private static final int THREAD_COUNT = 75;
    private static final int TOTAL_REQUESTS = 20000;
    private static final int WARMUP_REQUESTS = 2000; // 增加预热请求数

    // Server Configuration
    private static final int SERVER_START_PORT = 9991; // 服务器起始端口
    private static final int SERVER_NODE_COUNT = 5; // 服务节点数量
    private static final String SERVER_HOST = "127.0.0.1"; // 服务器主机地址

    // Metrics - 使用 ConcurrentLinkedQueue 减少锁竞争
    private static final ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failCount = new AtomicInteger(0);

    private static UserService userService;
    private static final Random random = new Random();
    // Use ThreadLocal to ensure each thread manages its own users, preventing race
    // conditions between Delete and Update/Get
    private static final ThreadLocal<List<Long>> threadLocalUserIds = ThreadLocal.withInitial(ArrayList::new);

    @org.junit.jupiter.api.Test
    public void testRpcPerformance() throws Exception {
        // Reset state
        // Reset state
        // 1. Start Server
        startServer();

        // 2. Initialize Client
        initClient();

        // 3. Warmup
        logger.info("Starting Warmup Phase ({} requests)...", WARMUP_REQUESTS);
        runTestPhase(WARMUP_REQUESTS, false);
        logger.info("Warmup Finished.");

        // Reset metrics for actual test
        latencies.clear();
        successCount.set(0);
        failCount.set(0);

        // 4. Actual Performance Test
        logger.info("Starting Performance Test ({} threads, {} requests)...", THREAD_COUNT, TOTAL_REQUESTS);
        long startTime = System.nanoTime();

        runTestPhase(TOTAL_REQUESTS, true);

        long endTime = System.nanoTime();
        long totalTimeNs = endTime - startTime;

        // 5. Report Metrics
        reportMetrics(totalTimeNs);

        // 6. Assertions - 验证性能指标
        int totalOps = successCount.get() + failCount.get();
        double successRate = (double) successCount.get() / totalOps * 100;
        List<Long> sortedLatencies = new ArrayList<>(latencies);
        Collections.sort(sortedLatencies);
        double avgLatency = sortedLatencies.stream().mapToLong(Long::longValue).average().orElse(0);

        // 断言成功率应 > 80% (在高并发下ZooKeeper服务发现有一定失败率)
        assertTrue(successRate > 80.0,
                String.format("成功率过低: %.2f%% (期望 > 80%%)", successRate));

        // 断言平均延迟应 < 50ms (50000 微秒) - 考虑到网络开销
        assertTrue(avgLatency < 50000,
                String.format("平均延迟过高: %.2f µs (期望 < 50000 µs)", avgLatency));

        logger.info(String.format("性能测试通过！成功率: %.2f%%, 平均延迟: %.2f µs", successRate, avgLatency));
    }

    /**
     * 主动检查所有服务节点是否已注册到ZK
     * 
     * @param expectedNodeCount 期望的服务节点数量
     * @param maxWaitSeconds    最大等待秒数
     */
    private static void waitForServicesRegistered(int expectedNodeCount, int maxWaitSeconds) throws Exception {
        CuratorFramework zkClient = CuratorFrameworkFactory.builder()
                .connectString("127.0.0.1:2285")
                .sessionTimeoutMs(40000)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .namespace("MY_RPC")
                .build();

        zkClient.start();
        // 等待ZK客户端连接成功
        zkClient.blockUntilConnected();

        String servicePath = "/common.service.UserService";

        try {
            for (int i = 0; i < maxWaitSeconds; i++) {
                try {
                    List<String> nodes = zkClient.getChildren().forPath(servicePath);
                    if (nodes != null && nodes.size() >= expectedNodeCount) {
                        logger.info("✓ 服务注册验证通过: {}/{} 个节点已注册",
                                nodes.size(), expectedNodeCount);
                        logger.debug("已注册节点: {}", nodes);
                        return;
                    }
                    logger.debug("等待服务注册... 当前: {}/{} ({}/{}s)",
                            nodes != null ? nodes.size() : 0, expectedNodeCount, i + 1, maxWaitSeconds);
                } catch (Exception e) {
                    logger.debug("ZK路径尚未创建: {} ({}/{}s)", servicePath, i + 1, maxWaitSeconds);
                }
                Thread.sleep(1000);
            }
            throw new RuntimeException(
                    String.format("服务注册超时！等待%d秒后仍未检测到%d个服务节点",
                            maxWaitSeconds, expectedNodeCount));
        } finally {
            zkClient.close();
        }
    }

    private static void startServer() throws Exception {
        logger.warn("开始启动 {} 个服务节点...", SERVER_NODE_COUNT);

        // 为每个服务节点创建独立的线程启动服务器
        for (int i = 0; i < SERVER_NODE_COUNT; i++) {
            final int port = SERVER_START_PORT + i;
            final int nodeIndex = i + 1;

            Thread serverThread = new Thread(() -> {
                try {
                    logger.info("[节点{}] 正在启动，端口: {}", nodeIndex, port);

                    // 创建 ServiceProvider
                    ServiceProvider serviceProvider = new ServiceProvider(SERVER_HOST, port);

                    // 创建 UserService 实例并注册到服务提供者
                    UserService userService = new UserServiceImpl();
                    serviceProvider.provideServiceInterface(userService, true); // canRetry = true

                    logger.info("[节点{}] UserService 已注册到 ZooKeeper，端口: {}", nodeIndex, port);

                    // 创建并启动 Netty RPC Server
                    NettyRPCServer server = new NettyRPCServer(serviceProvider);
                    server.start(port); // 这是阻塞调用，会一直运行直到服务器关闭

                } catch (Exception e) {
                    logger.error("[节点{}] 服务器启动失败，端口: {}", nodeIndex, port, e);
                }
            }, "ServerNode-" + nodeIndex);

            serverThread.setDaemon(true); // 设置为守护线程，测试结束时自动关闭
            serverThread.start();

            // 每启动一个节点后短暂等待，避免端口冲突
            Thread.sleep(500);
        }

        // 主动检查服务注册状态，而不是盲目等待
        logger.warn("等待所有 {} 个服务节点注册到 ZooKeeper...", SERVER_NODE_COUNT);
        waitForServicesRegistered(SERVER_NODE_COUNT, 60); // 最多等60秒
        logger.warn("所有服务已成功注册，准备开始测试");

        // 额外等待2秒确保Netty服务器完全就绪
        Thread.sleep(2000);
        logger.info("所有 {} 个服务节点已就绪", SERVER_NODE_COUNT);
    }

    private static void initClient() {
        NettyRpcClient rpcClient = new NettyRpcClient();
        ClientProxy clientProxy = new ClientProxy(rpcClient, new GuavaRetry());
        userService = clientProxy.getProxy(UserService.class);
    }

    private static void runTestPhase(int requestCount, boolean recordMetrics) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(requestCount);

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                try {
                    long start = System.nanoTime();
                    boolean success = performRandomOperation();
                    long end = System.nanoTime();

                    if (recordMetrics) {
                        latencies.add((end - start) / 1000); // Microseconds
                        if (success) {
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    if (recordMetrics) {
                        failCount.incrementAndGet();
                    }
                    logger.error("Request failed", e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
    }

    private static boolean performRandomOperation() {
        List<Long> myUserIds = threadLocalUserIds.get();
        int op = random.nextInt(4);

        // If no users owned by this thread, force Insert
        if (myUserIds.isEmpty()) {
            op = 0;
        }

        Long userId = -1L;
        int index = -1;

        // Select a user for non-insert operations
        if (op != 0) {
            index = random.nextInt(myUserIds.size());
            userId = myUserIds.get(index);
        }

        Result<?> result;

        try {
            switch (op) {
                case 0: // Insert
                    User user = User.builder()
                            .userName("User" + System.nanoTime())
                            .sex(random.nextBoolean())
                            .age(random.nextInt(100))
                            .email("user" + System.nanoTime() + "@test.com")
                            .build();
                    Result<Long> insertResult = userService.insertUser(user).get();
                    result = insertResult;
                    // Only add to local list if insert succeeded
                    if (result != null && result.isSuccess()) {
                        myUserIds.add(insertResult.getData());
                    }
                    break;
                case 1: // Get
                    result = userService.getUserById(userId).get();
                    break;
                case 2: // Update
                    User updateUser = User.builder()
                            .id(userId)
                            .userName("UserUpdated" + userId)
                            .build();
                    result = userService.updateUser(updateUser).get();
                    break;
                case 3: // Delete
                    result = userService.deleteUserById(userId).get();
                    // Only remove from local list if delete succeeded
                    if (result != null && result.isSuccess()) {
                        myUserIds.remove(index);
                    }
                    break;
                default:
                    return false;
            }
            return result != null && result.isSuccess();
        } catch (Exception e) {
            return false;
        }
    }

    private static void reportMetrics(long totalTimeNs) {
        double totalTimeSec = totalTimeNs / 1_000_000_000.0;
        int totalOps = successCount.get() + failCount.get();
        double qps = totalOps / totalTimeSec;

        List<Long> sortedLatencies = new ArrayList<>(latencies);
        Collections.sort(sortedLatencies);

        double avgLatency = sortedLatencies.stream().mapToLong(Long::longValue).average().orElse(0);
        long p50 = getPercentile(sortedLatencies, 50);
        long p95 = getPercentile(sortedLatencies, 95);
        long p99 = getPercentile(sortedLatencies, 99);

        logger.info("==================================================");
        logger.info("Performance Test Results");
        logger.info("==================================================");
        logger.info("Total Requests: {}", totalOps);
        logger.info("Successful: {}", successCount.get());
        logger.info("Failed: {}", failCount.get());
        logger.info("Total Time: {} s", String.format("%.2f", totalTimeSec));
        logger.info("QPS: {}", String.format("%.2f", qps));
        logger.info("Latency (us):");
        logger.info("  Avg: {}", String.format("%.2f", avgLatency));
        logger.info("  P50: {}", p50);
        logger.info("  P95: {}", p95);
        logger.info("  P99: {}", p99);
        logger.info("==================================================");
    }

    private static long getPercentile(List<Long> sortedData, int percentile) {
        if (sortedData.isEmpty())
            return 0;
        int index = (int) Math.ceil(percentile / 100.0 * sortedData.size()) - 1;
        return sortedData.get(Math.max(0, Math.min(index, sortedData.size() - 1)));
    }
}
