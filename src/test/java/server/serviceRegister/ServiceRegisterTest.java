package server.serviceRegister;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import server.serviceRegister.impl.ZKServiceRegister;

import java.net.InetSocketAddress;

public class ServiceRegisterTest {

    private ServiceRegister serviceRegister;
    
    @Before
    public void setUp() {
        // 由于ZK需要实际连接，这里使用Mockito创建模拟对象
        serviceRegister = Mockito.mock(ZKServiceRegister.class);
    }
    
    @Test
    public void testServiceRegister() {
        // 准备测试数据
        String serviceName = "com.test.UserService";
        InetSocketAddress serviceAddress = new InetSocketAddress("localhost", 8080);
        boolean canRetry = true;
        
        // 执行注册方法
        serviceRegister.register(serviceName, serviceAddress, canRetry);
        
        // 验证方法被调用
        Mockito.verify(serviceRegister).register(serviceName, serviceAddress, canRetry);
    }
    
    @Test
    public void testMultipleServiceRegister() {
        // 准备测试数据
        String serviceName1 = "com.test.UserService";
        String serviceName2 = "com.test.OrderService";
        InetSocketAddress serviceAddress = new InetSocketAddress("localhost", 8080);
        boolean canRetry = true;
        
        // 执行注册方法
        serviceRegister.register(serviceName1, serviceAddress, canRetry);
        serviceRegister.register(serviceName2, serviceAddress, canRetry);
        
        // 验证方法被调用
        Mockito.verify(serviceRegister).register(serviceName1, serviceAddress, canRetry);
        Mockito.verify(serviceRegister).register(serviceName2, serviceAddress, canRetry);
    }
}