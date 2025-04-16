package server.serviceRegister.impl;

import common.util.AddressUtil;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import server.serviceRegister.ServiceRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public class ZKServiceRegister implements ServiceRegister {
    private static final Logger logger = LoggerFactory.getLogger(ZKServiceRegister.class);
    private CuratorFramework client;
    private static final String ROOT_PATH = "MY_RPC";
    private static final String RETRY_GROUP = "CanRetry";

    //负责zookeeper客户端的初始化，并与zookeeper服务端进行连接
    public ZKServiceRegister(){
        // 指数时间重试
        RetryPolicy policy = new ExponentialBackoffRetry(1000, 3);
        // zookeeper的地址固定，不管是服务提供者还是，消费者都要与之建立连接
        // sessionTimeoutMs 与 zoo.cfg中的tickTime 有关系，
        // zk还会根据minSessionTimeout与maxSessionTimeout两个参数重新调整最后的超时值。默认分别为tickTime 的2倍和20倍
        // 使用心跳监听状态
        this.client = CuratorFrameworkFactory.builder().connectString("127.0.0.1:2285")
                .sessionTimeoutMs(40000).retryPolicy(policy).namespace(ROOT_PATH).build();
        this.client.start();
        logger.info("zookeeper 连接成功");
    }

    @Override
    public void register(String serviceName, InetSocketAddress serviceAddress, boolean canRetry) {
        try {
            String servicePath = "/" + serviceName;
            String address = AddressUtil.toString(serviceAddress);
            String instancePath = servicePath + "/" + address;

            // 安全创建永久服务节点（如果已存在，忽略异常）
            try {
                client.create()
                        .creatingParentsIfNeeded()
                        .withMode(CreateMode.PERSISTENT)
                        .forPath(servicePath);
            } catch (Exception e) {
                if (!(e instanceof org.apache.zookeeper.KeeperException.NodeExistsException)) {
                    throw e; // 只有节点已存在可忽略，其他抛出
                }
            }

            // 创建服务实例节点（临时节点）
            client.create()
                    .creatingParentsIfNeeded()
                    .withMode(CreateMode.EPHEMERAL)
                    .forPath(instancePath);

            // 如果支持 retry，创建 retry 节点（也包含唯一地址）
            if (canRetry) {
                String retryPath = "/" + RETRY_GROUP + "/" + serviceName + "/" + address;
                client.create()
                        .creatingParentsIfNeeded()
                        .withMode(CreateMode.EPHEMERAL)
                        .forPath(retryPath);
            }

            logger.info("服务注册成功: {}", instancePath);
        } catch (Exception e) {
            logger.error("服务注册失败: {}", e.getMessage(), e);
        }
    }
}
