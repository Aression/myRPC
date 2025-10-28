package basic;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ProducerConsumerWithLatch {

    // --- 共享资源和控制变量 ---
    private static final BlockingQueue<String> queue = new ArrayBlockingQueue<>(10);
    
    // 目标消费数量，达到此值后停止
    private static final int TARGET_CONSUMPTION_COUNT = 100;
    
    // CountDownLatch：初始值为 1，表示需要等待 1 次倒计时（即第 100 次消费）
    private static final CountDownLatch latch = new CountDownLatch(1);
    
    // 追踪已生产的产品总数
    private static final AtomicInteger productCounter = new AtomicInteger(0);

    // 追踪已消费的产品总数（使用 AtomicInteger 仅作计数和并发安全，不作为停止机制）
    private static final AtomicInteger consumedCounter = new AtomicInteger(0); 

    // ----------------------------------------------------------------------
    // 生产者类 (实现 Callable<Integer>)
    // ----------------------------------------------------------------------
    static class Producer implements Callable<Integer> {
        private final long productionRateMs;

        public Producer(long rateMs) {
            this.productionRateMs = rateMs;
        }

        @Override
        public Integer call() {
            try {
                // 持续生产，直到主线程关闭线程池或被 CountDownLatch 间接触发停止
                while (!Thread.currentThread().isInterrupted()) {
                    int productId = productCounter.incrementAndGet();
                    String product = "Product-" + productId;

                    // put() 方法可能抛出 InterruptedException
                    queue.put(product);
                    
                    if (productId % 20 == 0 || productId <= 5) {
                         System.out.println("✅ Producer produced: " + product + " (Queue size: " + queue.size() + ")");
                    }

                    // 恒定速率等待
                    Thread.sleep(productionRateMs);
                }
            } catch (InterruptedException e) {
                // 收到中断信号，正常退出
                Thread.currentThread().interrupt();
            } 
            // 返回生产总数
            return productCounter.get();
        }
    }

    // ----------------------------------------------------------------------
    // 消费者类 (实现 Callable<Integer>)
    // ----------------------------------------------------------------------
    static class Consumer implements Callable<Integer> {
        private final String name;

        public Consumer(int id) {
            this.name = "Consumer-" + id;
        }

        @Override
        public Integer call() {
            int localConsumed = 0;
            try {
                // 持续消费，直到主线程关闭线程池或被 CountDownLatch 间接触发停止
                while (!Thread.currentThread().isInterrupted()) {
                    // take() 方法可能抛出 InterruptedException
                    String product = queue.take(); 
                    
                    // 模拟消费时间，保持消费速度大于生产速度
                    Thread.sleep(10); 

                    int currentConsumed = consumedCounter.incrementAndGet();
                    localConsumed++;
                    
                    if (currentConsumed % 20 == 0 || currentConsumed <= 5 || currentConsumed >= TARGET_CONSUMPTION_COUNT - 5) {
                        System.out.println("  ➡️ " + name + " consumed: " + product + 
                                           " | Total: " + currentConsumed + "/" + TARGET_CONSUMPTION_COUNT);
                    }
                    
                    // --- 核心停止逻辑：CountDownLatch 倒计时 ---
                    if (currentConsumed == TARGET_CONSUMPTION_COUNT) {
                        System.out.println("\n🔥 Target reached! " + name + " initiated countdown.");
                        // 倒计时减 1，由于初始值为 1，这将唤醒 main 线程
                        latch.countDown(); 
                        
                        // 此时当前线程可以继续消费队列中剩余的产品，直到被主线程中断。
                    }
                    
                    // 可选：如果计数已完成，且队列为空，可以自己退出
                    if (latch.getCount() == 0 && queue.isEmpty()) {
                        break; 
                    }
                }
            } catch (InterruptedException e) {
                // 收到中断信号，正常退出
                Thread.currentThread().interrupt();
            } finally {
                System.out.println("  🛑 " + name + " stopped. Consumed by self: " + localConsumed);
            }
            // 返回该消费者自己消费的总数
            return localConsumed; 
        }
    }

    // ----------------------------------------------------------------------
    // 主执行方法
    // ----------------------------------------------------------------------
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        final int NUM_CONSUMERS = 3; 
        final long PRODUCER_RATE_MS = 50; 

        System.out.println("Starting Producer-Consumer Model with CountDownLatch...");
        System.out.println("Target Consumption: " + TARGET_CONSUMPTION_COUNT);
        System.out.println("--------------------------------------------------");

        // 创建一个固定线程池，包含生产者和所有消费者
        ExecutorService executor = Executors.newFixedThreadPool(NUM_CONSUMERS + 1);
        
        // 用于保存 Callable 任务的 Future 结果
        Future<Integer> producerFuture = null;
        Future<?>[] consumerFutures = new Future[NUM_CONSUMERS];

        // 1. 启动生产者
        Producer producer = new Producer(PRODUCER_RATE_MS);
        producerFuture = executor.submit(producer);

        // 2. 启动多个消费者
        for (int i = 0; i < NUM_CONSUMERS; i++) {
            Consumer consumer = new Consumer(i + 1);
            consumerFutures[i] = executor.submit(consumer);
        }

        // 3. --- 主线程等待 CountDownLatch 归零 ---
        System.out.println("\nMain thread awaiting CountDownLatch...");
        // 阻塞主线程，直到某个消费者调用 latch.countDown()
        latch.await(); 
        
        // CountDownLatch 归零，表示已达到目标消费数量

        // 4. 关闭线程池
        System.out.println("\nShutdown initiated. Interrupting all tasks...");
        executor.shutdownNow(); // 中断所有正在执行或等待的任务

        // 5. 等待线程池完全终止并获取结果
        if (executor.awaitTermination(5, TimeUnit.SECONDS)) {
            System.out.println("\n----------------- FINISHED -----------------");
            
            // 尝试获取生产者和消费者的返回值
            try {
                System.out.println("Total products produced: " + producerFuture.get());
            } catch (CancellationException | ExecutionException e) {
                // 生产者任务被取消/中断，但我们有 productCounter 追踪
                System.out.println("Total products produced: " + productCounter.get() + " (Producer interrupted)");
            }

            System.out.println("Total products consumed: " + consumedCounter.get());
            System.out.println("Shutdown successful.");
        } else {
            System.err.println("Some tasks did not terminate promptly.");
        }
    }
}