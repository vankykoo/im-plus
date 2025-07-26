package com.vanky.im.message.service.impl;

import com.vanky.im.message.service.OfflineMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 离线消息服务实现类
 * 使用Redis管理用户的离线消息队列和未读数
 */
@Slf4j
@Service
public class OfflineMessageServiceImpl implements OfflineMessageService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // Redis key前缀
    private static final String OFFLINE_MSG_PREFIX = "user:offline_msg:";
    private static final String UNREAD_COUNT_PREFIX = "user:conversation:unread:";
    
    // 离线消息队列最大长度
    private static final int MAX_OFFLINE_MSG_SIZE = 10000;
    // 离线消息TTL（7天）
    private static final long OFFLINE_MSG_TTL_SECONDS = 7 * 24 * 60 * 60;

    @Override
    public void addOfflineMessage(String userId, String msgId) {
        try {
            String key = OFFLINE_MSG_PREFIX + userId;
            
            // 使用LPUSH添加到队列头部（最新消息在前）
            redisTemplate.opsForList().leftPush(key, msgId);
            
            // 限制队列长度，删除最旧的消息
            redisTemplate.opsForList().trim(key, 0, MAX_OFFLINE_MSG_SIZE - 1);
            
            // 设置过期时间
            redisTemplate.expire(key, OFFLINE_MSG_TTL_SECONDS, TimeUnit.SECONDS);
            
            log.debug("添加离线消息 - 用户ID: {}, 消息ID: {}", userId, msgId);
            
        } catch (Exception e) {
            log.error("添加离线消息失败 - 用户ID: {}, 消息ID: {}", userId, msgId, e);
        }
    }

    @Override
    public void addOfflineMessages(String userId, List<String> msgIds) {
        if (msgIds == null || msgIds.isEmpty()) {
            return;
        }
        
        try {
            String key = OFFLINE_MSG_PREFIX + userId;
            
            // 批量添加消息
            Object[] msgArray = msgIds.toArray();
            redisTemplate.opsForList().leftPushAll(key, msgArray);
            
            // 限制队列长度
            redisTemplate.opsForList().trim(key, 0, MAX_OFFLINE_MSG_SIZE - 1);
            
            // 设置过期时间
            redisTemplate.expire(key, OFFLINE_MSG_TTL_SECONDS, TimeUnit.SECONDS);
            
            log.debug("批量添加离线消息 - 用户ID: {}, 消息数量: {}", userId, msgIds.size());
            
        } catch (Exception e) {
            log.error("批量添加离线消息失败 - 用户ID: {}, 消息数量: {}", userId, msgIds.size(), e);
        }
    }

    @Override
    public List<String> getOfflineMessages(String userId, int limit) {
        try {
            String key = OFFLINE_MSG_PREFIX + userId;
            
            List<Object> messages;
            if (limit <= 0) {
                // 获取全部消息
                messages = redisTemplate.opsForList().range(key, 0, -1);
            } else {
                // 获取指定数量的消息
                messages = redisTemplate.opsForList().range(key, 0, limit - 1);
            }
            
            List<String> result = messages != null ? 
                    messages.stream().map(Object::toString).collect(Collectors.toList()) : 
                    List.of();
            
            log.debug("获取离线消息 - 用户ID: {}, 限制数量: {}, 实际数量: {}", userId, limit, result.size());
            return result;
            
        } catch (Exception e) {
            log.error("获取离线消息失败 - 用户ID: {}", userId, e);
            return List.of();
        }
    }

    @Override
    public void clearOfflineMessages(String userId, List<String> msgIds) {
        try {
            String key = OFFLINE_MSG_PREFIX + userId;
            
            if (msgIds == null || msgIds.isEmpty()) {
                // 清除全部离线消息
                redisTemplate.delete(key);
                log.debug("清除全部离线消息 - 用户ID: {}", userId);
            } else {
                // 清除指定的离线消息
                for (String msgId : msgIds) {
                    redisTemplate.opsForList().remove(key, 1, msgId);
                }
                log.debug("清除指定离线消息 - 用户ID: {}, 消息数量: {}", userId, msgIds.size());
            }
            
        } catch (Exception e) {
            log.error("清除离线消息失败 - 用户ID: {}", userId, e);
        }
    }

    @Override
    public long getOfflineMessageCount(String userId) {
        try {
            String key = OFFLINE_MSG_PREFIX + userId;
            Long count = redisTemplate.opsForList().size(key);
            long result = count != null ? count : 0;
            
            log.debug("获取离线消息数量 - 用户ID: {}, 数量: {}", userId, result);
            return result;
            
        } catch (Exception e) {
            log.error("获取离线消息数量失败 - 用户ID: {}", userId, e);
            return 0;
        }
    }

    @Override
    public long incrementUnreadCount(String userId, String conversationId, int increment) {
        try {
            String key = UNREAD_COUNT_PREFIX + userId + ":" + conversationId;
            Long newCount = redisTemplate.opsForValue().increment(key, increment);
            long result = newCount != null ? newCount : increment;
            
            // 设置过期时间（30天）
            redisTemplate.expire(key, 30 * 24 * 60 * 60, TimeUnit.SECONDS);
            
            log.debug("增加未读数 - 用户ID: {}, 会话ID: {}, 增量: {}, 新未读数: {}", 
                    userId, conversationId, increment, result);
            return result;
            
        } catch (Exception e) {
            log.error("增加未读数失败 - 用户ID: {}, 会话ID: {}, 增量: {}", 
                    userId, conversationId, increment, e);
            return increment;
        }
    }

    @Override
    public long incrementUnreadCount(String userId, String conversationId) {
        return incrementUnreadCount(userId, conversationId, 1);
    }

    @Override
    public long getUnreadCount(String userId, String conversationId) {
        try {
            String key = UNREAD_COUNT_PREFIX + userId + ":" + conversationId;
            Object count = redisTemplate.opsForValue().get(key);
            long result = count != null ? Long.parseLong(count.toString()) : 0;
            
            log.debug("获取未读数 - 用户ID: {}, 会话ID: {}, 未读数: {}", userId, conversationId, result);
            return result;
            
        } catch (Exception e) {
            log.error("获取未读数失败 - 用户ID: {}, 会话ID: {}", userId, conversationId, e);
            return 0;
        }
    }

    @Override
    public void resetUnreadCount(String userId, String conversationId) {
        try {
            String key = UNREAD_COUNT_PREFIX + userId + ":" + conversationId;
            redisTemplate.delete(key);
            
            log.debug("重置未读数 - 用户ID: {}, 会话ID: {}", userId, conversationId);
            
        } catch (Exception e) {
            log.error("重置未读数失败 - 用户ID: {}, 会话ID: {}", userId, conversationId, e);
        }
    }

    @Override
    public long getTotalUnreadCount(String userId) {
        try {
            String pattern = UNREAD_COUNT_PREFIX + userId + ":*";
            var keys = redisTemplate.keys(pattern);
            
            if (keys == null || keys.isEmpty()) {
                return 0;
            }
            
            long totalCount = 0;
            for (String key : keys) {
                Object count = redisTemplate.opsForValue().get(key);
                if (count != null) {
                    totalCount += Long.parseLong(count.toString());
                }
            }
            
            log.debug("获取总未读数 - 用户ID: {}, 总未读数: {}", userId, totalCount);
            return totalCount;
            
        } catch (Exception e) {
            log.error("获取总未读数失败 - 用户ID: {}", userId, e);
            return 0;
        }
    }
}
