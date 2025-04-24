package common.message;

import java.io.Serializable;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RpcResponse implements Serializable {
    // 对应请求id
    private String requestId;

    // 响应体调试字段
    private Integer code;
    private String message;

    // 响应数据字段
    private Class<?> dataType;
    private Object data;
    
    // 链路追踪相关字段
    private String traceId;
    private String spanId;
    
    public static RpcResponse success(Object data) {
        return RpcResponse.builder()
                .code(200)
                .data(data)
                .dataType(data != null ? data.getClass() : null)
                .build();
    }
    
    public static RpcResponse fail(Integer code, String message) {
        return RpcResponse.builder()
                .code(code)
                .message(message)
                .build();
    }
}
