package com.vanky.im.message.processor;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.message.entity.PrivateMessage;
import com.vanky.im.message.service.*;
import com.vanky.im.message.util.MessageConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 私聊消息处理器
 * 实现写扩散模式的消息存储逻辑
 */
@Slf4j
@Component
public class PrivateMessageProcessor {

    @Autowired
    private RedisService redisService;
    
    @Autowired
    private PrivateMessageService privateMessageService;
    
    @Autowired
    private UserMsgListService userMsgListService;
    
    @Autowired
    private ConversationService conversationService;

    // 用户消息链缓存最大保留条数
    private static final int MAX_USER_MSG_CACHE_SIZE = 1000;
    // 消息缓存TTL（1天）
    private static final long MESSAGE_CACHE_TTL = 24 * 60 * 60;

    /**
     * 处理私聊消息的写扩散存储
     * @param chatMessage 原始消息
     * @param conversationId 会话ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void processPrivateMessage(ChatMessage chatMessage, String conversationId) {
        try {
            String fromUserId = chatMessage.getFromId();
            String toUserId = chatMessage.getToId();

            // 1. 生成消息ID（会话ID已由调用方提供）
            String msgId = MessageConverter.generateMsgId();
            
            log.info("开始处理私聊消息 - 会话ID: {}, 消息ID: {}, 发送方: {}, 接收方: {}", 
                    conversationId, msgId, fromUserId, toUserId);
            
            // 2. 原子生成Seq
            Long seq = redisService.generateSeq(conversationId);
            log.debug("生成会话序列号 - 会话ID: {}, Seq: {}", conversationId, seq);
            
            // 3. 数据入库（事务）
            saveMessageData(chatMessage, msgId, conversationId, seq, fromUserId, toUserId);
            
            // 4. 缓存更新
            updateCache(chatMessage, msgId, conversationId, seq, fromUserId, toUserId);
            
            log.info("私聊消息处理完成 - 会话ID: {}, 消息ID: {}, Seq: {}", conversationId, msgId, seq);
            
        } catch (Exception e) {
            log.error("处理私聊消息失败 - 发送方: {}, 接收方: {}, 原始消息ID: {}", 
                    chatMessage.getFromId(), chatMessage.getToId(), chatMessage.getUid(), e);
            throw e;
        }
    }

    /**
     * 保存消息数据到数据库
     */
    private void saveMessageData(ChatMessage chatMessage, String msgId, String conversationId, 
                                Long seq, String fromUserId, String toUserId) {
        // 1. 保存消息主体到message表
        PrivateMessage privateMessage = MessageConverter.convertToPrivateMessage(chatMessage, msgId, conversationId);
        privateMessageService.save(privateMessage);
        log.debug("保存消息主体完成 - 消息ID: {}", msgId);
        
        // 2. 写扩散：为发送方和接收方在user_msg_list表中插入记录
        userMsgListService.saveWriteExpandRecords(msgId, conversationId, seq, fromUserId, toUserId);
        log.debug("写扩散记录保存完成 - 发送方: {}, 接收方: {}", fromUserId, toUserId);
        
        // 3. 处理会话信息
        conversationService.handleConversation(conversationId, fromUserId, toUserId);
    }



    /**
     * 更新缓存
     */
    private void updateCache(ChatMessage chatMessage, String msgId, String conversationId, 
                           Long seq, String fromUserId, String toUserId) {
        
        // 1. 将新消息缓存到Redis (String, msgId -> message_json, TTL 1天)
        PrivateMessage privateMessage = MessageConverter.convertToPrivateMessage(chatMessage, msgId, conversationId);
        String messageJson = MessageConverter.toJson(privateMessage);
        redisService.cacheMessage(msgId, messageJson, MESSAGE_CACHE_TTL);
        
        // 2. 将消息的msgId和seq存入用户消息链的缓存中(ZSet)
        redisService.addToUserMsgList(fromUserId, msgId, seq, MAX_USER_MSG_CACHE_SIZE);
        redisService.addToUserMsgList(toUserId, msgId, seq, MAX_USER_MSG_CACHE_SIZE);
        
        log.debug("缓存更新完成 - 消息ID: {}, 发送方: {}, 接收方: {}", msgId, fromUserId, toUserId);
    }
}