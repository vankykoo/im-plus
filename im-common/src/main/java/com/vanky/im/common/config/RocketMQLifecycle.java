package com.vanky.im.common.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

/**
 * @author vanky
 * @create 2025/6/5
 * @description RocketMQ生命周期管理，确保应用关闭时能够正常关闭RocketMQ生产者
 */
@Slf4j
@Component
public class RocketMQLifecycle implements ApplicationListener<ContextClosedEvent> {

    private final DefaultMQProducer producer;

    @Autowired
    public RocketMQLifecycle(DefaultMQProducer producer) {
        this.producer = producer;
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        if (producer != null) {
            log.info("关闭RocketMQ生产者...");
            producer.shutdown();
            log.info("RocketMQ生产者已关闭");
        }
    }
}