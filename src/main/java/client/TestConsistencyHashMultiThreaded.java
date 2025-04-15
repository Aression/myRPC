package client;

import client.proxy.ClientProxy;
import common.pojo.User;
import common.result.Result;
import common.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.Callable;

/**
 * 测试多线程环境下的一致性哈希负载均衡
 */
public class TestConsistencyHashMultiThreaded {
    private static final Logger logger = LoggerFactory.getLogger(TestConsistencyHashMultiThreaded.class);
    private static final int TOTAL_OPERATIONS = 1000;
    private static final int THREAD_COUNT = 10;
    private static final int BATCH_SIZE = 100;
    private static final double READ_RATIO = 0.7;

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
        logger.info("=== 开始测试多线程环境下的一致性哈希负载均衡 ===");
        logger.info("注意：确保已启动 MultiNodeServer 以便测试多节点负载均衡");
        logger.info("可以使用命令：java -cp target/myRPC-1.0-SNAPSHOT.jar server.MultiNodeServer");

        try {
            ClientProxy clientProxy = new ClientProxy();
            UserService userService = clientProxy.getProxy(UserService.class);

            // 准备测试数据
            logger.info("\n[准备阶段] 开始准备测试数据...");
            List<User> testUsers = prepareTestData();
            logger.info("[准备阶段] 测试数据准备完成 - 成功: {}, 失败: {}", testUsers.size(), TOTAL_OPERATIONS - testUsers.size());

            // 验证一致性哈希效果
            verifyConsistencyHash(userService);

            // 执行多线程测试
            logger.info("\n[测试阶段] 开始多线程混合读写操作测试");
            logger.info("[测试阶段] 测试参数:");
            logger.info("  - 总操作数: {}", TOTAL_OPERATIONS);
            logger.info("  - 并发线程数: {}", THREAD_COUNT);
            logger.info("  - 每批处理数量: {}", BATCH_SIZE);
            logger.info("  - 读操作比例: {}", READ_RATIO);

            long startTime = System.currentTimeMillis();
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            List<Future<Map<String, Integer>>> futures = new ArrayList<>();

            for (int i = 0; i < THREAD_COUNT; i++) {
                futures.add(executor.submit(new TestTask(testUsers)));
            }

            // 收集结果
            Map<String, Integer> nodeDistribution = new HashMap<>();
            for (Future<Map<String, Integer>> future : futures) {
                Map<String, Integer> threadResult = future.get();
                threadResult.forEach((node, count) -> 
                    nodeDistribution.merge(node, count, Integer::sum));
            }

            long totalTimeMs = System.currentTimeMillis() - startTime;
            logger.info("\n[测试阶段] 多线程混合读写操作测试完成");
            logger.info("[测试阶段] 总耗时: {} ms", totalTimeMs);
            
            // 分析多线程测试结果
            analyzeResults(nodeDistribution);

            // 测试新用户分布
            testNewUserDistribution(userService);

            // 打印详细统计信息
            printDetailedStatistics();

            // 关闭线程池
            executor.shutdown();
            boolean terminated = executor.awaitTermination(5, TimeUnit.SECONDS);
            if (!terminated) {
                logger.warn("[测试阶段] 线程池未能在指定时间内关闭，尝试强制关闭");
                executor.shutdownNow();
            }
            
            logger.info("\n=== 多线程环境下的一致性哈希负载均衡测试完成 ===");
            return;
        } catch (Exception e) {
            logger.error("[错误] 测试过程中出现异常: {}", e.getMessage(), e);
        }
    }

    private static List<User> prepareTestData() {
        List<User> users = new ArrayList<>();
        ClientProxy clientProxy = new ClientProxy();
        UserService userService = clientProxy.getProxy(UserService.class);
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
                        successCount++;
                        break;
                    } else {
                        currentRetry++;
                        retryCount++;
                        logger.warn("[准备阶段] 用户 {} 插入失败，重试次数: {}/{}", user.getId(), currentRetry, MAX_RETRY);
                        try {
                            Thread.sleep(100); // 短暂延迟后重试
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            logger.error("[准备阶段] 线程被中断，停止数据准备");
                            return users;
                        }
                    }
                } catch (Exception e) {
                    currentRetry++;
                    retryCount++;
                    logger.error("[准备阶段] 用户 {} 插入异常: {}", user.getId(), e.getMessage());
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("[准备阶段] 线程被中断，停止数据准备");
                        return users;
                    }
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

    private static void analyzeResults(Map<String, Integer> nodeDistribution) {
        logger.info("\n[分析] 验证一致性哈希效果");
        int totalNodes = nodeDistribution.size();
        logger.info("[分析] 总节点数: {}", totalNodes);

        if (totalNodes == 0) {
            logger.warn("[分析] 未检测到有效节点");
            return;
        }

        double avgLoad = nodeDistribution.values().stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);

        logger.info("[分析] 各节点负载详情 (平均负载: {} 请求/节点):", String.format("%.2f", avgLoad));
        nodeDistribution.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(entry -> {
                String node = entry.getKey();
                int count = entry.getValue();
                double deviation = avgLoad > 0 ? 100.0 * (count - avgLoad) / avgLoad : 0;
                logger.info("  - 节点 {}: {} 请求 (偏差: {}%)", 
                    node, count, String.format("%.2f", deviation));
            });
    }

    private static class TestTask implements Callable<Map<String, Integer>> {
        private final List<User> testUsers;
        private final UserService userService;
        private final Random random;
        private final Map<String, Integer> nodeDistribution;

        public TestTask(List<User> testUsers) {
            this.testUsers = testUsers;
            ClientProxy clientProxy = new ClientProxy();
            this.userService = clientProxy.getProxy(UserService.class);
            this.random = new Random();
            this.nodeDistribution = new HashMap<>();
        }

        @Override
        public Map<String, Integer> call() {
            for (int i = 0; i < BATCH_SIZE; i++) {
                try {
                    if (random.nextDouble() < READ_RATIO) {
                        // 读操作
                        User user = testUsers.get(random.nextInt(testUsers.size()));
                        Result<User> result = userService.getUserById(user.getId());
                        if (result.isSuccess()) {
                            String nodeId = extractNodeInfo(result.getMessage());
                            if (nodeId != null && !nodeId.isEmpty()) {
                                nodeDistribution.merge(nodeId, 1, Integer::sum);
                                recordNodeLoad(result.getMessage(), true);
                            }
                        }
                    } else {
                        // 写操作
                        User newUser = generateRealisticUser(random.nextInt(1000));
                        Result<Integer> result = userService.insertUser(newUser);
                        if (result.isSuccess()) {
                            String nodeId = extractNodeInfo(result.getMessage());
                            if (nodeId != null && !nodeId.isEmpty()) {
                                nodeDistribution.merge(nodeId, 1, Integer::sum);
                                recordNodeLoad(result.getMessage(), false);
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("[测试任务] 执行出错: {}", e.getMessage(), e);
                }
            }
            return nodeDistribution;
        }
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
        logger.info("\n[验证] 开始验证一致性哈希效果");
        
        Map<Integer, Map<String, Integer>> userRouteResults = new HashMap<>();
        
        // 对每个用户ID进行多次请求，验证路由一致性
        for (int id = 1; id <= 5; id++) {
            logger.info("[验证] 测试用户ID: {}", id);
            Map<String, Integer> nodeDistribution = new HashMap<>();
            
            // 为确保测试结果清晰可见，额外进行几次专门的测试请求
            for (int i = 0; i < 5; i++) {
                Result<User> result = userService.getUserById(id);
                String message = result.getMessage();
                String nodeId = extractNodeInfo(message);
                
                // 记录节点负载
                recordNodeLoad(message, true);
                
                // 确保记录最后一次结果用于最终统计展示
                userMessageMap.put(id, message);
                
                // 更新节点分布统计
                userNodeDistribution.computeIfAbsent(id, k -> new ConcurrentHashMap<>())
                    .merge(message, 1, Integer::sum);
                
                // 本次测试中的分布统计
                nodeDistribution.merge(nodeId, 1, Integer::sum);
                
                try {
                    Thread.sleep(50); // 短暂延迟，避免请求过于密集
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("[验证] 线程被中断");
                    break;
                }
            }
            
            userRouteResults.put(id, nodeDistribution);
            logger.info("[验证] 用户ID: {} 路由分布: {}", id, formatNodeDistribution(nodeDistribution));
        }
        
        // 统计路由一致性
        int consistentCount = 0;
        int totalCount = userRouteResults.size();
        
        for (Map.Entry<Integer, Map<String, Integer>> entry : userRouteResults.entrySet()) {
            // 如果该用户的所有请求都分发到同一节点，表示路由一致
            if (entry.getValue().size() == 1) {
                consistentCount++;
            }
        }
        
        double consistencyRate = totalCount > 0 ? 100.0 * consistentCount / totalCount : 0;
        logger.info("[验证] 一致性哈希路由一致性: {}/{} ({}%)", 
            consistentCount, totalCount, String.format("%.2f", consistencyRate));
    }
    
    /**
     * 测试新用户的负载分布
     */
    private static void testNewUserDistribution(UserService userService) {
        logger.info("\n[测试阶段] 测试新用户负载分布");
        int testCount = 20;
        
        Map<String, Integer> newUserNodeDistribution = new HashMap<>();
        int successCount = 0;
        
        logger.info("[测试阶段] 创建 {} 个新用户", testCount);
        
        for (int i = 0; i < testCount; i++) {
            User user = generateRealisticUser(1000 + i);
            Result<Integer> result = userService.insertUser(user);
            
            if (result.isSuccess()) {
                successCount++;
                String message = result.getMessage();
                String nodeId = extractNodeInfo(message);
                
                // 记录节点负载
                recordNodeLoad(message, false);
                
                // 统计新用户分布
                newUserNodeDistribution.merge(nodeId, 1, Integer::sum);
            }
            
            try {
                Thread.sleep(10); // 短暂延迟
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("[测试阶段] 线程被中断");
                break;
            }
        }
        
        double successRate = testCount > 0 ? 100.0 * successCount / testCount : 0;
        logger.info("[测试阶段] 新用户创建: 成功 {}/{} ({}%)", 
            successCount, testCount, String.format("%.2f", successRate));
        logger.info("[测试阶段] 新用户分布情况: ");
        
        // 格式化展示新用户分布
        final int finalSuccessCount = successCount; // 创建final变量
        newUserNodeDistribution.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(entry -> {
                String node = entry.getKey();
                int count = entry.getValue();
                double percent = finalSuccessCount > 0 ? 100.0 * count / finalSuccessCount : 0;
                logger.info("  - 节点 {}: {} 个用户 ({}%)", node, count, String.format("%.2f", percent));
            });
    }

    /**
     * 打印详细统计信息
     */
    private static void printDetailedStatistics() {
        totalNodes = nodeLoadStats.size();
        logger.info("\n============ 测试详细统计信息 ============");
        
        // 输出请求成功情况
        logger.info("\n[统计] 请求总体情况:");
        int totalReads = nodeReadStats.values().stream().mapToInt(Integer::intValue).sum();
        int totalWrites = nodeWriteStats.values().stream().mapToInt(Integer::intValue).sum();
        int totalRequests = totalReads + totalWrites;
        logger.info("  - 总读取操作数: {}", totalReads);
        logger.info("  - 总写入操作数: {}", totalWrites);
        logger.info("  - 总操作数: {}", totalRequests);
        
        logger.info("\n[统计] 服务节点负载分布情况:");
        logger.info("  - 总节点数: {}", totalNodes);
        
        if (totalNodes == 0) {
            logger.warn("  - 未检测到有效节点");
            return;
        }
        
        // 计算统计指标
        double avgLoad = (double) totalRequests / totalNodes;
        
        // 格式化输出每个节点负载
        List<Map.Entry<String, Integer>> sortedEntries = new ArrayList<>(nodeLoadStats.entrySet());
        sortedEntries.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        
        logger.info("\n[统计] 各节点负载详情 (平均负载: {} 请求/节点):", String.format("%.2f", avgLoad));
        for (Map.Entry<String, Integer> entry : sortedEntries) {
            String node = entry.getKey();
            int load = entry.getValue();
            int reads = nodeReadStats.getOrDefault(node, 0);
            int writes = nodeWriteStats.getOrDefault(node, 0);
            
            double loadPercent = totalRequests > 0 ? 100.0 * load / totalRequests : 0;
            double deviation = avgLoad > 0 ? 100.0 * (load - avgLoad) / avgLoad : 0;
            
            logger.info("  - 节点 {}:", node);
            logger.info("    * 总请求数: {} (占比: {}%)", load, String.format("%.2f", loadPercent));
            logger.info("    * 读取请求: {} (占比: {}%)", reads, String.format("%.2f", 100.0 * reads / load));
            logger.info("    * 写入请求: {} (占比: {}%)", writes, String.format("%.2f",100.0 * writes / load));
            logger.info("    * 负载偏差: {}%", String.format("%.2f", deviation));
        }
        
        // 计算标准差，评估负载均衡程度
        double sumSquaredDiff = nodeLoadStats.values().stream()
            .mapToDouble(load -> Math.pow(load - avgLoad, 2))
            .sum();
        double stdDev = Math.sqrt(sumSquaredDiff / totalNodes);
        double relativeStdDev = avgLoad > 0 ? 100 * stdDev / avgLoad : 0;
        
        logger.info("\n[统计] 负载均衡指标:");
        logger.info("  - 标准差: {}", String.format("%.2f", stdDev));
        logger.info("  - 相对标准差: {}%", String.format("%.2f", relativeStdDev));
    }
    
    /**
     * 格式化节点分布信息
     */
    private static String formatNodeDistribution(Map<String, Integer> distribution) {
        if (distribution == null || distribution.isEmpty()) {
            return "无数据";
        }
        
        StringBuilder sb = new StringBuilder();
        
        // 排序以保证输出有序
        distribution.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(entry -> {
                String nodeId = entry.getKey();
                int count = entry.getValue();
                
                // 计算百分比
                double percent = distribution.values().stream().mapToInt(Integer::intValue).sum();
                percent = percent > 0 ? 100.0 * count / percent : 0;
                
                sb.append("\n    * ").append(nodeId)
                  .append(" = ").append(count).append("次")
                  .append(" (").append(String.format("%.2f", percent)).append("%)");
            });
        
        return sb.toString();
    }

    /**
     * 生成真实用户数据
     */
    private static User generateRealisticUser(int id) {
        Random random = new Random();
        String firstName = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
        String lastName = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
        String userName = firstName + lastName + (id % 1000); // 添加数字后缀避免重名
        
        return User.builder()
                .id(id)
                .userName(userName)
                .sex(random.nextBoolean())
                .build();
    }
} 