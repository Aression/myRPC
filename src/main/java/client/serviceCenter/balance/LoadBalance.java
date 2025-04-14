package client.serviceCenter.balance;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * 负载均衡接口
 */
public interface LoadBalance {
    /**
     * 从服务列表中选择一个服务地址
     * @param serviceName 服务名称
     * @param addressList 可用的服务地址列表
     * @return 选中的服务地址
     */
    InetSocketAddress select(String serviceName, List<String> addressList);
    
    /**
     * 从服务列表中选择一个服务地址（带特征码版本）
     * @param serviceName 服务名称
     * @param addressList 可用的服务地址列表
     * @param featureCode 请求特征码，用于一致性哈希
     * @return 选中的服务地址
     */
    InetSocketAddress select(String serviceName, List<String> addressList, String featureCode);
} 