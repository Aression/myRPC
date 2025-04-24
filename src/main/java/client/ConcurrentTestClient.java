package client;

import client.proxy.ClientProxy;
import client.serviceCenter.balance.LoadBalance.BalanceType;
import common.pojo.User;
import common.result.Result;
import common.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Random;

public class ConcurrentTestClient {
    private static final Logger logger = LoggerFactory.getLogger(ConcurrentTestClient.class);
    private static final int THREAD_COUNT = 10;
    private static final int OPERATIONS_PER_THREAD = 20;
    private static final Random random = new Random();

    // 使用ConcurrentHashMap统计不同错误码的数量
    private static final ConcurrentHashMap<Integer, AtomicInteger> errorCodeStats = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger> operationStats = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try {
            // 创建用户服务代理
            ClientProxy clientProxy = new ClientProxy(BalanceType.CONSISTENCY_HASH);
            UserService userService = clientProxy.getProxy(UserService.class);
            
            // 创建线程池
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            
            // 初始化操作统计
            operationStats.put("CREATE_SUCCESS", new AtomicInteger(0));
            operationStats.put("CREATE_FAIL", new AtomicInteger(0));
            operationStats.put("READ_SUCCESS", new AtomicInteger(0));
            operationStats.put("READ_FAIL", new AtomicInteger(0));
            operationStats.put("UPDATE_SUCCESS", new AtomicInteger(0));
            operationStats.put("UPDATE_FAIL", new AtomicInteger(0));
            operationStats.put("DELETE_SUCCESS", new AtomicInteger(0));
            operationStats.put("DELETE_FAIL", new AtomicInteger(0));
            
            logger.info("开始并发测试，线程数: {}, 每线程操作数: {}", THREAD_COUNT, OPERATIONS_PER_THREAD);
            
            // 启动多个线程执行混合CRUD操作
            for (int i = 0; i < THREAD_COUNT; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                            int userId = threadId * 1000 + j;
                            executeMixedOperation(userService, userId, successCount, failCount);
                        }
                    } catch (Exception e) {
                        logger.error("线程 {} 执行过程中发生异常: {}", threadId, e.getMessage(), e);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            // 等待所有线程完成
            latch.await();
            
            // 关闭线程池
            executor.shutdown();
            
            // 输出测试结果统计
            logger.info("并发测试完成，成功操作: {}, 失败操作: {}", successCount.get(), failCount.get());
            
            // 输出各操作类型的统计信息
            logger.info("操作类型统计:");
            for (Map.Entry<String, AtomicInteger> entry : operationStats.entrySet()) {
                logger.info("  {} : {}", entry.getKey(), entry.getValue().get());
            }
            
            // 输出错误码统计信息
            logger.info("错误码统计:");
            if (errorCodeStats.isEmpty()) {
                logger.info("  没有错误发生");
            } else {
                for (Map.Entry<Integer, AtomicInteger> entry : errorCodeStats.entrySet()) {
                    logger.info("  错误码 {} : {} 次", entry.getKey(), entry.getValue().get());
                }
            }
            
            logger.info(clientProxy.reportServiceStatus());
            
        } catch (Exception e) {
            logger.error("并发测试过程中出现异常: {}", e.getMessage(), e);
        }
    }
    
    private static void executeMixedOperation(UserService userService, int userId, AtomicInteger successCount, AtomicInteger failCount) {
        try {
            // 随机选择一个CRUD操作
            int operation = random.nextInt(4);
            
            switch (operation) {
                case 0:
                    // 创建用户
                    createUser(userService, userId, successCount, failCount);
                    break;
                case 1:
                    // 查询用户
                    queryUser(userService, userId, successCount, failCount);
                    break;
                case 2:
                    // 更新用户
                    updateUser(userService, userId, successCount, failCount);
                    break;
                case 3:
                    // 删除用户
                    deleteUser(userService, userId, successCount, failCount);
                    break;
            }
        } catch (Exception e) {
            logger.error("执行混合操作时发生异常，用户ID: {}, 错误: {}", userId, e.getMessage());
            failCount.incrementAndGet();
        }
    }
    
    private static void createUser(UserService userService, int userId, AtomicInteger successCount, AtomicInteger failCount) {
        User newUser = User.builder()
                .id(userId)
                .userName("用户" + userId)
                .sex(random.nextBoolean())
                .build();
        
        Result<Integer> result = userService.insertUser(newUser);
        if (result.isSuccess()) {
            logger.info("线程成功插入用户，ID: {}, 返回信息: {}", userId, result.getMessage());
            successCount.incrementAndGet();
            operationStats.get("CREATE_SUCCESS").incrementAndGet();
        } else {
            logger.error("线程插入用户失败，ID: {}, 错误码: {}, 错误信息: {}", 
                    userId, result.getCode(), result.getMessage());
            failCount.incrementAndGet();
            operationStats.get("CREATE_FAIL").incrementAndGet();
            // 统计错误码
            incrementErrorCodeCount(result.getCode());
        }
    }
    
    private static void queryUser(UserService userService, int userId, AtomicInteger successCount, AtomicInteger failCount) {
        Result<User> result = userService.getUserById(userId);
        if (result.isSuccess()) {
            logger.info("线程成功查询用户，ID: {}, 用户信息: {}", userId, result.getData());
            successCount.incrementAndGet();
            operationStats.get("READ_SUCCESS").incrementAndGet();
        } else {
            logger.info("线程查询用户未找到或失败，ID: {}, 错误码: {}, 错误信息: {}", 
                    userId, result.getCode(), result.getMessage());
            failCount.incrementAndGet();
            operationStats.get("READ_FAIL").incrementAndGet();
            // 统计错误码
            incrementErrorCodeCount(result.getCode());
        }
    }
    
    private static void updateUser(UserService userService, int userId, AtomicInteger successCount, AtomicInteger failCount) {
        User updatedUser = User.builder()
                .id(userId)
                .userName("更新用户" + userId)
                .sex(random.nextBoolean())
                .build();
        
        Result<Boolean> result = userService.updateUser(updatedUser);
        if (result.isSuccess() && Boolean.TRUE.equals(result.getData())) {
            logger.info("线程成功更新用户，ID: {}, 返回信息: {}", userId, result.getMessage());
            successCount.incrementAndGet();
            operationStats.get("UPDATE_SUCCESS").incrementAndGet();
        } else {
            logger.info("线程更新用户失败，ID: {}, 错误码: {}, 错误信息: {}", 
                    userId, result.getCode(), result.getMessage());
            failCount.incrementAndGet();
            operationStats.get("UPDATE_FAIL").incrementAndGet();
            // 统计错误码
            incrementErrorCodeCount(result.getCode());
        }
    }
    
    private static void deleteUser(UserService userService, int userId, AtomicInteger successCount, AtomicInteger failCount) {
        Result<Boolean> result = userService.deleteUserById(userId);
        if (result.isSuccess() && Boolean.TRUE.equals(result.getData())) {
            logger.info("线程成功删除用户，ID: {}, 返回信息: {}", userId, result.getMessage());
            successCount.incrementAndGet();
            operationStats.get("DELETE_SUCCESS").incrementAndGet();
        } else {
            logger.info("线程删除用户失败，ID: {}, 错误码: {}, 错误信息: {}", 
                    userId, result.getCode(), result.getMessage());
            failCount.incrementAndGet();
            operationStats.get("DELETE_FAIL").incrementAndGet();
            // 统计错误码
            incrementErrorCodeCount(result.getCode());
        }
    }
    
    // 增加错误码计数的辅助方法
    private static void incrementErrorCodeCount(Integer errorCode) {
        if (errorCode != null) {
            errorCodeStats.computeIfAbsent(errorCode, k -> new AtomicInteger(0)).incrementAndGet();
        }
    }
} 