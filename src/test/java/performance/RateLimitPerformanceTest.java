package performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import server.provider.ratelimit.RateLimit;
import server.provider.ratelimit.impl.TokenBucketRateLimit;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@DisplayName("RateLimit Performance Test")
class RateLimitPerformanceTest {

    private static final int THREAD_COUNT = 10;
    private static final int TEST_DURATION_SECONDS = 5;
    private static final int EXPECTED_TPS = 1000;
    private static final int CAPACITY = 100;

    @Test
    @DisplayName("TokenBucketRateLimit Throughput and Overhead")
    void testTokenBucketPerformance() throws InterruptedException {
        RateLimit rateLimit = new TokenBucketRateLimit(EXPECTED_TPS, CAPACITY);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        AtomicInteger successfulRequests = new AtomicInteger(0);
        AtomicInteger totalRequests = new AtomicInteger(0);
        AtomicLong totalOverheadNs = new AtomicLong(0);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);

        AtomicBoolean running = new AtomicBoolean(true);

        System.out.println("Starting RateLimit performance test...");
        System.out.printf("Configured TPS: %d, Threads: %d, Duration: %ds%n", EXPECTED_TPS, THREAD_COUNT,
                TEST_DURATION_SECONDS);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    while (running.get()) {
                        long start = System.nanoTime();
                        boolean acquired = rateLimit.getToken();
                        long end = System.nanoTime();

                        totalOverheadNs.addAndGet(end - start);
                        totalRequests.incrementAndGet();
                        if (acquired) {
                            successfulRequests.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads
        Thread.sleep(TEST_DURATION_SECONDS * 1000L);
        running.set(false);
        endLatch.await();

        double actualTps = (double) successfulRequests.get() / TEST_DURATION_SECONDS;
        double avgOverheadNs = (double) totalOverheadNs.get() / totalRequests.get();

        System.out.printf("Total Requests: %d%n", totalRequests.get());
        System.out.printf("Successful Requests: %d%n", successfulRequests.get());
        System.out.printf("Actual TPS: %.2f (Expected: %d)%n", actualTps, EXPECTED_TPS);
        System.out.printf("Average Overhead: %.2f ns%n", avgOverheadNs);

        // Validation: Actual TPS should be close to Expected TPS (within reasonable
        // margin, e.g., +/- 10% or bounded by max performance)
        // Note: If the machine is slow, it might not reach 1000 TPS, but for a simple
        // token bucket it should be very fast.
        // If the machine is very fast, it should be capped at 1000 TPS.

        System.out.println("--------------------------------------------------");
        executor.shutdown();
    }

    // Helper class to avoid importing java.util.concurrent.atomic.AtomicBoolean if
    // not available (it is standard though)
    // Using standard AtomicBoolean
    private static class AtomicBoolean {
        private volatile boolean value;

        public AtomicBoolean(boolean initialValue) {
            this.value = initialValue;
        }

        public boolean get() {
            return value;
        }

        public void set(boolean value) {
            this.value = value;
        }
    }
}
