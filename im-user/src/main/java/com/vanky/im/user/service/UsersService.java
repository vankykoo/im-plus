package com.vanky.im.user.service;

import com.vanky.im.user.entity.Users;
import com.baomidou.mybatisplus.extension.service.IService;
import com.vanky.im.user.model.request.UserLoginRequest;
import com.vanky.im.user.model.request.UserRegisterRequest;
import com.vanky.im.user.model.response.UserLoginResponse;

/**
* @author vanky
* @description 针对表【users(用户信息表)】的数据库操作Service
* @createDate 2025-05-25 22:55:08
*/
public interface UsersService extends IService<Users> {

    /**
     * 用户注册
     * @param request 注册请求
     * @return 注册成功的用户信息(已脱敏)
     */
    String register(UserRegisterRequest request);
    
    /**
     * 用户登录
     * @param request 登录请求
     * @return 登录成功的用户信息和token
     */
    UserLoginResponse login(UserLoginRequest request);
    
    /**
     * 用户退出登录
     * @param userId 用户ID
     * @return 退出结果
     */
    String logout(String userId);
}
