package performance;

import client.serviceCenter.balance.LoadBalance;
import client.serviceCenter.balance.LoadBalance.BalanceType;
import client.serviceCenter.balance.impl.ConsistencyHashBalance;
import client.serviceCenter.balance.impl.RandomLoadBalance;
import client.serviceCenter.balance.impl.SequenceLoadBalance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@DisplayName("LoadBalance Performance Test")
class LoadBalancePerformanceTest {

    private static final int ADDRESS_COUNT = 100;
    private static final int THREAD_COUNT = 10;
    private static final int REQUESTS_PER_THREAD = 100_000;
    private static final int TOTAL_REQUESTS = THREAD_COUNT * REQUESTS_PER_THREAD;

    private List<InetSocketAddress> addressList;
    private String serviceName = "com.test.PerformanceService";

    @BeforeEach
    void setUp() {
        addressList = new ArrayList<>();
        for (int i = 0; i < ADDRESS_COUNT; i++) {
            addressList.add(new InetSocketAddress("192.168.0." + i, 8080));
        }
    }

    @Test
    @DisplayName("RandomLoadBalance Performance")
    void testRandomLoadBalance() throws InterruptedException {
        LoadBalance loadBalance = new RandomLoadBalance();
        runPerformanceTest("RandomLoadBalance", loadBalance);
    }

    @Test
    @DisplayName("SequenceLoadBalance Performance")
    void testSequenceLoadBalance() throws InterruptedException {
        LoadBalance loadBalance = new SequenceLoadBalance();
        runPerformanceTest("SequenceLoadBalance", loadBalance);
    }

    @Test
    @DisplayName("ConsistencyHashBalance Performance")
    void testConsistencyHashBalance() throws InterruptedException {
        LoadBalance loadBalance = new ConsistencyHashBalance();
        runPerformanceTest("ConsistencyHashBalance", loadBalance);
    }

    private void runPerformanceTest(String name, LoadBalance loadBalance) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        AtomicLong totalTimeNs = new AtomicLong(0);
        Map<InetSocketAddress, Integer> distribution = new ConcurrentHashMap<>();

        System.out.println("Starting performance test for: " + name);
        long startTime = System.nanoTime();

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                long threadTotalTime = 0;
                try {
                    for (int j = 0; j < REQUESTS_PER_THREAD; j++) {
                        long start = System.nanoTime();
                        // For ConsistencyHash, we simulate different requests
                        InetSocketAddress address = loadBalance.select(serviceName, addressList, j);
                        long end = System.nanoTime();
                        threadTotalTime += (end - start);

                        distribution.merge(address, 1, Integer::sum);
                    }
                } finally {
                    totalTimeNs.addAndGet(threadTotalTime);
                    latch.countDown();
                }
            });
        }

        latch.await();
        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        double avgLatencyNs = (double) totalTimeNs.get() / TOTAL_REQUESTS;
        double tps = (double) TOTAL_REQUESTS / durationMs * 1000;

        System.out.printf("[%s] Total Requests: %d%n", name, TOTAL_REQUESTS);
        System.out.printf("[%s] Total Time: %d ms%n", name, durationMs);
        System.out.printf("[%s] TPS: %.2f%n", name, tps);
        System.out.printf("[%s] Avg Latency: %.2f ns%n", name, avgLatencyNs);

        // Calculate standard deviation for distribution
        double mean = (double) TOTAL_REQUESTS / ADDRESS_COUNT;
        double variance = distribution.values().stream()
                .mapToDouble(count -> Math.pow(count - mean, 2))
                .sum() / ADDRESS_COUNT;
        double stdDev = Math.sqrt(variance);

        System.out.printf("[%s] Distribution StdDev: %.2f (Lower is better)%n", name, stdDev);
        System.out.println("--------------------------------------------------");

        executor.shutdown();
    }
}
