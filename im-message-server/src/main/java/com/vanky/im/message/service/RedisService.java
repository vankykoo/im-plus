package com.vanky.im.message.service;

import com.vanky.im.common.model.UserSession;

import java.util.List;
import java.util.Set;

/**
 * Redis服务接口
 */
public interface RedisService {

    /**
     * 原子生成会话序列号
     * @param conversationId 会话ID
     * @return 序列号
     */
    Long generateSeq(String conversationId);

    /**
     * 原子生成用户级全局序列号
     * @param userId 用户ID
     * @return 用户级全局序列号
     */
    Long generateUserGlobalSeq(String userId);

    /**
     * 缓存消息
     * @param msgId 消息ID
     * @param messageJson 消息JSON
     * @param ttlSeconds TTL秒数
     */
    void cacheMessage(String msgId, String messageJson, long ttlSeconds);

    /**
     * 获取缓存的消息
     * @param msgId 消息ID
     * @return 消息JSON
     */
    String getCachedMessage(String msgId);

    /**
     * 添加消息到用户消息链缓存
     * @param userId 用户ID
     * @param msgId 消息ID
     * @param seq 序列号
     * @param maxSize 最大保留条数
     */
    void addToUserMsgList(String userId, String msgId, Long seq, int maxSize);

    /**
     * 获取用户消息链
     * @param userId 用户ID
     * @param start 开始位置
     * @param end 结束位置
     * @return 消息ID列表
     */
    Set<String> getUserMsgList(String userId, long start, long end);

    /**
     * 删除缓存
     * @param key 缓存键
     */
    void delete(String key);

    // ========== 新增方法：用户在线状态管理 ==========

    /**
     * 获取用户在线状态
     * @param userId 用户ID
     * @return 用户会话信息，如果用户离线则返回null
     */
    UserSession getUserSession(String userId);

    /**
     * 检查用户是否在线
     * @param userId 用户ID
     * @return true-在线，false-离线
     */
    boolean isUserOnline(String userId);

    // ========== 新增方法：会话列表管理 ==========

    /**
     * 更新会话的最新消息信息
     * @param conversationId 会话ID
     * @param latestMsgId 最新消息ID
     * @param latestMsgContent 最新消息内容摘要
     * @param latestMsgTime 最新消息时间戳
     */
    void updateConversationLatestMsg(String conversationId, String latestMsgId,
                                   String latestMsgContent, long latestMsgTime);

    /**
     * 激活用户的会话（更新会话列表排序）
     * @param userId 用户ID
     * @param conversationId 会话ID
     * @param timestamp 激活时间戳
     */
    void activateUserConversation(String userId, String conversationId, long timestamp);

    // ========== 新增方法：离线消息同步支持 ==========

    /**
     * 获取用户的最大全局序列号
     * 用于离线消息同步时判断是否有新消息
     * @param userId 用户ID
     * @return 用户最大全局序列号，如果用户无消息则返回0
     */
    Long getUserMaxGlobalSeq(String userId);
}