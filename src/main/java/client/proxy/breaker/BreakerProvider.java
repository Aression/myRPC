package client.proxy.breaker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.*;

public class BreakerProvider {
    private static final Logger logger = LoggerFactory.getLogger(BreakerProvider.class);
    private Map<String, Breaker> breakerMap = new ConcurrentHashMap<>();
    
    public synchronized Breaker getBreaker(String serviceName){
        Breaker breaker;
        if(breakerMap.containsKey(serviceName)){
            breaker = breakerMap.get(serviceName);
        }else{
            logger.info("为服务"+serviceName+"创建一个新的熔断器");
            breaker = new Breaker(1, 0.5, 10000); // TODO：解耦熔断器参数，避免硬编码
            breakerMap.put(serviceName, breaker);
        }
        return breaker;
    }
}
