package common.message;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class RpcRequest implements Serializable {
    //服务类名，在服务端接口指向实现类
    private String interfaceName;
    //调用的方法名
    private String methodName;
    //参数列表和对应类型
    private Object[] params;
    private Class<?>[] paramsType;
}
