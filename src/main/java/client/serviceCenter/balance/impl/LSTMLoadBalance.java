package client.serviceCenter.balance.impl;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.*;
import client.serviceCenter.balance.LoadBalance;
import common.util.AddressUtil;

/**
 * 基于LSTM（长短期记忆网络）思想的负载均衡实现
 * 
 * 这个实现模拟了LSTM的记忆机制，通过记录服务调用历史和性能表现
 * 来动态调整负载均衡决策，适应服务负载变化。
 */
public class LSTMLoadBalance implements LoadBalance {
    private static final Logger logger = LoggerFactory.getLogger(LSTMLoadBalance.class);
    // 历史窗口大小
    private static final int HISTORY_WINDOW_SIZE = 100;
    // 基础学习率
    private static final double BASE_LEARNING_RATE = 0.05;
    // 最小学习率
    private static final double MIN_LEARNING_RATE = 0.01;
    // 最大学习率
    private static final double MAX_LEARNING_RATE = 0.1;
    // 服务调用历史记录，记录每个服务的调用情况
    private final Map<String, Map<InetSocketAddress, List<CallRecord>>> serviceCallHistory = new ConcurrentHashMap<>();
    // 服务权重，记录每个服务节点的权重
    private final Map<String, Map<InetSocketAddress, Double>> serviceWeights = new ConcurrentHashMap<>();
    
    /**
     * 调用记录类，记录每次调用的性能表现
     */
    private static class CallRecord {
        // 响应时间
        private final long responseTime;
        // 调用成功标志
        private final boolean success;
        // 调用时间戳
        private final long timestamp;
        
        public CallRecord(long responseTime, boolean success, long timestamp) {
            this.responseTime = responseTime;
            this.success = success;
            this.timestamp = timestamp;
        }
    }
    
    @Override
    public InetSocketAddress select(String serviceName, List<InetSocketAddress> addressList, long featureCode) {
        if (addressList == null || addressList.isEmpty()) {
            return null;
        }
        
        // 获取或初始化服务权重
        Map<InetSocketAddress, Double> weights = serviceWeights.computeIfAbsent(serviceName, k -> {
            Map<InetSocketAddress, Double> newWeights = new ConcurrentHashMap<>();
            // 初始化权重为均等
            for (InetSocketAddress address : addressList) {
                newWeights.put(address, 1.0);
            }
            return newWeights;
        });
        
        // 确保所有地址都在权重表中
        for (InetSocketAddress address : addressList) {
            weights.putIfAbsent(address, 1.0);
        }
        
        // 移除不在地址列表中的地址
        weights.keySet().retainAll(addressList);
        
        // 根据权重选择地址
        double totalWeight = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        double randomValue = Math.random() * totalWeight;
        double cumulativeWeight = 0.0;
        
        InetSocketAddress selectedAddress = addressList.get(0); // 默认选第一个
        
        for (InetSocketAddress address : addressList) {
            cumulativeWeight += weights.getOrDefault(address, 1.0);
            if (randomValue <= cumulativeWeight) {
                selectedAddress = address;
                break;
            }
        }
        
        // 负载均衡选择日志
        logger.info("LSTM选择：服务[" + serviceName + "]，特征码[" + featureCode + "]，选择节点[" + AddressUtil.toString(selectedAddress) + "]，权重[" + weights.get(selectedAddress) + "]");
        
        return selectedAddress;
    }
    
    /**
     * 反馈调用结果，用于调整权重
     * 
     * @param serviceName 服务名称
     * @param address 调用的地址
     * @param responseTime 响应时间（毫秒）
     * @param success 是否调用成功
     */
    public void feedback(String serviceName, InetSocketAddress address, long responseTime, boolean success) {
        // 记录调用历史
        Map<InetSocketAddress, List<CallRecord>> addressHistory = serviceCallHistory.computeIfAbsent(serviceName, k -> new ConcurrentHashMap<>());
        List<CallRecord> history = addressHistory.computeIfAbsent(address, k -> new ArrayList<>());
        
        // 添加新的调用记录
        history.add(new CallRecord(responseTime, success, System.currentTimeMillis()));
        
        // 限制历史记录大小
        if (history.size() > HISTORY_WINDOW_SIZE) {
            history.remove(0);
        }
        
        // 更新权重
        updateWeight(serviceName, address, responseTime, success);
    }
    
    /**
     * 更新服务节点权重
     */
    private void updateWeight(String serviceName, InetSocketAddress address, long responseTime, boolean success) {
        Map<InetSocketAddress, Double> weights = serviceWeights.computeIfAbsent(serviceName, k -> new ConcurrentHashMap<>());
        double currentWeight = weights.getOrDefault(address, 1.0);
        
        // 计算性能分数 (0-1之间，越大越好)
        double performanceScore;
        if (!success) {
            performanceScore = 0.0; // 失败请求给予最低分
        } else {
            // 将响应时间转换为性能分数，响应时间越短，分数越高
            // 假设最大可接受响应时间为1000ms
            double normalizedResponseTime = Math.min(responseTime, 1000) / 1000.0;
            performanceScore = 1.0 - normalizedResponseTime;
        }
        
        // 计算动态学习率(基于历史记录数量)
        int historySize = serviceCallHistory.getOrDefault(serviceName, new ConcurrentHashMap<>())
                .getOrDefault(address, new ArrayList<>()).size();
        double dynamicLearningRate = Math.min(MAX_LEARNING_RATE, 
                Math.max(MIN_LEARNING_RATE, BASE_LEARNING_RATE * (1 + Math.log(historySize + 1))));
                
        // 计算权重调整(使用Sigmoid函数平滑调整)
        double sigmoidScore = 1.0 / (1.0 + Math.exp(-(performanceScore - 0.5) * 10));
        double adjustment = dynamicLearningRate * (sigmoidScore - 0.5);
        
        // 应用权重调整(使用指数平滑)
        double newWeight = Math.max(0.1, currentWeight * (1 + adjustment));
        weights.put(address, newWeight);
        
        // 权重更新日志
        logger.info("LSTM权重更新：服务[" + serviceName + "]，节点[" + AddressUtil.toString(address) + 
                          "]，响应时间[" + responseTime + "ms]，成功[" + success + 
                          "]，旧权重[" + currentWeight + "]，新权重[" + newWeight + "]");
    }
    
    /**
     * 重置特定服务的所有历史和权重数据
     */
    public void resetService(String serviceName) {
        serviceCallHistory.remove(serviceName);
        serviceWeights.remove(serviceName);
    }

    @Override
    public BalanceType getType() {
        return BalanceType.LSTM;
    }
}