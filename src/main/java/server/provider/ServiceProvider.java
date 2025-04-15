package server.provider;

import server.serviceRegister.ServiceRegister;
import server.serviceRegister.impl.ZKServiceRegister;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

public class ServiceProvider {
    //存放服务实例，<接口全限定名，接口实现类实例>
    private Map<String, Object> interfaceProvider;

    // zookeeper相关内容
    private int port;
    private String host;
    private ServiceRegister serviceRegister;

    public ServiceProvider(String host, int port){
        this.host = host;
        this.port = port;
        this.interfaceProvider = new HashMap<>();
        this.serviceRegister = new ZKServiceRegister();
    }

    //本地注册服务
    public void provideServiceInterface(Object service, boolean canRetry){
        String serviceName = service.getClass().getName();
        Class<?>[] interfaceNames = service.getClass().getInterfaces();
        for(Class<?> clazz:interfaceNames){
            interfaceProvider.put(
                    clazz.getName(),
                    service
            );//将接口的全限定名和对应服务实例注册到map中
            serviceRegister.register(
                    clazz.getName(),
                    new InetSocketAddress(host, port),
                    canRetry
            );//同时在zookeeper中注册服务
        }
    }

    //从map获取服务实例
    public Object getService(String interfaceName){
        return interfaceProvider.get(interfaceName);
    }
}
