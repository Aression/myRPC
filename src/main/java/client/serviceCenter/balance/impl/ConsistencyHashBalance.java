package client.serviceCenter.balance.impl;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.*;

import client.serviceCenter.balance.LoadBalance;
import common.util.AddressUtil;
import common.util.HashUtil;

/**
 * 基于一致性哈希算法的负载均衡实现
 */
public class ConsistencyHashBalance implements LoadBalance {
    private static final Logger logger = LoggerFactory.getLogger(ConsistencyHashBalance.class);
    // 虚拟节点数量
    private static final int VIRTUAL_NODE_NUM = 500;
    // 虚拟节点后缀
    private static final String VIRTUAL_NODE_SUFFIX = "#";
    // 缓存不同服务的哈希环，key是服务名称
    private final Map<String, SortedMap<Integer, InetSocketAddress>> serviceHashRingMap = new ConcurrentHashMap<>();
    
    @Override
    public InetSocketAddress select(String serviceName, List<InetSocketAddress> addressList, String featureCode) {
        // 获取或创建该服务的哈希环
        SortedMap<Integer, InetSocketAddress> ring = serviceHashRingMap.computeIfAbsent(serviceName, k -> {
            SortedMap<Integer, InetSocketAddress> newRing = new TreeMap<>();
            // 为每个实际节点创建虚拟节点
            for (InetSocketAddress address : addressList) {
                addVirtualNodes(newRing, address);
            }
            return newRing;
        });
        
        // 如果哈希环为空，则直接返回null
        if (ring.isEmpty()) {
            return null;
        }
        
        // 使用特征码计算哈希值，调用HashUtil的方法
        int hash = Math.abs(HashUtil.murmurHash(featureCode));
        
        // 获取哈希值大于等于当前哈希值的子映射
        SortedMap<Integer, InetSocketAddress> subMap = ring.tailMap(hash);
        
        // 如果子映射为空，则取哈希环的第一个节点
        Integer targetKey = subMap.isEmpty() ? ring.firstKey() : subMap.firstKey();
        
        // 获取目标节点地址
        InetSocketAddress targetAddr = ring.get(targetKey);
        
        // 负载均衡选择日志
        logger.info("哈希选择：服务[" + serviceName + "]，特征码[" + featureCode + "]，哈希值[" + hash + "]，选择节点[" + AddressUtil.toString(targetAddr) + "]");

        return targetAddr;
    }
    
    /**
     * 添加虚拟节点到哈希环
     */
    private void addVirtualNodes(SortedMap<Integer, InetSocketAddress> ring, InetSocketAddress realNode) {
        for (int i = 0; i < VIRTUAL_NODE_NUM; i++) {
            // 为每个真实节点创建虚拟节点
            String virtualNodeName = realNode.toString() + VIRTUAL_NODE_SUFFIX + i;
            int hash = Math.abs(HashUtil.murmurHash(virtualNodeName));
            ring.put(hash, realNode);
        }
    }
    
    /**
     * 更新服务的地址列表（当服务节点变化时调用）
     */
    public void updateServiceAddresses(String serviceName, List<InetSocketAddress> addressList) {
        SortedMap<Integer, InetSocketAddress> ring = new TreeMap<>();
        for (InetSocketAddress address : addressList) {
            addVirtualNodes(ring, address);
        }
        serviceHashRingMap.put(serviceName, ring);
    }

    @Override
    public BalanceType getType() {
        return BalanceType.CONSISTENCY_HASH;
    }
}
