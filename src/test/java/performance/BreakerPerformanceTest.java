package performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import client.proxy.breaker.Breaker;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@DisplayName("Circuit Breaker Performance Test")
class BreakerPerformanceTest {

    private static final int THREAD_COUNT = 10;
    private static final int REQUESTS_PER_THREAD = 100_000;
    private static final int TOTAL_REQUESTS = THREAD_COUNT * REQUESTS_PER_THREAD;

    @Test
    @DisplayName("Breaker Overhead in Closed State")
    void testBreakerOverhead() throws InterruptedException {
        // High threshold to keep it closed
        Breaker breaker = new Breaker(1000000, 0.5, 1000);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        AtomicLong totalTimeNs = new AtomicLong(0);

        System.out.println("Starting Breaker overhead test (Closed State)...");
        long startTime = System.nanoTime();

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                long threadTotalTime = 0;
                try {
                    for (int j = 0; j < REQUESTS_PER_THREAD; j++) {
                        long start = System.nanoTime();
                        boolean allowed = breaker.allowRequest();
                        long end = System.nanoTime();
                        threadTotalTime += (end - start);

                        if (allowed) {
                            // Simulate success to keep it closed
                            breaker.recordSuccess();
                        }
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

        double avgOverheadNs = (double) totalTimeNs.get() / TOTAL_REQUESTS;
        double ops = (double) TOTAL_REQUESTS / durationMs * 1000;

        System.out.printf("Total Requests: %d%n", TOTAL_REQUESTS);
        System.out.printf("Total Time: %d ms%n", durationMs);
        System.out.printf("Operations Per Second: %.2f%n", ops);
        System.out.printf("Average Overhead: %.2f ns%n", avgOverheadNs);
        System.out.println("--------------------------------------------------");

        executor.shutdown();
    }
}
