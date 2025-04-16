package common.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 哈希工具类，用于生成一致性哈希特征码
 */
public class HashUtil {
    private static final Logger logger = LoggerFactory.getLogger(HashUtil.class);
    
    // 虚拟节点数量，更多的虚拟节点能够使得分布更均匀
    private static final int VIRTUAL_NODE_NUM = 500;
    
    /**
     * 使用MurmurHash算法计算字符串的哈希值
     * 
     * @param key 输入字符串
     * @return 哈希值
     */
    public static int murmurHash(String key) {
        // 这里使用简单的实现，生产中可使用guava的Hashing
        byte[] bytes = key.getBytes();
        int h = 0;
        for (byte b : bytes) {
            h = 31 * h + (b & 0xff);
        }
        return h;
    }
    
    /**
     * 使用MD5算法计算字符串的哈希值
     * 
     * @param key 输入字符串
     * @return 哈希值的十六进制字符串
     */
    public static String md5Hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(key.getBytes());
            byte[] digest = md.digest();
            
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("MD5哈希计算失败: {}", e.getMessage(), e);
            throw new RuntimeException("MD5哈希计算失败", e);
        }
    }
    
    /**
     * 计算特征码，用于确保相同请求被路由到相同服务节点
     * 
     * @param serviceName 服务名称
     * @param methodName 方法名称
     * @param params 参数列表
     * @return 特征码
     */
    public static String generateFeatureCode(String serviceName, String methodName, Object[] params) {
        StringBuilder sb = new StringBuilder();
        sb.append(serviceName).append("#").append(methodName);
        
        // 添加参数信息
        if (params != null && params.length > 0) {
            for (Object param : params) {
                if (param != null) {
                    sb.append("#").append(param.hashCode());
                } else {
                    sb.append("#null");
                }
            }
        }
        
        return md5Hash(sb.toString());
    }
    
    /**
     * 基于一致性哈希选择节点
     * 
     * @param key 用于选择节点的键
     * @param nodes 可用节点列表
     * @return 选中的节点
     */
    public static <T> T selectNode(String key, List<T> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        
        if (nodes.size() == 1) {
            return nodes.get(0);
        }
        
        SortedMap<Integer, T> hashRing = buildHashRing(nodes);
        int hash = Math.abs(murmurHash(key));
        
        if (hashRing.isEmpty()) {
            return null;
        }
        
        // 如果没有大于当前hash值的节点，则返回环首的节点
        if (!hashRing.tailMap(hash).isEmpty()) {
            return hashRing.tailMap(hash).get(hashRing.tailMap(hash).firstKey());
        } else {
            return hashRing.get(hashRing.firstKey());
        }
    }
    
    /**
     * 构建一致性哈希环
     * 
     * @param nodes 可用节点列表
     * @return 哈希环
     */
    private static <T> SortedMap<Integer, T> buildHashRing(List<T> nodes) {
        SortedMap<Integer, T> hashRing = new TreeMap<>();
        
        for (T node : nodes) {
            // 为每个节点生成多个虚拟节点
            for (int i = 0; i < VIRTUAL_NODE_NUM; i++) {
                String virtualNodeName = node.toString() + "#" + i;
                int hash = Math.abs(murmurHash(virtualNodeName));
                hashRing.put(hash, node);
            }
        }
        
        return hashRing;
    }
    
    /**
     * 对输入的字符串进行哈希，然后平均分配到指定数量的桶中
     * 
     * @param key 输入字符串
     * @param bucketCount 桶的数量
     * @return 桶的索引（0到bucketCount-1）
     */
    public static int getBucketIndex(String key, int bucketCount) {
        if (bucketCount <= 0) {
            throw new IllegalArgumentException("桶数必须大于0");
        }
        return Math.abs(murmurHash(key) % bucketCount);
    }
} 