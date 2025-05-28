package com.vanky.im.user.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author vanky
 * @date 2025/5/26
 * @description 用户登录响应对象
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserLoginResponse {
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 登录token，用于连接netty服务器时验证
     */
    private String token;
} 