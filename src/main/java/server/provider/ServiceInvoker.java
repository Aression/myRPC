package server.provider;

import java.lang.reflect.Method;

/**
 * 服务调用器接口
 * 用于抽象具体的服务调用方式（反射、LambdaMetafactory等）
 */
public interface ServiceInvoker {
    /**
     * 调用服务方法
     * 
     * @param service 服务实例
     * @param method  目标方法
     * @param args    参数列表
     * @return 调用结果
     * @throws Exception 调用异常
     */
    Object invoke(Object service, Method method, Object[] args) throws Exception;
}
