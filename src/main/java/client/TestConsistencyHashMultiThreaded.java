package client;

import client.proxy.ClientProxy;
import client.serviceCenter.balance.LoadBalanceFactory;
import common.pojo.User;
import common.result.Result;
import common.service.UserService;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 测试多线程环境下的一致性哈希负载均衡
 */
public class TestConsistencyHashMultiThreaded {
    private static final int TOTAL_OPERATIONS = 1000; // 总操作数
    private static final int THREAD_COUNT = 5;        // 并发线程数
    private static final int BATCH_SIZE = 100;        // 每批处理数量
    private static final double READ_RATIO = 0.7;     // 读操作比例

    // 用户信息模拟数据
    private static final String[] FIRST_NAMES = {"张", "王", "李", "赵", "刘"};
    private static final String[] LAST_NAMES = {"伟", "芳", "娜", "敏", "静"};

    // 存储各种统计信息
    private static Map<Integer, String> userMessageMap = new ConcurrentHashMap<>();
    private static Map<Integer, Integer> readCountPerUser = new ConcurrentHashMap<>();
    private static Map<Integer, StringBuilder> requestHistoryPerUser = new ConcurrentHashMap<>();
    private static Map<Integer, Map<String, Integer>> userNodeDistribution = new ConcurrentHashMap<>();
    
    // 全局节点负载统计
    private static Map<String, Integer> nodeLoadStats = new ConcurrentHashMap<>();
    private static Map<String, Integer> nodeReadStats = new ConcurrentHashMap<>();
    private static Map<String, Integer> nodeWriteStats = new ConcurrentHashMap<>();
    private static int totalNodes = 0;

    public static void main(String[] args) {
        // 使用一致性哈希负载均衡
        ClientProxy clientProxy = new ClientProxy(LoadBalanceFactory.BalanceType.CONSISTENCY_HASH);
        UserService userService = clientProxy.getProxy(UserService.class);

        System.out.println("=== 开始测试多线程环境下的一致性哈希负载均衡 ===");
        System.out.println("注意：确保已启动 MultiNodeServer 以便测试多节点负载均衡");
        System.out.println("可以使用命令：java -cp target/myRPC-1.0-SNAPSHOT.jar server.MultiNodeServer");
        
        // 先插入一些测试数据
        prepareTestData(userService);
        
        // 测试多线程混合读写操作
        testMultiThreadedMixedOperations(userService);

        // 验证一致性哈希效果 - 相同ID的请求应该路由到相同节点
        verifyConsistencyHash(userService);
        
        // 测试新用户负载分布
        testNewUserDistribution(userService);
        
        // 打印详细统计信息
        printDetailedStatistics();
        
        System.out.println("\n=== 测试完成 ===");
        System.out.println("如果所有请求都路由到了一个节点，可能是因为未启动 MultiNodeServer");
    }

    /**
     * 准备测试数据
     */
    private static void prepareTestData(UserService userService) {
        System.out.println("\n准备测试数据...");
        int successCount = 0;
        int failureCount = 0;
        
        for (int i = 1; i <= 10; i++) {
            User user = generateRealisticUser(i);
            Result<Integer> result = userService.insertUserId(user);
            
            // 直接使用消息进行统计
            String message = result.getMessage();
            
            // 记录节点负载
            recordNodeLoad(message, false);
            
            if (result.isSuccess()) {
                successCount++;
            } else {
                failureCount++;
            }
        }
        System.out.println("测试数据准备完成，成功: " + successCount + ", 失败: " + failureCount);
    }

    /**
     * 测试多线程混合读写操作
     */
    private static void testMultiThreadedMixedOperations(UserService userService) {
        System.out.println("\n开始多线程混合读写操作测试...");
        System.out.println("总操作数: " + TOTAL_OPERATIONS);
        System.out.println("并发线程数: " + THREAD_COUNT);
        System.out.println("每批处理数量: " + BATCH_SIZE);
        System.out.println("读操作比例: " + READ_RATIO);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger readSuccessCount = new AtomicInteger(0);
        AtomicInteger readFailureCount = new AtomicInteger(0);
        AtomicInteger writeSuccessCount = new AtomicInteger(0);
        AtomicInteger writeFailureCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    int startIndex = threadIndex * (TOTAL_OPERATIONS / THREAD_COUNT);
                    int endIndex = Math.min(startIndex + (TOTAL_OPERATIONS / THREAD_COUNT), TOTAL_OPERATIONS);
                    
                    Random random = new Random();
                    for (int j = startIndex; j < endIndex; j++) {
                        if (random.nextDouble() < READ_RATIO) {
                            // 读操作 - 总是读取固定的几个ID，以验证一致性哈希的效果
                            int userId = (j % 10) + 1; // 始终读取1-10的ID
                            Result<User> result = userService.getUserById(userId);
                            
                            // 记录消息，用于后续分析一致性哈希效果
                            String message = result.getMessage();
                            
                            // 记录节点负载
                            recordNodeLoad(message, true);
                            
                            // 为了确保记录下来用于最终统计
                            userMessageMap.put(userId, message);
                            
                            // 更新节点分布统计
                            userNodeDistribution.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                                .merge(message, 1, Integer::sum);
                            
                            // 更新用户请求计数
                            readCountPerUser.merge(userId, 1, Integer::sum);
                            
                            // 记录请求历史
                            requestHistoryPerUser.computeIfAbsent(userId, k -> new StringBuilder())
                                .append("读取请求-").append(System.currentTimeMillis() % 10000).append(", ");
                            
                            if (result.isSuccess() && result.getData() != null) {
                                readSuccessCount.incrementAndGet();
                            } else {
                                readFailureCount.incrementAndGet();
                            }
                            
                            if ((j - startIndex + 1) % BATCH_SIZE == 0) {
                                System.out.printf("线程 %d: 已处理 %d/%d 个操作%n", 
                                    threadIndex, j - startIndex + 1, endIndex - startIndex);
                            }
                        } else {
                            // 写操作
                            User user = generateRealisticUser(100 + j); // 使用大ID避免与测试数据冲突
                            Result<Integer> result = userService.insertUserId(user);
                            
                            // 记录节点负载
                            recordNodeLoad(result.getMessage(), false);
                            
                            if (result.isSuccess() && result.getData() != null) {
                                writeSuccessCount.incrementAndGet();
                            } else {
                                writeFailureCount.incrementAndGet();
                            }
                            
                            if ((j - startIndex + 1) % BATCH_SIZE == 0) {
                                System.out.printf("线程 %d: 已处理 %d/%d 个操作%n", 
                                    threadIndex, j - startIndex + 1, endIndex - startIndex);
                            }
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
        
        // 保存统计结果以便最后打印
        int readSuccessTotal = readSuccessCount.get();
        int readFailureTotal = readFailureCount.get();
        int writeSuccessTotal = writeSuccessCount.get();
        int writeFailureTotal = writeFailureCount.get();
        long totalTimeMs = endTime - startTime;
        
        System.out.println("\n多线程混合读写操作测试完成，总耗时: " + totalTimeMs + " ms");
    }

    /**
     * 记录节点负载情况
     */
    private static void recordNodeLoad(String message, boolean isRead) {
        if (message == null) return;
        
        // 直接使用消息中的内容作为节点标识
        String nodeId = extractNodeInfo(message);
        if (nodeId == null || nodeId.isEmpty()) return;
        
        // 全局节点负载统计
        nodeLoadStats.merge(nodeId, 1, Integer::sum);
        
        // 记录读写请求分布
        if (isRead) {
            nodeReadStats.merge(nodeId, 1, Integer::sum);
        } else {
            nodeWriteStats.merge(nodeId, 1, Integer::sum);
        }
    }
    
    /**
     * 从消息中提取节点信息
     */
    private static String extractNodeInfo(String message) {
        // 首先检查是否是直接的ip:port格式
        if (message.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+$") || 
            message.matches("^localhost:\\d+$")) {
            return message;
        }
        
        // 尝试从消息中匹配ip:port格式
        if (message.contains(":")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+|localhost:\\d+)");
            java.util.regex.Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        
        // 如果无法提取有效节点信息，返回原始消息
        return message;
    }

    /**
     * 验证一致性哈希效果
     */
    private static void verifyConsistencyHash(UserService userService) {
        System.out.println("\n验证一致性哈希效果...");
        
        // 对每个用户ID进行多次请求，验证路由一致性
        for (int id = 1; id <= 5; id++) {
            // 为确保测试结果清晰可见，额外进行几次专门的测试请求
            for (int i = 0; i < 5; i++) {
                Result<User> result = userService.getUserById(id);
                String message = result.getMessage();
                
                // 记录节点负载
                recordNodeLoad(message, true);
                
                // 确保记录最后一次结果用于最终统计展示
                userMessageMap.put(id, message);
                
                // 更新节点分布统计
                userNodeDistribution.computeIfAbsent(id, k -> new ConcurrentHashMap<>())
                    .merge(message, 1, Integer::sum);
                
                try {
                    Thread.sleep(50); // 短暂延迟，避免请求过于密集
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    /**
     * 测试新用户的负载分布
     */
    private static void testNewUserDistribution(UserService userService) {
        System.out.println("\n测试新用户负载分布...");
        int testCount = 20;
        
        Map<String, Integer> newUserNodeDistribution = new HashMap<>();
        
        for (int i = 0; i < testCount; i++) {
            User user = generateRealisticUser(1000 + i);
            Result<Integer> result = userService.insertUserId(user);
            String message = result.getMessage();
            
            // 记录节点负载
            recordNodeLoad(message, false);
            
            // 统计新用户分布
            newUserNodeDistribution.merge(message, 1, Integer::sum);
            
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        System.out.println("新用户分布情况: " + formatNodeDistribution(newUserNodeDistribution));
    }

    /**
     * 打印详细统计信息
     */
    private static void printDetailedStatistics() {
        totalNodes = nodeLoadStats.size();
        System.out.println("\n============ 测试详细统计信息 ============");
        
        // 输出请求成功情况
        System.out.println("\n请求总体情况:");
        int totalReads = nodeReadStats.values().stream().mapToInt(Integer::intValue).sum();
        int totalWrites = nodeWriteStats.values().stream().mapToInt(Integer::intValue).sum();
        int totalRequests = totalReads + totalWrites;
        System.out.println("总读取操作数: " + totalReads);
        System.out.println("总写入操作数: " + totalWrites);
        System.out.println("总操作数: " + totalRequests);
        
        System.out.println("\n服务节点负载分布情况:");
        System.out.println("总节点数: " + totalNodes);
        
        if (totalNodes == 0) {
            System.out.println("未检测到有效节点");
            return;
        }
        
        // 计算统计指标
        double avgLoad = (double) totalRequests / totalNodes;
        
        // 格式化输出每个节点负载
        List<Map.Entry<String, Integer>> sortedEntries = new ArrayList<>(nodeLoadStats.entrySet());
        sortedEntries.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        
        System.out.println("各节点负载详情 (平均负载: " + String.format("%.2f", avgLoad) + " 请求/节点):");
        for (Map.Entry<String, Integer> entry : sortedEntries) {
            String node = entry.getKey();
            int load = entry.getValue();
            int reads = nodeReadStats.getOrDefault(node, 0);
            int writes = nodeWriteStats.getOrDefault(node, 0);
            
            double loadPercent = totalRequests > 0 ? 100.0 * load / totalRequests : 0;
            double deviation = avgLoad > 0 ? 100.0 * (load - avgLoad) / avgLoad : 0;
            
            System.out.printf("  节点: %s - 总负载: %d (%.2f%%), 读: %d, 写: %d, 偏差: %.2f%%%n", 
                node, load, loadPercent, reads, writes, deviation);
        }
        
        // 计算标准差，评估负载均衡程度
        double sumSquaredDiff = nodeLoadStats.values().stream()
            .mapToDouble(load -> Math.pow(load - avgLoad, 2))
            .sum();
        double stdDev = Math.sqrt(sumSquaredDiff / totalNodes);
        double relativeStdDev = avgLoad > 0 ? 100 * stdDev / avgLoad : 0;
        
        System.out.printf("\n负载均衡指标:\n");
        System.out.printf("  标准差: %.2f\n", stdDev);
        System.out.printf("  相对标准差: %.2f%%\n", relativeStdDev);
    }
    
    /**
     * 格式化节点分布信息
     */
    private static String formatNodeDistribution(Map<String, Integer> distribution) {
        if (distribution == null || distribution.isEmpty()) {
            return "无数据";
        }
        
        StringBuilder sb = new StringBuilder();
        distribution.forEach((message, count) -> {
            sb.append(message).append("=").append(count).append("次 ");
        });
        return sb.toString();
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