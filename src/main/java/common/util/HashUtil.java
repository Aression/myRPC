package common.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 哈希工具类，用于生成一致性哈希特征码
 */
public class HashUtil {
    private static final Logger logger = LoggerFactory.getLogger(HashUtil.class);
    
    /**
     * 使用改进的MurmurHash算法计算字符串的哈希值
     * 
     * @param key 输入字符串
     * @return 哈希值
     */
    public static long murmurHash(String key) {
        byte[] data = key.getBytes();
        int length = data.length;
        int seed = 0x9747b28c; // 使用固定种子
        
        // m和r是MurmurHash算法的魔数
        final int m = 0x5bd1e995;
        final int r = 24;
        
        // 初始化哈希值
        int h = seed ^ length;
        
        // 处理所有完整的4字节块
        int len4 = length >> 2;
        
        for (int i = 0; i < len4; i++) {
            int i4 = i << 2;
            int k = data[i4] & 0xff;
            k |= (data[i4 + 1] & 0xff) << 8;
            k |= (data[i4 + 2] & 0xff) << 16;
            k |= (data[i4 + 3] & 0xff) << 24;
            
            k *= m;
            k ^= k >>> r;
            k *= m;
            
            h *= m;
            h ^= k;
        }
        
        // 处理剩余字节
        int offset = len4 << 2;
        switch (length & 3) {
            case 3:
                h ^= (data[offset + 2] & 0xff) << 16;
            case 2:
                h ^= (data[offset + 1] & 0xff) << 8;
            case 1:
                h ^= (data[offset] & 0xff);
                h *= m;
        }
        
        // 最终混合
        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;
        
        return h & 0x7fffffffffffffffL; // 确保返回正数
    }
    
    /**
     * 计算特征码，用于确保相同请求被路由到相同服务节点
     * 
     * @param serviceName 服务名称
     * @param methodName 方法名称
     * @param params 参数列表
     * @return 特征码
     */
    public static long generateFeatureCode(String serviceName, String methodName, Object[] params) {
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
        
        return Math.abs(HashUtil.murmurHash(sb.toString()));
    }
}