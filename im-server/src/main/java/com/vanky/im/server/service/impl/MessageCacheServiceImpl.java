package com.vanky.im.server.service.impl;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.common.util.RedisUtils;
import com.vanky.im.server.constant.RedisKeyConstant;
import com.vanky.im.server.entity.GroupMessage;
import com.vanky.im.server.entity.PrivateMessage;
import com.vanky.im.server.service.ConversationService;
import com.vanky.im.server.service.MessageCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 消息缓存服务实现类
 * @author vanky
 * @date 2025-06-08
 */
@Service
@Slf4j
public class MessageCacheServiceImpl implements MessageCacheService {

    @Autowired
    private RedisUtils redisUtils;
    
    @Autowired
    private ConversationService conversationService;

    @Override
    public void cachePrivateMessage(PrivateMessage privateMessage, ChatMessage chatMessage, String conversationId) {
        try {
            // 1. 将消息本身缓存到Redis
            String msgKey = RedisKeyConstant.PRIVATE_MSG_PREFIX + privateMessage.getMsgId();
            redisUtils.set(msgKey, privateMessage, RedisKeyConstant.MSG_CACHE_EXPIRE_TIME, TimeUnit.SECONDS);
            
            // 2. 更新会话表中最后消息时间
            conversationService.updateLastMsgTime(conversationId, chatMessage.getTimestamp());
            
            // 3. 将消息添加到会话消息链的Redis缓存
            addMessageToRecentCache(conversationId, privateMessage.getMsgId(), privateMessage.getId());
            
            log.info("私聊消息缓存成功 - 消息ID: {}, 会话ID: {}", privateMessage.getMsgId(), conversationId);
        } catch (Exception e) {
            log.error("私聊消息缓存失败 - 消息ID: {}, 会话ID: {}, 错误: {}", 
                    privateMessage.getMsgId(), conversationId, e.getMessage(), e);
        }
    }

    @Override
    public void cacheGroupMessage(GroupMessage groupMessage, ChatMessage chatMessage, String conversationId) {
        try {
            // 1. 将消息本身缓存到Redis
            String msgKey = RedisKeyConstant.GROUP_MSG_PREFIX + groupMessage.getMsgId();
            redisUtils.set(msgKey, groupMessage, RedisKeyConstant.MSG_CACHE_EXPIRE_TIME, TimeUnit.SECONDS);
            
            // 2. 更新会话表中最后消息时间
            conversationService.updateLastMsgTime(conversationId, chatMessage.getTimestamp());
            
            // 3. 将会话信息缓存到Redis
            cacheConversationInfo(conversationId);
            
            // 4. 将消息添加到会话消息链的Redis缓存
            addMessageToRecentCache(conversationId, groupMessage.getMsgId(), groupMessage.getId());
            
            log.info("群聊消息缓存成功 - 消息ID: {}, 会话ID: {}", groupMessage.getMsgId(), conversationId);
        } catch (Exception e) {
            log.error("群聊消息缓存失败 - 消息ID: {}, 会话ID: {}, 错误: {}", 
                    groupMessage.getMsgId(), conversationId, e.getMessage(), e);
        }
    }

    @Override
    public void addMessageToRecentCache(String conversationId, Long msgId, Long seq) {
        try {
            // 使用ZSet将消息ID按照序列号排序存储
            String recentKey = RedisKeyConstant.CONVERSATION_RECENT_MSG_PREFIX + conversationId;
            redisUtils.zSetAdd(recentKey, msgId, seq.doubleValue());
            
            // 设置过期时间
            redisUtils.expire(recentKey, RedisKeyConstant.CONVERSATION_RECENT_MSG_EXPIRE_TIME, TimeUnit.SECONDS);
            
            // 如果消息数量超过限制，则移除旧消息
            long size = redisUtils.zSetSize(recentKey);
            if (size > RedisKeyConstant.RECENT_MESSAGE_LIMIT) {
                // 移除最旧的消息，保留最新的RECENT_MESSAGE_LIMIT个消息
                long removeCount = size - RedisKeyConstant.RECENT_MESSAGE_LIMIT;
                redisUtils.getRedisTemplate().opsForZSet().removeRange(recentKey, 0, removeCount - 1);
            }
            
            log.debug("消息已添加到会话最近消息缓存 - 会话ID: {}, 消息ID: {}, 序列号: {}", conversationId, msgId, seq);
        } catch (Exception e) {
            log.error("添加消息到会话最近消息缓存失败 - 会话ID: {}, 消息ID: {}, 序列号: {}, 错误: {}", 
                    conversationId, msgId, seq, e.getMessage(), e);
        }
    }
    
    /**
     * 缓存会话信息
     * @param conversationId 会话ID
     */
    private void cacheConversationInfo(String conversationId) {
        try {
            // TODO: 获取会话信息并缓存
            // 这里需要根据具体业务逻辑来实现
            // 例如，可以通过调用会话服务获取会话详细信息，然后缓存到Redis
            
            String infoKey = RedisKeyConstant.CONVERSATION_INFO_PREFIX + conversationId;
            Map<String, Object> conversationInfo = new HashMap<>();
            // 填充会话信息
            // conversationInfo.put("key", value);
            
            // 将会话信息缓存到Redis
            redisUtils.hashSetAll(infoKey, conversationInfo);
            redisUtils.expire(infoKey, RedisKeyConstant.CONVERSATION_INFO_EXPIRE_TIME, TimeUnit.SECONDS);
            
            log.debug("会话信息已缓存到Redis - 会话ID: {}", conversationId);
        } catch (Exception e) {
            log.error("缓存会话信息失败 - 会话ID: {}, 错误: {}", conversationId, e.getMessage(), e);
        }
    }
} 