package client;

import client.proxy.ClientProxy;
import common.pojo.User;
import common.result.Result;
import common.service.UserService;

public class TestClient {
    public static void main(String[] args) {
        ClientProxy clientProxy = new ClientProxy();
        UserService proxy = clientProxy.getProxy(UserService.class);

        try {
            // 测试插入用户 - 使用一个新的用户ID
            User newUser = User.builder().id(100).userName("测试用户").sex(true).build();
            Result<Integer> insertResult = proxy.insertUserId(newUser);
            
            if (insertResult.isSuccess()) {
                System.out.println("向服务端插入用户成功，ID: " + insertResult.getData());
                System.out.println("服务端返回消息：" + insertResult.getMessage());
            } else {
                System.out.println("插入用户失败，错误码：" + insertResult.getCode());
                System.out.println("错误消息：" + insertResult.getMessage());
            }
            
            // 测试查询用户
            Result<User> queryResult = proxy.getUserById(100);
            
            if (queryResult.isSuccess()) {
                User user = queryResult.getData();
                System.out.println("从服务端获取的user: " + user);
                System.out.println("服务端返回消息：" + queryResult.getMessage());
            } else {
                System.out.println("查询用户失败，错误码：" + queryResult.getCode());
                System.out.println("错误消息：" + queryResult.getMessage());
            }
            
            // 测试删除用户
            Result<Boolean> deleteResult = proxy.deleteUserById(100);
            
            if (deleteResult.isSuccess()) {
                System.out.println("删除用户成功：" + deleteResult.getMessage());
            } else {
                System.out.println("删除用户失败，错误码：" + deleteResult.getCode());
                System.out.println("错误消息：" + deleteResult.getMessage());
            }
        } catch (Exception e) {
            System.out.println("RPC调用过程中出现异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
