package client.serviceCenter.balance.impl;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import client.serviceCenter.balance.LoadBalance;

/**
 * 随机负载均衡实现
 */
public class RandomLoadBalance implements LoadBalance {
    
    @Override
    public InetSocketAddress select(String serviceName, List<InetSocketAddress> addressList, long featureCode) {
        if (addressList == null || addressList.isEmpty()) {
            return null;
        }
        
        // 随机选择一个地址
        int index = ThreadLocalRandom.current().nextInt(addressList.size());
        return addressList.get(index);
    }

    @Override
    public BalanceType getType() {
        return BalanceType.RANDOM;
    }
} 