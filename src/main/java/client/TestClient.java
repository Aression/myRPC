package client;

import client.proxy.ClientProxy;
import client.serviceCenter.balance.LoadBalance.BalanceType;
import common.pojo.User;
import common.result.Result;
import common.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestClient {
    private static final Logger logger = LoggerFactory.getLogger(TestClient.class);

    public static void main(String[] args) {
        try {
            // 创建用户服务代理
            ClientProxy clientProxy = new ClientProxy(BalanceType.CONSISTENCY_HASH);
            UserService userService = clientProxy.getProxy(UserService.class);

            // 测试插入用户
            User newUser = User.builder()
                    .id(1)
                    .userName("张三")
                    .sex(true)
                    .build();
            Result<Integer> insertResult = userService.insertUser(newUser);
            if (insertResult.isSuccess()) {
                logger.info("向服务端插入用户成功，ID: {}", insertResult.getData());
                logger.info("服务端返回消息：{}", insertResult.getMessage());
            } else {
                logger.error("插入用户失败，错误码：{}", insertResult.getCode());
                logger.error("错误消息：{}", insertResult.getMessage());
            }

            // 测试查询用户
            Result<User> queryResult = userService.getUserById(1);
            if (queryResult.isSuccess()) {
                User user = queryResult.getData();
                logger.info("从服务端获取的user: {}", user);
                logger.info("服务端返回消息：{}", queryResult.getMessage());
            } else {
                logger.error("查询用户失败，错误码：{}", queryResult.getCode());
                logger.error("错误消息：{}", queryResult.getMessage());
            }

            // 测试更新用户
            User updatedUser = User.builder()
                    .id(1)
                    .userName("李四")
                    .sex(false)
                    .build();
            Result<Boolean> updateResult = userService.updateUser(updatedUser);
            if (updateResult.isSuccess()) {
                logger.info("更新用户成功：{}", updateResult.getMessage());
            } else {
                logger.error("更新用户失败，错误码：{}", updateResult.getCode());
                logger.error("错误消息：{}", updateResult.getMessage());
            }

            // 再次查询用户以验证更新
            Result<User> queryResultAfterUpdate = userService.getUserById(1);
            if (queryResultAfterUpdate.isSuccess()) {
                User user = queryResultAfterUpdate.getData();
                logger.info("更新后从服务端获取的user: {}", user);
                logger.info("服务端返回消息：{}", queryResultAfterUpdate.getMessage());
            } else {
                logger.error("查询用户失败，错误码：{}", queryResultAfterUpdate.getCode());
                logger.error("错误消息：{}", queryResultAfterUpdate.getMessage());
            }

            // 测试删除用户
            Result<Boolean> deleteResult = userService.deleteUserById(1);
            if (deleteResult.isSuccess()) {
                logger.info("删除用户成功：{}", deleteResult.getMessage());
            } else {
                logger.error("删除用户失败，错误码：{}", deleteResult.getCode());
                logger.error("错误消息：{}", deleteResult.getMessage());
            }

            // 再次查询用户以验证删除
            Result<User> queryResultAfterDelete = userService.getUserById(1);
            if (queryResultAfterDelete.isSuccess()) {
                User user = queryResultAfterDelete.getData();
                logger.info("删除后从服务端获取的user: {}", user);
                logger.info("服务端返回消息：{}", queryResultAfterDelete.getMessage());
            } else {
                logger.error("查询用户失败，错误码：{}", queryResultAfterDelete.getCode());
                logger.error("错误消息：{}", queryResultAfterDelete.getMessage());
            }

            logger.info(clientProxy.reportServiceStatus());
        } catch (Exception e) {
            logger.error("RPC调用过程中出现异常: {}", e.getMessage(), e);
        }

    }
}
