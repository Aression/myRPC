package common.service;

import common.pojo.User;

public interface UserService {
    User getUserById(Integer id);
    Integer insertUserId(User user);
    boolean deleteUserById(Integer id); // 添加删除方法
}
