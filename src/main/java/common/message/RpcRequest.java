package common.message;

import java.io.Serializable;

import common.util.HashUtil;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
public class RpcRequest implements Serializable {
    // 请求id
    private String requestId;

    // 调用的方法
    private String interfaceName;
    private String methodName;
    
    // 涉及的参数
    private Object[] params;
    private Class<?>[] paramsType;

    // 请求开始时间
    private long timestamp;

    // 特征码 - 不通过setter修改
    @Setter(AccessLevel.NONE)
    private long featureCode;

    // 链路追踪相关字段
    private String traceId;
    private String spanId;
    
    @Builder
    public RpcRequest(String requestId, String interfaceName, String methodName, 
                    Object[] params, Class<?>[] paramsType, long timestamp, 
                    String traceId, String spanId) {
        this.requestId = requestId;
        this.interfaceName = interfaceName;
        this.methodName = methodName;
        this.params = params;
        this.paramsType = paramsType;
        this.timestamp = timestamp;
        this.traceId = traceId;
        this.spanId = spanId;
        
        // 在构造时计算特征码
        this.featureCode = HashUtil.generateFeatureCode(interfaceName, methodName, params);
    }
}
