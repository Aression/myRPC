# 从零开始的RPC实现

### version1 基础架构


### version2 引入Netty高性能网络通信框架

引入Netty高性能网络通信框架，实现客户端与服务端之间的通信。
在服务端和客户端分别通过Netty重新实现ServerHandler和ClientHandler，实现消息的编解码和业务逻辑处理。

### version3 引入Zookeeper服务注册与发现

引入zookeeper作为注册中心，实现服务注册与发现。服务端上线时，在注册中心注册自己的服务和对应地址；客户端启动时，从注册中心获取服务列表，并缓存到本地，后续调用时，从本地缓存中获取服务地址，进行远程调用。

特点：
- 高性能：全量数据存储在内存中，直接服务于客户端的所有非事务请求，尤其适用于读多写少的场景。
- 高可用：zookeeper一般以集群方式部署，即使单个节点故障，也不会影响整体服务。
- 严格顺序访问：对每个更新请求，zookeeper都会分配一个全局唯一的递增编号，保证顺序性。

使用方式：
- 在服务端的ServiceProvider中引入zookeeper，在provideServiceInterface方法注册服务时，同时向zookeeper提交服务信息
- 在客户端NettyRpcClient中，需要调用一个方法时，首先从zookeeper申请查询服务名对应的地址和端口，再进行请求操作
- 运行时，先在zookeeper安装目录的bin目录下打开zkServer.cmd把zookeeper服务跑起来，然后先后启动server和client

### version4 自定义序列化方法+性能对比

1. 在common包中实现自定义的编解码类，规定传输的数据包结构：

    ```
        /**
        * 消息结构如下:
        * +---------------+---------------+-----------------+-------------+
        * |  消息类型      |  序列化类型    |    数据长度      |    数据      |
        * |    2字节      |    2字节      |     4字节       |   N字节     |
        * +---------------+---------------+-----------------+-------------+
        * 
        * 1. 消息类型(2字节):
        *    - 0: RpcRequest请求消息
        *    - 1: RpcResponse响应消息
        * 
        * 2. 序列化类型(2字节):
        *    - 0: Java原生序列化
        *    - 1: JSON序列化
        * 
        * 3. 数据长度(4字节):
        *    - 标识序列化后的数据长度
        * 
        * 4. 数据(N字节):
        *    - 序列化后的消息体数据
        */
    ```

2. 实现ObjectSerializer和基于fastjson的ObjectSerializer方法；

3. 更新提供的用户服务以模拟实际的处理过程；
    - 遇到了rpc返回数据不能为空的问题，如果data字段为空则无法正常反序列化；解决方案是约定一个无效数据(id=-1)作为默认值。

4. 实现多节点的服务提供者和多线程混合读写的测试客户端，进行初步性能对比：
    - json：
        ```
        插入成功次数: 7011
        插入失败次数: 0
        删除成功次数: 1
        删除失败次数: 2988
        总耗时: 11826ms
        平均每秒处理数: 845.594452900389
        ```
    - object:
        ```
        插入成功次数: 7022
        插入失败次数: 0
        删除成功次数: 0
        删除失败次数: 2978
        总耗时: 10281ms
        平均每秒处理数: 972.6680284019064
        ```
    - protobuf:
        ```
        插入成功次数: 7047
        插入失败次数: 0
        删除成功次数: 0
        删除失败次数: 2953
        总耗时: 10211ms
        平均每秒处理数: 979.3360101850946
        ```

    对比结果：尽管操作更复杂，需要编写.proto文件并预编译对应的消息类，但综合看来protobuf的性能最强

5. 使用Result<T>泛型包装返回消息，弃用原有的-1标识符以提升可维护性。

### version5 基于一致性哈希的负载均衡

1. 实现了基于一致性哈希算法的负载均衡策略，提高了请求分发的一致性和系统稳定性：

   ```
   // 一致性哈希特点
   - 确定性：相同的请求总是路由到相同的服务节点，提高了缓存命中率
   - 平衡性：通过虚拟节点技术，使请求均匀分布到各个服务节点
   - 单调性：当有节点加入或退出时，只影响哈希环上相邻的节点，最小化重新分配的影响
   ```

2. 引入了负载均衡接口设计，支持多种负载均衡策略：
   - 一致性哈希(ConsistencyHashBalance)：根据请求特征路由到固定节点
   - 随机(RandomLoadBalance)：随机选择一个可用节点

3. 优化了ZK服务中心的服务发现逻辑：
   - 增加了本地服务地址缓存，减少对ZK的访问频率
   - 添加了服务节点变更监听器，当服务节点发生变化时自动更新本地缓存和哈希环

4. 提供了负载均衡工厂类，方便切换不同的负载均衡策略：
   ```java
   // 使用一致性哈希负载均衡
   ClientProxy clientProxy = new ClientProxy(LoadBalanceFactory.BalanceType.CONSISTENCY_HASH);
   
   // 使用随机负载均衡
   ClientProxy clientProxy = new ClientProxy(LoadBalanceFactory.BalanceType.RANDOM);
   ```
5. 修改了RPC协议消息体的data字段：
    - request：增加了通过时间戳生成的请求指纹以便一致性哈希策略进行请求分配
    - response：增加了ip：port字段，便于请求端统计负载均衡情况

### version 6 幂等性服务重试+client侧半开器熔断+server侧令牌桶限流
1. 实现了服务重试机制，支持幂等性服务的自动重试：
   ```java
   Retryer<RpcResponse> retryer = RetryerBuilder.<RpcResponse>newBuilder()
                //无论出现什么异常，都进行重试
                .retryIfException()
                //返回结果为 error时进行重试
                .retryIfResult(response -> Objects.equals(response.getCode(), 500))
                //重试等待策略：等待 2s 后再进行重试
                .withWaitStrategy(WaitStrategies.fixedWait(2, TimeUnit.SECONDS))
                //重试停止策略：重试达到 3 次
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .withRetryListener(new RetryListener() {
                    @Override
                    public <V> void onRetry(Attempt<V> attempt) {
                        System.out.println("RetryListener: 第" + attempt.getAttemptNumber() + "次调用");
                    }
                })
                .build();
   ```
   - 服务端手动指定服务是否是幂等的，在上线服务的时候控制是否可以自动重试；
   - 

2. 引入了熔断器模式，防止故障扩散：
   - 三种状态：关闭(CLOSED)、开启(OPEN)、半开(HALF_OPEN)
   - 关键参数：
     ```java
        failureThreshold: 失败次数阈值
        half2OpenSuccessRate: 半开状态恢复所需成功率
        retryTimePeriod: 熔断恢复等待时间
     ```
   - 核心方法：
     ```java
        allowRequest(): 判断请求是否允许通过
        recordSuccess(): 记录成功请求
        recordFailure(): 记录失败请求
     ```
     两个记录方法在clientproxy中完成请求后执行：
     ```java
        // 状态码5xx以及429(熔断器被触发)视为失败请求
        if(response.getCode()/100==5 || response.getCode()==429) breaker.recordFailure();
        // 状态码2xx和4xx都视为成功请求
        else if(response.getCode()/100==2 || response.getCode()/100==4) breaker.recordSuccess();
        // 对于非Result类型的返回，直接返回响应数据
        return response.getData();
     ```

3. 实现了基于令牌桶算法的限流器：
   - 关键参数：
     ```java
        private static int waitTimeRate; // 决定是否重新生成令牌的等待时间阈值
        private static int bucketCapacity; // 令牌桶容量上限
        private volatile int curCapacity; // 当前的令牌桶容量
     ```
   - 工作原理：
     ```java
        @Override
        public synchronized boolean getToken(){
            if(curCapacity>0){
                curCapacity--;
                return true; // 令牌桶有容量，拿走一个令牌并返回true
            }
            long currentTime = System.currentTimeMillis();
            if(currentTime - lastAccessTime>=waitTimeRate){
                // 令牌桶没有剩余令牌
                // 则根据距离上次请求过去的时间计算等待时间中生成的令牌数量，更新当前令牌数
                if((currentTime-lastAccessTime)/waitTimeRate>=2){
                    curCapacity += (int)(currentTime-lastAccessTime)/waitTimeRate-1;
                }

                // 保持桶容量不超过上限
                if(curCapacity>bucketCapacity) curCapacity = bucketCapacity;
                
                // 更新最后一次访问时间并返回
                lastAccessTime = currentTime;
                return true;
            }
            return false; 
        }
     ```

4. 其他事项：
    - 迁移大部分命令行输出到slf4j+logback
    - 迁移文件数据存储结构
    - 完善部分文档

### version 7 统一配置与插件实现一致性
1. 新增统一配置文件 `src/main/resources/application.properties`：
   - rpc.serializer.type=protobuf|json|object|0|1|2
   - rpc.loadbalance.type=consistency_hash|random|sequence|lstm
   - rpc.zk.connect=127.0.0.1:2285
   - rpc.zk.sessionTimeoutMs=40000
   - rpc.zk.namespace=MY_RPC
   - rpc.ratelimit.impl=configurable_token_bucket|token_bucket|synchronized_token_bucket
   - rpc.ratelimit.rate.ms=10
   - rpc.ratelimit.capacity=300

2. 配置优先级：系统属性可覆盖同名键（例如 `-Drpc.zk.connect=...`）。未提供配置文件时，系统按内置默认工作（负载均衡一致性哈希，序列化优先 Protobuf 再 JSON，限流可配置令牌桶，默认 ZK 参数）。

3. 相关文档：`doc/实现一致性与配置化设计.md`