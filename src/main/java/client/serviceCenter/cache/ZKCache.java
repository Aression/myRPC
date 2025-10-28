package client.serviceCenter.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * ZooKeeper 服务地址的本地缓存（优化版）
 * <p>
 * 核心修改：
 * 1. 新增 setServices 方法，用于全量替换指定服务的地址列表，完美适配 ZK Watcher 的回调。
 * 2. 移除了不适用于 ZK Watcher 场景的 editServiceAddress 方法，简化了API。
 * 3. 保留了原有的线程安全、单例模式和缓存自动过期清理的健壮设计。
 */
public class ZKCache {
    private static final Logger logger = LoggerFactory.getLogger(ZKCache.class);

    // 使用 ConcurrentHashMap 保证基础操作的线程安全
    private final Map<String, CopyOnWriteArrayList<String>> cache = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> lastAccessTime = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> lastUpdateTime = new ConcurrentHashMap<>();

    private static volatile ZKCache instance;

    private static final long DEFAULT_EXPIRE_TIME = 30 * 60 * 1000; // 30分钟
    private static final long DEFAULT_CLEAN_INTERVAL = 5 * 60 * 1000; // 5分钟

    private final ScheduledExecutorService cleanExecutor;
    private volatile long expireTime;
    
    // 使用读写锁保证跨多个Map的复合操作的原子性
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();

    private ZKCache() {
        this.expireTime = DEFAULT_EXPIRE_TIME;
        ThreadFactory threadFactory = r -> {
            Thread thread = Executors.defaultThreadFactory().newThread(r);
            thread.setName("ZKCache-Cleaner");
            thread.setDaemon(true);
            return thread;
        };
        this.cleanExecutor = Executors.newScheduledThreadPool(1, threadFactory);
        startCleanTask();
    }

    public static ZKCache getInstance() {
        if (instance == null) {
            synchronized (ZKCache.class) {
                if (instance == null) {
                    instance = new ZKCache();
                }
            }
        }
        return instance;
    }

    /**
     * 【新增核心方法】全量设置/替换一个服务的地址列表。
     * 此方法是线程安全的，会完全覆盖该服务之前的地址列表。
     *
     * @param serviceName 服务名称
     * @param addresses   该服务的最新完整地址列表
     */
    public void setServices(String serviceName, List<String> addresses) {
        cacheLock.writeLock().lock();
        try {
            // 使用新的列表创建一个 CopyOnWriteArrayList 并放入缓存
            cache.put(serviceName, new CopyOnWriteArrayList<>(addresses));
            long now = System.currentTimeMillis();
            // 更新最后更新时间
            lastUpdateTime.computeIfAbsent(serviceName, k -> new AtomicLong()).set(now);
            // 更新操作也视为一次访问，防止刚更新完就被清理
            lastAccessTime.computeIfAbsent(serviceName, k -> new AtomicLong()).set(now);
            logger.debug("缓存已更新: 服务 [{}] -> 地址 {}", serviceName, addresses);
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * 获取服务的所有地址。
     *
     * @param serviceName 服务名称
     * @return 服务地址列表。如果服务不存在，返回空列表。
     */
    public List<String> getServices(String serviceName) {
        // 更新访问时间不需要加锁，因为 ConcurrentHashMap 的操作是原子的
        lastAccessTime.computeIfAbsent(serviceName, k -> new AtomicLong()).set(System.currentTimeMillis());
        
        // 从缓存获取数据。由于值是 CopyOnWriteArrayList，读取是无锁且线程安全的。
        List<String> addresses = cache.get(serviceName);
        
        return addresses != null ? addresses : Collections.emptyList();
    }

    /**
     * 添加单个服务地址到缓存（如果不存在）。
     * 主要用于手动管理或测试。
     *
     * @param serviceName 服务名称
     * @param address     服务地址
     */
    public void addService(String serviceName, String address) {
        cacheLock.writeLock().lock();
        try {
            CopyOnWriteArrayList<String> addressList = cache.computeIfAbsent(serviceName, k -> new CopyOnWriteArrayList<>());
            // addIfAbsent 是 CopyOnWriteArrayList 的原子操作
            if (addressList.addIfAbsent(address)) {
                long now = System.currentTimeMillis();
                lastUpdateTime.computeIfAbsent(serviceName, k -> new AtomicLong()).set(now);
                lastAccessTime.computeIfAbsent(serviceName, k -> new AtomicLong()).set(now);
            }
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * 删除单个服务地址。
     *
     * @param serviceName 服务名称
     * @param address     要删除的地址
     */
    public void deleteServiceAddress(String serviceName, String address) {
        cacheLock.writeLock().lock();
        try {
            List<String> addressList = cache.get(serviceName);
            if (addressList != null && addressList.remove(address)) {
                long now = System.currentTimeMillis();
                lastUpdateTime.computeIfAbsent(serviceName, k -> new AtomicLong()).set(now);
                lastAccessTime.computeIfAbsent(serviceName, k -> new AtomicLong()).set(now);
                // 如果列表为空，则从缓存中移除该服务
                if (addressList.isEmpty()) {
                    cache.remove(serviceName);
                    lastAccessTime.remove(serviceName);
                    lastUpdateTime.remove(serviceName);
                }
            }
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    public void clear() {
        cacheLock.writeLock().lock();
        try {
            cache.clear();
            lastAccessTime.clear();
            lastUpdateTime.clear();
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    public Set<String> getAllServiceNames() {
        // keySet() 返回的是 ConcurrentHashMap 的弱一致性视图，无需加锁
        return cache.keySet();
    }
    
    public void shutdown() {
        if (cleanExecutor != null && !cleanExecutor.isShutdown()) {
            cleanExecutor.shutdown();
            try {
                if (!cleanExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    cleanExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // --- 缓存清理逻辑保持不变 ---
    
    public void setExpireTime(long expireTime) {
        this.expireTime = expireTime;
    }

    private void startCleanTask() {
        cleanExecutor.scheduleAtFixedRate(this::cleanExpiredCache,
                DEFAULT_CLEAN_INTERVAL, DEFAULT_CLEAN_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private void cleanExpiredCache() {
        long currentTime = System.currentTimeMillis();
        Set<String> keysToRemove = new HashSet<>();

        // 遍历查找过期的key，此过程无锁，因为我们遍历的是 ConcurrentHashMap 的 keySet
        for (String serviceName : cache.keySet()) {
            AtomicLong lastAccess = lastAccessTime.get(serviceName);
            AtomicLong lastUpdate = lastUpdateTime.get(serviceName);

            if (lastAccess != null && lastUpdate != null &&
                    (currentTime - lastAccess.get() > expireTime) &&
                    (currentTime - lastUpdate.get() > expireTime)) {
                keysToRemove.add(serviceName);
            }
        }
        
        if (!keysToRemove.isEmpty()) {
            logger.info("准备清理过期缓存: {}", keysToRemove);
            // 获取写锁以进行安全的批量删除
            cacheLock.writeLock().lock();
            try {
                for (String serviceName : keysToRemove) {
                    // 双重检查，防止在获取锁的过程中缓存被访问或更新
                    AtomicLong lastAccess = lastAccessTime.get(serviceName);
                    AtomicLong lastUpdate = lastUpdateTime.get(serviceName);
                    if (lastAccess != null && lastUpdate != null &&
                        (currentTime - lastAccess.get() > expireTime) &&
                        (currentTime - lastUpdate.get() > expireTime)) {
                        cache.remove(serviceName);
                        lastAccessTime.remove(serviceName);
                        lastUpdateTime.remove(serviceName);
                    }
                }
            } finally {
                cacheLock.writeLock().unlock();
            }
        }
    }
}