package com.vanky.im.gateway.session;

import com.vanky.im.common.protocol.ChatMessage;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 消息发送者, 封装了发送消息的方法
 */
@Slf4j
@Component
public class MsgSender {
    
    @Autowired
    private UserChannelManager userChannelManager;
    
    /**
     * 发送消息到指定用户
     * 
     * @param userId 用户ID
     * @param message 消息内容
     * @return 是否发送成功
     */
    public boolean sendToUser(String userId, ChatMessage message) {
        Channel channel = userChannelManager.getChannel(userId);
        if (channel == null || !channel.isActive()) {
            log.info("用户 {} 不在线，消息发送失败", userId);
            return false;
        }
        
        return sendToChannel(channel, message);
    }
    
    /**
     * 发送消息到指定Channel
     * 
     * @param channel 目标Channel
     * @param message 消息内容
     * @return 是否发送成功
     */
    public boolean sendToChannel(Channel channel, ChatMessage message) {
        if (channel == null || !channel.isActive()) {
            log.info("Channel不可用，消息发送失败");
            return false;
        }
        
        try {
            // 使用writeAndFlush发送消息，并且为异步操作添加监听器
            channel.writeAndFlush(message).addListener(future -> {
                if (future.isSuccess()) {
                    log.debug("消息发送成功 - 消息ID: {}, 接收方: {}", 
                            message.getUid(), message.getToId());
                } else {
                    log.warn("消息发送失败 - 消息ID: {}, 接收方: {}, 原因: {}", 
                            message.getUid(), message.getToId(), future.cause().getMessage());
                }
            });
            return true;
        } catch (Exception e) {
            log.error("发送消息异常 - 消息ID: {}, 接收方: {}", 
                    message.getUid(), message.getToId(), e);
            return false;
        }
    }
}
