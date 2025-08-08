package com.vanky.im.gateway.conversation;

import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.common.protocol.ChatMessage;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * 会话分发中心
 * 负责将消息根据会话ID路由到对应的处理队列
 * 
 * @author vanky
 * @create 2025/8/7
 * @description 实现会话级消息分发，确保同一会话的消息串行处理
 */
@Slf4j
@Component
public class ConversationDispatcher {
    
    @Autowired
    private ConversationProcessorConfig config;
    
    @Autowired
    private ConversationWorkerPool workerPool;
    
    private volatile boolean initialized = false;
    
    /**
     * 初始化分发器
     */
    @PostConstruct
    public void init() {
        if (!config.isEnabled()) {
            log.info("会话级串行化处理已禁用，跳过ConversationDispatcher初始化");
            return;
        }
        
        try {
            config.validate();
            log.info("初始化ConversationDispatcher - {}", config.getSummary());
            initialized = true;
            log.info("ConversationDispatcher初始化完成");
        } catch (Exception e) {
            log.error("ConversationDispatcher初始化失败", e);
            throw new RuntimeException("Failed to initialize ConversationDispatcher", e);
        }
    }
    
    /**
     * 销毁分发器
     */
    @PreDestroy
    public void destroy() {
        if (initialized) {
            log.info("正在关闭ConversationDispatcher...");
            initialized = false;
            log.info("ConversationDispatcher已关闭");
        }
    }
    
    /**
     * 分发消息到对应的会话队列
     * 
     * @param msg 聊天消息
     * @param channel 发送方的网络连接通道
     * @return true如果分发成功，false如果分发失败
     */
    public boolean dispatch(ChatMessage msg, Channel channel) {
        if (!isEnabled()) {
            if (config.isVerboseLogging()) {
                log.debug("会话级串行化处理未启用，跳过分发 - 消息类型: {}, 消息ID: {}", 
                         msg.getType(), msg.getUid());
            }
            return false;
        }
        
        try {
            // 获取或生成会话ID
            String conversationId = getOrGenerateConversationId(msg);
            if (conversationId == null || conversationId.trim().isEmpty()) {
                log.warn("无法获取会话ID，跳过分发 - 消息类型: {}, 发送方: {}, 接收方: {}", 
                        msg.getType(), msg.getFromId(), msg.getToId());
                return false;
            }
            
            // 创建会话消息包装
            ConversationMessage conversationMessage = new ConversationMessage(msg, channel, conversationId);
            
            // 提交到工作线程池
            boolean submitted = workerPool.submitMessage(conversationId, conversationMessage);

            log.info("消息分发{} - 会话ID: {}, 消息类型: {}, 消息ID: {}, 发送方: {}, 接收方: {}",
                     submitted ? "成功" : "失败", conversationId, msg.getType(), msg.getUid(),
                     msg.getFromId(), msg.getToId());
            
            return submitted;
            
        } catch (Exception e) {
            log.error("消息分发异常 - 消息类型: {}, 发送方: {}, 接收方: {}, 消息ID: {}", 
                     msg.getType(), msg.getFromId(), msg.getToId(), msg.getUid(), e);
            return false;
        }
    }
    
    /**
     * 检查分发器是否已启用并初始化
     * 
     * @return true如果已启用，false否则
     */
    public boolean isEnabled() {
        return config.isEnabled() && initialized;
    }
    
    /**
     * 获取或生成会话ID
     * 
     * @param msg 聊天消息
     * @return 会话ID，如果无法生成则返回null
     */
    private String getOrGenerateConversationId(ChatMessage msg) {
        // 优先使用消息中已有的会话ID
        String conversationId = msg.getConversationId();
        if (conversationId != null && !conversationId.trim().isEmpty()) {
            return conversationId.trim();
        }
        
        // 根据消息类型生成会话ID
        int messageType = msg.getType();
        String fromId = msg.getFromId();
        String toId = msg.getToId();
        
        if (fromId == null || toId == null) {
            log.warn("消息缺少发送方或接收方ID - 消息类型: {}, 发送方: {}, 接收方: {}", 
                    messageType, fromId, toId);
            return null;
        }
        
        if (messageType == MessageTypeConstants.PRIVATE_CHAT_MESSAGE) {
            // 私聊消息：生成private_小ID_大ID格式
            return generatePrivateConversationId(fromId, toId);
        } else if (messageType == MessageTypeConstants.GROUP_CHAT_MESSAGE) {
            // 群聊消息：生成group_群组ID格式
            return generateGroupConversationId(toId);
        } else {
            log.warn("不支持的消息类型用于会话ID生成 - 消息类型: {}", messageType);
            return null;
        }
    }
    
    /**
     * 生成私聊会话ID
     * 
     * @param fromId 发送方ID
     * @param toId 接收方ID
     * @return 私聊会话ID
     */
    private String generatePrivateConversationId(String fromId, String toId) {
        try {
            long id1 = Long.parseLong(fromId);
            long id2 = Long.parseLong(toId);
            
            if (id1 < id2) {
                return "private_" + id1 + "_" + id2;
            } else {
                return "private_" + id2 + "_" + id1;
            }
        } catch (NumberFormatException e) {
            // 如果ID不是数字，使用字符串比较
            if (fromId.compareTo(toId) < 0) {
                return "private_" + fromId + "_" + toId;
            } else {
                return "private_" + toId + "_" + fromId;
            }
        }
    }
    
    /**
     * 生成群聊会话ID
     * 
     * @param groupId 群组ID
     * @return 群聊会话ID
     */
    private String generateGroupConversationId(String groupId) {
        return "group_" + groupId;
    }
}
