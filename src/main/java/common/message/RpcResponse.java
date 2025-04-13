package common.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.alibaba.fastjson.JSON;

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
    
    // 服务器地址（ip:port）
    private String serverAddress;

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
    
    /**
     * 转换响应数据为指定类型
     * @param targetType 目标类型
     */
    public void convertData(Class<?> targetType) {
        if (data == null || targetType == null) {
            return;
        }
        
        // 如果类型已匹配，不需要转换
        if (targetType.isInstance(data)) {
            return;
        }
        
        try {
            // 处理基本类型
            if (targetType == String.class) {
                data = data.toString();
            } else if (targetType == Integer.class || targetType == int.class) {
                if (data instanceof String) {
                    data = Integer.parseInt((String) data);
                } else if (data instanceof Number) {
                    data = ((Number) data).intValue();
                }
            } else if (targetType == Boolean.class || targetType == boolean.class) {
                if (data instanceof String) {
                    data = Boolean.parseBoolean((String) data);
                }
            } else if (targetType == Long.class || targetType == long.class) {
                if (data instanceof String) {
                    data = Long.parseLong((String) data);
                } else if (data instanceof Number) {
                    data = ((Number) data).longValue();
                }
            } else if (targetType == Double.class || targetType == double.class) {
                if (data instanceof String) {
                    data = Double.parseDouble((String) data);
                } else if (data instanceof Number) {
                    data = ((Number) data).doubleValue();
                }
            } else if (targetType == Float.class || targetType == float.class) {
                if (data instanceof String) {
                    data = Float.parseFloat((String) data);
                } else if (data instanceof Number) {
                    data = ((Number) data).floatValue();
                }
            } 
            // 处理复杂对象
            else if (data instanceof String) {
                String jsonStr = (String) data;
                // 检查是否是JSON格式
                if (jsonStr.trim().startsWith("{") && jsonStr.trim().endsWith("}")) {
                    data = JSON.parseObject(jsonStr, targetType);
                }
            }
            // 更新dataType
            dataType = data.getClass();
            
        } catch (Exception e) {
            System.out.println("数据类型转换失败: " + e.getMessage());
        }
    }
}
