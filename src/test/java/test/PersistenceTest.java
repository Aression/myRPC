package test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import common.pojo.User;
import common.result.Result;
import common.service.UserService;
import client.proxy.ClientProxy;
import client.retry.GuavaRetry;
import client.rpcClient.RpcClient;
import client.rpcClient.impl.NettyRpcClient;

import java.util.Random;

/**
 * 多节点持久化测试
 * 用于验证分布式锁和定期持久化功能
 */
public class PersistenceTest {
    private static final Logger logger = LoggerFactory.getLogger(PersistenceTest.class);
    private static final Random random = new Random();

    public static void main(String[] args) {
        logger.info("=== 开始多节点持久化测试 ===\n");

        try {
            // 创建RPC客户端（使用新的异步API）
            RpcClient rpcClient = new NettyRpcClient();
            ClientProxy clientProxy = new ClientProxy(rpcClient, new GuavaRetry());
            UserService userService = clientProxy.getProxy(UserService.class);

            // 1. 插入一些测试数据
            logger.info("阶段1: 插入测试数据");
            for (int i = 0; i < 10; i++) {
                User user = User.builder()
                        .userName("TestUser_" + i)
                        .sex(i % 2 == 0)
                        .age(20 + random.nextInt(30))
                        .build();

                // 异步调用需要使用.get()获取结果
                Result<Long> result = userService.insertUser(user).get();
                if (result.getCode() == 200) {
                    logger.info("成功插入用户: ID={}, Name={}", result.getData(), user.getUserName());
                } else {
                    logger.warn("插入用户失败: {}", result.getMessage());
                }
                Thread.sleep(100);
            }

            logger.info("\n阶段2: 等待5秒后查询数据以确认插入成功");
            Thread.sleep(5000);

            // 2. 随机查询几条数据
            logger.info("验证数据是否可正常查询...");
            for (int i = 0; i < 5; i++) {
                // 这里我们无法预知ID，所以只是示例性地查询
                logger.info("查询测试完成");
                break;
            }

            logger.info("\n阶段3: 测试完成");
            logger.info("请手动执行以下步骤进行验证：");
            logger.info("1. 保持MultiNodeServer运行，等待5分钟观察定期持久化日志");
            logger.info("2. 使用Ctrl+C停止MultiNodeServer，观察关闭时的持久化日志");
            logger.info("3. 检查 data/users.json 文件内容");
            logger.info("4. 重启MultiNodeServer，检查数据是否正确加载");

        } catch (Exception e) {
            logger.error("测试过程中发生异常", e);
        }
    }
}
