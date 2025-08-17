package com.vanky.im.user.service;

import com.vanky.im.user.entity.Users;
import com.baomidou.mybatisplus.extension.service.IService;
import com.vanky.im.user.model.request.UserLoginRequest;
import com.vanky.im.user.model.request.UserRegisterRequest;
import com.vanky.im.user.model.response.UserLoginResponse;
import com.vanky.im.user.model.response.UserInfoDTO;
import com.vanky.im.user.model.response.UserStatusDTO;

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

    /**
     * 根据用户ID获取用户信息
     * 遵循开放封闭原则，扩展功能而不修改现有代码
     *
     * @param userId 用户ID
     * @return 用户信息DTO，如果用户不存在则返回null
     */
    UserInfoDTO getUserInfoById(String userId);

    /**
     * 根据用户ID获取用户状态
     *
     * @param userId 用户ID
     * @return 用户状态DTO，如果用户不存在则返回null
     */
    UserStatusDTO getUserStatusById(String userId);

    /**
     * 根据用户ID获取用户昵称
     * 为消息服务提供简化的用户名查询接口
     *
     * @param userId 用户ID
     * @return 用户昵称，如果用户不存在则返回null
     */
    String getUsernameById(String userId);
}
