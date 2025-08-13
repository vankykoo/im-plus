package com.vanky.im.gateway.server.processor.client;

import com.vanky.im.common.protocol.ChatMessage;

import com.vanky.im.gateway.mq.MessageQueueService;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author vanky
 * @create 2025/5/22 21:07
 * @description 群组消息处理器
 */
@Slf4j
@Component
public class GroupMsgProcessor {
    
    @Autowired
    private MessageQueueService messageQueueService;
    

    
    /**
     * 处理群聊消息
     * @param msg 聊天消息
     * @param senderChannel 发送方的Channel
     */
    public void process(ChatMessage msg, Channel senderChannel) {
        try {
            log.info("处理群聊消息 - 发送方: {}, 群组: {}, 客户端序列号: {}",
                    msg.getFromId(), msg.getToId(), msg.getClientSeq());

            // 1. 使用客户端传入的会话ID，如果为空则作为兜底生成
            String conversationId = msg.getConversationId();
            if (conversationId == null || conversationId.trim().isEmpty()) {
                conversationId = "group_" + msg.getToId();
                log.warn("客户端未提供会话ID，网关层兜底生成 - 会话ID: {}", conversationId);
            } else {
                log.debug("使用客户端提供的会话ID: {}", conversationId);
            }

            // 2. 直接转发原始消息到RocketMQ，消息ID将在message-server中生成
            log.debug("转发消息到MQ - 会话ID: {}, 客户端序列号: {}", conversationId, msg.getClientSeq());

            // 3. 投递消息到RocketMQ
            messageQueueService.sendMessageToGroup(conversationId, msg, senderChannel);

            log.info("群聊消息转发完成 - 会话ID: {}, 客户端序列号: {}", conversationId, msg.getClientSeq());

        } catch (Exception e) {
            log.error("处理群聊消息失败 - 发送方: {}, 群组ID: {}, 客户端序列号: {}, 错误: {}",
                    msg.getFromId(), msg.getToId(), msg.getClientSeq(), e.getMessage(), e);
        }
    }
}
