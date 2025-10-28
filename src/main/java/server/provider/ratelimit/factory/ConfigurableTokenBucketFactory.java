package server.provider.ratelimit.factory;
import server.provider.ratelimit.RateLimit;
import server.provider.ratelimit.impl.ConfigurableTokenBucketRateLimit;

public class ConfigurableTokenBucketFactory implements RateLimitFactory {
    @Override
    public String getName() {
        return "configurable_token_bucket";
    }

    @Override
    public RateLimit create(int rateMs, int capacity) {
        // 内部实现会从 AppConfig 读取参数，这里的入参忽略
        // 但为兼容性，若用户未提供配置，依然可使用默认值
        return new ConfigurableTokenBucketRateLimit();
    }
}


