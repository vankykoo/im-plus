package com.vanky.im.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息表
 * @TableName users
 */
@TableName(value ="users")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Users {
    /**
     * 用户唯一ID，自增主键
     */
    @TableId(type = IdType.AUTO)
    @TableField(value = "id")
    private Long id;

    /**
     * 用户自定义ID，用于登录和显示
     */
    @TableField(value = "user_id")
    private String userId;

    /**
     * 用户昵称
     */
    @TableField(value = "username")
    private String username;

    /**
     * 加密后的用户密码
     */
    @TableField(value = "password")
    private String password;

    /**
     * 用户状态
     */
    @TableField(value = "status")
    private Integer status;

    /**
     * 最后登录时间
     */
    @TableField(value = "last_login_time")
    private LocalDateTime lastLoginTime;

    /**
     * 记录创建时间
     */
    @TableField(value = "create_time")
    private LocalDateTime createTime;

    /**
     * 记录更新时间
     */
    @TableField(value = "update_time")
    private LocalDateTime updateTime;
}