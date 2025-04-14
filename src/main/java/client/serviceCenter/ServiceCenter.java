package client.serviceCenter;

import java.net.InetSocketAddress;

/**
 * 服务发现中心接口
 */
public interface ServiceCenter {
    /**
     * 服务发现
     * @param serviceName 服务名称
     * @return 服务地址
     */
    InetSocketAddress serviceDiscovery(String serviceName);
    
    /**
     * 服务发现（带特征码版本）
     * @param serviceName 服务名称
     * @param featureCode 请求特征码，用于一致性哈希
     * @return 服务地址
     */
    InetSocketAddress serviceDiscovery(String serviceName, String featureCode);
}
