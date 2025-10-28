package server.provider.ratelimit.factory;

import server.provider.ratelimit.RateLimit;

/**
 * RateLimit 工厂接口，通过 SPI 提供不同限流实现的创建能力。
 * 通过 getName 提供稳定的实现标识，避免基于类名的脆弱匹配。
 */
public interface RateLimitFactory {
    /** 实现的稳定名称，例如：configurable_token_bucket / token_bucket / synchronized_token_bucket */
    String getName();

    /**
     * 创建一个 RateLimit 实例。
     * 对于可配置实现，可忽略入参并在内部读取配置；
     * 对于其他实现，应使用传入参数构造。
     */
    RateLimit create(int rateMs, int capacity);
}


