package common.service.impl;

import common.pojo.User;
import common.result.Result;
import common.service.UserService;
import common.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import common.cache.CacheManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.HashSet;
import com.alibaba.fastjson.JSON;

public class UserServiceImpl implements UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);
    private final Map<Integer, User> userStore = new ConcurrentHashMap<>();
    
    // 缓存相关配置
    private static final String USER_BUSINESS = "user"; // 业务名称
    private static final String USER_CACHE_PREFIX = "user:"; // Redis缓存前缀
    private static final String USER_BLOOM_FILTER_KEY = "user:bloom"; // 布隆过滤器键
    private static final int CACHE_EXPIRE_SECONDS = 3600; // 缓存过期时间，1小时
    private static final ReadWriteLock rwLock = new ReentrantReadWriteLock(); // 读写锁
    
    // 用于防止缓存击穿的互斥锁前缀
    private static final String MUTEX_KEY_PREFIX = "user:mutex:";
    // 互斥锁超时时间（毫秒）
    private static final int MUTEX_EXPIRE_MS = 5000;

    public UserServiceImpl() {
        List<User> users = JsonFileUtil.readAllUsers();
        logger.info("从文件加载的用户数据数量: {}", users.size());
        users.forEach(user -> {
            userStore.put(user.getId(), user);
            // 初始化布隆过滤器
            BloomFilterUtil.add(USER_BUSINESS, String.valueOf(user.getId()));
        });
        logger.info("已从文件加载 {} 条用户数据", userStore.size());
        
        // 启动时进行缓存一致性检查
        boolean isConsistent = checkCacheConsistency();
        if (!isConsistent) {
            logger.warn("检测到缓存数据不一致，开始重建缓存...");
            rebuildCache();
        } else {
            logger.info("缓存一致性检查通过");
        }
    }

    /**
     * 检查缓存一致性
     * 确保Redis缓存、本地缓存与数据源（JSON文件）保持一致
     * @return 缓存是否一致
     */
    private boolean checkCacheConsistency() {
        logger.info("开始进行缓存一致性检查...");
        boolean isConsistent = true;
        
        // 1. 获取Redis中所有用户ID
        Set<String> redisKeys = RedisUtil.keys(USER_CACHE_PREFIX + "*");
        Set<Integer> redisUserIds = new HashSet<>();
        if (redisKeys != null) {
            for (String key : redisKeys) {
                String idStr = key.substring(USER_CACHE_PREFIX.length());
                try {
                    redisUserIds.add(Integer.parseInt(idStr));
                } catch (NumberFormatException e) {
                    logger.warn("Redis缓存中存在无效的用户ID: {}", idStr);
                }
            }
        }
        
        // 2. 获取本地缓存中的所有用户ID
        Set<String> caffeineKeys = CaffeineUtil.getAllKeys(USER_BUSINESS);
        Set<Integer> caffeineUserIds = new HashSet<>();
        if (caffeineKeys != null) {
            for (String idStr : caffeineKeys) {
                try {
                    caffeineUserIds.add(Integer.parseInt(idStr));
                } catch (NumberFormatException e) {
                    logger.warn("本地缓存中存在无效的用户ID: {}", idStr);
                }
            }
        }
        
        // 3. 获取数据源（内存中的userStore）的用户ID
        Set<Integer> storeUserIds = userStore.keySet();
        
        // 4. 比较各缓存中的用户ID数量是否一致
        logger.info("数据源用户数: {}, Redis缓存用户数: {}, 本地缓存用户数: {}", 
                   storeUserIds.size(), redisUserIds.size(), caffeineUserIds.size());
        
        if (redisUserIds.size() != storeUserIds.size() || caffeineUserIds.size() != storeUserIds.size()) {
            logger.warn("缓存中的用户数量与数据源不一致");
            return false;
        }
        
        // 5. 检查每个ID的内容是否一致
        for (Integer userId : storeUserIds) {
            String idStr = String.valueOf(userId);
            String cacheKey = USER_CACHE_PREFIX + idStr;
            
            // 检查Redis缓存
            String userJson = RedisUtil.get(cacheKey);
            if (userJson == null) {
                logger.warn("Redis缓存中缺少用户: {}", userId);
                isConsistent = false;
                continue;
            }
            
            try {
                User redisUser = JSON.parseObject(userJson, User.class);
                User storeUser = userStore.get(userId);
                
                // 比较Redis缓存与数据源的数据是否一致
                if (!JSON.toJSONString(redisUser).equals(JSON.toJSONString(storeUser))) {
                    logger.warn("Redis缓存中的用户数据与数据源不一致: {}", userId);
                    isConsistent = false;
                }
            } catch (Exception e) {
                logger.error("解析Redis缓存数据失败: {}", e.getMessage(), e);
                isConsistent = false;
            }
            
            // 检查本地缓存
            User caffeineUser = CaffeineUtil.getIfPresent(USER_BUSINESS, idStr);
            if (caffeineUser == null) {
                logger.warn("本地缓存中缺少用户: {}", userId);
                isConsistent = false;
                continue;
            }
            
            // 比较本地缓存与数据源的数据是否一致
            if (!JSON.toJSONString(caffeineUser).equals(JSON.toJSONString(userStore.get(userId)))) {
                logger.warn("本地缓存中的用户数据与数据源不一致: {}", userId);
                isConsistent = false;
            }
            
            // 检查布隆过滤器
            if (!BloomFilterUtil.mightContain(USER_BUSINESS, idStr)) {
                logger.warn("布隆过滤器中缺少用户ID: {}", userId);
                isConsistent = false;
            }
        }
        
        // 6. 检查Redis中是否有多余的用户
        for (Integer userId : redisUserIds) {
            if (!storeUserIds.contains(userId)) {
                logger.warn("Redis缓存中存在数据源中没有的用户: {}", userId);
                isConsistent = false;
            }
        }
        
        // 7. 检查本地缓存中是否有多余的用户
        for (Integer userId : caffeineUserIds) {
            if (!storeUserIds.contains(userId)) {
                logger.warn("本地缓存中存在数据源中没有的用户: {}", userId);
                isConsistent = false;
            }
        }
        
        return isConsistent;
    }

    @Override
    public Result<User> getUserById(Integer id) {
        logger.info("客户端查询了id={}的用户", id);
        
        if (id == null) {
            logger.warn("查询用户失败：用户ID为空");
            return Result.fail(400, "用户ID不能为空");
        }
        
        String idStr = String.valueOf(id);
        String cacheKey = USER_CACHE_PREFIX + idStr;
        
        // 1. 先查本地缓存（L1缓存）
        User cachedUser = CaffeineUtil.getIfPresent(USER_BUSINESS, idStr);
        if (cachedUser != null) {
            logger.debug("本地缓存命中: id={}", id);
            return Result.success(cachedUser);
        }
        
        // 2. 查Redis缓存（L2缓存）
        String userJson = RedisUtil.get(cacheKey);
        if (userJson != null) {
            try {
                // Redis缓存命中
                User user = JSON.parseObject(userJson, User.class);
                // 回填本地缓存
                CaffeineUtil.put(USER_BUSINESS, idStr, user);
                logger.debug("Redis缓存命中: id={}", id);
                return Result.success(user);
            } catch (Exception e) {
                logger.error("解析Redis缓存数据失败: {}", e.getMessage(), e);
                // 解析失败，删除可能损坏的缓存
                RedisUtil.del(cacheKey);
            }
        }
        
        // 3. 检查布隆过滤器，防止缓存穿透
        if (!BloomFilterUtil.mightContain(USER_BUSINESS, idStr)) {
            logger.info("布隆过滤器显示该ID不存在: id={}", id);
            return Result.fail(404, "用户不存在");
        }
        
        // 4. 获取分布式锁，防止缓存击穿
        String mutexKey = MUTEX_KEY_PREFIX + idStr;
        String requestId = UUID.randomUUID().toString();
        boolean getLock = false;
        
        try {
            // 尝试获取锁
            getLock = RedisUtil.tryGetDistributedLock(mutexKey, requestId, MUTEX_EXPIRE_MS);
            
            if (getLock) {
                // 双重检查，再次检查缓存
                userJson = RedisUtil.get(cacheKey);
                if (userJson != null) {
                    try {
                        User user = JSON.parseObject(userJson, User.class);
                        CaffeineUtil.put(USER_BUSINESS, idStr, user);
                        return Result.success(user);
                    } catch (Exception ignored) {
                        // 继续从数据源获取
                    }
                }
                
                // 5. 从数据源获取
                rwLock.readLock().lock();
                try {
                    User user = userStore.get(id);
                    
                    if (user == null) {
                        logger.info("未找到id={}的用户", id);
                        // 将存在的ID添加到布隆过滤器中，防止缓存穿透
                        // 注意：这里不需要添加，因为不存在的用户不应该被添加到布隆过滤器中
                        return Result.fail(404, "用户不存在");
                    }
                    
                    // 6. 更新缓存
                    logger.info("找到用户: {}", user);
                    userJson = JSON.toJSONString(user);
                    // 添加到Redis缓存，设置随机过期时间防止雪崩
                    int randomExpireTime = CacheManager.getRandomizedExpireTime(CACHE_EXPIRE_SECONDS);
                    RedisUtil.set(cacheKey, userJson, randomExpireTime);
                    // 添加到本地缓存
                    CaffeineUtil.put(USER_BUSINESS, idStr, user);
                    
                    return Result.success(user);
                } finally {
                    rwLock.readLock().unlock();
                }
            } else {
                // 没有获取到锁，短暂睡眠后重试从缓存获取
                try {
                    TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                // 重试获取缓存
                userJson = RedisUtil.get(cacheKey);
                if (userJson != null) {
                    try {
                        User user = JSON.parseObject(userJson, User.class);
                        CaffeineUtil.put(USER_BUSINESS, idStr, user);
                        return Result.success(user);
                    } catch (Exception ignored) {
                        // 直接从数据源获取
                    }
                }
                
                // 如果仍然没有获取到，从数据源读取
                rwLock.readLock().lock();
                try {
                    User user = userStore.get(id);
                    if (user == null) {
                        logger.info("未找到id={}的用户", id);
                        return Result.fail(404, "用户不存在");
                    }
                    logger.info("找到用户: {}", user);
                    return Result.success(user);
                } finally {
                    rwLock.readLock().unlock();
                }
            }
        } finally {
            // 释放分布式锁
            if (getLock) {
                RedisUtil.releaseDistributedLock(mutexKey, requestId);
            }
        }
    }

    @Override
    public Result<Integer> insertUser(User user) {
        if (user == null) {
            logger.warn("插入用户失败：用户对象为空");
            return Result.fail(400, "用户对象为空");
        }
        if (user.getId() == null) {
            logger.warn("插入用户失败：用户ID不能为空");
            return Result.fail(400, "用户ID不能为空");
        }
        
        rwLock.writeLock().lock();
        try {
            if (userStore.containsKey(user.getId())) {
                logger.warn("插入用户失败：用户ID已存在");
                return Result.fail(409, "用户ID已存在");
            }
            logger.info("客户端插入数据：{}", user);
            userStore.put(user.getId(), user);
            
            // 保存到文件
            JsonFileUtil.saveAllUsers(new ArrayList<>(userStore.values()));
            logger.info("用户数据已保存到文件");
            
            // 更新缓存
            String idStr = String.valueOf(user.getId());
            String cacheKey = USER_CACHE_PREFIX + idStr;
            
            // 添加到Redis缓存，使用随机过期时间防止雪崩
            int randomExpireTime = CacheManager.getRandomizedExpireTime(CACHE_EXPIRE_SECONDS);
            RedisUtil.set(cacheKey, JSON.toJSONString(user), randomExpireTime);
            // 添加到本地缓存
            CaffeineUtil.put(USER_BUSINESS, idStr, user);
            // 添加到布隆过滤器
            BloomFilterUtil.add(USER_BUSINESS, idStr);
            
            return Result.success(user.getId());
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public Result<Boolean> deleteUserById(Integer id) {
        if (id == null) {
            return Result.fail(400, "用户ID不能为空");
        }
        
        rwLock.writeLock().lock();
        try {
            // 检查用户是否存在
            boolean exists = userStore.containsKey(id);
            if (!exists) {
                return Result.fail(404, "用户不存在");
            }
            
            boolean success = userStore.remove(id) != null;
            
            if (success) {
                // 保存到文件
                JsonFileUtil.saveAllUsers(new ArrayList<>(userStore.values()));
                
                // 删除缓存
                String idStr = String.valueOf(id);
                String cacheKey = USER_CACHE_PREFIX + idStr;
                
                // 删除Redis缓存
                RedisUtil.del(cacheKey);
                // 删除本地缓存
                CaffeineUtil.invalidate(USER_BUSINESS, idStr);
                
                // 注意：不从布隆过滤器删除，因为布隆过滤器不支持删除
                // 这可能导致已删除的ID在布隆过滤器中仍然存在，但这不会影响正确性
                // 只会导致对已删除ID的查询需要访问数据源来确认不存在
                
                return Result.success(true, "用户删除成功");
            } else {
                return Result.fail(500, "用户删除失败");
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }
    
    @Override
    public Result<Boolean> updateUser(User user) {
        if (user == null) {
            logger.warn("更新用户失败：用户对象为空");
            return Result.fail(400, "用户对象为空");
        }
        
        if (user.getId() == null) {
            logger.warn("更新用户失败：用户ID不能为空");
            return Result.fail(400, "用户ID不能为空");
        }
        
        rwLock.writeLock().lock();
        try {
            // 检查用户是否存在
            Integer id = user.getId();
            User existingUser = userStore.get(id);
            
            if (existingUser == null) {
                logger.warn("更新用户失败：ID为{}的用户不存在", id);
                return Result.fail(404, "用户不存在");
            }
            
            // 更新用户信息
            userStore.put(id, user);
            logger.info("用户信息更新成功：{}", user);
            
            // 保存到文件
            JsonFileUtil.saveAllUsers(new ArrayList<>(userStore.values()));
            logger.info("用户数据已保存到文件");
            
            // 更新缓存
            String idStr = String.valueOf(id);
            String cacheKey = USER_CACHE_PREFIX + idStr;
            
            // 添加到Redis缓存，使用随机过期时间防止雪崩
            int randomExpireTime = CacheManager.getRandomizedExpireTime(CACHE_EXPIRE_SECONDS);
            RedisUtil.set(cacheKey, JSON.toJSONString(user), randomExpireTime);
            // 更新本地缓存
            CaffeineUtil.put(USER_BUSINESS, idStr, user);
            
            return Result.success(true, "用户更新成功");
        } finally {
            rwLock.writeLock().unlock();
        }
    }
    
    /**
     * 重建缓存，用于缓存预热或修复缓存数据不一致
     */
    public void rebuildCache() {
        rwLock.readLock().lock();
        try {
            logger.info("开始重建用户缓存...");
            
            // 清空本地缓存
            CaffeineUtil.invalidateAll(USER_BUSINESS);
            
            // 清空Redis缓存
            Set<String> redisKeys = RedisUtil.keys(USER_CACHE_PREFIX + "*");
            if (redisKeys != null) {
                for (String key : redisKeys) {
                    RedisUtil.del(key);
                }
            }
            
            // 遍历所有用户，更新缓存
            for (User user : userStore.values()) {
                String idStr = String.valueOf(user.getId());
                String cacheKey = USER_CACHE_PREFIX + idStr;
                
                // 更新Redis缓存，使用随机过期时间防止雪崩
                int randomExpireTime = CacheManager.getRandomizedExpireTime(CACHE_EXPIRE_SECONDS);
                RedisUtil.set(cacheKey, JSON.toJSONString(user), randomExpireTime);
                // 更新本地缓存
                CaffeineUtil.put(USER_BUSINESS, idStr, user);
                // 更新布隆过滤器
                BloomFilterUtil.add(USER_BUSINESS, idStr);
            }
            
            logger.info("用户缓存重建完成，共更新 {} 条记录", userStore.size());
        } finally {
            rwLock.readLock().unlock();
        }
    }
}
