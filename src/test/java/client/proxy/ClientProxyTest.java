package client.proxy;

import client.rpcClient.RpcClient;
import client.serviceCenter.balance.LoadBalance;
import common.message.RpcRequest;
import common.message.RpcResponse;
import common.pojo.User;
import common.result.Result;
import common.service.UserService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class ClientProxyTest {

    private RpcClient mockRpcClient;
    private ClientProxy clientProxy;
    private UserService userService;
    
    @Before
    public void setUp() {
        // 创建RPC客户端的模拟对象
        mockRpcClient = Mockito.mock(RpcClient.class);
        
        // 创建客户端代理
        clientProxy = new ClientProxy(mockRpcClient, null);
        
        // 创建代理服务
        userService = (UserService) Proxy.newProxyInstance(
                UserService.class.getClassLoader(),
                new Class<?>[]{UserService.class},
                clientProxy);
    }
    
    @Test
    public void testProxyCreation() {
        // 验证代理对象创建成功
        Assert.assertNotNull("代理对象不应为空", userService);
        Assert.assertTrue("应该是代理对象", Proxy.isProxyClass(userService.getClass()));
        Assert.assertEquals("代理处理器应该是ClientProxy", clientProxy, Proxy.getInvocationHandler(userService));
    }
    
    @Test
    public void testProxyMethodInvocation() throws Throwable {
        // 准备测试数据
        Integer userId = 1;
        User expectedUser = User.builder()
                .id(userId)
                .userName("测试用户")
                .age(25)
                .build();
        
        // 配置模拟对象行为
        RpcResponse mockResponse = RpcResponse.success(expectedUser);
        when(mockRpcClient.sendRequest(any(RpcRequest.class))).thenReturn(mockResponse);
        
        // 调用代理方法
        Result<User> result = userService.getUserById(userId);
        User user = result.getData();
        
        // 验证结果
        Assert.assertNotNull("返回结果不应为空", user);
        Assert.assertEquals("用户ID应匹配", userId, user.getId());
        Assert.assertEquals("用户名应匹配", expectedUser.getUserName(), user.getUserName());
        
        // 验证RPC客户端的sendRequest方法被调用
        Mockito.verify(mockRpcClient).sendRequest(any(RpcRequest.class));
    }
    
    @Test
    public void testLoadBalanceTypeConstructor() {
        // 测试使用负载均衡类型构造函数
        ClientProxy proxy = new ClientProxy(LoadBalance.BalanceType.RANDOM);
        Assert.assertNotNull("使用负载均衡类型创建的代理不应为空", proxy);
    }
    
    @Test
    public void testDefaultConstructor() {
        // 测试默认构造函数
        ClientProxy proxy = new ClientProxy();
        Assert.assertNotNull("使用默认构造函数创建的代理不应为空", proxy);
    }
}