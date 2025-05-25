package com.vanky.im.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户信息表
 * @TableName users
 */
@TableName(value ="users")
@Data
public class Users {
    /**
     * 用户唯一ID，自增主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户自定义ID，用于登录和显示
     */
    private String userId;

    /**
     * 用户昵称
     */
    private String username;

    /**
     * 加密后的用户密码
     */
    private String password;

    /**
     * 用户状态
     */
    private Integer status;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginTime;

    /**
     * 记录创建时间
     */
    private LocalDateTime createTime;

    /**
     * 记录更新时间
     */
    private LocalDateTime updateTime;
}