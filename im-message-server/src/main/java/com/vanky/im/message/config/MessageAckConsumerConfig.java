package com.vanky.im.message.config;

import com.vanky.im.common.constant.TopicConstants;
import com.vanky.im.message.mq.MessageAckConsumer;
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
 * ACK消息消费者配置
 * 配置专门处理ACK消息的并发消费者
 * 
 * @author vanky
 * @create 2025-08-09
 * @description ACK消息消费者配置，使用并发消费模式提升ACK处理性能
 */
@Slf4j
@Configuration
public class MessageAckConsumerConfig {
    
    @Value("${rocketmq.name-server}")
    private String nameServer;
    
    @Value("${rocketmq.consumer.ack.consume-thread-min:10}")
    private int consumeThreadMin;
    
    @Value("${rocketmq.consumer.ack.consume-thread-max:30}")
    private int consumeThreadMax;
    
    @Value("${rocketmq.consumer.ack.consume-timeout:10000}")
    private long consumeTimeout;
    
    @Value("${rocketmq.consumer.ack.max-reconsume-times:3}")
    private int maxReconsumeTimes;
    
    @Value("${rocketmq.consumer.ack.consume-message-batch-max-size:10}")
    private int consumeMessageBatchMaxSize;
    
    @Autowired
    private MessageAckConsumer messageAckConsumer;
    
    @Bean(name = "messageAckMQPushConsumer", destroyMethod = "shutdown")
    public MQPushConsumer messageAckMQPushConsumer() throws MQClientException {
        log.info("初始化ACK消息消费者，消费者组: {}, 订阅主题: {}", 
                TopicConstants.CONSUMER_GROUP_MESSAGE_ACK, 
                TopicConstants.TOPIC_MESSAGE_ACK);
        
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(TopicConstants.CONSUMER_GROUP_MESSAGE_ACK);
        consumer.setNamesrvAddr(nameServer);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setMessageModel(MessageModel.CLUSTERING);
        
        // 设置消费线程数（ACK消息使用更多线程提升并发性能）
        consumer.setConsumeThreadMin(consumeThreadMin);
        consumer.setConsumeThreadMax(consumeThreadMax);
        
        // 设置消费超时时间（ACK消息处理较快，设置较短超时）
        consumer.setConsumeTimeout(consumeTimeout);
        
        // 设置最大重试次数
        consumer.setMaxReconsumeTimes(maxReconsumeTimes);
        
        // 设置批量消费最大消息数（ACK消息可以批量处理）
        consumer.setConsumeMessageBatchMaxSize(consumeMessageBatchMaxSize);
        
        try {
            // 订阅ACK消息主题，接收所有ACK消息
            consumer.subscribe(TopicConstants.TOPIC_MESSAGE_ACK, "*");
            
            // 注册消息监听器
            consumer.registerMessageListener(messageAckConsumer);
            
            // 启动消费者
            consumer.start();
            log.info("ACK消息消费者启动成功，消费者组: {}, 订阅主题: {}, 线程数: {}-{}", 
                    TopicConstants.CONSUMER_GROUP_MESSAGE_ACK, 
                    TopicConstants.TOPIC_MESSAGE_ACK,
                    consumeThreadMin, consumeThreadMax);
            
        } catch (MQClientException e) {
            log.error("ACK消息消费者启动失败", e);
            throw e;
        }
        
        return consumer;
    }
}
