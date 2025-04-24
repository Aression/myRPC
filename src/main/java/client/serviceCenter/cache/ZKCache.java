package client.serviceCenter.cache;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.*;


public class ZKCache {
    private static final Logger logger = LoggerFactory.getLogger(ZKCache.class);
    private final Map<String, List<String>> cache;
    private final Map<String, Long> lastAccessTime; // 记录最后访问时间
    private final Map<String, Long> lastUpdateTime; // 记录最后更新时间
    private static volatile ZKCache instance;
    
    // 默认缓存过期时间（毫秒）
    private static final long DEFAULT_EXPIRE_TIME = 30 * 60 * 1000; // 30分钟
    // 默认清理间隔（毫秒）
    private static final long DEFAULT_CLEAN_INTERVAL = 5 * 60 * 1000; // 5分钟
    
    private final ScheduledExecutorService cleanExecutor;
    private long expireTime;
    
    private ZKCache() {
        this.cache = new ConcurrentHashMap<>();
        this.lastAccessTime = new ConcurrentHashMap<>();
        this.lastUpdateTime = new ConcurrentHashMap<>();
        this.expireTime = DEFAULT_EXPIRE_TIME;
        this.cleanExecutor = Executors.newSingleThreadScheduledExecutor();
        startCleanTask();
    }
    
    /**
     * 设置缓存过期时间
     * @param expireTime 过期时间（毫秒）
     */
    public void setExpireTime(long expireTime) {
        this.expireTime = expireTime;
    }
    
    /**
     * 启动清理任务
     */
    private void startCleanTask() {
        cleanExecutor.scheduleAtFixedRate(() -> {
            try {
                cleanExpiredCache();
            } catch (Exception e) {
                logger.warn("清理缓存时发生错误: " + e.getMessage());
            }
        }, DEFAULT_CLEAN_INTERVAL, DEFAULT_CLEAN_INTERVAL, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 清理过期的缓存
     */
    private void cleanExpiredCache() {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<String, List<String>>> iterator = cache.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<String, List<String>> entry = iterator.next();
            String serviceName = entry.getKey();
            Long lastAccess = lastAccessTime.get(serviceName);
            Long lastUpdate = lastUpdateTime.get(serviceName);
            
            // 如果最后访问时间和最后更新时间都超过过期时间，则清理该服务缓存
            if (lastAccess != null && lastUpdate != null &&
                (currentTime - lastAccess > expireTime) &&
                (currentTime - lastUpdate > expireTime)) {
                iterator.remove();
                lastAccessTime.remove(serviceName);
                lastUpdateTime.remove(serviceName);
                logger.info("清理过期缓存: " + serviceName);
            }
        }
    }
    
    /**
     * 获取ZKCache单例实例
     * @return ZKCache实例
     */
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
     * 添加服务地址到缓存
     * @param serviceName 服务名称
     * @param address 服务地址
     */
    public void addService(String serviceName, String address) {
        List<String> addressList = cache.computeIfAbsent(serviceName, k -> new ArrayList<>());
        if (!addressList.contains(address)) {
            addressList.add(address);
            lastUpdateTime.put(serviceName, System.currentTimeMillis());
        }
    }

    /**
     * 编辑服务地址
     * @param serviceName 服务名称
     * @param oldAddress 旧地址
     * @param newAddress 新地址
     */
    public void editServiceAddress(String serviceName, String oldAddress, String newAddress) {
        List<String> addressList = cache.get(serviceName);
        if (addressList != null) {
            int index = addressList.indexOf(oldAddress);
            if (index != -1) {
                addressList.set(index, newAddress);
                lastUpdateTime.put(serviceName, System.currentTimeMillis());
            }
        }
    }

    /**
     * 获取服务的所有地址
     * @param serviceName 服务名称
     * @return 服务地址列表
     */
    public List<String> getServices(String serviceName) {
        lastAccessTime.put(serviceName, System.currentTimeMillis());
        return cache.getOrDefault(serviceName, new ArrayList<>());
    }

    /**
     * 删除服务地址
     * @param serviceName 服务名称
     * @param address 要删除的地址
     */
    public void deleteServiceAddress(String serviceName, String address) {
        List<String> addressList = cache.get(serviceName);
        if (addressList != null) {
            addressList.remove(address);
            if (addressList.isEmpty()) {
                cache.remove(serviceName);
                lastAccessTime.remove(serviceName);
                lastUpdateTime.remove(serviceName);
            } else {
                lastUpdateTime.put(serviceName, System.currentTimeMillis());
            }
        }
    }

    /**
     * 清空缓存
     */
    public void clear() {
        cache.clear();
    }

    /**
     * 获取所有服务名称
     * @return 服务名称列表
     */
    public Set<String> getAllServiceNames() {
        return cache.keySet();
    }

    /**
     * 关闭缓存清理任务
     */
    public void shutdown() {
        cleanExecutor.shutdown();
        try {
            if (!cleanExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                cleanExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
