package com.vanky.im.message.service.impl;

import com.vanky.im.message.service.UserInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户信息服务实现类
 * 
 * @author vanky
 * @since 2025-07-28
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-07-28 22:55:09 +08:00; Reason: 创建用户信息服务实现类，暂时使用占位符实现;
// }}
// {{START MODIFICATIONS}}
@Slf4j
@Service
public class UserInfoServiceImpl implements UserInfoService {
    
    @Override
    public String getUsernameById(Long userId) {
        // TODO: 实现跨模块调用im-user服务获取用户昵称
        // 暂时返回占位符
        if (userId == null) {
            return "未知用户";
        }
        
        try {
            // 这里应该调用im-user模块的用户服务
            // 可以通过以下方式实现：
            // 1. RestTemplate调用HTTP接口
            // 2. Feign客户端调用
            // 3. 直接注入用户服务（如果在同一个应用中）
            
            log.debug("获取用户昵称 - 用户ID: {}", userId);
            return "用户" + userId; // 占位符实现
            
        } catch (Exception e) {
            log.warn("获取用户昵称失败 - 用户ID: {}", userId, e);
            return "用户" + userId;
        }
    }
    
    @Override
    public String getUserAvatarById(Long userId) {
        // TODO: 实现跨模块调用im-user服务获取用户头像
        // 暂时返回默认头像
        if (userId == null) {
            return "/default/user_avatar.png";
        }
        
        try {
            log.debug("获取用户头像 - 用户ID: {}", userId);
            return "/default/user_avatar.png"; // 占位符实现
            
        } catch (Exception e) {
            log.warn("获取用户头像失败 - 用户ID: {}", userId, e);
            return "/default/user_avatar.png";
        }
    }
}
// {{END MODIFICATIONS}}
