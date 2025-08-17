package com.vanky.im.message;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author vanky
 * @create 2025/6/6
 * @description IM 消息服务应用程序入口
 *
 * 启用Nacos服务发现，支持分布式部署和服务注册
 */
@SpringBootApplication(scanBasePackages = {"com.vanky.im.message", "com.vanky.im.common"})
@EnableDiscoveryClient  // 启用Nacos服务发现
@EnableTransactionManagement
@EnableFeignClients
@MapperScan("com.vanky.im.message.mapper")
public class MessageServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessageServerApplication.class, args);
    }
}