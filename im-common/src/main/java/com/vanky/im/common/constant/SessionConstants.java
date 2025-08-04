package com.vanky.im.common.constant;

/**
 * @author vanky
 * @create 2025/7/1
 * @description 会话常量类工具方法
 *
 * 更新记录 (2025-08-04 11:44:44 +08:00):
 * - 移除Redis key常量定义，已迁移到RedisKeyConstants类
 * - 保留工具方法，内部调用RedisKeyConstants获取key
 */
public class SessionConstants {

    /**
     * 获取用户会话的Redis键
     * @param userId 用户ID
     * @return Redis键
     */
    public static String getUserSessionKey(String userId) {
        return RedisKeyConstants.getUserSessionKey(userId);
    }


}
