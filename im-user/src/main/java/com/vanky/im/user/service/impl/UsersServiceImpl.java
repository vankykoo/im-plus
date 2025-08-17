package com.vanky.im.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vanky.im.common.util.TokenUtil;
import com.vanky.im.user.entity.Users;
import com.vanky.im.user.model.response.UserLoginResponse;
import com.vanky.im.user.model.response.UserInfoDTO;
import com.vanky.im.user.model.response.UserStatusDTO;
import com.vanky.im.user.service.UsersService;
import com.vanky.im.user.mapper.UsersMapper;
import com.vanky.im.user.model.request.UserLoginRequest;
import com.vanky.im.user.model.request.UserRegisterRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
* @author vanky
* @description 针对表【users(用户信息表)】的数据库操作Service实现
* @createDate 2025-05-25 22:55:08
*/
@Service
public class UsersServiceImpl extends ServiceImpl<UsersMapper, Users> implements UsersService{

    @Autowired
    private TokenUtil tokenUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String register(UserRegisterRequest request) {
        // 检查用户ID是否已存在
        long count = this.lambdaQuery()
                .eq(Users::getUserId, request.getUserId())
                .count();
        if (count > 0) {
            throw new RuntimeException("用户ID已存在");
        }

        // 创建用户对象
        Users user = new Users();
        user.setUserId(request.getUserId());
        user.setUsername(request.getUsername());
        
        // 对密码进行加密
        String encryptedPassword = encryptPassword(request.getPassword());
        user.setPassword(encryptedPassword);
        
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        // 保存用户
        boolean success = this.save(user);
        if (!success) {
            throw new RuntimeException("注册失败");
        }

        // 返回用户信息（已脱敏）
        user.setPassword(null);
        return "注册成功，userId：" + user.getUserId();
    }
    
    @Override
    public UserLoginResponse login(UserLoginRequest request) {
        // 根据用户ID查询用户
        Users user = this.lambdaQuery()
                .eq(Users::getUserId, request.getUserId())
                .one();
                
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 检查用户状态
        if (user.getStatus() != 1) {
            throw new RuntimeException("用户状态异常");
        }
        
        // 验证密码
        String encryptedPassword = user.getPassword();
        boolean passwordMatch = encryptedPassword.equals(encryptPassword(request.getPassword()));
        
        if (!passwordMatch) {
            throw new RuntimeException("密码错误");
        }
        
        // 更新最后登录时间
        user.setLastLoginTime(LocalDateTime.now());
        this.updateById(user);
        
        // 生成token
        String token = tokenUtil.generateToken(user.getUserId());
        
        // 创建并返回用户登录响应（包含token）
        return new UserLoginResponse(user.getUserId(), user.getUsername(), token);
    }
    
    @Override
    public String logout(String userId) {
        // 根据用户ID查询用户
        Users user = this.lambdaQuery()
                .eq(Users::getUserId, userId)
                .one();
                
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        return "退出成功";
    }

    @Override
    public UserInfoDTO getUserInfoById(String userId) {
        Users user = this.lambdaQuery()
                .eq(Users::getUserId, userId)
                .one();

        if (user == null) {
            return null;
        }

        // 转换为DTO，体现数据传输对象的单一职责
        UserInfoDTO userInfo = new UserInfoDTO();
        userInfo.setUserId(user.getUserId());
        userInfo.setUsername(user.getUsername());
        userInfo.setStatus(user.getStatus());
        userInfo.setStatusDesc(getStatusDescription(user.getStatus()));
        userInfo.setCreateTime(user.getCreateTime() != null ?
            user.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : null);
        userInfo.setLastLoginTime(user.getLastLoginTime() != null ?
            user.getLastLoginTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : null);

        return userInfo;
    }

    @Override
    public UserStatusDTO getUserStatusById(String userId) {
        Users user = this.lambdaQuery()
                .eq(Users::getUserId, userId)
                .select(Users::getUserId, Users::getStatus, Users::getUpdateTime)
                .one();

        if (user == null) {
            return null;
        }

        UserStatusDTO statusDTO = new UserStatusDTO();
        statusDTO.setUserId(user.getUserId());
        statusDTO.setStatus(user.getStatus());
        statusDTO.setStatusDesc(getStatusDescription(user.getStatus()));
        statusDTO.setUpdateTime(user.getUpdateTime() != null ?
            user.getUpdateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() :
            System.currentTimeMillis());

        return statusDTO;
    }

    @Override
    public String getUsernameById(String userId) {
        Users user = this.lambdaQuery()
                .eq(Users::getUserId, userId)
                .select(Users::getUsername)
                .one();

        return user != null ? user.getUsername() : null;
    }



    /**
     * 获取状态描述
     * 遵循DRY原则，避免重复的状态描述逻辑
     */
    private String getStatusDescription(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 1: return "正常";
            case 0: return "禁用";
            case -1: return "删除";
            case 2: return "冻结";
            default: return "未知";
        }
    }

    /**
     * 密码加密
     */
    private String encryptPassword(String password) {
        return DigestUtils.md5DigestAsHex((password + "salt").getBytes(StandardCharsets.UTF_8));
    }
}