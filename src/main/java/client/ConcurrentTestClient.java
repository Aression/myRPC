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
import java.util.UUID;
import java.util.Set;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ConcurrentTestClient {
    private static final Logger logger = LoggerFactory.getLogger(ConcurrentTestClient.class);
    private static final int THREAD_COUNT = 10;
    private static final int OPERATIONS_PER_THREAD = 20;
    private static final Random random = new Random();

    // 使用ConcurrentHashMap统计不同错误码的数量
    private static final ConcurrentHashMap<Integer, AtomicInteger> errorCodeStats = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger> operationStats = new ConcurrentHashMap<>();
    
    // 用于跟踪已创建用户的ID集合，使用线程安全的集合避免同步块
    private static final Set<Integer> createdUserIds = ConcurrentHashMap.newKeySet();

    // 预定义用户类型
    private static final List<String> USER_TYPES = Arrays.asList("学生", "工程师", "管理层", "营销", "客户");
    
    // 预定义更新类型
    private static final List<String> UPDATE_TYPES = Arrays.asList(
            "年龄更新用户", "邮箱更新用户", "电话更新用户", "地址更新用户", "综合更新用户", "多字段更新用户");

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
            
            // 初始化操作统计 - 使用stream初始化
            Stream.of("CREATE", "READ", "UPDATE", "DELETE")
                .flatMap(op -> Stream.of(op + "_SUCCESS", op + "_FAIL"))
                .forEach(key -> operationStats.put(key, new AtomicInteger(0)));
            
            logger.info("开始并发测试，线程数: {}, 每线程操作数: {}", THREAD_COUNT, OPERATIONS_PER_THREAD);
            
            // 首先创建一批用户以确保有数据可查询 - 使用stream并行创建
            CountDownLatch preCreateLatch = new CountDownLatch(THREAD_COUNT);
            IntStream.range(0, THREAD_COUNT)
                .forEach(threadId -> {
                    executor.submit(() -> {
                        try {
                            IntStream.range(0, OPERATIONS_PER_THREAD / 4)
                                .map(j -> threadId * 1000 + j)
                                .forEach(userId -> {
                                    createUser(userService, userId, successCount, failCount);
                                    createdUserIds.add(userId);
                                });
                        } catch (Exception e) {
                            logger.error("预创建用户过程中发生异常: {}", e.getMessage(), e);
                        } finally {
                            preCreateLatch.countDown();
                        }
                    });
                });
            
            // 等待预创建完成
            preCreateLatch.await();
            logger.info("预创建用户完成，共创建 {} 个用户", createdUserIds.size());
            
            // 启动多个线程执行混合CRUD操作 - 使用stream
            IntStream.range(0, THREAD_COUNT)
                .forEach(threadId -> {
                    executor.submit(() -> {
                        try {
                            IntStream.range(0, OPERATIONS_PER_THREAD)
                                .mapToObj(j -> generateUserId(j, threadId))
                                .forEach(userId -> executeMixedOperation(userService, userId, successCount, failCount));
                        } catch (Exception e) {
                            logger.error("线程 {} 执行过程中发生异常: {}", threadId, e.getMessage(), e);
                        } finally {
                            latch.countDown();
                        }
                    });
                });
            
            // 等待所有线程完成
            latch.await();
            
            // 关闭线程池
            executor.shutdown();
            
            // 输出测试结果统计
            logger.info("并发测试完成，成功操作: {}, 失败操作: {}", successCount.get(), failCount.get());
            
            // 输出各操作类型的统计信息 - 使用stream分组输出
            logger.info("操作类型统计:");
            operationStats.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> logger.info("  {} : {}", entry.getKey(), entry.getValue().get()));
            
            // 输出错误码统计信息 - 使用stream
            logger.info("错误码统计:");
            if (errorCodeStats.isEmpty()) {
                logger.info("  没有错误发生");
            } else {
                errorCodeStats.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> logger.info("  错误码 {} : {} 次", entry.getKey(), entry.getValue().get()));
            }
            
            logger.info(clientProxy.reportServiceStatus());
            
        } catch (Exception e) {
            logger.error("并发测试过程中出现异常: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 使用不同的策略生成用户ID，增加请求多样性
     */
    private static int generateUserId(int j, int threadId) {
        int userId;
        switch (j % 3) {
            // 使用随机生成的UUID哈希值的一部分作为特征码
            case 0:
                userId = Math.abs(UUID.randomUUID().hashCode() % 10000);
                break;
            // 使用线程ID和迭代次数
            case 1:
                userId = threadId * 1000 + j;
                break;
            // 使用当前时间的特征
            default:
                userId = (int)(System.nanoTime() % 10000);
                break;
        }
        return userId;
    }
    
    private static void executeMixedOperation(UserService userService, int userId, AtomicInteger successCount, AtomicInteger failCount) {
        try {
            // 根据情况选择操作，优先创建，减少无效的查询和删除
            int operationChance = random.nextInt(100);
            int operation;
            
            boolean userExists = createdUserIds.contains(userId);
            
            // 使用表达式简化逻辑
            if (userExists) {
                // 用户存在时，有33%概率更新，33%概率查询，33%概率删除
                if (operationChance < 33) {
                    operation = 1; // 查询
                } else if (operationChance < 66) {
                    operation = 2; // 更新
                } else {
                    operation = 3; // 删除
                }
            } else {
                // 用户不存在时，有70%概率创建，20%概率查询，10%概率更新或删除
                if (operationChance < 70) {
                    operation = 0; // 创建
                } else if (operationChance < 90) {
                    operation = 1; // 查询（可能失败，但这是预期的）
                } else if (operationChance < 95) {
                    operation = 2; // 更新（可能失败）
                } else {
                    operation = 3; // 删除（可能失败）
                }
            }
            
            // 使用函数式接口处理结果
            Function<Result<?>, Boolean> processResult = result -> {
                boolean isSuccess = result.isSuccess() && (!(result.getData() instanceof Boolean) || Boolean.TRUE.equals(result.getData()));
                if (isSuccess) {
                    successCount.incrementAndGet();
                } else {
                    failCount.incrementAndGet();
                    incrementErrorCodeCount(result.getCode());
                }
                return isSuccess;
            };
            
            // 执行选择的操作
            switch (operation) {
                case 0:
                    // 创建用户
                    Result<Integer> result = createUser(userService, userId, successCount, failCount);
                    if (result.isSuccess()) {
                        createdUserIds.add(userId);
                    }
                    break;
                case 1:
                    queryUser(userService, userId, successCount, failCount);
                    break;
                case 2:
                    updateUser(userService, userId, successCount, failCount);
                    break;
                case 3:
                    // 删除用户
                    Result<Boolean> result2 = deleteUser(userService, userId, successCount, failCount);
                    if (result2.isSuccess() && Boolean.TRUE.equals(result2.getData()) && userExists) {
                        createdUserIds.remove(userId);
                    }
                    break;
            }
        } catch (Exception e) {
            logger.error("执行混合操作时发生异常，用户ID: {}, 错误: {}", userId, e.getMessage());
            failCount.incrementAndGet();
        }
    }
    
    private static Result<Integer> createUser(UserService userService, int userId, AtomicInteger successCount, AtomicInteger failCount) {
        // 增加请求特征多样性
        String userName = "用户" + userId;
        if (random.nextBoolean()) {
            userName += "_" + UUID.randomUUID().toString().substring(0, 8);
        }
        
        // 使用函数式编程构建用户对象
        User newUser = buildUserByType(userId, userName, userId % USER_TYPES.size());
        
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
        return result;
    }
    
    /**
     * 根据用户类型构建不同的用户对象
     */
    private static User buildUserByType(int userId, String userName, int userTypeIndex) {
        String userType = USER_TYPES.get(userTypeIndex);
        
        // 根据类型添加特定属性
        switch (userTypeIndex) {
            case 0: // 学生
                return User.builder()
                       .id(userId)
                       .userName(userName)
                       .sex(random.nextBoolean())
                       .lastUpdateTime(System.currentTimeMillis())
                       .userType(userType)
                       .age(18 + random.nextInt(30))
                       .email("student" + userId + "@school.edu")
                       .build();
            case 1: // 工程师
                return User.builder()
                       .id(userId)
                       .userName(userName)
                       .sex(random.nextBoolean())
                       .lastUpdateTime(System.currentTimeMillis())
                       .userType(userType)
                       .age(25 + random.nextInt(35))
                       .email("engineer" + userId + "@company.com")
                       .phone("13" + (10000000 + userId))
                       .build();
            case 2: // 管理层
                return User.builder()
                       .id(userId)
                       .userName(userName)
                       .sex(random.nextBoolean())
                       .lastUpdateTime(System.currentTimeMillis())
                       .userType(userType)
                       .age(40 + random.nextInt(20))
                       .phone("18" + (10000000 + userId))
                       .address("城市" + (userId % 10))
                       .build();
            case 3: // 营销
                return User.builder()
                       .id(userId)
                       .userName(userName)
                       .sex(random.nextBoolean())
                       .lastUpdateTime(System.currentTimeMillis())
                       .userType(userType)
                       .age(20 + random.nextInt(40))
                       .email("marketing" + userId + "@company.com")
                       .address("区域" + (userId % 20))
                       .build();
            case 4: // 客户
            default:
                return User.builder()
                       .id(userId)
                       .userName(userName)
                       .sex(random.nextBoolean())
                       .lastUpdateTime(System.currentTimeMillis())
                       .userType(userType)
                       .age(30 + random.nextInt(30))
                       .phone("15" + (10000000 + userId))
                       .email("customer" + userId + "@example.com")
                       .build();
        }
    }
    
    private static Result<User> queryUser(UserService userService, int userId, AtomicInteger successCount, AtomicInteger failCount) {
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
        return result;
    }
    
    private static Result<Boolean> updateUser(UserService userService, int userId, AtomicInteger successCount, AtomicInteger failCount) {
        // 增加更新请求的差异性
        int updateTypeIndex = random.nextInt(UPDATE_TYPES.size());
        String updateType = UPDATE_TYPES.get(updateTypeIndex);
        String userName = "更新用户" + userId + "_" + updateType;
        
        // 构建更新对象
        User updatedUser = buildUpdateUserByType(userId, userName, updateTypeIndex);
        
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
        return result;
    }
    
    /**
     * 根据更新类型构建不同的更新对象
     */
    private static User buildUpdateUserByType(int userId, String userName, int updateTypeIndex) {
        String updateType = UPDATE_TYPES.get(updateTypeIndex);
        
        // 构建基础对象
        switch (updateTypeIndex) {
            case 0: // 年龄更新
                return User.builder()
                        .id(userId)
                        .userName(userName)
                        .sex(random.nextBoolean())
                        .age(20 + random.nextInt(50))
                        .userType(updateType)
                        .lastUpdateTime(System.currentTimeMillis())
                        .build();
            case 1: // 邮箱更新
                return User.builder()
                        .id(userId)
                        .userName(userName)
                        .sex(random.nextBoolean())
                        .email("updated" + userId + "@test.com")
                        .userType(updateType)
                        .lastUpdateTime(System.currentTimeMillis())
                        .build();
            case 2: // 电话更新
                return User.builder()
                        .id(userId)
                        .userName(userName)
                        .sex(random.nextBoolean())
                        .phone("139" + (10000000 + userId))
                        .userType(updateType)
                        .lastUpdateTime(System.currentTimeMillis())
                        .build();
            case 3: // 地址更新
                return User.builder()
                        .id(userId)
                        .userName(userName)
                        .sex(random.nextBoolean())
                        .address("地址" + UUID.randomUUID().toString().substring(0, 6))
                        .userType(updateType)
                        .lastUpdateTime(System.currentTimeMillis())
                        .build();
            case 4: // 综合更新
                return User.builder()
                        .id(userId)
                        .userName(userName)
                        .sex(random.nextBoolean())
                        .age(25 + random.nextInt(30))
                        .email("combo" + userId + "@example.com")
                        .userType(updateType)
                        .lastUpdateTime(System.currentTimeMillis())
                        .build();
            case 5: // 多字段更新
            default:
                return User.builder()
                        .id(userId)
                        .userName(userName)
                        .sex(random.nextBoolean())
                        .phone("159" + (10000000 + userId))
                        .address("新区域" + (userId % 30))
                        .userType(updateType)
                        .lastUpdateTime(System.currentTimeMillis())
                        .build();
        }
    }
    
    private static Result<Boolean> deleteUser(UserService userService, int userId, AtomicInteger successCount, AtomicInteger failCount) {
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
        return result;
    }
    
    // 增加错误码计数的辅助方法
    private static void incrementErrorCodeCount(Integer errorCode) {
        if (errorCode != null) {
            errorCodeStats.computeIfAbsent(errorCode, k -> new AtomicInteger(0)).incrementAndGet();
        }
    }
} 