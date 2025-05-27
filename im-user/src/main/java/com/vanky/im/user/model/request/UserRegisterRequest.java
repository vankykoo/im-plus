package com.vanky.im.user.model.request;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @author vanky
 * @date 2025/5/25
 * @description 用户注册请求对象
 */
@Data
public class UserRegisterRequest {
    
    @NotBlank(message = "用户ID不能为空")
    private String userId;
    
    @NotBlank(message = "用户名不能为空")
    private String username;
    
    @NotBlank(message = "密码不能为空")
    private String password;
} 