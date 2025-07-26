package com.vanky.im.user;

import com.vanky.im.common.config.RocketMQConfig;
import com.vanky.im.common.config.RocketMQLifecycle;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author vanky
 * @date 2025/5/25
 * @description 用户服务启动类
 */
@SpringBootApplication
@EnableTransactionManagement
@MapperScan("com.vanky.im.user.mapper")
@ComponentScan(
    basePackages = {"com.vanky.im.user", "com.vanky.im.common"},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
            RocketMQConfig.class,
            RocketMQLifecycle.class
        })
    }
)
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}