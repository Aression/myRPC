package client.serviceCenter.balance;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于一致性哈希算法的负载均衡实现
 */
public class ConsistencyHashBalance implements LoadBalance {
    // 虚拟节点数量，增加虚拟节点可以使哈希更均匀
    private static final int VIRTUAL_NODE_NUM = 160;
    // 虚拟节点后缀
    private static final String VIRTUAL_NODE_SUFFIX = "#";
    // 哈希环，key是哈希值，value是节点地址
    private final SortedMap<Integer, String> hashRing = new TreeMap<>();
    // 缓存不同服务的哈希环，key是服务名称
    private final Map<String, SortedMap<Integer, String>> serviceHashRingMap = new ConcurrentHashMap<>();
    
    @Override
    public InetSocketAddress select(String serviceName, List<String> addressList) {
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
        
        // 使用线程ID和时间作为哈希因子
        long threadId = Thread.currentThread().getId();
        long currentTime = System.nanoTime();
        String hashKey = serviceName + "#" + threadId + "#" + currentTime % addressList.size();
        
        // 计算哈希值
        int hash = getHash(hashKey);
        
        // 获取哈希值大于等于当前哈希值的子映射
        SortedMap<Integer, String> subMap = ring.tailMap(hash);
        
        // 如果子映射为空，则取哈希环的第一个节点
        Integer targetKey = subMap.isEmpty() ? ring.firstKey() : subMap.firstKey();
        
        // 获取目标节点地址
        String targetAddr = ring.get(targetKey);
        
        // 输出更详细的负载均衡选择日志
        System.out.println("哈希选择：键[" + hashKey + "]，哈希值[" + hash + "]，选择节点[" + targetAddr + "]");
        
        // 解析地址为InetSocketAddress并返回
        return parseAddress(targetAddr);
    }
    
    /**
     * 添加虚拟节点到哈希环
     */
    private void addVirtualNodes(SortedMap<Integer, String> ring, String realNode) {
        for (int i = 0; i < VIRTUAL_NODE_NUM; i++) {
            String virtualNodeName = realNode + VIRTUAL_NODE_SUFFIX + i;
            int hash = getHash(virtualNodeName);
            ring.put(hash, realNode);
        }
    }
    
    /**
     * 使用FNV1_32_HASH算法计算哈希值
     */
    private int getHash(String key) {
        final int p = 16777619;
        int hash = (int) 2166136261L;
        for (int i = 0; i < key.length(); i++) {
            hash = (hash ^ key.charAt(i)) * p;
        }
        hash += hash << 13;
        hash ^= hash >> 7;
        hash += hash << 3;
        hash ^= hash >> 17;
        hash += hash << 5;
        
        // 取绝对值，避免负数
        return Math.abs(hash);
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
