package server.provider;

import java.util.HashMap;
import java.util.Map;

public class ServiceProvider {
    //存放服务实例，<接口全限定名，接口实现类实例>
    private Map<String, Object> interfaceProvider;

    //空构造函数
    public ServiceProvider(){
        this.interfaceProvider = new HashMap<>();
    }

    //本地注册服务
    public void provideServiceInterface(Object service){
        String serviceName = service.getClass().getName();
        Class<?>[] interfaceNames = service.getClass().getInterfaces();
        for(Class<?> clazz:interfaceNames){
            interfaceProvider.put(
                    clazz.getName(),
                    service
            );//将接口的全限定名和对应服务实例注册到map中
        }
    }

    //从map获取服务实例
    public Object getService(String interfaceName){
        return interfaceProvider.get(interfaceName);
    }
}
