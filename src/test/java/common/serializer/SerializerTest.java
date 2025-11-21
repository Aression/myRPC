package common.serializer;

import common.message.RpcRequest;
import common.message.RpcResponse;
import common.pojo.User;
import common.serializer.impl.JsonSerializer;
import common.serializer.impl.ProtobufSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Serializer SPI & 实现测试")
class SerializerTest {

    private User testUser;
    private RpcRequest testRequest;
    private RpcResponse testResponse;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .userName("测试用户")
                .age(25)
                .sex(true)
                .email("test@example.com")
                .phone("13800138000")
                .address("北京市海淀区")
                .userType("普通用户")
                .lastUpdateTime(System.currentTimeMillis())
                .build();

        testRequest = RpcRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .interfaceName("common.service.UserService")
                .methodName("getUserById")
                .params(new Object[] { 1 })
                .paramsType(new Class<?>[] { Integer.class })
                .timestamp(System.currentTimeMillis())
                .traceId("trace-" + UUID.randomUUID())
                .spanId("span-" + UUID.randomUUID())
                .build();

        testResponse = RpcResponse.builder()
                .requestId(testRequest.getRequestId())
                .code(200)
                .message("success")
                .data(testUser)
                .dataType(User.class)
                .traceId(testRequest.getTraceId())
                .spanId(testRequest.getSpanId())
                .build();
    }

    @Nested
    @DisplayName("SPI 加载与工厂")
    class SpiLoadingTests {

        @Test
        @DisplayName("ServiceLoader 应发现所有实现")
        void shouldLoadSerializersViaSpi() {
            ServiceLoader<Serializer> serviceLoader = ServiceLoader.load(Serializer.class);
            int count = 0;
            for (Serializer serializer : serviceLoader) {
                count++;
                System.out.println("加载到序列化器: " + serializer.getClass().getSimpleName()
                        + ", type=" + serializer.getType()
                        + ", name=" + serializer.getSerializerName());
            }
            assertTrue(count > 0, "ServiceLoader 至少应加载一个实现");
        }

        @Test
        @DisplayName("工厂应按类型返回实现")
        void shouldLoadSerializerViaFactory() {
            for (int i = 1; i <= 2; i++) {
                Serializer serializer = SerializerFactory.getSerializerByCode(i);
                assertNotNull(serializer, "序列化器不应为空");
            }
        }
    }

    @Nested
    @DisplayName("ProtobufSerializer")
    class ProtobufSerializerTests {

        @Test
        @DisplayName("序列化/反序列化请求与响应")
        void shouldSerializeAndDeserializeRequestAndResponse() {
            Serializer serializer = new ProtobufSerializer();
            assertEquals(2, serializer.getType());
            assertEquals("ProtobufSerializer", serializer.getSerializerName());

            byte[] requestBytes = serializer.serialize(testRequest);
            assertNotNull(requestBytes, "序列化结果不应为空");
            RpcRequest deserializedRequest = (RpcRequest) serializer.deserialize(requestBytes, 0);
            assertEquals(testRequest.getRequestId(), deserializedRequest.getRequestId(), "请求ID应保持一致");

            byte[] responseBytes = serializer.serialize(testResponse);
            assertNotNull(responseBytes, "序列化结果不应为空");
            RpcResponse deserializedResponse = (RpcResponse) serializer.deserialize(responseBytes, 1);
            assertEquals(testResponse.getCode(), deserializedResponse.getCode(), "响应码应保持一致");
        }
    }

    @Nested
    @DisplayName("JsonSerializer")
    class JsonSerializerTests {

        @Test
        @DisplayName("序列化/反序列化请求与响应")
        void shouldSerializeAndDeserializeRequestAndResponse() {
            Serializer serializer = new JsonSerializer();
            assertEquals(1, serializer.getType());
            assertEquals("JsonSerializer", serializer.getSerializerName());

            byte[] requestBytes = serializer.serialize(testRequest);
            assertNotNull(requestBytes, "序列化结果不应为空");
            RpcRequest deserializedRequest = (RpcRequest) serializer.deserialize(requestBytes, 0);
            assertEquals(testRequest.getRequestId(), deserializedRequest.getRequestId(), "请求ID应保持一致");
            assertEquals(testRequest.getInterfaceName(), deserializedRequest.getInterfaceName(), "接口名应保持一致");

            byte[] responseBytes = serializer.serialize(testResponse);
            assertNotNull(responseBytes, "序列化结果不应为空");
            RpcResponse deserializedResponse = (RpcResponse) serializer.deserialize(responseBytes, 1);
            assertEquals(testResponse.getCode(), deserializedResponse.getCode(), "响应码应保持一致");
            assertEquals(testResponse.getRequestId(), deserializedResponse.getRequestId(), "请求ID应保持一致");
        }
    }
}
