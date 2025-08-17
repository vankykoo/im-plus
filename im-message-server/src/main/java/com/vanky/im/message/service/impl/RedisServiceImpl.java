package com.vanky.im.message.service.impl;

import com.vanky.im.common.constant.RedisKeyConstants;
import com.vanky.im.common.constant.SessionConstants;
import com.vanky.im.common.model.UserSession;
import com.vanky.im.message.constant.MessageConstants;
import com.vanky.im.message.mapper.UserMsgListMapper;
import com.vanky.im.message.service.ConversationMsgListService;
import com.vanky.im.message.service.RedisService;
import com.vanky.im.message.client.SequenceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.List;

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

    @Autowired
    private UserMsgListMapper userMsgListMapper;

    @Autowired
    private SequenceClient sequenceClient;

    @Override
    public Long generateSeq(String conversationId) {
        // {{CHENGQI:
        // Action: Modified; Timestamp: 2025-08-12 22:12:45 +08:00; Reason: 删除Redis降级逻辑，改为只使用SequenceClient生成会话级序列号;
        // }}
        // {{START MODIFICATIONS}}
        try {
            // 使用新的序列号服务生成会话级序列号
            Long seq = sequenceClient.getNextSequence(conversationId);
            
            if (seq != null) {
                log.debug("使用序列号服务生成会话seq - 会话ID: {}, seq: {}", conversationId, seq);
                return seq;
            } else {
                log.error("序列号服务返回null - 会话ID: {}", conversationId);
                throw new RuntimeException("序列号服务生成会话序列号失败");
            }

        } catch (Exception e) {
            log.error("生成会话序列号失败, conversationId: {}", conversationId, e);
            throw new RuntimeException("生成会话序列号失败", e);
        }
        // {{END MODIFICATIONS}}
    }

    @Override
    public Long generateUserGlobalSeq(String userId) {
        // {{CHENGQI:
        // Action: Modified; Timestamp: 2025-08-12 22:12:45 +08:00; Reason: 删除Redis降级逻辑，改为只使用SequenceClient生成用户级全局序列号;
        // }}
        // {{START MODIFICATIONS}}
        try {
            // 使用新的序列号服务生成用户级全局序列号
            String businessKey = "user_" + userId;
            Long seq = sequenceClient.getNextSequence(businessKey);

            if (seq != null) {
                log.debug("使用序列号服务生成用户全局seq - 用户ID: {}, seq: {}", userId, seq);
                return seq;
            } else {
                log.error("序列号服务返回null - 用户ID: {}", userId);
                throw new RuntimeException("序列号服务生成用户全局序列号失败");
            }

        } catch (Exception e) {
            log.error("生成用户全局序列号失败 - 用户ID: {}", userId, e);
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
        // Action: Modified; Timestamp: 2025-08-15 16:15:00 +08:00; Reason: 移除Redis user:global:seq键相关逻辑，直接使用数据库查询，符合持久化第一原则;
        // }}
        // {{START MODIFICATIONS}}
        try {
            // 直接从数据库查询用户的最大seq
            Long dbMaxSeq = userMsgListMapper.selectMaxSeqByUserId(userId);

            if (dbMaxSeq != null && dbMaxSeq > 0) {
                log.debug("从数据库获取用户最大全局序列号 - 用户ID: {}, 最大序列号: {}", userId, dbMaxSeq);
                return dbMaxSeq;
            } else {
                // 用户还没有任何消息
                log.debug("用户无消息记录 - 用户ID: {}", userId);
                return 0L;
            }

        } catch (Exception e) {
            log.error("从数据库获取用户最大全局序列号失败 - 用户ID: {}", userId, e);
            return 0L;
        }
        // {{END MODIFICATIONS}}
    }



    // ========== 新增方法实现：消息已读功能支持 ==========

    @Override
    public Long getUserLastReadSeq(String userId, String conversationId) {
        try {
            String key = RedisKeyConstants.USER_LAST_READ_SEQ_PREFIX + userId + ":" + conversationId;
            Object value = redisTemplate.opsForValue().get(key);

            if (value instanceof Long) {
                return (Long) value;
            } else if (value instanceof Integer) {
                return ((Integer) value).longValue();
            } else if (value instanceof String) {
                try {
                    return Long.parseLong((String) value);
                } catch (NumberFormatException e) {
                    log.warn("无法解析已读序列号字符串 - 用户: {}, 会话: {}, 值: {}", userId, conversationId, value);
                    return null;
                }
            }

            return null;

        } catch (Exception e) {
            log.error("获取用户最后已读序列号失败 - 用户: {}, 会话: {}", userId, conversationId, e);
            return null;
        }
    }

    @Override
    public void setUserLastReadSeq(String userId, String conversationId, long lastReadSeq) {
        try {
            String key = RedisKeyConstants.USER_LAST_READ_SEQ_PREFIX + userId + ":" + conversationId;
            redisTemplate.opsForValue().set(key, lastReadSeq, RedisKeyConstants.USER_READ_SEQ_TTL_SECONDS, TimeUnit.SECONDS);

            log.debug("设置用户最后已读序列号 - 用户: {}, 会话: {}, 序列号: {}", userId, conversationId, lastReadSeq);

        } catch (Exception e) {
            log.error("设置用户最后已读序列号失败 - 用户: {}, 会话: {}, 序列号: {}", userId, conversationId, lastReadSeq, e);
        }
    }

    @Override
    public long incrementGroupReadCount(String msgId) {
        try {
            String key = RedisKeyConstants.GROUP_READ_COUNT_PREFIX + msgId;
            Long count = redisTemplate.opsForValue().increment(key);

            // 设置TTL（仅在第一次创建时）
            if (count != null && count == 1) {
                redisTemplate.expire(key, RedisKeyConstants.GROUP_READ_COUNT_TTL_SECONDS, TimeUnit.SECONDS);
            }

            log.debug("增加群聊消息已读计数 - 消息: {}, 当前计数: {}", msgId, count);
            return count != null ? count : 0L;

        } catch (Exception e) {
            log.error("增加群聊消息已读计数失败 - 消息: {}", msgId, e);
            return 0L;
        }
    }

    @Override
    public int getGroupReadCount(String msgId) {
        try {
            String key = RedisKeyConstants.GROUP_READ_COUNT_PREFIX + msgId;
            Object value = redisTemplate.opsForValue().get(key);

            if (value instanceof Long) {
                return ((Long) value).intValue();
            } else if (value instanceof Integer) {
                return (Integer) value;
            } else if (value instanceof String) {
                try {
                    return Integer.parseInt((String) value);
                } catch (NumberFormatException e) {
                    log.warn("无法解析已读计数字符串 - 消息: {}, 值: {}", msgId, value);
                    return 0;
                }
            }

            return 0;

        } catch (Exception e) {
            log.error("获取群聊消息已读计数失败 - 消息: {}", msgId, e);
            return 0;
        }
    }

    @Override
    public void addGroupReadUser(String msgId, String userId) {
        try {
            String key = RedisKeyConstants.GROUP_READ_USERS_PREFIX + msgId;
            redisTemplate.opsForSet().add(key, userId);

            // 设置TTL
            redisTemplate.expire(key, RedisKeyConstants.GROUP_READ_USERS_TTL_SECONDS, TimeUnit.SECONDS);

            log.debug("添加群聊消息已读用户 - 消息: {}, 用户: {}", msgId, userId);

        } catch (Exception e) {
            log.error("添加群聊消息已读用户失败 - 消息: {}, 用户: {}", msgId, userId, e);
        }
    }

    @Override
    public List<String> getGroupReadUsers(String msgId) {
        try {
            String key = RedisKeyConstants.GROUP_READ_USERS_PREFIX + msgId;
            Set<Object> members = redisTemplate.opsForSet().members(key);

            if (members != null && !members.isEmpty()) {
                return members.stream()
                        .map(Object::toString)
                        .collect(java.util.stream.Collectors.toList());
            }

            return List.of();

        } catch (Exception e) {
            log.error("获取群聊消息已读用户列表失败 - 消息: {}", msgId, e);
            return List.of();
        }
    }
}