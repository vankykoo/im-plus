package com.vanky.im.user.controller;

import com.vanky.im.common.model.ApiResponse;
import com.vanky.im.user.model.request.UserLoginRequest;
import com.vanky.im.user.model.request.UserRegisterRequest;
import com.vanky.im.user.service.UsersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * @author vanky
 * @date 2025/5/25
 * @description 用户控制器
 */
@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UsersService usersService;

    /**
     * 用户注册
     * @param request 注册请求
     * @return 注册结果
     */
    @PostMapping("/register")
    public ApiResponse<String> register(@Validated @RequestBody UserRegisterRequest request) {
        try {
            return ApiResponse.success(usersService.register(request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
    
    /**
     * 用户登录
     * @param request 登录请求
     * @return 登录结果
     */
    @PostMapping("/login")
    public ApiResponse<String> login(@Validated @RequestBody UserLoginRequest request) {
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
} 