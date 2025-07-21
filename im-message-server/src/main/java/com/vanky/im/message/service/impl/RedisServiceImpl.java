package com.vanky.im.message.service.impl;

import com.vanky.im.message.service.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis服务实现类
 */
@Slf4j
@Service
public class RedisServiceImpl implements RedisService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String CONVERSATION_SEQ_PREFIX = "conversation:seq:";
    private static final String MESSAGE_CACHE_PREFIX = "msg:";
    private static final String USER_MSG_LIST_PREFIX = "user:msg:list:";

    @Override
    public Long generateSeq(String conversationId) {
        String key = CONVERSATION_SEQ_PREFIX + conversationId;
        try {
            return redisTemplate.opsForValue().increment(key);
        } catch (Exception e) {
            log.error("生成会话序列号失败, conversationId: {}", conversationId, e);
            throw new RuntimeException("生成会话序列号失败", e);
        }
    }

    @Override
    public void cacheMessage(String msgId, String messageJson, long ttlSeconds) {
        String key = MESSAGE_CACHE_PREFIX + msgId;
        try {
            redisTemplate.opsForValue().set(key, messageJson, ttlSeconds, TimeUnit.SECONDS);
            log.debug("缓存消息成功, msgId: {}, ttl: {}s", msgId, ttlSeconds);
        } catch (Exception e) {
            log.error("缓存消息失败, msgId: {}", msgId, e);
            throw new RuntimeException("缓存消息失败", e);
        }
    }

    @Override
    public String getCachedMessage(String msgId) {
        String key = MESSAGE_CACHE_PREFIX + msgId;
        try {
            Object value = redisTemplate.opsForValue().get(key);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.error("获取缓存消息失败, msgId: {}", msgId, e);
            return null;
        }
    }

    @Override
    public void addToUserMsgList(String userId, String msgId, Long seq, int maxSize) {
        String key = USER_MSG_LIST_PREFIX + userId;
        try {
            ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
            
            // 添加新消息
            zSetOps.add(key, msgId, seq.doubleValue());
            
            // 保留最近的N条消息
            Long size = zSetOps.zCard(key);
            if (size != null && size > maxSize) {
                // 删除最旧的消息
                long removeCount = size - maxSize;
                zSetOps.removeRange(key, 0, removeCount - 1);
            }
            
            log.debug("添加消息到用户消息链成功, userId: {}, msgId: {}, seq: {}", userId, msgId, seq);
        } catch (Exception e) {
            log.error("添加消息到用户消息链失败, userId: {}, msgId: {}", userId, msgId, e);
            throw new RuntimeException("添加消息到用户消息链失败", e);
        }
    }

    @Override
    public Set<String> getUserMsgList(String userId, long start, long end) {
        String key = USER_MSG_LIST_PREFIX + userId;
        try {
            ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
            Set<Object> result = zSetOps.reverseRange(key, start, end);
            return result != null ? (Set<String>) (Set<?>) result : Set.of();
        } catch (Exception e) {
            log.error("获取用户消息链失败, userId: {}", userId, e);
            return Set.of();
        }
    }

    @Override
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
            log.debug("删除缓存成功, key: {}", key);
        } catch (Exception e) {
            log.error("删除缓存失败, key: {}", key, e);
        }
    }
}