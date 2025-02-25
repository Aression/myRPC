package common.service;

import common.pojo.User;

public interface UserService {
    User getUserById(Integer id);
    Integer insertUserId(User user);
}
