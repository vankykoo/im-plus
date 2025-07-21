package com.vanky.im.common.config;

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author vanky
 * @create 2025/6/5
 * @description RocketMQ配置类
 */
@Configuration
public class RocketMQConfig {

    @Value("${rocketmq.name-server:localhost:9876}")
    private String nameServer;

    @Value("${rocketmq.producer.group:default-producer-group}")
    private String producerGroup;

    @Value("${rocketmq.producer.send-message-timeout:3000}")
    private int sendMessageTimeout;

    @Value("${rocketmq.producer.compress-message-body-threshold:4096}")
    private int compressMessageBodyThreshold;

    @Value("${rocketmq.producer.max-message-size:4194304}")
    private int maxMessageSize;

    @Value("${rocketmq.producer.retry-times-when-send-failed:2}")
    private int retryTimesWhenSendFailed;

    @Value("${rocketmq.producer.retry-times-when-send-async-failed:2}")
    private int retryTimesWhenSendAsyncFailed;

    @Value("${rocketmq.producer.retry-next-server:true}")
    private boolean retryNextServer;

    /**
     * 初始化RocketMQ生产者
     */
    @Bean
    public DefaultMQProducer defaultMQProducer() throws MQClientException {
        DefaultMQProducer producer = new DefaultMQProducer(producerGroup);
        producer.setNamesrvAddr(nameServer);
        producer.setSendMsgTimeout(sendMessageTimeout);
        producer.setCompressMsgBodyOverHowmuch(compressMessageBodyThreshold);
        producer.setMaxMessageSize(maxMessageSize);
        producer.setRetryTimesWhenSendFailed(retryTimesWhenSendFailed);
        producer.setRetryTimesWhenSendAsyncFailed(retryTimesWhenSendAsyncFailed);
        producer.setRetryAnotherBrokerWhenNotStoreOK(retryNextServer);
        producer.start();
        return producer;
    }

    /**
     * 网关推送消息生产者
     */
    @Bean(name = "gatewayPushProducer")
    public DefaultMQProducer gatewayPushProducer() throws MQClientException {
        DefaultMQProducer producer = new DefaultMQProducer("gateway-push-producer-group");
        producer.setNamesrvAddr(nameServer);
        producer.setSendMsgTimeout(sendMessageTimeout);
        producer.setRetryTimesWhenSendFailed(retryTimesWhenSendFailed);
        producer.start();
        return producer;
    }
}