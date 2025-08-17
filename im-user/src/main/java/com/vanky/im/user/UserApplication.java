package com.vanky.im.user;

import com.vanky.im.common.config.RocketMQConfig;
import com.vanky.im.common.config.RocketMQLifecycle;
import com.vanky.im.common.config.RedisConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author vanky
 * @date 2025/5/25
 * @description 用户服务启动类
 *
 * 启用Nacos服务发现，支持分布式部署和服务注册
 */
@SpringBootApplication
@EnableDiscoveryClient  // 启用Nacos服务发现
@EnableTransactionManagement
@MapperScan("com.vanky.im.user.mapper")
@ComponentScan(
    basePackages = {"com.vanky.im.user", "com.vanky.im.common.util", "com.vanky.im.common.model", "com.vanky.im.common.constant", "com.vanky.im.common.exception"},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
            RocketMQConfig.class,
            RocketMQLifecycle.class
        })
    }
)
@Import(RedisConfig.class)
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}