package com.vanky.im.message.handler.impl;

import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.message.handler.ImMessageHandler;
import com.vanky.im.message.handler.MessageHandlerStats;
import com.vanky.im.message.handler.MessageProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一消息处理器实现
 * 
 * @author vanky
 * @create 2025-08-09
 * @description 统一消息分发器实现，提供消息路由、异常处理和监控功能
 */
@Slf4j
@Component
public class ImMessageHandlerImpl implements ImMessageHandler {
    
    /** 消息处理器映射 */
    private final Map<Integer, MessageProcessor> processors = new ConcurrentHashMap<>();
    
    /** 处理统计信息 */
    private final MessageHandlerStats stats = new MessageHandlerStats();
    
    @PostConstruct
    public void init() {
        log.info("ImMessageHandler初始化完成，支持动态注册消息处理器");
    }
    
    @Override
    public void handleMessage(ChatMessage chatMessage, String conversationId) throws Exception {
        int messageType = chatMessage.getType();
        // 优先从ThreadLocal获取开始时间，以包含MQ消费的等待时间
        Long holderStartTime = com.vanky.im.message.mq.MessageProcessingTimeHolder.getStartTime();
        long startTime = (holderStartTime != null) ? holderStartTime : System.currentTimeMillis();
        stats.recordProcessStart(messageType);
        
        try {
            // 查找对应的处理器
            MessageProcessor processor = processors.get(messageType);
            if (processor == null) {
                log.warn("未找到消息类型 {} 的处理器 - 消息ID: {}, 会话ID: {}", 
                        messageType, chatMessage.getUid(), conversationId);
                throw new IllegalArgumentException("不支持的消息类型: " + messageType);
            }
            
            // 执行消息处理
            processor.process(chatMessage, conversationId);
            
            // 记录成功
            long endTime = System.currentTimeMillis();
            stats.recordProcessSuccess(messageType, startTime);
            long duration = endTime - startTime;
            
            log.info("消息处理成功 - 类型: {}, 消息ID: {}, 会话ID: {}, 处理器: {}, 耗时: {}ms",
                    messageType, chatMessage.getUid(), conversationId, processor.getProcessorName(), duration);
            
        } catch (Exception e) {
            // 记录失败
            long endTime = System.currentTimeMillis();
            stats.recordProcessFailure(messageType, startTime);
            long duration = endTime - startTime;

            log.error("消息处理失败 - 类型: {}, 消息ID: {}, 会话ID: {}, 处理器: {}, 耗时: {}ms, 错误: {}",
                    messageType, chatMessage.getUid(), conversationId, "N/A", duration, e.getMessage(), e);
            
            // 重新抛出异常，由调用方决定重试策略
            throw e;
        }
    }
    
    @Override
    public void registerProcessor(int messageType, MessageProcessor processor) {
        if (processor == null) {
            throw new IllegalArgumentException("MessageProcessor不能为null");
        }
        
        MessageProcessor oldProcessor = processors.put(messageType, processor);
        
        if (oldProcessor != null) {
            log.warn("消息类型 {} 的处理器被替换: {} -> {}", 
                    messageType, oldProcessor.getProcessorName(), processor.getProcessorName());
        } else {
            log.info("注册消息处理器 - 类型: {} ({}), 处理器: {}", 
                    messageType, MessageTypeConstants.getMessageTypeLabel(messageType), 
                    processor.getProcessorName());
        }
    }
    
    @Override
    public MessageHandlerStats getStats() {
        return stats;
    }
    
    /**
     * 获取已注册的处理器数量
     * 
     * @return 处理器数量
     */
    public int getProcessorCount() {
        return processors.size();
    }
    
    /**
     * 获取支持的消息类型
     * 
     * @return 消息类型数组
     */
    public int[] getSupportedMessageTypes() {
        return processors.keySet().stream().mapToInt(Integer::intValue).toArray();
    }
    
    /**
     * 检查是否支持指定消息类型
     * 
     * @param messageType 消息类型
     * @return true-支持，false-不支持
     */
    public boolean isMessageTypeSupported(int messageType) {
        return processors.containsKey(messageType);
    }
}
