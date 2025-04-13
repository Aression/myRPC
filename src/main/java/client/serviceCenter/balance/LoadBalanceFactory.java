package client.serviceCenter.balance;

/**
 * 负载均衡工厂类
 */
public class LoadBalanceFactory {
    
    /**
     * 负载均衡策略类型
     */
    public enum BalanceType {
        /**
         * 一致性哈希算法
         */
        CONSISTENCY_HASH,
        
        /**
         * 随机算法
         */
        RANDOM
    }
    
    /**
     * 创建负载均衡策略
     * @param type 负载均衡类型
     * @return 负载均衡实现
     */
    public static LoadBalance getLoadBalance(BalanceType type) {
        switch (type) {
            case CONSISTENCY_HASH:
                return new ConsistencyHashBalance();
            case RANDOM:
                return new RandomLoadBalance();
            default:
                // 默认使用一致性哈希
                return new ConsistencyHashBalance();
        }
    }
} 