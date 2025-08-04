package com.vanky.im.message.service.impl;

import com.vanky.im.common.constant.RedisKeyConstants;
import com.vanky.im.common.constant.SessionConstants;
import com.vanky.im.common.model.UserSession;
import com.vanky.im.message.constant.MessageConstants;
import com.vanky.im.message.service.ConversationMsgListService;
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

    @Autowired
    private ConversationMsgListService conversationMsgListService;

    @Override
    public Long generateSeq(String conversationId) {
        // {{CHENGQI:
        // Action: Modified; Timestamp: 2025-08-04 20:52:36 +08:00; Reason: 修复seq存储类型问题，统一使用整型存储而不是字符串;
        // }}
        // {{START MODIFICATIONS}}
        String key = RedisKeyConstants.getConversationSeqKey(conversationId);
        try {
            // 1. 检查Redis中是否已存在该会话的seq
            Object currentSeqObj = redisTemplate.opsForValue().get(key);

            if (currentSeqObj != null) {
                // Redis中存在，直接递增
                return redisTemplate.opsForValue().increment(key);
            }

            // 2. Redis中不存在，查询数据库获取最大seq
            log.debug("Redis中不存在会话seq，查询数据库 - 会话ID: {}", conversationId);
            Long maxSeqFromDb = conversationMsgListService.getMaxSeq(conversationId);

            // 3. 初始化Redis中的seq值（存储为整型，不是字符串）
            Long nextSeq = maxSeqFromDb + 1;
            redisTemplate.opsForValue().set(key, nextSeq);

            log.info("初始化会话seq - 会话ID: {}, 数据库最大seq: {}, 生成seq: {}",
                    conversationId, maxSeqFromDb, nextSeq);

            return nextSeq;

        } catch (Exception e) {
            log.error("生成会话序列号失败, conversationId: {}", conversationId, e);
            throw new RuntimeException("生成会话序列号失败", e);
        }
        // {{END MODIFICATIONS}}
    }

    @Override
    public Long generateUserGlobalSeq(String userId) {
        // {{CHENGQI:
        // Action: Added; Timestamp: 2025-07-28 23:08:31 +08:00; Reason: 实现用户级全局序列号生成;
        // }}
        // {{START MODIFICATIONS}}
        String key = RedisKeyConstants.getUserGlobalSeqKey(userId);
        try {
            return redisTemplate.opsForValue().increment(key);
        } catch (Exception e) {
            log.error("生成用户全局序列号失败, userId: {}", userId, e);
            throw new RuntimeException("生成用户全局序列号失败", e);
        }
        // {{END MODIFICATIONS}}
    }

    @Override
    public void cacheMessage(String msgId, String messageJson, long ttlSeconds) {
        String key = RedisKeyConstants.getMessageCacheKey(msgId);
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
        String key = RedisKeyConstants.getMessageCacheKey(msgId);
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
        String key = RedisKeyConstants.getUserMsgListKey(userId);
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
        String key = RedisKeyConstants.getUserMsgListKey(userId);
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
            String key = RedisKeyConstants.CONVERSATION_LATEST_MSG_PREFIX + conversationId;

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
            String key = RedisKeyConstants.USER_CONVERSATION_LIST_PREFIX + userId;
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

    // ========== 新增方法实现：离线消息同步支持 ==========

    @Override
    public Long getUserMaxGlobalSeq(String userId) {
        // {{CHENGQI:
        // Action: Added; Timestamp: 2025-07-29 14:15:29 +08:00; Reason: 实现获取用户最大全局序列号方法，支持离线消息同步功能;
        // }}
        // {{START MODIFICATIONS}}
        try {
            String key = RedisKeyConstants.getUserGlobalSeqKey(userId);
            Object value = redisTemplate.opsForValue().get(key);

            if (value instanceof Long) {
                Long maxSeq = (Long) value;
                log.debug("获取用户最大全局序列号 - 用户ID: {}, 最大序列号: {}", userId, maxSeq);
                return maxSeq;
            } else if (value instanceof Integer) {
                Long maxSeq = ((Integer) value).longValue();
                log.debug("获取用户最大全局序列号 - 用户ID: {}, 最大序列号: {}", userId, maxSeq);
                return maxSeq;
            }

            log.debug("用户最大全局序列号不存在，返回默认值0 - 用户ID: {}", userId);
            return 0L;

        } catch (Exception e) {
            log.error("获取用户最大全局序列号失败 - 用户ID: {}", userId, e);
            return 0L;
        }
        // {{END MODIFICATIONS}}
    }
}