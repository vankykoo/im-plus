package com.vanky.im.user.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息数据传输对象
 * 遵循单一职责原则，专门负责用户基本信息的传输
 * 
 * @author vanky
 * @since 2025-08-14
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoDTO {
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 用户名/昵称
     */
    private String username;

    /**
     * 用户状态：1-正常，0-禁用，-1-删除
     */
    private Integer status;
    
    /**
     * 用户状态描述
     */
    private String statusDesc;
    
    /**
     * 创建时间戳
     */
    private Long createTime;
    
    /**
     * 最后登录时间戳
     */
    private Long lastLoginTime;
    
    /**
     * 构造方法 - 基本信息
     */
    public UserInfoDTO(String userId, String username) {
        this.userId = userId;
        this.username = username;
        this.status = 1; // 默认正常状态
        this.statusDesc = "正常";
    }

    /**
     * 构造方法 - 包含状态信息
     */
    public UserInfoDTO(String userId, String username, Integer status, String statusDesc) {
        this.userId = userId;
        this.username = username;
        this.status = status;
        this.statusDesc = statusDesc;
    }
}
