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