package common.message;

import common.pojo.User;
import common.util.HashUtil;
import org.junit.Assert;
import org.junit.Test;

import java.util.UUID;

public class RpcMessageTest {

    @Test
    public void testRpcRequestCreation() {
        // 测试请求创建
        String requestId = UUID.randomUUID().toString();
        String interfaceName = "common.service.UserService";
        String methodName = "getUserById";
        Object[] params = new Object[]{1};
        Class<?>[] paramsType = new Class<?>[]{Integer.class};
        long timestamp = System.currentTimeMillis();
        String traceId = "trace-" + UUID.randomUUID().toString();
        String spanId = "span-" + UUID.randomUUID().toString();
        
        RpcRequest request = RpcRequest.builder()
                .requestId(requestId)
                .interfaceName(interfaceName)
                .methodName(methodName)
                .params(params)
                .paramsType(paramsType)
                .timestamp(timestamp)
                .traceId(traceId)
                .spanId(spanId)
                .build();
        
        // 验证请求属性
        Assert.assertEquals("请求ID应匹配", requestId, request.getRequestId());
        Assert.assertEquals("接口名应匹配", interfaceName, request.getInterfaceName());
        Assert.assertEquals("方法名应匹配", methodName, request.getMethodName());
        Assert.assertEquals("参数应匹配", params, request.getParams());
        Assert.assertEquals("参数类型应匹配", paramsType, request.getParamsType());
        Assert.assertEquals("时间戳应匹配", timestamp, request.getTimestamp());
        Assert.assertEquals("追踪ID应匹配", traceId, request.getTraceId());
        Assert.assertEquals("跨度ID应匹配", spanId, request.getSpanId());
        
        // 验证特征码是否正确计算
        long expectedFeatureCode = HashUtil.generateFeatureCode(interfaceName, methodName, params);
        Assert.assertEquals("特征码应正确计算", expectedFeatureCode, request.getFeatureCode());
    }
    
    @Test
    public void testRpcResponseCreation() {
        // 测试响应创建
        String requestId = UUID.randomUUID().toString();
        Integer code = 200;
        String message = "success";
        User user = User.builder().id(1).userName("测试用户").build();
        Class<?> dataType = User.class;
        String traceId = "trace-" + UUID.randomUUID().toString();
        String spanId = "span-" + UUID.randomUUID().toString();
        
        RpcResponse response = RpcResponse.builder()
                .requestId(requestId)
                .code(code)
                .message(message)
                .data(user)
                .dataType(dataType)
                .traceId(traceId)
                .spanId(spanId)
                .build();
        
        // 验证响应属性
        Assert.assertEquals("请求ID应匹配", requestId, response.getRequestId());
        Assert.assertEquals("响应码应匹配", code, response.getCode());
        Assert.assertEquals("响应消息应匹配", message, response.getMessage());
        Assert.assertEquals("响应数据应匹配", user, response.getData());
        Assert.assertEquals("数据类型应匹配", dataType, response.getDataType());
        Assert.assertEquals("追踪ID应匹配", traceId, response.getTraceId());
        Assert.assertEquals("跨度ID应匹配", spanId, response.getSpanId());
    }
    
    @Test
    public void testRpcResponseStaticMethods() {
        // 测试成功响应静态方法
        User user = User.builder().id(1).userName("测试用户").build();
        RpcResponse successResponse = RpcResponse.success(user);
        
        Assert.assertEquals("成功响应码应为200", Integer.valueOf(200), successResponse.getCode());
        Assert.assertEquals("成功响应数据应匹配", user, successResponse.getData());
        Assert.assertEquals("成功响应数据类型应匹配", User.class, successResponse.getDataType());
        
        // 测试失败响应静态方法
        Integer errorCode = 500;
        String errorMessage = "Internal Server Error";
        RpcResponse failResponse = RpcResponse.fail(errorCode, errorMessage);
        
        Assert.assertEquals("失败响应码应匹配", errorCode, failResponse.getCode());
        Assert.assertEquals("失败响应消息应匹配", errorMessage, failResponse.getMessage());
        Assert.assertNull("失败响应数据应为空", failResponse.getData());
    }
}