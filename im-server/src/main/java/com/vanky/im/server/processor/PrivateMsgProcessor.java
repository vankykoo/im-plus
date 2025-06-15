package com.vanky.im.server.processor;

import com.vanky.im.common.constant.ErrorConstants;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.common.exception.BusinessException;
import com.vanky.im.server.entity.PrivateMessage;
import com.vanky.im.server.service.PrivateMessageService;
import com.vanky.im.server.service.ConversationMsgListService;
import com.vanky.im.server.service.MessageCacheService;
import com.vanky.im.server.util.MessageConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 私聊消息处理器
 * @author vanky
 * @date 2024-05-27
 */
@Slf4j
@Component
public class PrivateMsgProcessor {

    @Autowired
    private PrivateMessageService privateMessageService;
    
    @Autowired
    private ConversationMsgListService conversationMsgListService;
    
    @Autowired
    private MessageCacheService messageCacheService;

    /**
     * 处理私聊消息
     * @param chatMessage 聊天消息
     * @param conversationId 会话ID
     */
    @Transactional
    public void processPrivateMessage(ChatMessage chatMessage, String conversationId) {
        try {
            log.info("开始处理私聊消息 - 消息ID: {}, 会话ID: {}", chatMessage.getUid(), conversationId);
            
            // 转换为私聊消息实体
            PrivateMessage privateMessage = MessageConverter.toPrivateMessage(chatMessage, conversationId);
            
            // 保存消息到数据库
            boolean saved = privateMessageService.save(privateMessage);
            
            if (!saved) {
                throw new BusinessException(ErrorConstants.MSG_SAVE_FAILED_CODE, 
                                           ErrorConstants.MSG_SAVE_FAILED_MSG);
            }
            
            // 获取下一个序列号
            Long nextSeq = conversationMsgListService.getNextSeq(conversationId);
            
            // 将消息添加到会话消息链
            boolean addedToConversation = conversationMsgListService.addMessageToConversation(
                conversationId, privateMessage.getMsgId(), nextSeq);
            
            if (!addedToConversation) {
                throw new BusinessException(ErrorConstants.MSG_ADD_TO_CONVERSATION_FAILED_CODE, 
                                           ErrorConstants.MSG_ADD_TO_CONVERSATION_FAILED_MSG);
            }
            
            // 缓存消息到Redis
            messageCacheService.cachePrivateMessage(privateMessage, chatMessage, conversationId);
            
            log.info("私聊消息处理成功 - 消息ID: {}, 会话ID: {}, 序列号: {}", 
                    chatMessage.getUid(), conversationId, nextSeq);
            
        } catch (Exception e) {
            log.error("处理私聊消息时发生错误 - 消息ID: {}, 会话ID: {}, 错误: {}", 
                     chatMessage.getUid(), conversationId, e.getMessage(), e);
            throw e;
        }
    }
}
