package common.util;

import org.redisson.Redisson;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于 Redisson 的 Redis 工具类
 */
public class RedisUtil {
    private static final Logger logger = LoggerFactory.getLogger(RedisUtil.class);

    private static final String REDIS_ADDRESS = "redis://localhost:6379";
    private static final String PASSWORD = null;
    private static final int DATABASE = 0;
    private static final int TIMEOUT_MILLIS = 5000;
    private static final int CONNECTION_POOL_SIZE = 64;
    private static final int MIN_IDLE_CONNECTION = 16;
    private static final int SUBSCRIPTION_POOL_SIZE = 25;
    private static final int RETRY_ATTEMPTS = 3;
    private static final int RETRY_INTERVAL_MILLIS = 1500;

    private static final long DEFAULT_BLOOM_CAPACITY = 100_000L;
    private static final double DEFAULT_BLOOM_FPP = 0.01D;

    private static final ReentrantLock INIT_LOCK = new ReentrantLock();
    private static volatile RedissonClient redissonClient;

    public static void initPool() {
        getClient();
    }

    public static RedissonClient getClient() {
        if (redissonClient == null) {
            INIT_LOCK.lock();
            try {
                if (redissonClient == null) {
                    Config config = new Config();
                    SingleServerConfig serverConfig = config.useSingleServer()
                            .setAddress(REDIS_ADDRESS)
                            .setTimeout(TIMEOUT_MILLIS)
                            .setConnectionPoolSize(CONNECTION_POOL_SIZE)
                            .setSubscriptionConnectionPoolSize(SUBSCRIPTION_POOL_SIZE)
                            .setConnectionMinimumIdleSize(MIN_IDLE_CONNECTION)
                            .setDatabase(DATABASE)
                            .setRetryAttempts(RETRY_ATTEMPTS)
                            .setRetryInterval(RETRY_INTERVAL_MILLIS);
                    if (PASSWORD != null && !PASSWORD.isEmpty()) {
                        serverConfig.setPassword(PASSWORD);
                    }
                    redissonClient = Redisson.create(config);
                    logger.info("Redisson 客户端初始化成功");
                }
            } catch (Exception e) {
                logger.error("初始化 Redisson 客户端失败: {}", e.getMessage(), e);
                throw new IllegalStateException("无法初始化 RedissonClient", e);
            } finally {
                INIT_LOCK.unlock();
            }
        }
        return redissonClient;
    }

    public static void destroy() {
        if (redissonClient != null) {
            INIT_LOCK.lock();
            try {
                if (redissonClient != null) {
                    redissonClient.shutdown();
                    redissonClient = null;
                    logger.info("Redisson 客户端已关闭");
                }
            } catch (Exception e) {
                logger.warn("关闭 Redisson 客户端失败: {}", e.getMessage(), e);
            } finally {
                INIT_LOCK.unlock();
            }
        }
    }

    // ---------------- String 操作 ----------------

    public static boolean set(String key, String value, int expireTimeSeconds) {
        try {
            RBucket<String> bucket = getClient().getBucket(key);
            if (expireTimeSeconds > 0) {
                bucket.set(value, Duration.ofSeconds(expireTimeSeconds));
            } else {
                bucket.set(value);
            }
            return true;
        } catch (Exception e) {
            logger.error("设置 Redis 键值失败: key={}, error={}", key, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取 String 类型的值
     * 注意：不要对 Lock Key 或 Hash Key 调用此方法，否则会报 WRONGTYPE 异常
     */
    public static String get(String key) {
        try {
            RBucket<String> bucket = getClient().getBucket(key);
            return bucket.get();
        } catch (Exception e) {
            logger.error("获取 Redis 键值失败: key={}, error={}", key, e.getMessage(), e);
            return null;
        }
    }

    // ---------------- 通用 Key 操作 ----------------

    /**
     * [修改] 使用 RKeys 接口检查键是否存在，更加通用，不会受限于 key 的类型
     */
    public static boolean exists(String key) {
        try {
            // getBucket(key).isExists() 在某些版本或特定类型下可能有歧义，改用 countExists
            return getClient().getKeys().countExists(key) > 0;
        } catch (Exception e) {
            logger.error("检查 Redis 键是否存在失败: key={}, error={}", key, e.getMessage(), e);
            return false;
        }
    }

    public static boolean del(String key) {
        try {
            return getClient().getKeys().delete(key) > 0;
        } catch (Exception e) {
            logger.error("删除 Redis 键失败: key={}, error={}", key, e.getMessage(), e);
            return false;
        }
    }

    public static boolean expire(String key, int expireTimeSeconds) {
        try {
            return getClient().getKeys().expire(key, expireTimeSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("设置 Redis 键过期时间失败: key={}, error={}", key, e.getMessage(), e);
            return false;
        }
    }

    // ---------------- 分布式锁操作 ----------------

    public static boolean tryGetDistributedLock(String lockKey, int leaseTimeMillis) {
        try {
            RLock lock = getClient().getLock(lockKey);
            return lock.tryLock(0, leaseTimeMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            logger.error("获取分布式锁失败: lockKey={}", lockKey, e);
            return false;
        }
    }

    public static boolean releaseDistributedLock(String lockKey) {
        try {
            RLock lock = getClient().getLock(lockKey);
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("释放分布式锁失败: lockKey={}", lockKey, e);
            return false;
        }
    }

    /**
     * [新增] 专门用于检查锁的状态，替代用 get() 来判断锁
     */
    public static boolean isLocked(String lockKey) {
        try {
            RLock lock = getClient().getLock(lockKey);
            return lock.isLocked();
        } catch (Exception e) {
            logger.error("检查锁状态失败: lockKey={}, error={}", lockKey, e.getMessage());
            return false;
        }
    }

    // ---------------- Bloom Filter / Hash / Keys ----------------
    // (保持原样，仅稍微格式化)

    public static boolean bfExists(String key, String value) {
        try {
            RBloomFilter<String> filter = getClient().getBloomFilter(key);
            if (!filter.isExists()) {
                return false;
            }
            return filter.contains(value);
        } catch (Exception e) {
            logger.error("布隆过滤器检查失败: key={}, value={}, error={}", key, value, e.getMessage(), e);
            return false;
        }
    }

    public static boolean bfAdd(String key, String value) {
        try {
            RBloomFilter<String> filter = getClient().getBloomFilter(key);
            if (!filter.isExists()) {
                filter.tryInit(DEFAULT_BLOOM_CAPACITY, DEFAULT_BLOOM_FPP);
            }
            return filter.add(value);
        } catch (Exception e) {
            logger.error("布隆过滤器添加失败: key={}, value={}, error={}", key, value, e.getMessage(), e);
            return false;
        }
    }

    public static boolean hset(String key, String field, String value) {
        try {
            return getClient().getMap(key).fastPut(field, value);
        } catch (Exception e) {
            logger.error("设置 Hash 字段失败: key={}, field={}, error={}", key, field, e.getMessage(), e);
            return false;
        }
    }

    public static String hget(String key, String field) {
        try {
            Object result = getClient().getMap(key).get(field);
            return result == null ? null : result.toString();
        } catch (Exception e) {
            logger.error("获取 Hash 字段失败: key={}, field={}, error={}", key, field, e.getMessage(), e);
            return null;
        }
    }

    public static Set<String> keys(String pattern) {
        try {
            RKeys keys = getClient().getKeys();
            Set<String> result = new HashSet<>();
            Iterable<String> iterable = keys.getKeysByPattern(pattern);
            if (iterable != null) {
                for (String key : iterable) {
                    result.add(key);
                }
            }
            return result;
        } catch (Exception e) {
            logger.error("获取 Redis 键集合失败: pattern={}, error={}", pattern, e.getMessage(), e);
            return Collections.emptySet();
        }
    }
}