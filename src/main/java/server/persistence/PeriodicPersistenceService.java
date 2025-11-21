package server.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import common.pojo.User;
import common.util.JsonFileUtil;
import common.util.RedisUtil;
import com.alibaba.fastjson.JSON;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 定期持久化服务
 * 负责定期将Redis中的数据持久化到本地文件
 * 使用分布式锁确保多节点环境下只有一个节点执行持久化
 * TODO: 把这个能力注册成装饰器模式以供用户在框架内为server调用。
 */
public class PeriodicPersistenceService {
    private static final Logger logger = LoggerFactory.getLogger(PeriodicPersistenceService.class);

    // 持久化间隔时间（分钟）
    private final int intervalMinutes;
    // 定时任务执行器
    private ScheduledExecutorService scheduler;
    // Redis缓存key前缀
    private static final String USER_CACHE_PREFIX = "user:";
    // 持久化锁key
    private static final String PERSIST_LOCK_KEY = "user:persist:lock";
    // 锁超时时间（毫秒）
    private static final int LOCK_EXPIRE_MS = 30000; // 30秒

    /**
     * 构造函数
     * 
     * @param intervalMinutes 持久化间隔时间（分钟）
     */
    public PeriodicPersistenceService(int intervalMinutes) {
        this.intervalMinutes = intervalMinutes;
    }

    /**
     * 启动定期持久化服务
     */
    public void start() {
        if (scheduler != null && !scheduler.isShutdown()) {
            logger.warn("定期持久化服务已经在运行中");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "PeriodicPersistence-Thread");
            thread.setDaemon(true); // 设置为守护线程
            return thread;
        });

        // 延迟1分钟后开始首次执行，之后每隔指定间隔执行一次
        scheduler.scheduleAtFixedRate(
                this::performPersistence,
                1, // 初始延迟1分钟
                intervalMinutes,
                TimeUnit.MINUTES);

        logger.info("定期持久化服务已启动，间隔: {} 分钟", intervalMinutes);
    }

    /**
     * 停止定期持久化服务
     */
    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            logger.info("正在停止定期持久化服务...");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
                logger.info("定期持久化服务已停止");
            } catch (InterruptedException e) {
                logger.error("停止定期持久化服务时被中断", e);
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 执行持久化操作
     */
    private void performPersistence() {
        boolean getLock = false;

        try {
            // 尝试获取分布式锁
            getLock = RedisUtil.tryGetDistributedLock(PERSIST_LOCK_KEY, LOCK_EXPIRE_MS);

            if (getLock) {
                logger.info("[定期持久化] 获取持久化锁成功，开始持久化用户数据...");

                // 从Redis获取所有用户数据
                Set<String> redisKeys = RedisUtil.keys(USER_CACHE_PREFIX + "*");
                List<User> usersToSave = new ArrayList<>();

                if (redisKeys != null && !redisKeys.isEmpty()) {
                    for (String key : redisKeys) {
                        String userJson = RedisUtil.get(key);
                        if (userJson != null) {
                            try {
                                User user = JSON.parseObject(userJson, User.class);
                                usersToSave.add(user);
                            } catch (Exception e) {
                                logger.error("[定期持久化] 解析Redis中的用户数据失败: key={}", key, e);
                            }
                        }
                    }

                    if (!usersToSave.isEmpty()) {
                        JsonFileUtil.saveAllUsers(usersToSave);
                        logger.info("[定期持久化] 持久化完成，共保存 {} 条用户数据", usersToSave.size());
                    } else {
                        logger.info("[定期持久化] 没有有效数据需要持久化");
                    }
                } else {
                    logger.info("[定期持久化] Redis中没有用户数据");
                }
            } else {
                logger.debug("[定期持久化] 未能获取持久化锁，其他节点可能正在执行持久化");
            }
        } catch (Exception e) {
            logger.error("[定期持久化] 持久化过程发生异常: {}", e.getMessage(), e);
        } finally {
            // 释放分布式锁
            if (getLock) {
                RedisUtil.releaseDistributedLock(PERSIST_LOCK_KEY);
                logger.debug("[定期持久化] 持久化锁已释放");
            }
        }
    }
}
