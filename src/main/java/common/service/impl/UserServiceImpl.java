package common.service.impl;

import common.pojo.User;
import common.result.Result;
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
    public Result<User> getUserById(Integer id) {
        System.out.println("客户端查询了id=" + id + "的用户");
        
        if (id == null) {
            return Result.fail(400, "用户ID不能为空");
        }
        
        User user = userStore.stream()
                .filter(u -> u.getId().equals(id))
                .findFirst()
                .orElse(null);
        
        if (user == null) {
            System.out.println("未找到id=" + id + "的用户");
            return Result.fail(404, "未找到指定用户");
        }
        
        System.out.println("找到用户: " + user);
        return Result.success(user, "查询用户成功");
    }

    @Override
    public Result<Integer> insertUserId(User user) {
        if (user == null) {
            System.out.println("插入用户失败：用户对象为空");
            return Result.fail(400, "用户对象不能为空");
        }
        
        if (user.getId() == null) {
            System.out.println("插入用户失败：用户ID不能为空");
            return Result.fail(400, "用户ID不能为空");
        }
        
        // 检查用户是否已存在
        if (userStore.stream().anyMatch(u -> u.getId().equals(user.getId()))) {
            System.out.println("插入用户失败：用户ID已存在");
            return Result.fail(409, "用户ID已存在");
        }
        
        System.out.println("客户端插入数据：" + user);
        userStore.add(user);
        
        // 保存到文件
        JsonFileUtil.saveAllUsers(userStore);
        System.out.println("用户数据已保存到文件");
        
        return Result.success(user.getId(), "用户添加成功");
    }

    @Override
    public Result<Boolean> deleteUserById(Integer id) {
        if (id == null) {
            return Result.fail(400, "用户ID不能为空");
        }
        
        // 检查用户是否存在
        boolean exists = userStore.stream().anyMatch(u -> u.getId().equals(id));
        if (!exists) {
            return Result.fail(404, "用户不存在");
        }
        
        boolean success = userStore.removeIf(user -> user.getId().equals(id));
        
        if (success) {
            // 保存到文件
            JsonFileUtil.saveAllUsers(userStore);
            return Result.success(true, "用户删除成功");
        } else {
            return Result.fail(500, "用户删除失败");
        }
    }
}
