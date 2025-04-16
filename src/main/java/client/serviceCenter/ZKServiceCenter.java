package client.serviceCenter;

import client.serviceCenter.balance.ConsistencyHashBalance;
import client.serviceCenter.balance.LoadBalance;
import client.serviceCenter.cache.ZKCache;
import common.util.AddressUtil;
import io.netty.util.internal.ThreadLocalRandom;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class ZKServiceCenter implements ServiceCenter{
    private static final Logger logger = LoggerFactory.getLogger(ZKServiceCenter.class);
    private final CuratorFramework client;
    private static final String ROOT_PATH="MY_RPC"; // zookeeper根路径节点
    private static final String RETRY_GROUP = "CanRetry"; //可重试服务组名称，不在该组的服务不进行重试


    // 负载均衡策略
    private final LoadBalance loadBalance;
    // 服务地址缓存
    private final ZKCache serviceAddressCache;
    
    public ZKServiceCenter(){
        this(new ConsistencyHashBalance());
    }
    
    public ZKServiceCenter(LoadBalance loadBalance){
        // 指数时间重试
        RetryPolicy policy = new ExponentialBackoffRetry(
                1000,
                3
        );
        // 设置心跳侦听
        this.client = CuratorFrameworkFactory.builder()
                .connectString("127.0.0.1:2285")
                .sessionTimeoutMs(40000)
                .retryPolicy(policy)
                .namespace(ROOT_PATH)
                .build();
        this.client.start();
        this.loadBalance = loadBalance;
        this.serviceAddressCache = ZKCache.getInstance();
        logger.info("Zookeeper 连接成功");
    }

    @Override
    public InetSocketAddress serviceDiscovery(String serviceName) {
        return serviceDiscovery(serviceName, null);
    }
    
    @Override
    public InetSocketAddress serviceDiscovery(String serviceName, String featureCode) {
        try{
            // 检查服务是否存在
            if(client.checkExists().forPath("/"+serviceName) == null) {
                logger.warn("服务 {} 未在 Zookeeper 中注册", serviceName);
                return null;
            }
            
            // 获取服务地址列表，优先使用缓存
            List<String> addressList = serviceAddressCache.getServices(serviceName);
            if (addressList.isEmpty()) {
                // 从ZK获取指定服务名称路径下的所有子节点
                addressList = client.getChildren().forPath("/"+serviceName);
                // 更新缓存
                for (String address : addressList) {
                    serviceAddressCache.addService(serviceName, address);
                }
                // 添加监听器，当节点变化时更新缓存
                registerWatcher(serviceName);
            }
            
            logger.info("发现服务 {} 的节点: {}", serviceName, addressList);
            
            if(addressList.isEmpty()) {
                logger.warn("服务 {} 没有可用的服务节点", serviceName);
                return null;
            }
            
            // 使用负载均衡策略选择服务节点
            InetSocketAddress socketAddress;

            // 转换为 InetSocketAddress 列表
            List<InetSocketAddress> inetSocketAddresList = new ArrayList<>();
            for (String addr : addressList) {
                inetSocketAddresList.add(AddressUtil.fromString(addr));
            }

            // 负载均衡算法自动选择合适的节点
            if (featureCode != null && !featureCode.isEmpty()) {
                socketAddress = loadBalance.select(serviceName, inetSocketAddresList, featureCode);
            } else {
                String randomFeatureCode = serviceName + "#" + ThreadLocalRandom.current().nextInt(10000);
                socketAddress = loadBalance.select(serviceName, inetSocketAddresList, randomFeatureCode);
            }
            
            logger.info("选择服务节点: {}", socketAddress);
            
            return socketAddress;
        } catch (Exception e) {
            logger.error("服务发现失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 注册监听器，当服务节点发生变化时更新缓存
     */
    private void registerWatcher(String serviceName) throws Exception {
        String servicePath = "/" + serviceName;
        CuratorCache cache = CuratorCache.build(client, servicePath);
        
        // 添加监听器
        CuratorCacheListener listener = CuratorCacheListener.builder()
            .forCreates(node -> updateCache(serviceName))
            .forDeletes(node -> updateCache(serviceName))
            .forChanges((oldNode, newNode) -> updateCache(serviceName))
            .build();
            
        cache.listenable().addListener(listener);
        cache.start();
    }
    
    private void updateCache(String serviceName) {
        try {
            String servicePath = "/" + serviceName;
            List<String> newAddressList = client.getChildren().forPath(servicePath);
            
            // 清空旧缓存
            serviceAddressCache.clear();
            // 更新新缓存
            for (String address : newAddressList) {
                serviceAddressCache.addService(serviceName, address);
            }

            // 转换为 InetSocketAddress 列表
            List<InetSocketAddress> inetSocketNewAddressList = new ArrayList<>();
            for (String addr : newAddressList) {
                inetSocketNewAddressList.add(AddressUtil.fromString(addr));
            }

            // 如果使用的是ConsistencyHashBalance，则更新哈希环
            if (loadBalance instanceof ConsistencyHashBalance) {
                ((ConsistencyHashBalance) loadBalance).updateServiceAddresses(
                        serviceName, inetSocketNewAddressList);
            }
            
            logger.info("服务 {} 的节点已更新: {}", serviceName, newAddressList);
        } catch (Exception e) {
            logger.error("更新缓存失败: {}", e.getMessage(), e);
        }
    }

	@Override
	public boolean checkRetry(String serviceName) {
		boolean canRetry = false;
        try{
            List<String> serviceList = client.getChildren().forPath("/"+RETRY_GROUP);
            for(String s:serviceList){
                if(s.equals(serviceName)){
                    logger.info("服务{}在重试白名单上，允许重试", serviceName);
                    canRetry = true;
                }
            }
        }catch(Exception e){
            logger.error("检查服务重试白名单失败: {}", e.getMessage(), e);
        }
        return canRetry; // 默认返回false
	}

}
