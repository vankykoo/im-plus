package com.vanky.im.gateway.config;

import com.vanky.im.gateway.mq.GatewayPushMessageConsumer;
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
 * Gateway推送消息消费者配置
 */
@Slf4j
@Configuration
public class GatewayPushConsumerConfig {
    
    @Value("${rocketmq.name-server}")
    private String nameServer;
    
    @Value("${rocketmq.push-consumer.group:im-gateway-push-consumer-group}")
    private String consumerGroup;
    
    @Value("${rocketmq.push-consumer.consume-thread-min:5}")
    private int consumeThreadMin;
    
    @Value("${rocketmq.push-consumer.consume-thread-max:20}")
    private int consumeThreadMax;
    
    @Value("${message.push.topic:TOPIC_PUSH_TO_GATEWAY}")
    private String pushToGatewayTopic;
    
    @Value("${server.node-id:}")
    private String gatewayNodeId;

    @Autowired
    private GatewayPushMessageConsumer messageConsumer;

    @Autowired
    private com.vanky.im.gateway.config.GatewayInstanceManager gatewayInstanceManager;
    
    @Bean(name = "pushConsumer", destroyMethod = "shutdown")
    public MQPushConsumer pushConsumer() throws MQClientException {
        log.info("初始化Gateway推送消费者，服务节点ID: {}, 订阅主题: {}", gatewayNodeId, pushToGatewayTopic);
        
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setMessageModel(MessageModel.CLUSTERING);
        
        // 设置消费线程数
        consumer.setConsumeThreadMin(consumeThreadMin);
        consumer.setConsumeThreadMax(consumeThreadMax);
        
        try {
            // 获取动态网关实例ID
            String currentGatewayId = gatewayInstanceManager.getGatewayInstanceId();

            // 订阅主题，并且指定Tag为当前网关节点ID，实现消息的精准路由
            // Tag过滤表达式格式：TagA || TagB || TagC
            consumer.subscribe(pushToGatewayTopic, currentGatewayId);

            // 注册消息监听器
            consumer.registerMessageListener(messageConsumer);

            // 启动消费者
            consumer.start();
            log.info("Gateway推送消费者启动成功，节点ID: {}, 订阅主题: {}", currentGatewayId, pushToGatewayTopic);

        } catch (MQClientException e) {
            log.error("Gateway推送消费者启动失败", e);
            throw e;
        }
        
        return consumer;
    }
} 