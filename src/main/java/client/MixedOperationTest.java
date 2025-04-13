package client;

import client.proxy.ClientProxy;
import common.pojo.User;
import common.result.Result;
import common.service.UserService;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MixedOperationTest {
    private static final int TOTAL_OPERATIONS = 10000; // 总操作数
    private static final int THREAD_COUNT = 10;        // 并发线程数
    private static final int BATCH_SIZE = 1000;        // 每批处理数量
    private static final double INSERT_RATIO = 0.7;    // 插入操作比例

    // 用户信息模拟数据
    private static final String[] FIRST_NAMES = {"张", "王", "李", "赵", "刘"};
    private static final String[] LAST_NAMES = {"伟", "芳", "娜", "敏", "静"};

    public static void main(String[] args) {
        ClientProxy clientProxy = new ClientProxy();
        UserService userService = clientProxy.getProxy(UserService.class);

        // 测试混合操作
        testMixedOperations(userService);
    }

    /**
     * 测试混合操作性能
     */
    private static void testMixedOperations(UserService userService) {
        System.out.println("\n开始混合操作性能测试...");
        System.out.println("总操作数: " + TOTAL_OPERATIONS);
        System.out.println("并发线程数: " + THREAD_COUNT);
        System.out.println("每批处理数量: " + BATCH_SIZE);
        System.out.println("插入操作比例: " + INSERT_RATIO);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger insertSuccessCount = new AtomicInteger(0);
        AtomicInteger insertFailureCount = new AtomicInteger(0);
        AtomicInteger deleteSuccessCount = new AtomicInteger(0);
        AtomicInteger deleteFailureCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    int startIndex = threadIndex * (TOTAL_OPERATIONS / THREAD_COUNT);
                    int endIndex = Math.min(startIndex + (TOTAL_OPERATIONS / THREAD_COUNT), TOTAL_OPERATIONS);
                    
                    Random random = new Random();
                    for (int j = startIndex; j < endIndex; j++) {
                        if (random.nextDouble() < INSERT_RATIO) {
                            // 插入操作
                            User user = generateRealisticUser(j);
                            Result<Integer> result = userService.insertUserId(user);
                            if (result.isSuccess() && result.getData() != null) {
                                insertSuccessCount.incrementAndGet();
                            } else {
                                insertFailureCount.incrementAndGet();
                            }
                        } else {
                            // 删除操作
                            Result<Boolean> result = userService.deleteUserById(j);
                            if (result.isSuccess() && Boolean.TRUE.equals(result.getData())) {
                                deleteSuccessCount.incrementAndGet();
                            } else {
                                deleteFailureCount.incrementAndGet();
                            }
                        }
                        
                        if ((j - startIndex + 1) % BATCH_SIZE == 0) {
                            System.out.printf("线程 %d: 已处理 %d/%d 个操作%n", 
                                threadIndex, j - startIndex + 1, endIndex - startIndex);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        long endTime = System.currentTimeMillis();
        printStatistics(insertSuccessCount.get(), insertFailureCount.get(),
                      deleteSuccessCount.get(), deleteFailureCount.get(),
                      endTime - startTime);
    }

    /**
     * 打印统计信息
     */
    private static void printStatistics(int insertSuccess, int insertFailure,
                                      int deleteSuccess, int deleteFailure,
                                      long totalTime) {
        System.out.println("\n混合操作性能测试结果:");
        System.out.println("插入成功次数: " + insertSuccess);
        System.out.println("插入失败次数: " + insertFailure);
        System.out.println("删除成功次数: " + deleteSuccess);
        System.out.println("删除失败次数: " + deleteFailure);
        System.out.println("总耗时: " + totalTime + "ms");
        System.out.println("平均每秒处理数: " + (TOTAL_OPERATIONS * 1000.0 / totalTime));
    }

    /**
     * 生成真实用户数据
     */
    private static User generateRealisticUser(int id) {
        Random random = new Random();
        return User.builder()
                .id(id)
                .userName(FIRST_NAMES[random.nextInt(FIRST_NAMES.length)] + 
                         LAST_NAMES[random.nextInt(LAST_NAMES.length)])
                .sex(random.nextBoolean())
                .build();
    }
}