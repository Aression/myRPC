package client.serviceCenter.balance;

import java.util.ServiceLoader;

import client.serviceCenter.balance.LoadBalance.BalanceType;
import client.serviceCenter.balance.impl.RandomLoadBalance;

/**
 * 负载均衡工厂类
 */
public class LoadBalanceFactory {
    /**
     * 创建负载均衡策略
     * @param type 负载均衡类型
     * @return 负载均衡实现
     */
    public static LoadBalance getLoadBalance(BalanceType type) {
        // 使用SPI机制加载所有LoadBalance实现
        ServiceLoader<LoadBalance> serviceLoader = ServiceLoader.load(LoadBalance.class);
        
        // 遍历所有实现，找到匹配type的负载均衡器
        for (LoadBalance loadBalance : serviceLoader) {
            if(loadBalance.getType() == type) {
                return loadBalance;
            }
        }

        // 如果没有匹配的实现，返回默认的负载均衡器
        return new RandomLoadBalance();
    }
}