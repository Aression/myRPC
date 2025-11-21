package server.serviceRegister;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import server.serviceRegister.impl.ZKServiceRegister;

import java.net.InetSocketAddress;

import static org.mockito.Mockito.verify;

@DisplayName("ServiceRegister 注册流程")
class ServiceRegisterTest {

    private ServiceRegister serviceRegister;

    @BeforeEach
    void setUp() {
        serviceRegister = Mockito.mock(ZKServiceRegister.class);
    }

    @Nested
    @DisplayName("register 调用链")
    class RegisterInvocationTests {

        @Test
        @DisplayName("单个服务注册应透传到实现")
        void shouldRegisterSingleService() {
            String serviceName = "com.test.UserService";
            InetSocketAddress address = new InetSocketAddress("localhost", 8080);
            boolean canRetry = true;

            serviceRegister.register(serviceName, address, canRetry);
            verify(serviceRegister).register(serviceName, address, canRetry);
        }

        @Test
        @DisplayName("多个服务注册应分别调用")
        void shouldRegisterMultipleServicesIndependently() {
            InetSocketAddress address = new InetSocketAddress("localhost", 8080);
            boolean canRetry = true;

            serviceRegister.register("com.test.UserService", address, canRetry);
            serviceRegister.register("com.test.OrderService", address, canRetry);

            verify(serviceRegister).register("com.test.UserService", address, canRetry);
            verify(serviceRegister).register("com.test.OrderService", address, canRetry);
        }
    }
}
