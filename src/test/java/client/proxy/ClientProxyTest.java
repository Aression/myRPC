package client.proxy;

import client.retry.GuavaRetry;
import client.rpcClient.RpcClient;
import common.message.RpcRequest;
import common.message.RpcResponse;
import common.pojo.User;
import common.result.Result;
import common.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("ClientProxy 测试")
class ClientProxyTest {

    @Nested
    @DisplayName("JDK 动态代理调用链路")
    class ProxyInvocationTests {

        private RpcClient mockRpcClient;
        private ClientProxy clientProxy;
        private UserService userService;

        @BeforeEach
        void setUp() {
            mockRpcClient = Mockito.mock(RpcClient.class);
            GuavaRetry retryStrategy = new GuavaRetry();
            clientProxy = new ClientProxy(mockRpcClient, retryStrategy);
            userService = clientProxy.getProxy(UserService.class);
        }

        @Test
        @DisplayName("能够构建合法的 JDK 代理")
        void shouldCreateProxyInstance() {
            assertNotNull(userService, "代理对象不应为空");
        }

        @Test
        @DisplayName("代理方法调用应通过 RpcClient 发起异步请求")
        void shouldDelegateInvocationToRpcClient() throws Exception {
            Long userId = 1L;
            User expectedUser = User.builder()
                    .id(userId)
                    .userName("测试用户")
                    .age(25)
                    .build();
            RpcResponse mockResponse = RpcResponse.success(expectedUser);

            // Mock异步响应
            when(mockRpcClient.sendRequestAsync(any(RpcRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(mockResponse));

            // 调用异步方法并获取结果
            CompletableFuture<Result<User>> resultFuture = userService.getUserById(userId);
            Result<User> result = resultFuture.get();
            User user = result.getData();

            assertNotNull(user, "返回结果不应为空");
            assertEquals(userId, user.getId(), "用户ID应匹配");
            assertEquals(expectedUser.getUserName(), user.getUserName(), "用户名应匹配");
        }
    }
}