package common.service;

import common.pojo.User;
import common.result.Result;

import java.util.concurrent.CompletableFuture;

/**
 * 用户服务接口 - 异步版本
 * 所有方法返回CompletableFuture以支持纯异步调用
 */
public interface UserService {
    /**
     * 根据ID获取用户信息（异步）
     * 
     * @param id 用户ID
     * @return CompletableFuture包装的用户信息结果
     */
    CompletableFuture<Result<User>> getUserById(Long id);

    /**
     * 插入用户并返回用户ID（异步）
     * 
     * @param user 要插入的用户
     * @return CompletableFuture包装的插入结果，成功返回用户ID
     */
    CompletableFuture<Result<Long>> insertUser(User user);

    /**
     * 根据ID删除用户（异步）
     * 
     * @param id 用户ID
     * @return CompletableFuture包装的删除结果
     */
    CompletableFuture<Result<Boolean>> deleteUserById(Long id);

    /**
     * 更新用户信息（异步）
     * 
     * @param user 要更新的用户
     * @return CompletableFuture包装的更新结果
     */
    CompletableFuture<Result<Boolean>> updateUser(User user);
}
