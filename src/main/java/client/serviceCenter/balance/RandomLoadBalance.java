package client.serviceCenter.balance;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Random;

/**
 * 随机负载均衡策略
 */
public class RandomLoadBalance implements LoadBalance {
    private final Random random = new Random();
    
    @Override
    public InetSocketAddress select(String serviceName, List<String> addressList) {
        if (addressList == null || addressList.isEmpty()) {
            return null;
        }
        
        // 随机选择一个地址
        int index = random.nextInt(addressList.size());
        String address = addressList.get(index);
        
        // 解析地址为InetSocketAddress并返回
        return parseAddress(address);
    }
    
    /**
     * 解析地址字符串为InetSocketAddress
     */
    private InetSocketAddress parseAddress(String address) {
        if (address == null) {
            return null;
        }
        String[] parts = address.split(":");
        return new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));
    }

	@Override
	public InetSocketAddress select(String serviceName, List<String> addressList, String featureCode) {
        // 保持同样的实现
		if (addressList == null || addressList.isEmpty()) {
            return null;
        }
        
        // 随机选择一个地址
        int index = random.nextInt(addressList.size());
        String address = addressList.get(index);
        
        // 解析地址为InetSocketAddress并返回
        return parseAddress(address);
	}
} 