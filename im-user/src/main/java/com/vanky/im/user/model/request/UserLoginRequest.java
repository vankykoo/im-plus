package com.vanky.im.user.model.request;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * @author vanky
 * @date 2025/5/26
 * @description 用户登录请求对象
 */
@Data
public class UserLoginRequest {
    
    @NotBlank(message = "用户ID不能为空")
    private String userId;
    
    @NotBlank(message = "密码不能为空")
    private String password;
} 