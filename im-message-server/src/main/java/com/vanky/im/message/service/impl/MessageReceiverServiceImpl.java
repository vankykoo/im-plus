package com.vanky.im.message.service.impl;

import com.vanky.im.message.service.MessageReceiverService;
import com.vanky.im.message.service.UserMsgListService;
import com.vanky.im.message.service.UserConversationListService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 消息接收者处理服务实现类
 * 负责处理新消息时对所有接收者的统一逻辑
 * 
 * @author vanky
 * @create 2025/7/29
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-07-29 21:05:00 +08:00; Reason: 创建MessageReceiverService实现类，修复启动时缺少Bean的问题;
// }}
// {{START MODIFICATIONS}}
@Service
public class MessageReceiverServiceImpl implements MessageReceiverService {

    private static final Logger log = LoggerFactory.getLogger(MessageReceiverServiceImpl.class);

    @Autowired
    private UserMsgListService userMsgListService;

    @Autowired
    private UserConversationListService userConversationListService;

    @Override
    public void processMessageReceivers(String msgId, String conversationId, List<String> receiverIds) {
        try {
            log.info("开始处理消息接收者 - 消息ID: {}, 会话ID: {}, 接收者数量: {}", 
                    msgId, conversationId, receiverIds.size());

            for (String receiverId : receiverIds) {
                processSingleReceiver(receiverId, msgId, conversationId);
            }

            log.info("消息接收者处理完成 - 消息ID: {}, 会话ID: {}", msgId, conversationId);

        } catch (Exception e) {
            log.error("处理消息接收者失败 - 消息ID: {}, 会话ID: {}", msgId, conversationId, e);
            throw new RuntimeException("处理消息接收者失败", e);
        }
    }

    @Override
    public Long processSingleReceiver(String userId, String msgId, String conversationId) {
        try {
            log.debug("处理单个接收者 - 用户ID: {}, 消息ID: {}, 会话ID: {}", userId, msgId, conversationId);

            // 1. 向 user_msg_list 表中插入记录，获得用户级全局seq
            Long userGlobalSeq = userMsgListService.saveUserMessageRecord(userId, msgId, conversationId);

            // 2. 更新 user_conversation_list 表中的会话记录
            userConversationListService.updateUserConversationMessage(
                    Long.valueOf(userId), conversationId, msgId);

            log.debug("单个接收者处理完成 - 用户ID: {}, 全局序列号: {}", userId, userGlobalSeq);
            return userGlobalSeq;

        } catch (Exception e) {
            log.error("处理单个接收者失败 - 用户ID: {}, 消息ID: {}, 会话ID: {}", 
                    userId, msgId, conversationId, e);
            throw new RuntimeException("处理单个接收者失败", e);
        }
    }
}
// {{END MODIFICATIONS}}
