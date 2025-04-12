package client;

import client.proxy.ClientProxy;
import common.pojo.User;
import common.service.UserService;

public class TestClient {
    public static void main(String[] args) {
        ClientProxy clientProxy = new ClientProxy();
        UserService proxy = clientProxy.getProxy(UserService.class);

        User newUser = User.builder().id(1).userName("514").sex(true).build();
        Integer id = proxy.insertUserId(newUser);
        if (id == -1) {
            System.out.println("插入用户失败");
        }else{
            System.out.println("向服务端插入的user id：" + id);
        }
        
        User user = proxy.getUserById(1);
        if (user == null) {
            System.out.println("查询用户失败");
            return;
        }
        System.out.println("从服务端获取的user：" + user);
        return;
    }
}
