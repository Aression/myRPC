package common.service.impl;

import common.pojo.User;
import common.service.UserService;

import java.util.Random;
import java.util.UUID;

public class UserServiceImpl implements UserService {

    @Override
    public User getUserById(Integer id) {
        System.out.println("客户端查询了id="+id+"的客户");
        Random random = new Random();
        return User.builder()
                .userName(UUID.randomUUID().toString()) // UUID.randomUUID 生成一个全局唯一的字符串作为随机用户名
                .id(id)
                .sex(random.nextBoolean())
                .build();
    }

    @Override
    public Integer insertUserId(User user) {
        System.out.println("客户端插入数据成功："+user.getUserName());
        return user.getId();
    }
}
