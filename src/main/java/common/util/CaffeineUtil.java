package common.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.Set;
import java.util.Collections;

/**
 * Caffeine本地缓存工具类
 */
public class CaffeineUtil {
    private static final Logger logger = LoggerFactory.getLogger(CaffeineUtil.class);
    
    // 存储不同业务的缓存实例
    private static final Map<String, Cache<String, Object>> CACHE_MAP = new ConcurrentHashMap<>();
    
    // 默认缓存配置
    private static final int DEFAULT_MAXIMUM_SIZE = 10000;
    private static final int DEFAULT_EXPIRE_AFTER_WRITE = 300; // 5分钟
    private static final int DEFAULT_EXPIRE_AFTER_ACCESS = 600; // 10分钟
    
    /**
     * 获取指定业务的缓存实例
     *
     * @param business 业务名称
     * @return 缓存实例
     */
    public static Cache<String, Object> getCache(String business) {
        return CACHE_MAP.computeIfAbsent(business, key -> createCache(
                DEFAULT_MAXIMUM_SIZE,
                DEFAULT_EXPIRE_AFTER_WRITE,
                DEFAULT_EXPIRE_AFTER_ACCESS
        ));
    }
    
    /**
     * 创建缓存实例
     *
     * @param maximumSize 最大缓存数量
     * @param expireAfterWrite 写入后过期时间（秒）
     * @param expireAfterAccess 访问后过期时间（秒）
     * @return 缓存实例
     */
    public static Cache<String, Object> createCache(int maximumSize, int expireAfterWrite, int expireAfterAccess) {
        return Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(expireAfterWrite, TimeUnit.SECONDS)
                .expireAfterAccess(expireAfterAccess, TimeUnit.SECONDS)
                .recordStats() // 开启统计
                .build();
    }
    
    /**
     * 从缓存获取值，如果不存在则通过加载函数获取并缓存
     *
     * @param business 业务名称
     * @param key 缓存键
     * @param loadFunction 加载函数
     * @param <T> 返回值类型
     * @return 缓存值
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(String business, String key, Function<String, T> loadFunction) {
        Cache<String, Object> cache = getCache(business);
        return (T) cache.get(key, loadFunction);
    }
    
    /**
     * 从缓存获取值，如果不存在则返回null
     *
     * @param business 业务名称
     * @param key 缓存键
     * @param <T> 返回值类型
     * @return 缓存值
     */
    @SuppressWarnings("unchecked")
    public static <T> T getIfPresent(String business, String key) {
        Cache<String, Object> cache = getCache(business);
        return (T) cache.getIfPresent(key);
    }
    
    /**
     * 将值放入缓存
     *
     * @param business 业务名称
     * @param key 缓存键
     * @param value 缓存值
     */
    public static void put(String business, String key, Object value) {
        Cache<String, Object> cache = getCache(business);
        cache.put(key, value);
    }
    
    /**
     * 从缓存移除指定键
     *
     * @param business 业务名称
     * @param key 缓存键
     */
    public static void invalidate(String business, String key) {
        Cache<String, Object> cache = getCache(business);
        cache.invalidate(key);
    }
    
    /**
     * 清空指定业务的缓存
     *
     * @param business 业务名称
     */
    public static void invalidateAll(String business) {
        Cache<String, Object> cache = getCache(business);
        cache.invalidateAll();
        logger.info("清空缓存: business={}", business);
    }
    
    /**
     * 获取指定业务缓存的统计信息
     *
     * @param business 业务名称
     * @return 统计信息字符串
     */
    public static String getStats(String business) {
        Cache<String, Object> cache = getCache(business);
        return cache.stats().toString();
    }
    
    /**
     * 获取指定业务缓存中的所有键
     *
     * @param business 业务名称
     * @return 键集合，如果不存在该业务的缓存则返回空集合
     */
    public static Set<String> getAllKeys(String business) {
        Cache<String, Object> cache = CACHE_MAP.get(business);
        if (cache == null) {
            return Collections.emptySet();
        }
        try {
            // 使用Caffeine的asMap()方法获取底层Map，然后获取其键集合
            return cache.asMap().keySet();
        } catch (Exception e) {
            logger.error("获取缓存键集合失败: business={}, error={}", business, e.getMessage(), e);
            return Collections.emptySet();
        }
    }
} 