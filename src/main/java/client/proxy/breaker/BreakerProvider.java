package client.proxy.breaker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.net.InetSocketAddress;

import org.slf4j.*;
import common.util.AppConfig;

public class BreakerProvider {
    private static final Logger logger = LoggerFactory.getLogger(BreakerProvider.class);
    private Map<InetSocketAddress, Breaker> breakerMap = new ConcurrentHashMap<>();

    // 单例模式
    private static final BreakerProvider INSTANCE = new BreakerProvider();

    private BreakerProvider() {
    }

    public static BreakerProvider getInstance() {
        return INSTANCE;
    }

    // 从配置加载的全局默认参数（支持系统属性与配置文件）
    private volatile Integer cachedFailureThreshold;
    private volatile Double cachedHalf2OpenSuccessRate;
    private volatile Long cachedRetryTimePeriodMs;

    private void initIfNeeded() {
        if (cachedFailureThreshold != null)
            return;
        synchronized (this) {
            if (cachedFailureThreshold != null)
                return;
            int failureThreshold = AppConfig.getInt("rpc.breaker.failureThreshold", 1);
            // 解析 double：优先字符串，其次默认
            double half2OpenSuccessRate;
            try {
                String rateStr = AppConfig.getString("rpc.breaker.half2OpenSuccessRate", "0.5");
                half2OpenSuccessRate = Double.parseDouble(rateStr.trim());
            } catch (Exception e) {
                half2OpenSuccessRate = 0.5;
            }
            // 解析重试时间（毫秒）为 int，足够使用；内部以 long 保存
            int retryTimePeriodMsInt = AppConfig.getInt("rpc.breaker.retryTimePeriodMs", 10000);
            long retryTimePeriodMs = (long) retryTimePeriodMsInt;
            this.cachedFailureThreshold = failureThreshold;
            this.cachedHalf2OpenSuccessRate = half2OpenSuccessRate;
            this.cachedRetryTimePeriodMs = retryTimePeriodMs;
            logger.info("Breaker 默认参数: failureThreshold={}, half2OpenSuccessRate={}, retryTimePeriodMs={}",
                    failureThreshold, half2OpenSuccessRate, retryTimePeriodMs);
        }
    }

    public synchronized Breaker getBreaker(InetSocketAddress address) {
        initIfNeeded();
        Breaker breaker;
        if (breakerMap.containsKey(address)) {
            breaker = breakerMap.get(address);
        } else {
            logger.info("为节点 " + address + " 创建一个新的熔断器");
            breaker = new Breaker(cachedFailureThreshold, cachedHalf2OpenSuccessRate, cachedRetryTimePeriodMs);
            breakerMap.put(address, breaker);
        }
        return breaker;
    }
}
