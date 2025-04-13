package common.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * RPC请求对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RpcRequest implements Serializable {
    /**
     * 接口名称
     */
    private String interfaceName;
    
    /**
     * 方法名称
     */
    private String methodName;
    
    /**
     * 参数
     */
    private Object[] params;
    
    /**
     * 参数类型
     */
    private Class<?>[] paramsType;
    
    /**
     * 请求时间戳，用于负载均衡
     */
    private long timestamp;
    
    /**
     * 获取请求的唯一特征码，用于一致性哈希负载均衡
     */
    public String getFeatureCode() {
        StringBuilder sb = new StringBuilder();
        sb.append(interfaceName).append("#")
          .append(methodName).append("#");
        
        // 添加参数特征
        if (params != null && params.length > 0) {
            for (Object param : params) {
                if (param != null) {
                    // 添加参数的简单特征
                    if (param instanceof Integer || param instanceof Long || param instanceof String) {
                        sb.append(param.toString()).append("-");
                    } else {
                        // 对于复杂对象，使用类名和hashCode
                        sb.append(param.getClass().getSimpleName())
                          .append(System.identityHashCode(param) % 1000).append("-");
                    }
                }
            }
        }
        
        // 添加时间戳
        sb.append("#").append(timestamp);
        
        return sb.toString();
    }
}
