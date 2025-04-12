package client.serviceCenter;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.net.InetSocketAddress;
import java.util.List;

public class ZKServiceCenter implements ServiceCenter{
    private CuratorFramework client;// zookeeper 客户端
    private static final String ROOT_PATH="MY_RPC";// zookeeper根路径节点

    public ZKServiceCenter(){
        // 指数时间重试
        RetryPolicy policy = new ExponentialBackoffRetry(
                1000,
                3
        );
        // 设置心跳侦听
        this.client = CuratorFrameworkFactory.builder()
                .connectString("127.0.0.1:2181")
                .sessionTimeoutMs(40000)
                .retryPolicy(policy)
                .namespace(ROOT_PATH)
                .build();
        this.client.start();
        System.out.println("Zookeeper 连接成功");
    }

    @Override
    public InetSocketAddress serviceDiscovery(String serviceName) {
        try{
            // 检查服务是否存在
            if(client.checkExists().forPath("/"+serviceName) == null) {
                System.out.println("服务 " + serviceName + " 未在 Zookeeper 中注册");
                return null;
            }
            
            // 获取指定服务名称路径下的所有子节点
            List<String> strings = client.getChildren().forPath("/"+serviceName);
            System.out.println("发现服务 " + serviceName + " 的节点: " + strings);
            
            if(strings.isEmpty()) {
                System.out.println("服务 " + serviceName + " 没有可用的服务节点");
                return null;
            }
            
            // TODO: 增加负载均衡策略
            String addr = strings.get(0);
            System.out.println("选择服务节点: " + addr);
            return parseAddress(addr);
        } catch (Exception e) {
            System.out.println("服务发现失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private String getServiceAddress(InetSocketAddress serverAddress){
        return serverAddress.getHostName()+":"+serverAddress.getPort();
    }

    private InetSocketAddress parseAddress(String address){
        String[] result = address.split(":");
        return new InetSocketAddress(
                result[0],
                Integer.parseInt(result[1])
        );
    }
}
