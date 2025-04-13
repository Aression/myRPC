package common.service;

import common.pojo.User;
import common.result.Result;

public interface UserService {
    /**
     * 根据ID获取用户信息
     * @param id 用户ID
     * @return 用户信息结果
     */
    Result<User> getUserById(Integer id);
    
    /**
     * 插入用户并返回用户ID
     * @param user 要插入的用户
     * @return 插入结果，成功返回用户ID
     */
    Result<Integer> insertUserId(User user);
    
    /**
     * 根据ID删除用户
     * @param id 用户ID
     * @return 删除结果
     */
    Result<Boolean> deleteUserById(Integer id);
}
