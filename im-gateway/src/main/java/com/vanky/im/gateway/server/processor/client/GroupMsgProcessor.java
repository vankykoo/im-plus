package com.vanky.im.gateway.server.processor.client;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.common.util.SnowflakeIdGenerator;
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
    
    private final SnowflakeIdGenerator snowflakeIdGenerator = SnowflakeIdGenerator.getInstance();
    
    /**
     * 处理群聊消息
     * @param msg 聊天消息
     * @param senderChannel 发送方的Channel
     */
    public void process(ChatMessage msg, Channel senderChannel) {
        try {
            log.info("处理群聊消息 - 发送方: {}, 群组: {}, 原始消息ID: {}",
                    msg.getFromId(), msg.getToId(), msg.getUid());

            // 1. 生成全局唯一的MsgId
            String globalMsgId = snowflakeIdGenerator.nextIdString();

            // 2. 使用客户端传入的会话ID，如果为空则作为兜底生成
            String conversationId = msg.getConversationId();
            if (conversationId == null || conversationId.trim().isEmpty()) {
                conversationId = "group_" + msg.getToId();
                log.warn("客户端未提供会话ID，网关层兜底生成 - 会话ID: {}", conversationId);
            } else {
                log.debug("使用客户端提供的会话ID: {}", conversationId);
            }

            // 3. 构建新的消息对象，设置全局MsgId和会话ID
            ChatMessage processedMsg = ChatMessage.newBuilder(msg)
                    .setUid(globalMsgId)
                    .setConversationId(conversationId) // 设置会话ID
                    .build();

            log.debug("消息处理信息 - 全局MsgId: {}, 会话ID: {}", globalMsgId, conversationId);

            // 4. 投递消息到RocketMQ
            messageQueueService.sendMessageToGroup(conversationId, processedMsg, senderChannel);

            log.info("群聊消息处理完成 - 全局MsgId: {}, 会话ID: {}", globalMsgId, conversationId);

        } catch (Exception e) {
            log.error("处理群聊消息失败 - 发送方: {}, 群组ID: {}, 消息ID: {}, 错误: {}",
                    msg.getFromId(), msg.getToId(), msg.getUid(), e.getMessage(), e);
        }
    }
}
