package com.vanky.im.message.service;

import java.util.List;

/**
 * 离线消息服务接口
 * 负责管理用户的离线消息队列和未读数
 */
public interface OfflineMessageService {

    /**
     * 添加离线消息到用户的离线消息队列
     * @param userId 用户ID
     * @param msgId 消息ID
     */
    void addOfflineMessage(String userId, String msgId);

    /**
     * 批量添加离线消息
     * @param userId 用户ID
     * @param msgIds 消息ID列表
     */
    void addOfflineMessages(String userId, List<String> msgIds);

    /**
     * 获取用户的离线消息列表
     * @param userId 用户ID
     * @param limit 获取数量限制，0表示获取全部
     * @return 离线消息ID列表
     */
    List<String> getOfflineMessages(String userId, int limit);

    /**
     * 清除用户的离线消息
     * @param userId 用户ID
     * @param msgIds 要清除的消息ID列表，如果为空则清除全部
     */
    void clearOfflineMessages(String userId, List<String> msgIds);

    /**
     * 获取用户的离线消息数量
     * @param userId 用户ID
     * @return 离线消息数量
     */
    long getOfflineMessageCount(String userId);

    /**
     * 原子性增加会话的未读数
     * @param userId 用户ID
     * @param conversationId 会话ID
     * @param increment 增加的数量，默认为1
     * @return 增加后的未读数
     */
    long incrementUnreadCount(String userId, String conversationId, int increment);

    /**
     * 原子性增加会话的未读数（默认增加1）
     * @param userId 用户ID
     * @param conversationId 会话ID
     * @return 增加后的未读数
     */
    long incrementUnreadCount(String userId, String conversationId);

    /**
     * 获取会话的未读数
     * @param userId 用户ID
     * @param conversationId 会话ID
     * @return 未读数
     */
    long getUnreadCount(String userId, String conversationId);

    /**
     * 重置会话的未读数
     * @param userId 用户ID
     * @param conversationId 会话ID
     */
    void resetUnreadCount(String userId, String conversationId);

    /**
     * 获取用户所有会话的总未读数
     * @param userId 用户ID
     * @return 总未读数
     */
    long getTotalUnreadCount(String userId);
}
