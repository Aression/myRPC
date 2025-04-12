package common.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RpcResponse implements Serializable {
    private int code;
    private String message;

    // 数据类型
    private Class<?> dataType;
    
    // 具体数据
    private Object data;

    //成功消息
    public static RpcResponse success(Object data){
        return RpcResponse.builder()
                .code(200)
                .data(data)
                .dataType(data != null ? data.getClass() : null)
                .build();
    }

    // 默认失败消息
    public static RpcResponse fail() {
        return fail(500, "服务器内部错误");
    }
    //失败消息
    public static RpcResponse fail(int code, String msg){
        return RpcResponse.builder()
                .code(code)
                .message(msg)
                .build();
    }
}
