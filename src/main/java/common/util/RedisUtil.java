package common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.*;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.params.SetParams;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Redis工具类，提供常用Redis操作
 */
public class RedisUtil {
    private static final Logger logger = LoggerFactory.getLogger(RedisUtil.class);
    
    // Redis连接池
    private static JedisPool jedisPool;
    // Redis配置
    private static final String HOST = "localhost";
    private static final int PORT = 6379;
    private static final String PASSWORD = null; // 如果有密码，在此设置
    private static final int DATABASE = 0;
    private static final int MAX_TOTAL = 50;
    private static final int MAX_IDLE = 10;
    private static final int MIN_IDLE = 5;
    private static final int MAX_WAIT_MILLIS = 3000;
    private static final int TIMEOUT = 2000;
    
    // 可重入锁，保证多线程环境下的初始化安全
    private static final ReentrantLock LOCK = new ReentrantLock();
    
    // Redis分布式锁相关
    private static final String LOCK_SUCCESS = "OK";
    private static final String SET_IF_NOT_EXIST = "NX";
    private static final String SET_WITH_EXPIRE_TIME = "PX";
    private static final Long RELEASE_SUCCESS = 1L;
    
    /**
     * 初始化Redis连接池
     */
    public static void initPool() {
        if (jedisPool == null) {
            LOCK.lock();
            try {
                if (jedisPool == null) {
                    // 连接池配置
                    JedisPoolConfig poolConfig = new JedisPoolConfig();
                    poolConfig.setMaxTotal(MAX_TOTAL);      // 最大连接数
                    poolConfig.setMaxIdle(MAX_IDLE);        // 最大空闲连接数
                    poolConfig.setMinIdle(MIN_IDLE);        // 最小空闲连接数
                    poolConfig.setMaxWait(java.time.Duration.ofMillis(MAX_WAIT_MILLIS)); // 获取连接的最大等待时间
                    poolConfig.setTestOnBorrow(true);       // 获取连接时测试连接是否可用
                    poolConfig.setTestOnReturn(true);       // 归还连接时测试连接是否可用
                    poolConfig.setBlockWhenExhausted(true); // 连接耗尽时是否阻塞
                    
                    // 创建连接池
                    if (PASSWORD != null && !PASSWORD.isEmpty()) {
                        jedisPool = new JedisPool(poolConfig, HOST, PORT, TIMEOUT, PASSWORD, DATABASE);
                    } else {
                        jedisPool = new JedisPool(poolConfig, HOST, PORT, TIMEOUT);
                    }
                    logger.info("Redis连接池初始化成功");
                }
            } finally {
                LOCK.unlock();
            }
        }
    }
    
    /**
     * 获取Jedis连接
     */
    public static Jedis getJedis() {
        if (jedisPool == null) {
            initPool();
        }
        try {
            return jedisPool.getResource();
        } catch (JedisConnectionException e) {
            logger.error("获取Redis连接失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 释放Jedis连接
     */
    public static void closeJedis(Jedis jedis) {
        if (jedis != null) {
            jedis.close();
        }
    }
    
    /**
     * 销毁连接池
     */
    public static void destroy() {
        if (jedisPool != null) {
            jedisPool.destroy();
            jedisPool = null;
        }
    }
    
    /**
     * 设置字符串键值对，可选过期时间
     *
     * @param key 键
     * @param value 值
     * @param expireTime 过期时间（秒），小于等于0表示不过期
     * @return 是否设置成功
     */
    public static boolean set(String key, String value, int expireTime) {
        Jedis jedis = null;
        try {
            jedis = getJedis();
            if (expireTime > 0) {
                jedis.setex(key, expireTime, value);
            } else {
                jedis.set(key, value);
            }
            return true;
        } catch (Exception e) {
            logger.error("设置Redis键值对失败: key={}, error={}", key, e.getMessage(), e);
            return false;
        } finally {
            closeJedis(jedis);
        }
    }
    
    /**
     * 获取字符串值
     *
     * @param key 键
     * @return 值，如果键不存在则返回null
     */
    public static String get(String key) {
        Jedis jedis = null;
        try {
            jedis = getJedis();
            return jedis.get(key);
        } catch (Exception e) {
            logger.error("获取Redis键值对失败: key={}, error={}", key, e.getMessage(), e);
            return null;
        } finally {
            closeJedis(jedis);
        }
    }
    
    /**
     * 设置键的过期时间
     *
     * @param key 键
     * @param expireTime 过期时间（秒）
     * @return 是否设置成功
     */
    public static boolean expire(String key, int expireTime) {
        Jedis jedis = null;
        try {
            jedis = getJedis();
            jedis.expire(key, expireTime);
            return true;
        } catch (Exception e) {
            logger.error("设置Redis键过期时间失败: key={}, error={}", key, e.getMessage(), e);
            return false;
        } finally {
            closeJedis(jedis);
        }
    }
    
    /**
     * 检查键是否存在
     *
     * @param key 键
     * @return 是否存在
     */
    public static boolean exists(String key) {
        Jedis jedis = null;
        try {
            jedis = getJedis();
            return jedis.exists(key);
        } catch (Exception e) {
            logger.error("检查Redis键是否存在失败: key={}, error={}", key, e.getMessage(), e);
            return false;
        } finally {
            closeJedis(jedis);
        }
    }
    
    /**
     * 删除键
     *
     * @param key 键
     * @return 是否删除成功
     */
    public static boolean del(String key) {
        Jedis jedis = null;
        try {
            jedis = getJedis();
            jedis.del(key);
            return true;
        } catch (Exception e) {
            logger.error("删除Redis键失败: key={}, error={}", key, e.getMessage(), e);
            return false;
        } finally {
            closeJedis(jedis);
        }
    }
    
    /**
     * 获取分布式锁
     *
     * @param lockKey 锁的键
     * @param requestId 请求标识
     * @param expireTime 锁的过期时间（毫秒）
     * @return 是否获取锁成功
     */
    public static boolean tryGetDistributedLock(String lockKey, String requestId, int expireTime) {
        Jedis jedis = null;
        try {
            jedis = getJedis();
            SetParams params = new SetParams().nx().px(expireTime);
            String result = jedis.set(lockKey, requestId, params);
            return LOCK_SUCCESS.equals(result);
        } catch (Exception e) {
            logger.error("获取分布式锁失败: lockKey={}, error={}", lockKey, e.getMessage(), e);
            return false;
        } finally {
            closeJedis(jedis);
        }
    }
    
    /**
     * 释放分布式锁
     *
     * @param lockKey 锁的键
     * @param requestId 请求标识
     * @return 是否释放锁成功
     */
    public static boolean releaseDistributedLock(String lockKey, String requestId) {
        Jedis jedis = null;
        try {
            jedis = getJedis();
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
            Object result = jedis.eval(script, Collections.singletonList(lockKey), Collections.singletonList(requestId));
            return RELEASE_SUCCESS.equals(result);
        } catch (Exception e) {
            logger.error("释放分布式锁失败: lockKey={}, error={}", lockKey, e.getMessage(), e);
            return false;
        } finally {
            closeJedis(jedis);
        }
    }
    
    /**
     * 使用布隆过滤器检查是否存在
     * 
     * @param key 布隆过滤器键
     * @param value 要检查的值
     * @return 是否可能存在（true可能存在，false一定不存在）
     */
    public static boolean bfExists(String key, String value) {
        Jedis jedis = null;
        try {
            jedis = getJedis();
            // 注意：需要Redis服务器安装布隆过滤器模块
            return Boolean.TRUE.equals(jedis.exists(key + ":" + value));
        } catch (Exception e) {
            logger.error("布隆过滤器检查失败: key={}, value={}, error={}", key, value, e.getMessage(), e);
            return false;
        } finally {
            closeJedis(jedis);
        }
    }
    
    /**
     * 添加值到布隆过滤器
     * 
     * @param key 布隆过滤器键
     * @param value 要添加的值
     * @return 是否添加成功
     */
    public static boolean bfAdd(String key, String value) {
        Jedis jedis = null;
        try {
            jedis = getJedis();
            // 注意：需要Redis服务器安装布隆过滤器模块
            jedis.set(key + ":" + value, "1");
            return true;
        } catch (Exception e) {
            logger.error("布隆过滤器添加失败: key={}, value={}, error={}", key, value, e.getMessage(), e);
            return false;
        } finally {
            closeJedis(jedis);
        }
    }
    
    /**
     * 向Hash表中添加值
     * 
     * @param key Hash表的键
     * @param field 字段名
     * @param value 字段值
     * @return 是否添加成功
     */
    public static boolean hset(String key, String field, String value) {
        Jedis jedis = null;
        try {
            jedis = getJedis();
            jedis.hset(key, field, value);
            return true;
        } catch (Exception e) {
            logger.error("设置Hash字段失败: key={}, field={}, error={}", key, field, e.getMessage(), e);
            return false;
        } finally {
            closeJedis(jedis);
        }
    }
    
    /**
     * 从Hash表获取值
     * 
     * @param key Hash表的键
     * @param field 字段名
     * @return 字段值
     */
    public static String hget(String key, String field) {
        Jedis jedis = null;
        try {
            jedis = getJedis();
            return jedis.hget(key, field);
        } catch (Exception e) {
            logger.error("获取Hash字段失败: key={}, field={}, error={}", key, field, e.getMessage(), e);
            return null;
        } finally {
            closeJedis(jedis);
        }
    }
    
    /**
     * 根据模式获取所有键
     *
     * @param pattern 键模式，如"user:*"
     * @return 匹配的键集合，如果出错则返回空集合
     */
    public static Set<String> keys(String pattern) {
        Jedis jedis = null;
        try {
            jedis = getJedis();
            return jedis.keys(pattern);
        } catch (Exception e) {
            logger.error("获取Redis键集合失败: pattern={}, error={}", pattern, e.getMessage(), e);
            return Collections.emptySet();
        } finally {
            closeJedis(jedis);
        }
    }
} 