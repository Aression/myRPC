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
} 