package client.serviceCenter.balance;

import client.serviceCenter.balance.LoadBalance.BalanceType;
import client.serviceCenter.balance.impl.ConsistencyHashBalance;
import client.serviceCenter.balance.impl.RandomLoadBalance;
import client.serviceCenter.balance.impl.SequenceLoadBalance;
import common.util.HashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LoadBalance 策略测试")
class LoadBalanceTest {

    private static final int ADDRESS_COUNT = 10;
    private static final int TEST_TIMES = 1000;
    private List<InetSocketAddress> addressList;
    private String serviceName;

    @BeforeEach
    void setUp() {
        serviceName = "com.test.UserService";
        addressList = new ArrayList<>();
        for (int i = 1; i <= ADDRESS_COUNT; i++) {
            addressList.add(new InetSocketAddress("192.168.1." + i, 8000 + i));
        }
    }

    @Nested
    @DisplayName("Factory 构造能力")
    class LoadBalanceFactoryTests {

        @Test
        @DisplayName("可按类型获取随机策略")
        void shouldCreateRandomLoadBalance() {
            LoadBalance loadBalance = LoadBalanceFactory.getLoadBalance(BalanceType.RANDOM);
            assertNotNull(loadBalance, "随机策略实例不应为空");
            assertEquals(BalanceType.RANDOM, loadBalance.getType(), "策略类型应匹配");
        }

        @Test
        @DisplayName("可按类型获取顺序策略")
        void shouldCreateSequenceLoadBalance() {
            LoadBalance loadBalance = LoadBalanceFactory.getLoadBalance(BalanceType.SEQUENCE);
            assertNotNull(loadBalance, "顺序策略实例不应为空");
            assertEquals(BalanceType.SEQUENCE, loadBalance.getType(), "策略类型应匹配");
        }

        @Test
        @DisplayName("可按类型获取一致性哈希策略")
        void shouldCreateConsistencyHashLoadBalance() {
            LoadBalance loadBalance = LoadBalanceFactory.getLoadBalance(BalanceType.CONSISTENCY_HASH);
            assertNotNull(loadBalance, "一致性哈希策略实例不应为空");
            assertEquals(BalanceType.CONSISTENCY_HASH, loadBalance.getType(), "策略类型应匹配");
        }
    }

    @Nested
    @DisplayName("随机策略")
    class RandomLoadBalanceTests {

        @Test
        @DisplayName("所有节点都应该被选到")
        void shouldCoverAllNodes() {
            LoadBalance loadBalance = new RandomLoadBalance();
            Map<InetSocketAddress, Integer> counter = new HashMap<>();
            for (int i = 0; i < TEST_TIMES; i++) {
                InetSocketAddress address = loadBalance.select(serviceName, addressList, 0);
                assertNotNull(address, "随机策略返回地址不应为空");
                counter.put(address, counter.getOrDefault(address, 0) + 1);
            }
            assertEquals(ADDRESS_COUNT, counter.size(), "所有节点都应该被选中");
        }
    }

    @Nested
    @DisplayName("顺序策略")
    class SequenceLoadBalanceTests {

        @Test
        @DisplayName("遍历序列应保持循环顺序")
        void shouldIterateInSequence() {
            LoadBalance loadBalance = new SequenceLoadBalance();
            List<InetSocketAddress> selected = new ArrayList<>();
            for (int i = 0; i < ADDRESS_COUNT * 2; i++) {
                InetSocketAddress address = loadBalance.select(serviceName, addressList, 0);
                assertNotNull(address, "顺序策略返回地址不应为空");
                selected.add(address);
            }
            for (int i = 0; i < ADDRESS_COUNT; i++) {
                assertEquals(selected.get(i), selected.get(i + ADDRESS_COUNT), "顺序策略应按固定顺序循环");
            }
        }
    }

    @Nested
    @DisplayName("一致性哈希策略")
    class ConsistencyHashBalanceTests {

        @Test
        @DisplayName("所有节点都应参与分发")
        void shouldCoverAllNodes() {
            LoadBalance loadBalance = new ConsistencyHashBalance();
            Map<InetSocketAddress, Integer> counter = new HashMap<>();
            for (long i = 1; i <= TEST_TIMES; i++) {
                InetSocketAddress address = selectWithFeature(loadBalance, i);
                assertNotNull(address, "返回地址不应为空");
                counter.put(address, counter.getOrDefault(address, 0) + 1);
            }
            assertEquals(ADDRESS_COUNT, counter.size(), "所有节点都应被选中");
        }

        @Test
        @DisplayName("相同特征码应始终命中同一节点")
        void shouldRouteSameFeatureCodeToSameNode() {
            LoadBalance loadBalance = new ConsistencyHashBalance();
            for (long i = 1; i <= 10; i++) {
                InetSocketAddress first = selectWithFeature(loadBalance, i);
                InetSocketAddress second = selectWithFeature(loadBalance, i);
                assertEquals(first, second, "相同特征码应稳定命中同一节点");
            }
        }

        @Test
        @DisplayName("generateFeatureCode 应确保请求重试一致")
        void shouldProduceStableRoutingForIdenticalRequests() {
            LoadBalance loadBalance = new ConsistencyHashBalance();
            String methodName = "getUserById";
            Object[] params1 = new Object[]{1};
            long featureCode1 = HashUtil.generateFeatureCode(serviceName, methodName, params1);
            InetSocketAddress node1 = loadBalance.select(serviceName, addressList, featureCode1);
            InetSocketAddress node2 = loadBalance.select(serviceName, addressList, featureCode1);
            assertEquals(node1, node2, "相同参数的请求应命中同一节点");

            Object[] params1b = new Object[]{1};
            long featureCode1b = HashUtil.generateFeatureCode(serviceName, methodName, params1b);
            assertEquals(featureCode1, featureCode1b, "参数相同应产生相同特征码");
            InetSocketAddress node3 = loadBalance.select(serviceName, addressList, featureCode1b);
            assertEquals(node1, node3, "相同特征码的请求应命中同一节点");

            Object[] params2 = new Object[]{2};
            long featureCode2 = HashUtil.generateFeatureCode(serviceName, methodName, params2);
            InetSocketAddress nodeOther1 = loadBalance.select(serviceName, addressList, featureCode2);
            InetSocketAddress nodeOther2 = loadBalance.select(serviceName, addressList, featureCode2);
            assertEquals(nodeOther1, nodeOther2, "另一组相同参数也应命中同一节点");
        }

        private InetSocketAddress selectWithFeature(LoadBalance loadBalance, long seed) {
            long featureCode = HashUtil.murmurHash(String.valueOf(seed));
            return loadBalance.select(serviceName, addressList, featureCode);
        }
    }
}