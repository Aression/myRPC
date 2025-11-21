package client.serviceCenter.balance.impl;

import client.serviceCenter.balance.LoadBalance;
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

@DisplayName("ConsistencyHashBalance 测试")
class ConsistencyHashTest {

    private static final int TEST_TIMES = 1000;
    private LoadBalance loadBalance;
    private List<InetSocketAddress> addressList;
    private String serviceName;

    @BeforeEach
    void setUp() {
        loadBalance = new ConsistencyHashBalance();
        serviceName = "com.test.UserService";
        addressList = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            addressList.add(new InetSocketAddress("192.168.1." + i, 8000 + i));
        }
    }

    @Nested
    @DisplayName("一致性与分布特性")
    class ConsistencyAndDistribution {

        @Test
        @DisplayName("相同特征码应永远路由到同一节点")
        void shouldKeepMappingStable() {
            Map<Long, InetSocketAddress> featureToAddressMap = new HashMap<>();
            for (long i = 1; i <= TEST_TIMES; i++) {
                featureToAddressMap.put(i, selectWithFeature(i));
            }
            for (long i = 1; i <= TEST_TIMES; i++) {
                assertEquals(featureToAddressMap.get(i), selectWithFeature(i), "相同特征码应该映射到相同节点");
            }
        }

        @Test
        @DisplayName("所有节点都应参与负载")
        void shouldDistributeTrafficAcrossNodes() {
            Map<InetSocketAddress, Integer> distributionMap = new HashMap<>();
            for (long i = 1; i <= TEST_TIMES; i++) {
                InetSocketAddress address = selectWithFeature(i);
                distributionMap.put(address, distributionMap.getOrDefault(address, 0) + 1);
            }
            assertEquals(addressList.size(), distributionMap.size(), "所有节点都应该被选中");
        }
    }

    @Nested
    @DisplayName("节点变更时的最小扰动")
    class NodeChangeImpacts {

        @Test
        @DisplayName("新增节点后，迁移比例应保持可控")
        void shouldLimitImpactWhenAddingNode() {
            Map<Long, InetSocketAddress> beforeChange = snapshotRouting(addressList);
            InetSocketAddress newAddress = new InetSocketAddress("192.168.1.100", 8100);
            List<InetSocketAddress> expandedList = new ArrayList<>(addressList);
            expandedList.add(newAddress);

            int changedCount = countChangedMappings(beforeChange, expandedList);
            double changeRate = changedCount * 100.0 / TEST_TIMES;
            System.out.println("添加节点后变化比例: " + changeRate + "%");
            assertTrue(changeRate < 20, "新增节点的迁移比例应低于20%");
        }

        @Test
        @DisplayName("移除节点后，非目标节点的映射应尽量稳定")
        void shouldLimitImpactWhenRemovingNode() {
            Map<Long, InetSocketAddress> beforeChange = snapshotRouting(addressList);
            List<InetSocketAddress> reducedList = new ArrayList<>(addressList);
            InetSocketAddress removed = reducedList.remove(0);

            int totalEligible = 0;
            int changedCount = 0;
            for (long i = 1; i <= TEST_TIMES; i++) {
                if (beforeChange.get(i).equals(removed)) {
                    continue;
                }
                totalEligible++;
                InetSocketAddress newAddress = selectWithFeature(i, reducedList);
                if (!newAddress.equals(beforeChange.get(i))) {
                    changedCount++;
                }
            }
            double changeRate = changedCount * 100.0 / totalEligible;
            System.out.println("移除节点后变化比例: " + changeRate + "%");
            assertTrue(changeRate < 20, "移除节点的迁移比例应低于20%");
        }

        private Map<Long, InetSocketAddress> snapshotRouting(List<InetSocketAddress> addresses) {
            Map<Long, InetSocketAddress> snapshot = new HashMap<>();
            for (long i = 1; i <= TEST_TIMES; i++) {
                snapshot.put(i, selectWithFeature(i, addresses));
            }
            return snapshot;
        }

        private int countChangedMappings(Map<Long, InetSocketAddress> baseline, List<InetSocketAddress> newList) {
            int changed = 0;
            for (long i = 1; i <= TEST_TIMES; i++) {
                InetSocketAddress next = selectWithFeature(i, newList);
                if (!next.equals(baseline.get(i))) {
                    changed++;
                }
            }
            return changed;
        }
    }

    private InetSocketAddress selectWithFeature(long seed) {
        return selectWithFeature(seed, addressList);
    }

    private InetSocketAddress selectWithFeature(long seed, List<InetSocketAddress> addresses) {
        long featureCode = HashUtil.murmurHash(String.valueOf(seed));
        return loadBalance.select(serviceName, addresses, featureCode);
    }
}