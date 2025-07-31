package com.vanky.im.message.service;

/**
 * 用户信息服务接口
 * 负责获取用户基本信息（跨模块调用）
 * 
 * @author vanky
 * @since 2025-07-28
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-07-28 22:55:09 +08:00; Reason: 创建用户信息服务接口，用于跨模块获取用户基本信息;
// }}
// {{START MODIFICATIONS}}
public interface UserInfoService {
    
    /**
     * 根据用户ID获取用户昵称
     * 
     * @param userId 用户ID
     * @return 用户昵称，如果用户不存在则返回默认值
     */
    String getUsernameById(Long userId);
    
    /**
     * 根据用户ID获取用户头像
     * 
     * @param userId 用户ID
     * @return 用户头像URL，如果用户不存在则返回默认头像
     */
    String getUserAvatarById(Long userId);
}
// {{END MODIFICATIONS}}
