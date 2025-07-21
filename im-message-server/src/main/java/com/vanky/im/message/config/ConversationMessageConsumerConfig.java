package com.vanky.im.message.config;

import com.vanky.im.common.constant.TopicConstants;
import com.vanky.im.message.mq.ConversationMessageConsumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.MQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 统一会话消息消费者配置
 * 配置原生RocketMQ消费者，处理私聊和群聊消息
 */
@Slf4j
@Configuration
public class ConversationMessageConsumerConfig {
    
    @Value("${rocketmq.name-server}")
    private String nameServer;
    
    @Value("${rocketmq.consumer.consume-thread-min:5}")
    private int consumeThreadMin;
    
    @Value("${rocketmq.consumer.consume-thread-max:20}")
    private int consumeThreadMax;
    
    @Value("${rocketmq.consumer.consume-timeout:15000}")
    private long consumeTimeout;
    
    @Value("${rocketmq.consumer.max-reconsume-times:3}")
    private int maxReconsumeTimes;
    
    @Value("${rocketmq.consumer.consume-message-batch-max-size:1}")
    private int consumeMessageBatchMaxSize;
    
    @Autowired
    private ConversationMessageConsumer conversationMessageConsumer;
    
    @Bean(name = "conversationMQPushConsumer", destroyMethod = "shutdown")
    public MQPushConsumer conversationMQPushConsumer() throws MQClientException {
        log.info("初始化统一会话消息消费者，消费者组: {}, 订阅主题: {}", 
                TopicConstants.CONSUMER_GROUP_CONVERSATION_MESSAGE, 
                TopicConstants.TOPIC_CONVERSATION_MESSAGE);
        
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(TopicConstants.CONSUMER_GROUP_CONVERSATION_MESSAGE);
        consumer.setNamesrvAddr(nameServer);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setMessageModel(MessageModel.CLUSTERING);
        
        // 设置消费线程数
        consumer.setConsumeThreadMin(consumeThreadMin);
        consumer.setConsumeThreadMax(consumeThreadMax);
        
        // 设置消费超时时间
        consumer.setConsumeTimeout(consumeTimeout);
        
        // 设置最大重试次数
        consumer.setMaxReconsumeTimes(maxReconsumeTimes);
        
        // 设置批量消费最大消息数
        consumer.setConsumeMessageBatchMaxSize(consumeMessageBatchMaxSize);
        
        try {
            // 订阅统一会话消息主题，接收所有消息
            consumer.subscribe(TopicConstants.TOPIC_CONVERSATION_MESSAGE, "*");
            
            // 注册消息监听器
            consumer.registerMessageListener(conversationMessageConsumer);
            
            // 启动消费者
            consumer.start();
            log.info("统一会话消息消费者启动成功，消费者组: {}, 订阅主题: {}", 
                    TopicConstants.CONSUMER_GROUP_CONVERSATION_MESSAGE, 
                    TopicConstants.TOPIC_CONVERSATION_MESSAGE);
            
        } catch (MQClientException e) {
            log.error("统一会话消息消费者启动失败", e);
            throw e;
        }
        
        return consumer;
    }
}
