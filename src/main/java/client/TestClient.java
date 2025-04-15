package client;

import client.proxy.ClientProxy;
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
            ClientProxy clientProxy = new ClientProxy();
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

            // 测试删除用户
            Result<Boolean> deleteResult = userService.deleteUserById(1);
            if (deleteResult.isSuccess()) {
                logger.info("删除用户成功：{}", deleteResult.getMessage());
            } else {
                logger.error("删除用户失败，错误码：{}", deleteResult.getCode());
                logger.error("错误消息：{}", deleteResult.getMessage());
            }
        } catch (Exception e) {
            logger.error("RPC调用过程中出现异常: {}", e.getMessage(), e);
        }
    }
}
