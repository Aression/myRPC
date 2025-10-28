package client.serviceCenter.balance;

import client.serviceCenter.balance.LoadBalance.BalanceType;
import client.serviceCenter.balance.impl.ConsistencyHashBalance;
import client.serviceCenter.balance.impl.RandomLoadBalance;
import client.serviceCenter.balance.impl.SequenceLoadBalance;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import common.util.HashUtil;

public class LoadBalanceTest {

    private List<InetSocketAddress> addressList;
    private String serviceName;
    private static final int ADDRESS_COUNT = 10;
    private static final int TEST_TIMES = 1000;
    
    @Before
    public void setUp() {
        // 初始化测试数据
        serviceName = "com.test.UserService";
        addressList = new ArrayList<>();
        
        // 创建10个测试地址
        for (int i = 1; i <= ADDRESS_COUNT; i++) {
            addressList.add(new InetSocketAddress("192.168.1." + i, 8000 + i));
        }
    }
    
    @Test
    public void testLoadBalanceFactory() {
        // 测试工厂类能否正确创建各种负载均衡器
        LoadBalance randomBalance = LoadBalanceFactory.getLoadBalance(BalanceType.RANDOM);
        Assert.assertNotNull("随机负载均衡器不应为空", randomBalance);
        Assert.assertEquals("负载均衡器类型应为RANDOM", BalanceType.RANDOM, randomBalance.getType());
        
        LoadBalance sequenceBalance = LoadBalanceFactory.getLoadBalance(BalanceType.SEQUENCE);
        Assert.assertNotNull("顺序负载均衡器不应为空", sequenceBalance);
        Assert.assertEquals("负载均衡器类型应为SEQUENCE", BalanceType.SEQUENCE, sequenceBalance.getType());
        
        LoadBalance consistencyHashBalance = LoadBalanceFactory.getLoadBalance(BalanceType.CONSISTENCY_HASH);
        Assert.assertNotNull("一致性哈希负载均衡器不应为空", consistencyHashBalance);
        Assert.assertEquals("负载均衡器类型应为CONSISTENCY_HASH", BalanceType.CONSISTENCY_HASH, consistencyHashBalance.getType());
    }
    
    @Test
    public void testRandomLoadBalance() {
        LoadBalance loadBalance = new RandomLoadBalance();
        Map<InetSocketAddress, Integer> countMap = new HashMap<>();
        
        // 进行多次选择，统计每个地址被选中的次数
        for (int i = 0; i < TEST_TIMES; i++) {
            InetSocketAddress selected = loadBalance.select(serviceName, addressList, 0);
            Assert.assertNotNull("选中的地址不应为空", selected);
            countMap.put(selected, countMap.getOrDefault(selected, 0) + 1);
        }
        
        // 验证每个地址都有被选中的机会
        Assert.assertEquals("所有地址都应该有被选中的机会", ADDRESS_COUNT, countMap.size());
        
        // 打印每个地址被选中的次数，用于观察分布是否均匀
        for (Map.Entry<InetSocketAddress, Integer> entry : countMap.entrySet()) {
            System.out.println("地址: " + entry.getKey() + ", 被选中次数: " + entry.getValue());
        }
    }
    
    @Test
    public void testSequenceLoadBalance() {
        LoadBalance loadBalance = new SequenceLoadBalance();
        List<InetSocketAddress> selectedList = new ArrayList<>();
        
        // 连续选择ADDRESS_COUNT*2次，应该是两轮循环
        for (int i = 0; i < ADDRESS_COUNT * 2; i++) {
            InetSocketAddress selected = loadBalance.select(serviceName, addressList, 0);
            Assert.assertNotNull("选中的地址不应为空", selected);
            selectedList.add(selected);
        }
        
        // 验证第一轮和第二轮选择的地址是否一致
        for (int i = 0; i < ADDRESS_COUNT; i++) {
            Assert.assertEquals("第一轮和第二轮选择的地址应该一致", 
                    selectedList.get(i), selectedList.get(i + ADDRESS_COUNT));
        }
    }
    
    @Test
    public void testConsistencyHashBalance() {
        LoadBalance loadBalance = new ConsistencyHashBalance();
        Map<InetSocketAddress, Integer> countMap = new HashMap<>();
        
        // 使用不同的特征码进行选择（传入已哈希的特征码，避免重复哈希）
        for (long i = 1; i <= TEST_TIMES; i++) {
            long featureCode = HashUtil.murmurHash(String.valueOf(i));
            InetSocketAddress selected = loadBalance.select(serviceName, addressList, featureCode);
            Assert.assertNotNull("选中的地址不应为空", selected);
            countMap.put(selected, countMap.getOrDefault(selected, 0) + 1);
        }
        
        // 验证所有地址都被选中
        Assert.assertEquals("所有地址都应该被选中", ADDRESS_COUNT, countMap.size());
        
        // 验证相同的特征码是否总是选择相同的地址
        for (long i = 1; i <= 10; i++) {
            long featureCode = HashUtil.murmurHash(String.valueOf(i));
            InetSocketAddress firstSelected = loadBalance.select(serviceName, addressList, featureCode);
            InetSocketAddress secondSelected = loadBalance.select(serviceName, addressList, featureCode);
            Assert.assertEquals("相同特征码应该选择相同的地址", firstSelected, secondSelected);
        }
        
        // 打印每个地址被选中的次数，用于观察分布
        for (Map.Entry<InetSocketAddress, Integer> entry : countMap.entrySet()) {
            System.out.println("地址: " + entry.getKey() + ", 被选中次数: " + entry.getValue());
        }
    }

    @Test
    public void testGenerateFeatureCodeRoutingConsistency() {
        LoadBalance loadBalance = new ConsistencyHashBalance();
        String methodName = "getUserById";
        
        // 相同的请求（相同的参数值），应生成相同特征码并路由到同一节点
        Object[] params1 = new Object[]{1};
        long featureCode1 = HashUtil.generateFeatureCode(serviceName, methodName, params1);
        InetSocketAddress nodeA1 = loadBalance.select(serviceName, addressList, featureCode1);
        InetSocketAddress nodeA2 = loadBalance.select(serviceName, addressList, featureCode1);
        Assert.assertEquals("相同请求应路由到同一节点", nodeA1, nodeA2);
        
        // 即使是新的对象实例但参数值相同，也应生成相同特征码并路由一致
        Object[] params1b = new Object[]{1};
        long featureCode1b = HashUtil.generateFeatureCode(serviceName, methodName, params1b);
        Assert.assertEquals("相同参数值应生成相同特征码", featureCode1, featureCode1b);
        InetSocketAddress nodeA3 = loadBalance.select(serviceName, addressList, featureCode1b);
        Assert.assertEquals("相同参数值的请求应路由到同一节点", nodeA1, nodeA3);
        
        // 另一个不同的请求，验证其自身的一致性（不强制不同节点）
        Object[] params2 = new Object[]{2};
        long featureCode2 = HashUtil.generateFeatureCode(serviceName, methodName, params2);
        InetSocketAddress nodeB1 = loadBalance.select(serviceName, addressList, featureCode2);
        InetSocketAddress nodeB2 = loadBalance.select(serviceName, addressList, featureCode2);
        Assert.assertEquals("相同请求应路由到同一节点", nodeB1, nodeB2);
    }
}