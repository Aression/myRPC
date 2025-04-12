package common.service.impl;

import common.pojo.User;
import common.service.UserService;
import common.util.JsonFileUtil;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class UserServiceImpl implements UserService {
    // 使用 CopyOnWriteArrayList 存储用户数据，保证线程安全
    private static final List<User> userStore = new CopyOnWriteArrayList<>();

    static {
        // 初始化时从文件加载用户数据
        userStore.addAll(JsonFileUtil.readAllUsers());
        System.out.println("已从文件加载 " + userStore.size() + " 条用户数据");
    }

    @Override
    public User getUserById(Integer id) {
        System.out.println("客户端查询了id=" + id + "的用户");
        User user = userStore.stream()
                .filter(u -> u.getId().equals(id))
                .findFirst()
                .orElse(null);
        
        if (user == null) {
            System.out.println("未找到id=" + id + "的用户");
            return null;
        }
        System.out.println("找到用户: " + user);
        return user;
    }

    @Override
    public Integer insertUserId(User user) {
        if (user == null) {
            System.out.println("插入用户失败：用户对象为空");
            return -1;
        }
        
        // 检查用户是否已存在
        if (userStore.stream().anyMatch(u -> u.getId().equals(user.getId()))) {
            System.out.println("插入用户失败：用户ID已存在");
            return -1;
        }
        
        System.out.println("客户端插入数据：" + user);
        userStore.add(user);
        
        // 保存到文件
        JsonFileUtil.saveAllUsers(userStore);
        System.out.println("用户数据已保存到文件");
        
        return user.getId();
    }

    @Override
    public boolean deleteUserById(Integer id) {
        return userStore.removeIf(user -> user.getId().equals(id));
    }
}
