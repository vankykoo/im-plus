package com.vanky.im.message.service.impl;

import com.vanky.im.common.constant.RedisKeyConstants;
import com.vanky.im.common.constant.SessionConstants;
import com.vanky.im.common.model.UserSession;
import com.vanky.im.message.constant.MessageConstants;
import com.vanky.im.message.mapper.UserMsgListMapper;
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

    @Autowired
    private UserMsgListMapper userMsgListMapper;

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
        // Action: Modified; Timestamp: 2025-08-05 17:45:00 +08:00; Reason: 优化用户全局序列号生成，添加数据库降级策略确保seq连续性;
        // }}
        // {{START MODIFICATIONS}}
        String key = RedisKeyConstants.getUserGlobalSeqKey(userId);
        try {
            // 1. 检查Redis中是否存在该用户的seq
            Object existingValue = redisTemplate.opsForValue().get(key);

            if (existingValue != null) {
                // Redis中存在，直接递增
                return redisTemplate.opsForValue().increment(key);
            }

            // 2. Redis中不存在，需要初始化
            log.info("Redis中不存在用户全局seq，开始初始化 - 用户ID: {}", userId);
            return initializeUserGlobalSeq(userId, key);

        } catch (Exception e) {
            log.error("生成用户全局序列号失败, userId: {}", userId, e);
            throw new RuntimeException("生成用户全局序列号失败", e);
        }
        // {{END MODIFICATIONS}}
    }

    /**
     * 初始化用户全局序列号
     * 从数据库查询最大seq，如果不存在则从1开始，并写入Redis缓存
     *
     * @param userId 用户ID
     * @param redisKey Redis键
     * @return 初始化后的下一个seq值
     */
    private Long initializeUserGlobalSeq(String userId, String redisKey) {
        try {
            // 1. 从数据库查询用户的最大seq
            Long maxSeqFromDb = userMsgListMapper.selectMaxSeqByUserId(userId);

            if (maxSeqFromDb != null && maxSeqFromDb > 0) {
                // 2. 数据库中存在记录，使用最大seq + 1作为下一个seq
                Long nextSeq = maxSeqFromDb + 1;

                // 3. 将下一个seq写入Redis缓存
                redisTemplate.opsForValue().set(redisKey, nextSeq);

                log.info("从数据库恢复用户全局seq - 用户ID: {}, 数据库最大seq: {}, 初始化Redis为: {}",
                        userId, maxSeqFromDb, nextSeq);

                return nextSeq;
            } else {
                // 4. 数据库中无记录，从seq=1开始
                Long initialSeq = 1L;
                redisTemplate.opsForValue().set(redisKey, initialSeq);

                log.info("用户首次生成全局seq - 用户ID: {}, 初始化seq: {}", userId, initialSeq);

                return initialSeq;
            }

        } catch (Exception e) {
            log.error("初始化用户全局序列号失败 - 用户ID: {}", userId, e);

            // 降级策略：如果数据库查询也失败，从1开始并写入Redis
            try {
                Long fallbackSeq = 1L;
                redisTemplate.opsForValue().set(redisKey, fallbackSeq);
                log.warn("初始化用户全局seq降级处理 - 用户ID: {}, 使用默认seq: {}", userId, fallbackSeq);
                return fallbackSeq;
            } catch (Exception redisException) {
                log.error("Redis写入也失败，抛出异常 - 用户ID: {}", userId, redisException);
                throw new RuntimeException("初始化用户全局序列号完全失败", redisException);
            }
        }
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
        // Action: Modified; Timestamp: 2025-08-05 17:45:00 +08:00; Reason: 优化获取用户最大全局序列号，添加数据库降级策略和Redis缓存恢复;
        // }}
        // {{START MODIFICATIONS}}
        String key = RedisKeyConstants.getUserGlobalSeqKey(userId);

        try {
            // 1. 尝试从Redis获取
            Object value = redisTemplate.opsForValue().get(key);

            if (value instanceof Long) {
                Long maxSeq = (Long) value;
                log.debug("从Redis获取用户最大全局序列号 - 用户ID: {}, 最大序列号: {}", userId, maxSeq);
                return maxSeq;
            } else if (value instanceof Integer) {
                Long maxSeq = ((Integer) value).longValue();
                log.debug("从Redis获取用户最大全局序列号 - 用户ID: {}, 最大序列号: {}", userId, maxSeq);
                return maxSeq;
            }

            // 2. Redis中不存在，降级到数据库查询
            log.info("Redis中不存在用户最大seq，降级到数据库查询 - 用户ID: {}", userId);
            return getUserMaxGlobalSeqWithFallback(userId, key);

        } catch (Exception e) {
            log.error("从Redis获取用户最大全局序列号失败，降级到数据库查询 - 用户ID: {}", userId, e);
            // Redis操作失败，降级到数据库查询
            return getUserMaxGlobalSeqWithFallback(userId, key);
        }
        // {{END MODIFICATIONS}}
    }

    /**
     * 数据库降级查询用户最大全局序列号，并恢复Redis缓存
     *
     * @param userId 用户ID
     * @param redisKey Redis键
     * @return 用户最大全局序列号
     */
    private Long getUserMaxGlobalSeqWithFallback(String userId, String redisKey) {
        try {
            // 1. 从数据库查询用户的最大seq
            Long dbMaxSeq = userMsgListMapper.selectMaxSeqByUserId(userId);

            if (dbMaxSeq != null && dbMaxSeq > 0) {
                // 2. 数据库中存在记录，恢复Redis缓存
                try {
                    // 注意：这里存储的是当前最大seq，不是下一个seq
                    // 因为getUserMaxGlobalSeq是查询当前最大值，不是生成新值
                    redisTemplate.opsForValue().set(redisKey, dbMaxSeq);
                    log.info("恢复Redis缓存成功 - 用户ID: {}, 最大seq: {}", userId, dbMaxSeq);
                } catch (Exception redisException) {
                    log.warn("恢复Redis缓存失败，但不影响返回结果 - 用户ID: {}, 最大seq: {}",
                            userId, dbMaxSeq, redisException);
                }

                log.info("从数据库获取用户最大全局序列号 - 用户ID: {}, 最大序列号: {}", userId, dbMaxSeq);
                return dbMaxSeq;
            } else {
                // 3. 数据库中无记录，用户还没有任何消息
                Long defaultSeq = 0L;

                try {
                    // 将0写入Redis缓存，表示用户还没有消息
                    redisTemplate.opsForValue().set(redisKey, defaultSeq);
                    log.info("用户无消息记录，设置Redis缓存为0 - 用户ID: {}", userId);
                } catch (Exception redisException) {
                    log.warn("设置Redis缓存失败，但不影响返回结果 - 用户ID: {}", userId, redisException);
                }

                log.info("用户无消息记录，返回默认值0 - 用户ID: {}", userId);
                return defaultSeq;
            }

        } catch (Exception e) {
            log.error("数据库查询用户最大全局序列号也失败 - 用户ID: {}", userId, e);
            // 完全降级：返回0，表示无法确定用户的seq状态
            return 0L;
        }
    }
}