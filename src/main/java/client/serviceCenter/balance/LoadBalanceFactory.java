package client.serviceCenter.balance;

import java.util.ServiceLoader;

import client.serviceCenter.balance.LoadBalance.BalanceType;
import client.serviceCenter.balance.impl.RandomLoadBalance;
import client.serviceCenter.balance.impl.ConsistencyHashBalance;
import common.util.AppConfig;

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

    /**
     * 从配置读取负载均衡类型，返回实现。默认：一致性哈希
     */
    public static LoadBalance getFromConfigOrDefault() {
        String name = AppConfig.getString("rpc.loadbalance.type", "consistency_hash");
        BalanceType type = parseBalanceType(name);
        LoadBalance lb = getLoadBalance(type);
        if (lb == null || lb instanceof RandomLoadBalance) {
            // 如果未加载到或落到随机，强制回退一致性哈希
            return new ConsistencyHashBalance();
        }
        return lb;
    }

    private static BalanceType parseBalanceType(String name) {
        if (name == null) return BalanceType.CONSISTENCY_HASH;
        switch (name.trim().toUpperCase()) {
            case "SEQUENCE": return BalanceType.SEQUENCE;
            case "RANDOM": return BalanceType.RANDOM;
            case "CONSISTENCY_HASH": return BalanceType.CONSISTENCY_HASH;
            case "LSTM": return BalanceType.LSTM;
            default: return BalanceType.CONSISTENCY_HASH;
        }
    }
}