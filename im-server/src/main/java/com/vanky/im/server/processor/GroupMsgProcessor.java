package com.vanky.im.server.processor;

import com.vanky.im.common.constant.ErrorConstants;
import com.vanky.im.common.exception.BusinessException;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.server.entity.GroupMessage;
import com.vanky.im.server.service.ConversationMsgListService;
import com.vanky.im.server.service.GroupMessageService;
import com.vanky.im.server.service.MessageCacheService;
import com.vanky.im.server.util.MessageConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 群聊消息处理器
 * @author vanky
 * @date 2025-06-08
 */
@Slf4j
@Component
public class GroupMsgProcessor {

    @Autowired
    private GroupMessageService groupMessageService;
    
    @Autowired
    private ConversationMsgListService conversationMsgListService;
    
    @Autowired
    private MessageCacheService messageCacheService;
    
    /**
     * 处理群聊消息
     * @param chatMessage 聊天消息
     * @param conversationId 会话ID
     */
    @Transactional
    public void processGroupMessage(ChatMessage chatMessage, String conversationId) {
        try {
            log.info("开始处理群聊消息 - 消息ID: {}, 会话ID: {}", chatMessage.getUid(), conversationId);
            
            // 转换为群聊消息实体
            GroupMessage groupMessage = MessageConverter.toGroupMessage(chatMessage, conversationId);
            
            // 保存消息到数据库
            boolean saved = groupMessageService.save(groupMessage);
            
            if (!saved) {
                throw new BusinessException(ErrorConstants.MSG_SAVE_FAILED_CODE, 
                                           ErrorConstants.MSG_SAVE_FAILED_MSG);
            }
            
            // 获取下一个序列号
            Long nextSeq = conversationMsgListService.getNextSeq(conversationId);
            
            // 将消息添加到会话消息链
            boolean addedToConversation = conversationMsgListService.addMessageToConversation(
                conversationId, groupMessage.getMsgId(), nextSeq);
            
            if (!addedToConversation) {
                throw new BusinessException(ErrorConstants.MSG_ADD_TO_CONVERSATION_FAILED_CODE, 
                                           ErrorConstants.MSG_ADD_TO_CONVERSATION_FAILED_MSG);
            }
            
            // 缓存消息到Redis
            messageCacheService.cacheGroupMessage(groupMessage, chatMessage, conversationId);
            
            log.info("群聊消息处理成功 - 消息ID: {}, 会话ID: {}, 序列号: {}", 
                    chatMessage.getUid(), conversationId, nextSeq);
            
        } catch (Exception e) {
            log.error("处理群聊消息时发生错误 - 消息ID: {}, 会话ID: {}, 错误: {}", 
                     chatMessage.getUid(), conversationId, e.getMessage(), e);
            throw e;
        }
    }
}
