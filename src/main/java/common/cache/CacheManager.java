package common.cache;

import common.pojo.User;
import common.service.impl.UserServiceImpl;
import common.util.CaffeineUtil;
import common.util.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 缓存管理器，用于处理缓存预热、定时过期和雪崩处理
 */
public class CacheManager {
    private static final Logger logger = LoggerFactory.getLogger(CacheManager.class);
    
    // 定时任务执行器
    private final ScheduledExecutorService scheduler;
    
    // 需要管理的服务实例
    private final UserServiceImpl userService;
    
    // 缓存预热完成状态
    private volatile boolean warmupCompleted = false;
    
    // 缓存监控统计间隔（秒）
    private static final int STATS_INTERVAL_SECONDS = 300; // 5分钟
    
    // 缓存重建间隔（秒）
    private static final int REBUILD_INTERVAL_SECONDS = 3600; // 1小时
    
    // 随机数生成器，用于给缓存过期时间添加随机偏移，防止缓存雪崩
    private final Random random = new Random();

    public CacheManager(UserServiceImpl userService) {
        this.userService = userService;
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        // 初始化Redis连接池
        RedisUtil.initPool();
        
        // 启动缓存预热
        warmupCache();
        
        // 启动缓存统计任务
        startCacheStatsTask();
        
        // 启动缓存重建任务
        startCacheRebuildTask();
        
        // 添加JVM关闭钩子，优雅关闭资源
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }
    
    /**
     * 缓存预热
     */
    private void warmupCache() {
        logger.info("开始缓存预热...");
        try {
            // 重建用户缓存
            userService.rebuildCache();
            warmupCompleted = true;
            logger.info("缓存预热完成");
        } catch (Exception e) {
            logger.error("缓存预热失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 启动缓存统计任务
     */
    private void startCacheStatsTask() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // 获取并记录缓存统计信息
                String userCacheStats = CaffeineUtil.getStats("user");
                logger.info("用户缓存统计: {}", userCacheStats);
            } catch (Exception e) {
                logger.error("获取缓存统计信息失败: {}", e.getMessage(), e);
            }
        }, STATS_INTERVAL_SECONDS, STATS_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }
    
    /**
     * 启动缓存重建任务
     */
    private void startCacheRebuildTask() {
        // 添加随机偏移，防止集群中所有服务同时重建缓存导致的雪崩
        int initialDelay = REBUILD_INTERVAL_SECONDS + random.nextInt(300);
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                logger.info("开始执行定时缓存重建...");
                userService.rebuildCache();
                logger.info("定时缓存重建完成");
            } catch (Exception e) {
                logger.error("定时缓存重建失败: {}", e.getMessage(), e);
            }
        }, initialDelay, REBUILD_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }
    
    /**
     * 检查缓存预热是否完成
     * 
     * @return 是否完成
     */
    public boolean isWarmupCompleted() {
        return warmupCompleted;
    }
    
    /**
     * 获取过期时间，添加随机偏移防止雪崩
     * 
     * @param baseExpireSeconds 基础过期时间（秒）
     * @return 添加随机偏移后的过期时间（秒）
     */
    public static int getRandomizedExpireTime(int baseExpireSeconds) {
        // 添加-10%到+10%的随机偏移
        double randomFactor = 0.9 + (Math.random() * 0.2);
        return (int) (baseExpireSeconds * randomFactor);
    }
    
    /**
     * 关闭缓存管理器
     */
    public void shutdown() {
        logger.info("正在关闭缓存管理器...");
        
        try {
            // 关闭定时任务
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            
            // 销毁Redis连接池
            RedisUtil.destroy();
            
            logger.info("缓存管理器已关闭");
        } catch (Exception e) {
            logger.error("关闭缓存管理器失败: {}", e.getMessage(), e);
        }
    }
} 