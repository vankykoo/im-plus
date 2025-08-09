package com.vanky.im.message.config;

import com.vanky.im.message.handler.ImMessageHandler;
import com.vanky.im.message.handler.MessageProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * 消息处理器注册配置
 * 自动注册所有MessageProcessor实现到ImMessageHandler
 * 
 * @author vanky
 * @create 2025-08-09
 * @description 消息处理器自动注册配置，扫描并注册所有消息处理器
 */
@Slf4j
@Configuration
public class MessageProcessorRegistryConfig {
    
    @Autowired
    private ImMessageHandler messageHandler;
    
    @Autowired
    private List<MessageProcessor> messageProcessors;
    
    @PostConstruct
    public void registerProcessors() {
        log.info("开始注册消息处理器，发现 {} 个处理器", messageProcessors.size());
        
        for (MessageProcessor processor : messageProcessors) {
            int[] supportedTypes = processor.getSupportedMessageTypes();
            
            for (int messageType : supportedTypes) {
                messageHandler.registerProcessor(messageType, processor);
            }
            
            log.info("注册消息处理器: {} - 支持消息类型: {}", 
                    processor.getProcessorName(), supportedTypes);
        }
        
        log.info("消息处理器注册完成，共注册 {} 个处理器", messageProcessors.size());
    }
}
