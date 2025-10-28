package client.proxy.breaker;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BreakerTest {

    private Breaker breaker;
    private static final int FAILURE_THRESHOLD = 3;
    private static final double HALF_TO_OPEN_SUCCESS_RATE = 0.5;
    private static final long RETRY_TIME_PERIOD = 1000; // 1秒
    
    @Before
    public void setUp() {
        // 创建熔断器实例，设置失败阈值为3，半开到关闭的成功率为50%，重试时间为1秒
        breaker = new Breaker(FAILURE_THRESHOLD, HALF_TO_OPEN_SUCCESS_RATE, RETRY_TIME_PERIOD);
    }
    
    @Test
    public void testInitialState() {
        // 测试初始状态应为关闭状态，允许请求通过
        Assert.assertTrue("初始状态应允许请求通过", breaker.allowRequest());
    }
    
    @Test
    public void testOpenStateAfterFailures() {
        // 测试达到失败阈值后熔断器应打开
        for (int i = 0; i < FAILURE_THRESHOLD; i++) {
            breaker.recordFailure();
        }
        
        // 熔断器应该打开，不允许请求通过
        Assert.assertFalse("达到失败阈值后应不允许请求通过", breaker.allowRequest());
    }
    
    @Test
    public void testHalfOpenStateAfterTimeout() throws InterruptedException {
        // 先使熔断器打开
        for (int i = 0; i < FAILURE_THRESHOLD; i++) {
            breaker.recordFailure();
        }
        
        // 确认熔断器已打开
        Assert.assertFalse("熔断器应处于打开状态", breaker.allowRequest());
        
        // 等待超过重试时间
        Thread.sleep(RETRY_TIME_PERIOD + 100);
        
        // 熔断器应进入半开状态，允许请求通过
        Assert.assertTrue("超过重试时间后应允许请求通过", breaker.allowRequest());
    }
    
    @Test
    public void testTransitionFromHalfOpenToClosed() throws InterruptedException {
        // 先使熔断器打开
        for (int i = 0; i < FAILURE_THRESHOLD; i++) {
            breaker.recordFailure();
        }
        
        // 等待超过重试时间，进入半开状态
        Thread.sleep(RETRY_TIME_PERIOD + 100);
        Assert.assertTrue("应进入半开状态", breaker.allowRequest());
        
        // 模拟成功请求
        int requestCount = 4;
        for (int i = 0; i < requestCount - 1; i++) {
            breaker.allowRequest(); // 增加请求计数
            breaker.recordSuccess();
        }
        
        // 再次请求，应该已经关闭
        Assert.assertTrue("熔断器应已关闭", breaker.allowRequest());
    }
    
    @Test
    public void testTransitionFromHalfOpenToOpen() throws InterruptedException {
        // 先使熔断器打开
        for (int i = 0; i < FAILURE_THRESHOLD; i++) {
            breaker.recordFailure();
        }
        
        // 等待超过重试时间，进入半开状态
        Thread.sleep(RETRY_TIME_PERIOD + 100);
        Assert.assertTrue("应进入半开状态", breaker.allowRequest());
        
        // 记录一次失败，应立即回到打开状态
        breaker.recordFailure();
        
        // 熔断器应该再次打开
        Assert.assertFalse("失败后应再次打开", breaker.allowRequest());
    }
    
    @Test
    public void testBreakerProvider() {
        // 测试BreakerProvider
        BreakerProvider provider = new BreakerProvider();
        
        // 获取同一服务的熔断器应该是同一个实例
        String serviceName = "testService";
        Breaker breaker1 = provider.getBreaker(serviceName);
        Breaker breaker2 = provider.getBreaker(serviceName);
        Assert.assertSame("同一服务名应返回同一熔断器实例", breaker1, breaker2);
        
        // 获取不同服务的熔断器应该是不同实例
        Breaker anotherBreaker = provider.getBreaker("anotherService");
        Assert.assertNotSame("不同服务名应返回不同熔断器实例", breaker1, anotherBreaker);
    }
}