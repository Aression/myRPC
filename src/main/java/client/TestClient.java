package client;

import client.proxy.ClientProxy;
import common.pojo.User;
import common.service.UserService;

public class TestClient {
    public static void main(String[] args) {
        ClientProxy clientProxy = new ClientProxy();
//        ClientProxy clientProxy = new ClientProxy("127.0.0.1", 9999,0);
        UserService proxy = clientProxy.getProxy(UserService.class);

        User user = proxy.getUserById(1);
        System.out.println("从服务端获取的user："+user.toString());

        User newUser = User.builder().id(114).userName("514").sex(true).build();
        Integer id = proxy.insertUserId(newUser);
        System.out.println("向服务端插入的use id："+id);
    }
}
