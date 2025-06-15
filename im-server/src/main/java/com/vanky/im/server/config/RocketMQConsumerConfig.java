package com.vanky.im.server.config;

import com.vanky.im.server.mq.ConversationMessageConsumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author vanky
 * @create 2025/6/5
 * @description RocketMQ消费者配置类
 */
@Slf4j
@Configuration
public class RocketMQConsumerConfig {

    @Value("${rocketmq.name-server:localhost:9876}")
    private String nameServer;

    @Value("${rocketmq.consumer.group:im-server-consumer-group}")
    private String consumerGroup;

    @Value("${rocketmq.consumer.consume-thread-min:20}")
    private int consumeThreadMin;

    @Value("${rocketmq.consumer.consume-thread-max:64}")
    private int consumeThreadMax;

    @Autowired
    private ConversationMessageConsumer conversationMessageConsumer;

    /**
     * 初始化会话消息消费者
     */
    @Bean
    public DefaultMQPushConsumer conversationConsumer() throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        consumer.setConsumeThreadMin(consumeThreadMin);
        consumer.setConsumeThreadMax(consumeThreadMax);
        
        // 订阅conversation_im_topic主题的所有消息
        consumer.subscribe("conversation_im_topic", "*");
        
        // 注册消息监听器
        consumer.registerMessageListener(conversationMessageConsumer);
        
        // 启动消费者
        consumer.start();
        
        log.info("RocketMQ消费者启动成功 - 消费者组: {}, NameServer: {}, 订阅主题: conversation_im_topic", 
                consumerGroup, nameServer);
        
        return consumer;
    }
}