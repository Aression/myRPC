package client.serviceCenter.balance.impl;

import client.serviceCenter.balance.LoadBalance;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConsistencyHashTest {

    private LoadBalance loadBalance;
    private List<InetSocketAddress> addressList;
    private String serviceName;
    private static final int TEST_TIMES = 1000;
    
    @Before
    public void setUp() {
        // 初始化一致性哈希负载均衡器
        loadBalance = new ConsistencyHashBalance();
        serviceName = "com.test.UserService";
        
        // 创建测试地址列表
        addressList = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            addressList.add(new InetSocketAddress("192.168.1." + i, 8000 + i));
        }
    }
    
    @Test
    public void testConsistency() {
        // 测试相同特征码的一致性
        Map<Long, InetSocketAddress> featureToAddressMap = new HashMap<>();
        
        // 第一轮选择
        for (long i = 1; i <= TEST_TIMES; i++) {
            long featureCode = common.util.HashUtil.murmurHash(String.valueOf(i));
            InetSocketAddress address = loadBalance.select(serviceName, addressList, featureCode);
            featureToAddressMap.put(i, address);
        }
        
        // 第二轮选择，验证一致性
        for (long i = 1; i <= TEST_TIMES; i++) {
            long featureCode = common.util.HashUtil.murmurHash(String.valueOf(i));
            InetSocketAddress address = loadBalance.select(serviceName, addressList, featureCode);
            Assert.assertEquals("相同特征码应该选择相同的地址", featureToAddressMap.get(i), address);
        }
    }
    
    @Test
    public void testDistribution() {
        // 测试地址分布
        Map<InetSocketAddress, Integer> distributionMap = new HashMap<>();
        
        // 使用不同特征码进行选择
        for (long i = 1; i <= TEST_TIMES; i++) {
            long featureCode = common.util.HashUtil.murmurHash(String.valueOf(i));
            InetSocketAddress address = loadBalance.select(serviceName, addressList, featureCode);
            distributionMap.put(address, distributionMap.getOrDefault(address, 0) + 1);
        }
        
        // 验证所有地址都被选中
        Assert.assertEquals("所有地址都应该被选中", addressList.size(), distributionMap.size());
        
        // 打印分布情况
        System.out.println("一致性哈希算法地址分布情况：");
        for (Map.Entry<InetSocketAddress, Integer> entry : distributionMap.entrySet()) {
            System.out.println("地址: " + entry.getKey() + ", 被选中次数: " + entry.getValue() + 
                    ", 百分比: " + (entry.getValue() * 100.0 / TEST_TIMES) + "%");
        }
    }
    
    @Test
    public void testNodeAddAndRemove() {
        // 测试节点增加和删除时的一致性
        Map<Long, InetSocketAddress> beforeChangeMap = new HashMap<>();
        
        // 记录初始选择结果
        for (long i = 1; i <= TEST_TIMES; i++) {
            long featureCode = common.util.HashUtil.murmurHash(String.valueOf(i));
            InetSocketAddress address = loadBalance.select(serviceName, addressList, featureCode);
            beforeChangeMap.put(i, address);
        }
        
        // 添加一个新节点
        InetSocketAddress newAddress = new InetSocketAddress("192.168.1.100", 8100);
        List<InetSocketAddress> newAddressList = new ArrayList<>(addressList);
        newAddressList.add(newAddress);
        
        // 记录添加节点后的变化
        int changedAfterAdd = 0;
        for (long i = 1; i <= TEST_TIMES; i++) {
            long featureCode = common.util.HashUtil.murmurHash(String.valueOf(i));
            InetSocketAddress address = loadBalance.select(serviceName, newAddressList, featureCode);
            if (!address.equals(beforeChangeMap.get(i))) {
                changedAfterAdd++;
            }
        }
        
        // 验证变化比例不超过20%
        double changeRateAfterAdd = changedAfterAdd * 100.0 / TEST_TIMES;
        System.out.println("添加节点后变化比例: " + changeRateAfterAdd + "%");
        Assert.assertTrue("添加节点后变化比例应该小于20%", changeRateAfterAdd < 20);
        
        // 移除一个节点
        List<InetSocketAddress> reducedAddressList = new ArrayList<>(addressList);
        reducedAddressList.remove(0);
        
        // 记录移除节点后的变化
        int changedAfterRemove = 0;
        for (long i = 1; i <= TEST_TIMES; i++) {
            // 跳过原本映射到被移除节点的特征码
            if (beforeChangeMap.get(i).equals(addressList.get(0))) {
                continue;
            }
            long featureCode = common.util.HashUtil.murmurHash(String.valueOf(i));
            InetSocketAddress address = loadBalance.select(serviceName, reducedAddressList, featureCode);
            if (!address.equals(beforeChangeMap.get(i))) {
                changedAfterRemove++;
            }
        }
        
        // 计算变化比例（排除必然变化的部分）
        int totalExcludeRemoved = 0;
        for (long i = 1; i <= TEST_TIMES; i++) {
            if (!beforeChangeMap.get(i).equals(addressList.get(0))) {
                totalExcludeRemoved++;
            }
        }
        
        double changeRateAfterRemove = changedAfterRemove * 100.0 / totalExcludeRemoved;
        System.out.println("移除节点后变化比例: " + changeRateAfterRemove + "%");
        Assert.assertTrue("移除节点后变化比例应该小于20%", changeRateAfterRemove < 20);
    }
}