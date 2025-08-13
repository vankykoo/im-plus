package com.vanky.im.gateway.server.processor.client;

import com.vanky.im.common.protocol.ChatMessage;

import com.vanky.im.gateway.mq.MessageQueueService;
import com.vanky.im.gateway.session.MsgSender;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author vanky
 * @create 2025/5/22 21:07
 * @description 私聊消息处理器
 */
@Slf4j
@Component
public class PrivateMsgProcessor {

    @Autowired
    private MessageQueueService messageQueueService;
    
    @Autowired
    private MsgSender msgSender;
    


    /**
     * 处理私聊消息
     * @param msg 聊天消息
     * @param senderChannel 发送方的Channel
     */
    public void process(ChatMessage msg, Channel senderChannel) {
        try {
            log.info("处理私聊消息 - 发送方: {}, 接收方: {}, 客户端序列号: {}",
                    msg.getFromId(), msg.getToId(), msg.getClientSeq());

            // 1. 使用客户端传入的会话ID，如果为空则作为兜底生成
            String conversationId = msg.getConversationId();
            if (conversationId == null || conversationId.trim().isEmpty()) {
                conversationId = generateConversationId(msg.getFromId(), msg.getToId());
                log.warn("客户端未提供会话ID，网关层兜底生成 - 会话ID: {}", conversationId);
            } else {
                log.debug("使用客户端提供的会话ID: {}", conversationId);
            }

            // 2. 直接转发原始消息到RocketMQ，消息ID将在message-server中生成
            log.debug("转发消息到MQ - 会话ID: {}, 客户端序列号: {}", conversationId, msg.getClientSeq());

            // 3. 投递消息到RocketMQ
            messageQueueService.sendMessageToPrivate(conversationId, msg, senderChannel);

            log.info("私聊消息转发完成 - 会话ID: {}, 客户端序列号: {}", conversationId, msg.getClientSeq());

        } catch (Exception e) {
            log.error("处理私聊消息失败 - 发送方: {}, 接收方: {}, 客户端序列号: {}, 错误: {}",
                    msg.getFromId(), msg.getToId(), msg.getClientSeq(), e.getMessage(), e);
        }
    }
    
    /**
     * 生成会话ID
     * 对于私聊，使用两个用户ID的组合生成唯一的会话ID
     * 规则：private_xx_xx，其中userid小的放前面
     * @param userId1 用户1的ID
     * @param userId2 用户2的ID
     * @return 会话ID
     */
    private String generateConversationId(String userId1, String userId2) {
        try {
            long id1 = Long.parseLong(userId1);
            long id2 = Long.parseLong(userId2);

            // 使用较小的ID在前，较大的ID在后，确保同一对用户的会话ID始终相同
            if (id1 < id2) {
                return "private_" + id1 + "_" + id2;
            } else {
                return "private_" + id2 + "_" + id1;
            }
        } catch (NumberFormatException e) {
            log.warn("用户ID格式不正确，使用字符串比较 - userId1: {}, userId2: {}", userId1, userId2);
            // 如果ID不是数字，则使用字符串比较
            int compare = userId1.compareTo(userId2);
            if (compare < 0) {
                return "private_" + userId1 + "_" + userId2;
            } else {
                return "private_" + userId2 + "_" + userId1;
            }
        }
    }
}
