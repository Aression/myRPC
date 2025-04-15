package common.service.impl;

import common.pojo.User;
import common.result.Result;
import common.service.UserService;
import common.util.JsonFileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;

public class UserServiceImpl implements UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);
    private final Map<Integer, User> userStore = new ConcurrentHashMap<>();

    public UserServiceImpl() {
        List<User> users = JsonFileUtil.readAllUsers();
        users.forEach(user -> userStore.put(user.getId(), user));
        logger.info("已从文件加载 {} 条用户数据", userStore.size());
    }

    @Override
    public Result<User> getUserById(Integer id) {
        logger.info("客户端查询了id={}的用户", id);
        User user = userStore.get(id);
        if (user == null) {
            logger.info("未找到id={}的用户", id);
            return Result.fail(404, "用户不存在");
        }
        logger.info("找到用户: {}", user);
        return Result.success(user);
    }

    @Override
    public Result<Integer> insertUser(User user) {
        if (user == null) {
            logger.warn("插入用户失败：用户对象为空");
            return Result.fail(400, "用户对象为空");
        }
        if (user.getId() == null) {
            logger.warn("插入用户失败：用户ID不能为空");
            return Result.fail(400, "用户ID不能为空");
        }
        if (userStore.containsKey(user.getId())) {
            logger.warn("插入用户失败：用户ID已存在");
            return Result.fail(409, "用户ID已存在");
        }
        logger.info("客户端插入数据：{}", user);
        userStore.put(user.getId(), user);
        JsonFileUtil.saveAllUsers(new ArrayList<>(userStore.values()));
        logger.info("用户数据已保存到文件");
        return Result.success(user.getId());
    }

    @Override
    public Result<Boolean> deleteUserById(Integer id) {
        if (id == null) {
            return Result.fail(400, "用户ID不能为空");
        }
        
        // 检查用户是否存在
        boolean exists = userStore.containsKey(id);
        if (!exists) {
            return Result.fail(404, "用户不存在");
        }
        
        boolean success = userStore.remove(id) != null;
        
        if (success) {
            // 保存到文件
            JsonFileUtil.saveAllUsers(new ArrayList<>(userStore.values()));
            return Result.success(true, "用户删除成功");
        } else {
            return Result.fail(500, "用户删除失败");
        }
    }
}
