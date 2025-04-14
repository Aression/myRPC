package client.serviceCenter.balance;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 基于一致性哈希算法的负载均衡实现
 * 优化点：
 * 1. 直接使用特征码作为哈希键
 * 2. 增加虚拟节点数量提高分布均匀性
 * 3. 改进哈希算法减少冲突概率
 */
public class ConsistencyHashBalance implements LoadBalance {
    // 增加虚拟节点数量，提高分布均匀性
    private static final int VIRTUAL_NODE_NUM = 500;
    // 虚拟节点后缀
    private static final String VIRTUAL_NODE_SUFFIX = "#";
    // 缓存不同服务的哈希环，key是服务名称
    private final Map<String, SortedMap<Integer, String>> serviceHashRingMap = new ConcurrentHashMap<>();
    
    @Override
    public InetSocketAddress select(String serviceName, List<String> addressList) {
        // 原始方法，生成随机特征码作为兼容处理
        String randomFeatureCode = serviceName + "#" + ThreadLocalRandom.current().nextInt(10000);
        return select(serviceName, addressList, randomFeatureCode);
    }
    
    @Override
    public InetSocketAddress select(String serviceName, List<String> addressList, String featureCode) {
        // 获取或创建该服务的哈希环
        SortedMap<Integer, String> ring = serviceHashRingMap.computeIfAbsent(serviceName, k -> {
            SortedMap<Integer, String> newRing = new TreeMap<>();
            // 为每个实际节点创建虚拟节点
            for (String address : addressList) {
                addVirtualNodes(newRing, address);
            }
            return newRing;
        });
        
        // 如果哈希环为空，则直接返回null
        if (ring.isEmpty()) {
            return null;
        }
        
        // 使用特征码计算哈希值
        int hash = getHash(featureCode);
        
        // 获取哈希值大于等于当前哈希值的子映射
        SortedMap<Integer, String> subMap = ring.tailMap(hash);
        
        // 如果子映射为空，则取哈希环的第一个节点
        Integer targetKey = subMap.isEmpty() ? ring.firstKey() : subMap.firstKey();
        
        // 获取目标节点地址
        String targetAddr = ring.get(targetKey);
        
        // 输出更详细的负载均衡选择日志
        System.out.println("哈希选择：服务[" + serviceName + "]，特征码[" + featureCode + "]，哈希值[" + hash + "]，选择节点[" + targetAddr + "]");
        
        // 解析地址为InetSocketAddress并返回
        return parseAddress(targetAddr);
    }
    
    /**
     * 添加虚拟节点到哈希环
     */
    private void addVirtualNodes(SortedMap<Integer, String> ring, String realNode) {
        for (int i = 0; i < VIRTUAL_NODE_NUM; i++) {
            // 为每个节点创建多个虚拟节点，分布在哈希环上不同位置
            for (int j = 0; j < 4; j++) {
                String virtualNodeName = realNode + VIRTUAL_NODE_SUFFIX + i + "#" + j;
                int hash = getHash(virtualNodeName);
                ring.put(hash, realNode);
            }
        }
    }
    
    /**
     * 改进的哈希算法 - MurmurHash变种
     */
    private int getHash(String key) {
        byte[] bytes = key.getBytes();
        int h1 = 0x3c2569a5; // 质数种子
        
        // MurmurHash变种
        for (int i = 0; i < bytes.length; i++) {
            h1 ^= bytes[i];
            h1 *= 0x5bd1e995;
            h1 ^= h1 >>> 15;
        }
        
        h1 *= 0xc2b2ae35;
        h1 ^= h1 >>> 16;
        
        // 处理0和负数
        if (h1 == 0) {
            h1 = 1;
        }
        return Math.abs(h1);
    }
    
    /**
     * 解析地址字符串为InetSocketAddress
     */
    private InetSocketAddress parseAddress(String address) {
        if (address == null) {
            return null;
        }
        String[] parts = address.split(":");
        return new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));
    }
    
    /**
     * 更新服务的地址列表（当服务节点变化时调用）
     */
    public void updateServiceAddresses(String serviceName, List<String> addressList) {
        SortedMap<Integer, String> ring = new TreeMap<>();
        for (String address : addressList) {
            addVirtualNodes(ring, address);
        }
        serviceHashRingMap.put(serviceName, ring);
    }
}
