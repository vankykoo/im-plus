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
     * 直接从数据库查询，用于离线消息同步时判断是否有新消息
     * @param userId 用户ID
     * @return 用户最大全局序列号，如果用户无消息则返回0
     */
    Long getUserMaxGlobalSeq(String userId);

    // ========== 新增方法：消息已读功能支持 ==========

    /**
     * 获取用户在指定会话的最后已读序列号
     * @param userId 用户ID
     * @param conversationId 会话ID
     * @return 最后已读序列号，如果没有记录则返回null
     */
    Long getUserLastReadSeq(String userId, String conversationId);

    /**
     * 设置用户在指定会话的最后已读序列号
     * @param userId 用户ID
     * @param conversationId 会话ID
     * @param lastReadSeq 最后已读序列号
     */
    void setUserLastReadSeq(String userId, String conversationId, long lastReadSeq);

    /**
     * 原子增加群聊消息的已读计数
     * @param msgId 消息ID
     * @return 增加后的已读数
     */
    long incrementGroupReadCount(String msgId);

    /**
     * 获取群聊消息的已读计数
     * @param msgId 消息ID
     * @return 已读数
     */
    int getGroupReadCount(String msgId);

    /**
     * 添加用户到群聊消息的已读用户列表（仅小群使用）
     * @param msgId 消息ID
     * @param userId 用户ID
     */
    void addGroupReadUser(String msgId, String userId);

    /**
     * 获取群聊消息的已读用户列表（仅小群使用）
     * @param msgId 消息ID
     * @return 已读用户ID列表
     */
    List<String> getGroupReadUsers(String msgId);

   /**
    * 获取用户会话
    * @param userId 用户ID
    * @return 用户会话
    */
   UserSession getUserSession(String userId);
}