package client.serviceCenter;

import client.serviceCenter.balance.LoadBalance;
import client.serviceCenter.balance.impl.ConsistencyHashBalance;
import client.serviceCenter.cache.ZKCache;
import common.util.AddressUtil;
import common.util.AppConfig;
import io.netty.util.internal.ThreadLocalRandom;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;

import org.slf4j.LoggerFactory;
import client.proxy.breaker.BreakerProvider;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

// 实现 AutoCloseable 接口，以便优雅地关闭资源
public class ZKServiceCenter implements ServiceCenter, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ZKServiceCenter.class);
    private final CuratorFramework client;
    private static final String ROOT_PATH = AppConfig.getString("rpc.zk.namespace", "MY_RPC");
    private static final String RETRY_GROUP = "CanRetry";

    private final Map<String, Map<InetSocketAddress, AtomicLong>> serviceRequestStats = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> serviceTotalRequests = new ConcurrentHashMap<>();

    private final LoadBalance loadBalance;
    private final ZKCache serviceAddressCache;

    // 用于管理和关闭 CuratorCache 实例，防止资源泄露
    private final Map<String, CuratorCache> watcherMap = new ConcurrentHashMap<>();

    public ZKServiceCenter() {
        this(new ConsistencyHashBalance());
    }

    public ZKServiceCenter(LoadBalance loadBalance) {
        RetryPolicy policy = new ExponentialBackoffRetry(1000, 3);
        this.client = CuratorFrameworkFactory.builder()
                .connectString(AppConfig.getString("rpc.zk.connect", "127.0.0.1:2285"))
                .sessionTimeoutMs(AppConfig.getInt("rpc.zk.sessionTimeoutMs", 40000))
                .retryPolicy(policy)
                .namespace(ROOT_PATH)
                .build();
        this.client.start();
        this.loadBalance = loadBalance;
        this.serviceAddressCache = ZKCache.getInstance();
        logger.info("Zookeeper 连接成功");
    }

    /**
     * @deprecated 当使用一致性哈希时，不推荐使用此方法。
     *             它内部生成一个随机特征码，这将导致一致性哈希退化为随机负载均衡。
     *             建议使用 {@link #serviceDiscovery(String, long)} 并传入有业务意义的特征码。
     */
    @Override
    @Deprecated
    public InetSocketAddress serviceDiscovery(String serviceName) {
        long randomFeatureCode = ThreadLocalRandom.current().nextLong();
        return serviceDiscovery(serviceName, randomFeatureCode);
    }

    @Override
    public InetSocketAddress serviceDiscovery(String serviceName, long featureCode) {
        try {
            // 从缓存获取最新的地址列表
            List<String> addressList = serviceAddressCache.getServices(serviceName);

            // 如果缓存为空，则从 ZK 获取并注册监听器
            if (addressList.isEmpty()) {
                if (client.checkExists().forPath("/" + serviceName) == null) {
                    logger.warn("服务 {} 未在 Zookeeper 中注册", serviceName);
                    return null;
                }
                addressList = client.getChildren().forPath("/" + serviceName);
                if (!addressList.isEmpty()) {
                    logger.info("首次发现服务 {}，节点: {}", serviceName, addressList);
                    // 更新缓存
                    serviceAddressCache.setServices(serviceName, addressList);
                    // 注册监听器，使用 computeIfAbsent 避免重复注册
                    registerWatcher(serviceName);
                }
            }

            if (addressList.isEmpty()) {
                logger.warn("服务 {} 没有可用的服务节点", serviceName);
                return null;
            }

            // 将地址列表转换为 InetSocketAddress 列表
            List<InetSocketAddress> inetSocketAddressList = convertToSocketAddressList(addressList);

            // 过滤掉熔断器不可用的节点
            List<InetSocketAddress> availableAddressList = inetSocketAddressList.stream()
                    .filter(addr -> BreakerProvider.getInstance().getBreaker(addr).isAvailable())
                    .collect(Collectors.toList());

            if (availableAddressList.isEmpty()) {
                // 如果所有节点都熔断了，尝试使用全部节点（或者直接失败，这里选择尝试全部，让 NettyRpcClient 去触发熔断更新）
                // 但根据 isAvailable 的逻辑，只有 OPEN 且未到重试时间的才会被过滤。
                // 如果全部 OPEN 且未到时间，那么应该返回 null 或者抛出异常。
                // 这里我们返回 null，表示无可用服务
                logger.warn("服务 {} 所有节点均处于熔断状态", serviceName);
                return null;
            }

            // 使用负载均衡策略选择服务节点
            // 传入过滤后的地址列表
            InetSocketAddress socketAddress = loadBalance.select(serviceName, availableAddressList, featureCode);

            // 更新服务请求统计
            updateStats(serviceName, socketAddress);

            logger.info("选择服务节点: {}", socketAddress);
            return socketAddress;
        } catch (Exception e) {
            logger.error("服务发现失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 注册监听器，当服务节点发生变化时更新缓存。
     * 使用 computeIfAbsent 确保每个服务只注册一个监听器。
     */
    private void registerWatcher(String serviceName) throws Exception {
        watcherMap.computeIfAbsent(serviceName, s -> {
            try {
                String servicePath = "/" + s;
                CuratorCache cache = CuratorCache.build(client, servicePath);

                CuratorCacheListener listener = CuratorCacheListener.builder()
                        .forAll((type, oldData, data) -> updateCache(s))
                        .build();

                cache.listenable().addListener(listener);
                cache.start();
                logger.info("已为服务 {} 注册 ZK 节点监听器", s);
                return cache;
            } catch (Exception e) {
                logger.error("为服务 {} 注册监听器失败", s, e);
                return null; // 返回null，下次调用时会重试
            }
        });
    }

    /**
     * ZK 节点变化时的回调方法。
     * 核心职责：从 ZK 拉取最新地址列表，并更新本地缓存。
     */
    private void updateCache(String serviceName) {
        try {
            String servicePath = "/" + serviceName;
            List<String> newAddressList = client.getChildren().forPath(servicePath);

            // 【核心修改】只更新当前服务的缓存，而不是清空所有
            serviceAddressCache.setServices(serviceName, newAddressList);

            // 【核心修改】不再需要手动调用 updateServiceAddresses
            // 新的 ConsistencyHashBalance 会在下次 select 时自动更新哈希环

            logger.info("服务 {} 的节点已通过监听器更新: {}", serviceName, newAddressList);
        } catch (Exception e) {
            logger.error("通过监听器更新缓存失败: {}", e.getMessage(), e);
        }
    }

    // 提取出的辅助方法，用于更新统计信息
    private void updateStats(String serviceName, InetSocketAddress socketAddress) {
        if (socketAddress != null) {
            serviceRequestStats.computeIfAbsent(serviceName, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(socketAddress, k -> new AtomicLong(0))
                    .incrementAndGet();

            serviceTotalRequests.computeIfAbsent(serviceName, k -> new AtomicLong(0))
                    .incrementAndGet();
        }
    }

    // 提取出的辅助方法，用于地址转换
    private List<InetSocketAddress> convertToSocketAddressList(List<String> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return Collections.emptyList();
        }
        return addresses.stream()
                .map(AddressUtil::fromString)
                .collect(Collectors.toList());
    }

    @Override
    public void close() {
        logger.info("正在关闭 ZKServiceCenter...");
        // 关闭所有 watcher
        watcherMap.values().forEach(CuratorCache::close);
        watcherMap.clear();
        // 关闭 ZK 客户端
        if (client != null) {
            client.close();
        }
        logger.info("ZKServiceCenter 已成功关闭。");
    }

    @Override
    public boolean checkRetry(String serviceName) {
        boolean canRetry = false;
        try {
            List<String> serviceList = client.getChildren().forPath("/" + RETRY_GROUP);
            for (String s : serviceList) {
                if (s.equals(serviceName)) {
                    logger.info("服务{}在重试白名单上，允许重试", serviceName);
                    canRetry = true;
                }
            }
        } catch (Exception e) {
            logger.error("检查服务重试白名单失败: {}", e.getMessage(), e);
        }
        return canRetry; // 默认返回false
    }

    @Override
    public String reportServiceDistribution() {
        StringBuilder report = new StringBuilder();
        report.append("服务分布统计报告:\n");

        for (Map.Entry<String, Map<InetSocketAddress, AtomicLong>> serviceEntry : serviceRequestStats.entrySet()) {
            String serviceName = serviceEntry.getKey();
            Map<InetSocketAddress, AtomicLong> addressStats = serviceEntry.getValue();
            long totalRequests = serviceTotalRequests.getOrDefault(serviceName, new AtomicLong(0)).get();

            report.append("\n服务名称: ").append(serviceName).append("\n");
            report.append("----------------------------------------\n");

            for (Map.Entry<InetSocketAddress, AtomicLong> addressEntry : addressStats.entrySet()) {
                InetSocketAddress address = addressEntry.getKey();
                long requests = addressEntry.getValue().get();
                double percentage = totalRequests > 0 ? (requests * 100.0 / totalRequests) : 0;

                report.append(String.format("地址: %s:%d, 请求数: %d (%.2f%%)\n",
                        address.getHostString(), address.getPort(), requests, percentage));
            }

            report.append(String.format("总请求数: %d\n", totalRequests));
            report.append("----------------------------------------\n");
        }
        return report.toString();
    }
}