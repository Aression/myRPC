package client.serviceCenter.balance.impl;

import client.serviceCenter.balance.LoadBalance;
import common.util.AddressUtil;
import common.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ConsistencyHashBalance implements LoadBalance {
    private static final Logger logger = LoggerFactory.getLogger(ConsistencyHashBalance.class);

    // 每个真实节点对应的虚拟节点数量
    private static final int VIRTUAL_NODE_NUM = 200;
    // 虚拟节点后缀分隔符
    private static final String VIRTUAL_NODE_SUFFIX = "#";

    // 缓存不同服务的哈希环，key是服务名称
    private final Map<String, HashRing> serviceHashRingMap = new ConcurrentHashMap<>();
    
    // 引入读写锁，用于保护哈希环的重建过程
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();


    @Override
    public InetSocketAddress select(String serviceName, List<InetSocketAddress> addressList, long featureCode) {
        if (addressList == null || addressList.isEmpty()) {
            return null;
        }
        if (addressList.size() == 1) {
            return addressList.get(0);
        }

        // 获取该服务的哈希环
        HashRing hashRing = getOrCreateHashRing(serviceName, addressList);

        if (hashRing == null || hashRing.isEmpty()) {
            return null;
        }
        
        // 直接使用已生成的特征码作为哈希值，避免重复哈希
        long hash = featureCode;

        InetSocketAddress targetAddr = hashRing.getNode(hash);

        return targetAddr;
    }

    /**
     * 获取或创建/更新服务的哈希环。
     * 使用读写锁来保证并发安全和性能。
     */
    private HashRing getOrCreateHashRing(String serviceName, List<InetSocketAddress> currentAddressList) {
        readLock.lock();
        try {
            HashRing hashRing = serviceHashRingMap.get(serviceName);
            // 双重检查锁定模式（DCL）
            // 第一次检查：如果哈希环存在且地址列表未变化，则直接返回
            if (hashRing != null && !hashRing.hasAddressListChanged(currentAddressList)) {
                return hashRing;
            }
        } finally {
            readLock.unlock();
        }

        writeLock.lock();
        try {
            // 第二次检查：获取写锁后再次检查，防止其他线程已经创建
            HashRing hashRing = serviceHashRingMap.get(serviceName);
            if (hashRing != null && !hashRing.hasAddressListChanged(currentAddressList)) {
                return hashRing;
            }
            
            // 创建新的哈希环
            logger.info("服务[{}]地址列表发生变化或首次创建，开始重建哈希环...", serviceName);
            HashRing newHashRing = new HashRing(currentAddressList);
            serviceHashRingMap.put(serviceName, newHashRing);
            logger.info("服务[{}]哈希环重建完成，包含 {} 个真实节点和 {} 个虚拟节点。",
                    serviceName, currentAddressList.size(), newHashRing.getVirtualNodeCount());
            return newHashRing;
        } finally {
            writeLock.unlock();
        }
    }


    /**
     * 内部类，封装哈希环的结构和操作，使其更内聚。
     */
    private static class HashRing {
        private final SortedMap<Long, InetSocketAddress> ring = new TreeMap<>();
        private final Collection<InetSocketAddress> addressSet; // 使用Set进行高效比较

        public HashRing(List<InetSocketAddress> realNodes) {
            this.addressSet = new HashSet<>(realNodes);
            for (InetSocketAddress node : realNodes) {
                addVirtualNodes(node);
            }
        }

        /**
         * 检查地址列表是否发生变化。
         * 使用HashSet.equals()，高效且准确。
         */
        public boolean hasAddressListChanged(List<InetSocketAddress> currentAddressList) {
            if (this.addressSet.size() != currentAddressList.size()) {
                return true;
            }
            return !this.addressSet.equals(new HashSet<>(currentAddressList));
        }

        /**
         * 添加虚拟节点到哈希环。
         * 采用 "IP:Port#i" 的单一简洁格式生成虚拟节点key，清晰且高效。
         */
        private void addVirtualNodes(InetSocketAddress realNode) {
            String nodeStr = AddressUtil.toString(realNode);
            for (int i = 0; i < VIRTUAL_NODE_NUM; i++) {
                String virtualNodeKey = nodeStr + VIRTUAL_NODE_SUFFIX + i;
                long hash = HashUtil.murmurHash(virtualNodeKey);
                ring.put(hash, realNode);
            }
        }

        /**
         * 根据请求的哈希值获取对应的节点。
         */
        public InetSocketAddress getNode(long hash) {
            if (ring.isEmpty()) {
                return null;
            }
            SortedMap<Long, InetSocketAddress> tailMap = ring.tailMap(hash);
            Long key = tailMap.isEmpty() ? ring.firstKey() : tailMap.firstKey();
            return ring.get(key);
        }

        public boolean isEmpty() {
            return ring.isEmpty();
        }
        
        public int getVirtualNodeCount() {
            return ring.size();
        }
    }

    @Override
    public BalanceType getType() {
        return BalanceType.CONSISTENCY_HASH;
    }
}