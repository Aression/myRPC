package client;

import client.proxy.ClientProxy;
import common.pojo.User;
import common.result.Result;
import common.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class MultiThreadedUserCRUDTest {
    private static final Logger logger = LoggerFactory.getLogger(MultiThreadedUserCRUDTest.class);
    private static final int TOTAL_OPERATIONS = 1000;
    private static final int THREAD_COUNT = 10;
    private static final int BATCH_SIZE = 100;

    // 使用线程安全的ConcurrentHashMap存储用户数据
    private static ConcurrentHashMap<Integer, User> userMap = new ConcurrentHashMap<>();
    // 使用ReentrantLock保证关键操作的线程安全
    private static ReentrantLock lock = new ReentrantLock();

    public static void main(String[] args) {
        logger.info("=== 开始多线程用户CRUD测试 ===");

        try {
            ClientProxy clientProxy = new ClientProxy();
            UserService userService = clientProxy.getProxy(UserService.class);

            // 准备测试数据
            logger.info("\n[准备阶段] 开始准备测试数据...");
            List<User> testUsers = prepareTestData(userService);
            logger.info("[准备阶段] 测试数据准备完成 - 成功: {}, 失败: {}", testUsers.size(), TOTAL_OPERATIONS - testUsers.size());

            // 执行多线程测试
            logger.info("\n[测试阶段] 开始多线程用户CRUD操作测试");
            logger.info("[测试阶段] 测试参数:");
            logger.info("  - 总操作数: {}", TOTAL_OPERATIONS);
            logger.info("  - 并发线程数: {}", THREAD_COUNT);
            logger.info("  - 每批处理数量: {}", BATCH_SIZE);

            long startTime = System.currentTimeMillis();
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            List<Future<Long>> futures = new ArrayList<>();

            for (int i = 0; i < THREAD_COUNT; i++) {
                futures.add(executor.submit(new CRUDTask(testUsers, userService)));
            }

            // 收集结果
            long totalResponseTime = 0;
            for (Future<Long> future : futures) {
                totalResponseTime += future.get();
            }

            long totalTimeMs = System.currentTimeMillis() - startTime;
            logger.info("\n[测试阶段] 多线程用户CRUD操作测试完成");
            logger.info("[测试阶段] 总耗时: {} ms", totalTimeMs);
            logger.info("[测试阶段] 平均响应时间: {} ms", totalResponseTime / (TOTAL_OPERATIONS * THREAD_COUNT));

            // 关闭线程池
            executor.shutdown();
            boolean terminated = executor.awaitTermination(5, TimeUnit.SECONDS);
            if (!terminated) {
                logger.warn("[测试阶段] 线程池未能在指定时间内关闭，尝试强制关闭");
                executor.shutdownNow();
            }

            logger.info("\n=== 多线程用户CRUD测试完成 ===");
        } catch (Exception e) {
            logger.error("[错误] 测试过程中出现异常: {}", e.getMessage(), e);
        }
    }

    private static List<User> prepareTestData(UserService userService) throws Exception {
        List<User> users = new ArrayList<>();
        Random random = new Random();

        logger.info("[准备阶段] 开始生成测试用户数据...");
        int successCount = 0;
        int failCount = 0;
        int retryCount = 0;
        final int MAX_RETRY = 3;

        for (int i = 0; i < TOTAL_OPERATIONS; i++) {
            User user = generateRealisticUser(i);
            Result<Integer> result = null;
            int currentRetry = 0;

            // 重试机制
            while (currentRetry < MAX_RETRY) {
                try {
                    result = userService.insertUser(user);
                    if (result.isSuccess()) {
                        users.add(user);
                        userMap.put(user.getId(), user); // 将用户数据存入ConcurrentHashMap
                        successCount++;
                        break;
                    } else {
                        currentRetry++;
                        retryCount++;
                        logger.warn("[准备阶段] 用户 {} 插入失败，重试次数: {}/{}", user.getId(), currentRetry, MAX_RETRY);
                        Thread.sleep(100); // 短暂延迟后重试
                    }
                } catch (Exception e) {
                    currentRetry++;
                    retryCount++;
                    logger.error("[准备阶段] 用户 {} 插入异常: {}", user.getId(), e.getMessage());
                    Thread.sleep(100);
                }
            }

            if (currentRetry >= MAX_RETRY) {
                failCount++;
                logger.error("[准备阶段] 用户 {} 插入失败，已达到最大重试次数", user.getId());
            }

            // 进度显示
            if ((i + 1) % 1000 == 0) {
                logger.info("[准备阶段] 进度: {}/{} (成功: {}, 失败: {}, 重试: {})",
                        i + 1, TOTAL_OPERATIONS, successCount, failCount, retryCount);
            }
        }

        logger.info("[准备阶段] 测试数据生成完成:");
        logger.info("  - 总尝试数: {}", TOTAL_OPERATIONS);
        logger.info("  - 成功数: {}", successCount);
        logger.info("  - 失败数: {}", failCount);
        logger.info("  - 重试次数: {}", retryCount);
        logger.info("  - 成功率: {}%", String.format("%.2f", 100.0 * successCount / TOTAL_OPERATIONS));

        return users;
    }

    private static class CRUDTask implements Callable<Long> {
        private final List<User> testUsers;
        private final UserService userService;
        private final Random random;

        public CRUDTask(List<User> testUsers, UserService userService) {
            this.testUsers = testUsers;
            this.userService = userService;
            this.random = new Random();
        }

        @Override
        public Long call() {
            long totalResponseTime = 0;

            for (int i = 0; i < BATCH_SIZE; i++) {
                try {
                    User user = testUsers.get(random.nextInt(testUsers.size()));
                    long startTime = System.currentTimeMillis();

                    // 随机选择CRUD操作
                    int operation = random.nextInt(4);
                    switch (operation) {
                        case 0:
                            // 创建用户
                            lock.lock();
                            try {
                                User newUser = generateRealisticUser(random.nextInt(1000));
                                Result<Integer> result = userService.insertUser(newUser);
                                if (result.isSuccess()) {
                                    userMap.put(newUser.getId(), newUser);
                                }
                            } finally {
                                lock.unlock();
                            }
                            break;
                        case 1:
                            // 读取用户
                            lock.lock();
                            try {
                                userService.getUserById(user.getId());
                            } finally {
                                lock.unlock();
                            }
                            break;
                        case 2:
                            // 更新用户
                            lock.lock();
                            try {
                                user.setUserName("Updated" + user.getUserName());
                                userService.updateUser(user);
                                userMap.put(user.getId(), user);
                            } finally {
                                lock.unlock();
                            }
                            break;
                        case 3:
                            // 删除用户
                            lock.lock();
                            try {
                                userService.deleteUserById(user.getId());
                                userMap.remove(user.getId());
                            } finally {
                                lock.unlock();
                            }
                            break;
                    }

                    long responseTime = System.currentTimeMillis() - startTime;
                    totalResponseTime += responseTime;
                } catch (Exception e) {
                    logger.error("[测试任务] 执行出错: {}", e.getMessage(), e);
                }
            }

            return totalResponseTime;
        }
    }

    private static User generateRealisticUser(int id) {
        Random random = new Random();
        String[] firstNames = {"张", "王", "李", "赵", "刘"};
        String[] lastNames = {"伟", "芳", "娜", "敏", "静"};
        String firstName = firstNames[random.nextInt(firstNames.length)];
        String lastName = lastNames[random.nextInt(lastNames.length)];
        String userName = firstName + lastName + (id % 1000); // 添加数字后缀避免重名

        return User.builder()
                .id(id)
                .userName(userName)
                .sex(random.nextBoolean())
                .build();
    }
}