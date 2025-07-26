package com.vanky.im.message.service.impl;

import com.vanky.im.common.constant.SessionConstants;
import com.vanky.im.common.model.UserSession;
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
    private static final String CONVERSATION_LATEST_MSG_PREFIX = "conversation:latest:";
    private static final String USER_CONVERSATION_LIST_PREFIX = "user:conversation:list:";

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

    // ========== 新增方法实现：用户在线状态管理 ==========

    @Override
    public UserSession getUserSession(String userId) {
        try {
            String sessionKey = SessionConstants.getUserSessionKey(userId);
            Object sessionObj = redisTemplate.opsForValue().get(sessionKey);

            if (sessionObj instanceof UserSession) {
                UserSession userSession = (UserSession) sessionObj;
                log.debug("获取用户会话信息 - 用户ID: {}, 网关ID: {}", userId, userSession.getNodeId());
                return userSession;
            }

            log.debug("用户会话不存在 - 用户ID: {}", userId);
            return null;

        } catch (Exception e) {
            log.error("获取用户会话信息失败 - 用户ID: {}", userId, e);
            return null;
        }
    }

    @Override
    public boolean isUserOnline(String userId) {
        try {
            String sessionKey = SessionConstants.getUserSessionKey(userId);
            Boolean exists = redisTemplate.hasKey(sessionKey);
            boolean online = Boolean.TRUE.equals(exists);

            log.debug("检查用户在线状态 - 用户ID: {}, 在线: {}", userId, online);
            return online;

        } catch (Exception e) {
            log.error("检查用户在线状态失败 - 用户ID: {}", userId, e);
            return false;
        }
    }

    // ========== 新增方法实现：会话列表管理 ==========

    @Override
    public void updateConversationLatestMsg(String conversationId, String latestMsgId,
                                          String latestMsgContent, long latestMsgTime) {
        try {
            String key = CONVERSATION_LATEST_MSG_PREFIX + conversationId;

            // 构建最新消息信息的JSON字符串
            String latestMsgInfo = String.format(
                "{\"msgId\":\"%s\",\"content\":\"%s\",\"time\":%d}",
                latestMsgId, latestMsgContent, latestMsgTime
            );

            // 缓存最新消息信息，TTL 30天
            redisTemplate.opsForValue().set(key, latestMsgInfo, 30 * 24 * 60 * 60, TimeUnit.SECONDS);

            log.debug("更新会话最新消息 - 会话ID: {}, 消息ID: {}, 时间: {}",
                    conversationId, latestMsgId, latestMsgTime);

        } catch (Exception e) {
            log.error("更新会话最新消息失败 - 会话ID: {}, 消息ID: {}", conversationId, latestMsgId, e);
        }
    }

    @Override
    public void activateUserConversation(String userId, String conversationId, long timestamp) {
        try {
            String key = USER_CONVERSATION_LIST_PREFIX + userId;
            ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();

            // 使用时间戳作为score，实现按时间排序
            zSetOps.add(key, conversationId, timestamp);

            // 设置过期时间（30天）
            redisTemplate.expire(key, 30 * 24 * 60 * 60, TimeUnit.SECONDS);

            log.debug("激活用户会话 - 用户ID: {}, 会话ID: {}, 时间戳: {}", userId, conversationId, timestamp);

        } catch (Exception e) {
            log.error("激活用户会话失败 - 用户ID: {}, 会话ID: {}", userId, conversationId, e);
        }
    }
}