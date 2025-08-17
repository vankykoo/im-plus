package com.vanky.im.user.controller;

import com.vanky.im.common.model.ApiResponse;
import com.vanky.im.user.model.request.UserLoginRequest;
import com.vanky.im.user.model.request.UserRegisterRequest;
import com.vanky.im.user.model.response.UserLoginResponse;
import com.vanky.im.user.model.response.UserInfoDTO;
import com.vanky.im.user.model.response.UserStatusDTO;
import com.vanky.im.user.model.response.FriendshipDTO;
import com.vanky.im.user.service.UsersService;
import com.vanky.im.user.service.FriendshipService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * @author vanky
 * @date 2025/5/25
 * @description 用户控制器
 */
@RestController
@RequestMapping("/users")
@Slf4j
public class UserController {

    @Autowired
    private UsersService usersService;

    @Autowired
    private FriendshipService friendshipService;

    /**
     * 用户注册
     * @param request 注册请求
     * @return 注册结果
     */
    @PostMapping("/register")
    public ApiResponse<String> register(@Validated @RequestBody UserRegisterRequest request) {
        log.info("register param :{}", request);

        try {
            return ApiResponse.success(usersService.register(request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
    
    /**
     * 用户登录
     * @param request 登录请求
     * @return 登录结果，包含用户信息和token
     */
    @PostMapping("/login")
    public ApiResponse<UserLoginResponse> login(@Validated @RequestBody UserLoginRequest request) {
        log.info("login param :{}", request);
        try {
            return ApiResponse.success(usersService.login(request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
    
    /**
     * 用户退出登录 (GET方式)
     * @param userId 用户ID
     * @return 退出结果
     */
    @GetMapping("/logout/{userId}")
    public ApiResponse<String> logout(@PathVariable("userId") String userId) {
        try {
            return ApiResponse.success(usersService.logout(userId));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 根据用户ID获取用户信息
     * 为 Feign 客户端提供用户信息查询接口
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    @GetMapping("/info/{userId}")
    public ApiResponse<UserInfoDTO> getUserInfo(@PathVariable("userId") String userId) {
        try {
            log.debug("查询用户信息 - 用户ID: {}", userId);
            UserInfoDTO userInfo = usersService.getUserInfoById(userId);
            if (userInfo == null) {
                return ApiResponse.error("用户不存在");
            }
            return ApiResponse.success(userInfo);
        } catch (Exception e) {
            log.error("查询用户信息失败 - 用户ID: {}", userId, e);
            return ApiResponse.error("查询用户信息失败: " + e.getMessage());
        }
    }

    /**
     * 根据用户ID获取用户状态
     * 为 Feign 客户端提供用户状态查询接口
     *
     * @param userId 用户ID
     * @return 用户状态
     */
    @GetMapping("/status/{userId}")
    public ApiResponse<UserStatusDTO> getUserStatus(@PathVariable("userId") String userId) {
        try {
            log.debug("查询用户状态 - 用户ID: {}", userId);
            UserStatusDTO userStatus = usersService.getUserStatusById(userId);
            if (userStatus == null) {
                return ApiResponse.error("用户不存在");
            }
            return ApiResponse.success(userStatus);
        } catch (Exception e) {
            log.error("查询用户状态失败 - 用户ID: {}", userId, e);
            return ApiResponse.error("查询用户状态失败: " + e.getMessage());
        }
    }

    /**
     * 根据用户ID获取用户昵称
     * 为 Feign 客户端提供简化的用户名查询接口
     *
     * @param userId 用户ID
     * @return 用户昵称
     */
    @GetMapping("/username/{userId}")
    public ApiResponse<String> getUsername(@PathVariable("userId") String userId) {
        try {
            log.debug("查询用户昵称 - 用户ID: {}", userId);
            String username = usersService.getUsernameById(userId);
            if (username == null) {
                return ApiResponse.error("用户不存在");
            }
            return ApiResponse.success(username);
        } catch (Exception e) {
            log.error("查询用户昵称失败 - 用户ID: {}", userId, e);
            return ApiResponse.error("查询用户昵称失败: " + e.getMessage());
        }
    }



    /**
     * 查询两个用户之间的好友关系
     * 为 Feign 客户端提供好友关系查询接口
     *
     * @param userId1 用户1的ID
     * @param userId2 用户2的ID
     * @return 好友关系信息
     */
    @GetMapping("/friendship")
    public ApiResponse<FriendshipDTO> getFriendship(
            @RequestParam("userId1") String userId1,
            @RequestParam("userId2") String userId2) {
        try {
            log.debug("查询好友关系 - 用户1: {}, 用户2: {}", userId1, userId2);
            FriendshipDTO friendship = friendshipService.getFriendshipInfo(userId1, userId2);
            return ApiResponse.success(friendship);
        } catch (Exception e) {
            log.error("查询好友关系失败 - 用户1: {}, 用户2: {}", userId1, userId2, e);
            return ApiResponse.error("查询好友关系失败: " + e.getMessage());
        }
    }

    /**
     * 检查两个用户是否为好友
     * 为 Feign 客户端提供简化的好友关系检查接口
     *
     * @param userId1 用户1的ID
     * @param userId2 用户2的ID
     * @return 是否为好友
     */
    @GetMapping("/friendship/check")
    public ApiResponse<Boolean> checkFriendship(
            @RequestParam("userId1") String userId1,
            @RequestParam("userId2") String userId2) {
        try {
            log.debug("检查好友关系 - 用户1: {}, 用户2: {}", userId1, userId2);
            boolean areFriends = friendshipService.areFriends(userId1, userId2);
            return ApiResponse.success(areFriends);
        } catch (Exception e) {
            log.error("检查好友关系失败 - 用户1: {}, 用户2: {}", userId1, userId2, e);
            return ApiResponse.error("检查好友关系失败: " + e.getMessage());
        }
    }
}