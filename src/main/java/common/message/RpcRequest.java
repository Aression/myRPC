package common.message;

import java.io.Serializable;

import common.util.HashUtil;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
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

    // 特征码和对应的动态getter
    private String featureCode;
    public String getFeatureCode(){
        if(featureCode == null || featureCode.isEmpty()) featureCode = HashUtil.generateFeatureCode(interfaceName, methodName, params);
        return featureCode;
    }


    // 链路追踪相关字段
    private String traceId;
    private String spanId;
}
