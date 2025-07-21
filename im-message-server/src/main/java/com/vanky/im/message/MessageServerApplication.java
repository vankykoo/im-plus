package com.vanky.im.message;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author vanky
 * @create 2025/6/6
 * @description IM 消息服务应用程序入口
 */
@SpringBootApplication(scanBasePackages = {"com.vanky.im.message", "com.vanky.im.common"})
@EnableTransactionManagement
@MapperScan("com.vanky.im.message.mapper")
public class MessageServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessageServerApplication.class, args);
    }
}