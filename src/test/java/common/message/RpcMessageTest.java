package common.message;

import common.pojo.User;
import common.util.HashUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RpcMessage 结构体测试")
class RpcMessageTest {

    @Nested
    @DisplayName("RpcRequest 构建")
    class RpcRequestTests {

        @Test
        @DisplayName("Builder 应填充所有字段并计算特征码")
        void shouldCreateRpcRequestWithAllFields() {
            String requestId = UUID.randomUUID().toString();
            String interfaceName = "common.service.UserService";
            String methodName = "getUserById";
            Object[] params = new Object[] { 1 };
            Class<?>[] paramsType = new Class<?>[] { Integer.class };
            long timestamp = System.currentTimeMillis();
            String traceId = "trace-" + UUID.randomUUID();
            String spanId = "span-" + UUID.randomUUID();

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

            assertEquals(requestId, request.getRequestId(), "请求ID应匹配");
            assertEquals(interfaceName, request.getInterfaceName(), "接口名应匹配");
            assertEquals(methodName, request.getMethodName(), "方法名应匹配");
            assertArrayEquals(params, request.getParams(), "参数应匹配");
            assertArrayEquals(paramsType, request.getParamsType(), "参数类型应匹配");
            assertEquals(timestamp, request.getTimestamp(), "时间戳应匹配");
            assertEquals(traceId, request.getTraceId(), "TraceId 应匹配");
            assertEquals(spanId, request.getSpanId(), "SpanId 应匹配");

            long expectedFeatureCode = HashUtil.generateFeatureCode(interfaceName, methodName, params);
            assertEquals(expectedFeatureCode, request.getFeatureCode(), "特征码应正确计算");
        }
    }

    @Nested
    @DisplayName("RpcResponse 构建")
    class RpcResponseTests {

        @Test
        @DisplayName("Builder 应还原响应所有字段")
        void shouldCreateRpcResponseWithAllFields() {
            String requestId = UUID.randomUUID().toString();
            Integer code = 200;
            String message = "success";
            User user = User.builder().id(1L).userName("测试用户").build();
            Class<?> dataType = User.class;
            String traceId = "trace-" + UUID.randomUUID();
            String spanId = "span-" + UUID.randomUUID();

            RpcResponse response = RpcResponse.builder()
                    .requestId(requestId)
                    .code(code)
                    .message(message)
                    .data(user)
                    .dataType(dataType)
                    .traceId(traceId)
                    .spanId(spanId)
                    .build();

            assertEquals(requestId, response.getRequestId(), "请求ID应匹配");
            assertEquals(code, response.getCode(), "响应码应匹配");
            assertEquals(message, response.getMessage(), "响应消息应匹配");
            assertEquals(user, response.getData(), "响应数据应匹配");
            assertEquals(dataType, response.getDataType(), "数据类型应匹配");
            assertEquals(traceId, response.getTraceId(), "TraceId 应匹配");
            assertEquals(spanId, response.getSpanId(), "SpanId 应匹配");
        }

        @Test
        @DisplayName("success / fail 静态方法应封装常见返回")
        void shouldSupportStaticFactoryMethods() {
            User user = User.builder().id(1L).userName("测试用户").build();
            RpcResponse success = RpcResponse.success(user);
            assertEquals(Integer.valueOf(200), success.getCode(), "成功响应码应为200");
            assertEquals(user, success.getData(), "成功响应数据应与输入一致");
            assertEquals(User.class, success.getDataType(), "成功响应数据类型应为User");

            Integer errorCode = 500;
            String errorMessage = "Internal Server Error";
            RpcResponse fail = RpcResponse.fail(errorCode, errorMessage);
            assertEquals(errorCode, fail.getCode(), "失败响应码应匹配");
            assertEquals(errorMessage, fail.getMessage(), "失败响应消息应匹配");
            assertNull(fail.getData(), "失败响应数据应为空");
        }
    }
}
