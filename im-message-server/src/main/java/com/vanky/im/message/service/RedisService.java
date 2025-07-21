package com.vanky.im.message.service;

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
}