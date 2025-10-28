package common.serializer;

import common.message.RpcRequest;
import common.message.RpcResponse;
import common.pojo.User;
import common.serializer.impl.JsonSerializer;
import common.serializer.impl.ProtobufSerializer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ServiceLoader;
import java.util.UUID;

public class SerializerTest {
    
    private User testUser;
    private RpcRequest testRequest;
    private RpcResponse testResponse;
    
    @Before
    public void setUp() {
        // 创建测试用例
        testUser = User.builder()
                .id(1)
                .userName("测试用户")
                .age(25)
                .sex(true)
                .email("test@example.com")
                .phone("13800138000")
                .address("北京市海淀区")
                .userType("普通用户")
                .lastUpdateTime(System.currentTimeMillis())
                .build();
        
        // 创建RPC请求对象
        testRequest = RpcRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .interfaceName("common.service.UserService")
                .methodName("getUserById")
                .params(new Object[]{1})
                .paramsType(new Class<?>[]{Integer.class})
                .timestamp(System.currentTimeMillis())
                .traceId("trace-" + UUID.randomUUID().toString())
                .spanId("span-" + UUID.randomUUID().toString())
                .build();
        
        // 创建RPC响应对象
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
    
    @Test
    public void testSerializerLoad() {
        System.out.println("开始测试序列化器加载...");
        
        ServiceLoader<Serializer> serviceLoader = ServiceLoader.load(Serializer.class);
        int count = 0;
        
        for (Serializer serializer : serviceLoader) {
            count++;
            System.out.println("加载到序列化器: " + serializer.getClass().getName() + 
                            ", 类型: " + serializer.getType() + 
                            ", 名称: " + serializer.getSerializerName());
        }
        
        System.out.println("共加载到 " + count + " 个序列化器");
        
        // 测试通过工厂类获取序列化器
        for (int i = 1; i <= 2; i++) {
            Serializer serializer = SerializerFactory.getSerializerByCode(i);
            Assert.assertNotNull("序列化器不应为空", serializer);
            System.out.println("通过工厂类获取序列化器成功，类型: " + i +
                    ", 实现类: " + serializer.getClass().getName());
        }
    }
    
    @Test
    public void testProtobufSerializerRoundTrip() {
        Serializer serializer = new ProtobufSerializer();
        Assert.assertEquals(2, serializer.getType());
        Assert.assertEquals("ProtobufSerializer", serializer.getSerializerName());

        byte[] requestBytes = serializer.serialize(testRequest);
        Assert.assertNotNull("序列化结果不应为空", requestBytes);
        RpcRequest deserializedRequest = (RpcRequest) serializer.deserialize(requestBytes, 0);
        Assert.assertEquals("反序列化后的请求ID应与原请求ID相等", testRequest.getRequestId(), deserializedRequest.getRequestId());

        byte[] responseBytes = serializer.serialize(testResponse);
        Assert.assertNotNull("序列化结果不应为空", responseBytes);
        RpcResponse deserializedResponse = (RpcResponse) serializer.deserialize(responseBytes, 1);
        Assert.assertEquals("反序列化后的响应码应与原响应码相等", testResponse.getCode(), deserializedResponse.getCode());
    }
    
    @Test
    public void testJsonSerializer() {
        // 获取JSON序列化器
        Serializer serializer = new JsonSerializer();
        Assert.assertEquals(1, serializer.getType());
        Assert.assertEquals("JsonSerializer", serializer.getSerializerName());
        
        // 测试RpcRequest对象序列化和反序列化
        byte[] requestBytes = serializer.serialize(testRequest);
        Assert.assertNotNull("序列化结果不应为空", requestBytes);
        RpcRequest deserializedRequest = (RpcRequest) serializer.deserialize(requestBytes, 0);
        Assert.assertEquals("反序列化后的请求ID应与原请求ID相等", testRequest.getRequestId(), deserializedRequest.getRequestId());
        Assert.assertEquals("反序列化后的接口名应与原接口名相等", testRequest.getInterfaceName(), deserializedRequest.getInterfaceName());
        
        // 测试RpcResponse对象序列化和反序列化
        byte[] responseBytes = serializer.serialize(testResponse);
        Assert.assertNotNull("序列化结果不应为空", responseBytes);
        RpcResponse deserializedResponse = (RpcResponse) serializer.deserialize(responseBytes, 1);
        Assert.assertEquals("反序列化后的响应码应与原响应码相等", testResponse.getCode(), deserializedResponse.getCode());
        Assert.assertEquals("反序列化后的请求ID应与原请求ID相等", testResponse.getRequestId(), deserializedResponse.getRequestId());
    }
}