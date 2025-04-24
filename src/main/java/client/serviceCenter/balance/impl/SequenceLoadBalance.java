package client.serviceCenter.balance.impl;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import client.serviceCenter.balance.LoadBalance;

/**
 * 顺序负载均衡实现
 */
public class SequenceLoadBalance implements LoadBalance {
    private final AtomicInteger counter = new AtomicInteger(0);
    
    @Override
    public InetSocketAddress select(String serviceName, List<InetSocketAddress> addressList, String featureCode) {
        if (addressList == null || addressList.isEmpty()) {
            return null;
        }
        
        // 顺序选择一个地址
        int index = counter.getAndIncrement() % addressList.size();
        return addressList.get(index);
    }

    @Override
    public BalanceType getType() {
        return BalanceType.SEQUENCE;
    }
}
