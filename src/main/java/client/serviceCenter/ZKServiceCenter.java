package client.serviceCenter;

import client.serviceCenter.balance.ConsistencyHashBalance;
import client.serviceCenter.balance.LoadBalance;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZKServiceCenter implements ServiceCenter{
    private CuratorFramework client;// zookeeper 客户端
    private static final String ROOT_PATH="MY_RPC";// zookeeper根路径节点
    // 负载均衡策略
    private final LoadBalance loadBalance;
    // 服务地址缓存
    private final Map<String, List<String>> serviceAddressCache = new HashMap<>();
    
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
                .connectString("127.0.0.1:2181")
                .sessionTimeoutMs(40000)
                .retryPolicy(policy)
                .namespace(ROOT_PATH)
                .build();
        this.client.start();
        this.loadBalance = loadBalance;
        System.out.println("Zookeeper 连接成功");
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
                System.out.println("服务 " + serviceName + " 未在 Zookeeper 中注册");
                return null;
            }
            
            // 获取服务地址列表，优先使用缓存
            List<String> addressList;
            if (serviceAddressCache.containsKey(serviceName)) {
                addressList = serviceAddressCache.get(serviceName);
            } else {
                // 从ZK获取指定服务名称路径下的所有子节点
                addressList = client.getChildren().forPath("/"+serviceName);
                // 更新缓存
                serviceAddressCache.put(serviceName, addressList);
                // 添加监听器，当节点变化时更新缓存
                registerWatcher(serviceName);
            }
            
            System.out.println("发现服务 " + serviceName + " 的节点: " + addressList);
            
            if(addressList.isEmpty()) {
                System.out.println("服务 " + serviceName + " 没有可用的服务节点");
                return null;
            }
            
            // 使用负载均衡策略选择服务节点
            InetSocketAddress socketAddress;
            // 根据是否有特征码决定使用哪个select方法
            if (featureCode != null && !featureCode.isEmpty()) {
                socketAddress = loadBalance.select(serviceName, addressList, featureCode);
            } else {
                socketAddress = loadBalance.select(serviceName, addressList);
            }
            
            System.out.println("选择服务节点: " + socketAddress);
            
            return socketAddress;
        } catch (Exception e) {
            System.out.println("服务发现失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 注册监听器，当服务节点发生变化时更新缓存
     */
    private void registerWatcher(String serviceName) throws Exception {
        String servicePath = "/" + serviceName;
        PathChildrenCache pathChildrenCache = new PathChildrenCache(client, servicePath, true);
        pathChildrenCache.start();
        
        // 添加监听器
        pathChildrenCache.getListenable().addListener((client, event) -> {
            // 子节点数据变化时
            if (event.getType() == PathChildrenCacheEvent.Type.CHILD_ADDED || 
                event.getType() == PathChildrenCacheEvent.Type.CHILD_REMOVED ||
                event.getType() == PathChildrenCacheEvent.Type.CHILD_UPDATED) {
                
                // 重新获取子节点并更新缓存
                List<String> newAddressList = client.getChildren().forPath(servicePath);
                serviceAddressCache.put(serviceName, newAddressList);
                
                // 如果使用的是ConsistencyHashBalance，则更新哈希环
                if (loadBalance instanceof ConsistencyHashBalance) {
                    ((ConsistencyHashBalance) loadBalance).updateServiceAddresses(
                            serviceName, newAddressList);
                }
                
                System.out.println("服务 " + serviceName + " 的节点已更新: " + newAddressList);
            }
        });
    }

    private String getServiceAddress(InetSocketAddress serverAddress){
        return serverAddress.getHostName()+":"+serverAddress.getPort();
    }

    private InetSocketAddress parseAddress(String address){
        String[] result = address.split(":");
        return new InetSocketAddress(
                result[0],
                Integer.parseInt(result[1])
        );
    }
}
