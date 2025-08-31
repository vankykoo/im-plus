package com.vanky.im.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.vanky.im.common.config.RedisConfig;

/**
 * @author vanky
 * @create 2025/6/5
 * @description IM 网关应用程序入口
 *
 * 这是 IM 网关的唯一启动类，负责启动 Spring Boot 应用和 Netty 服务器。
 * Netty 服务器（TCP、UDP、WebSocket）的启动由 NettyServerRunner 负责，
 * 它实现了 ApplicationRunner 接口，在 Spring Boot 应用启动完成后初始化并启动 Netty 服务器。
 *
 * 启用Nacos服务发现，支持分布式部署和服务注册
 */
@SpringBootApplication
@EnableDiscoveryClient  // 启用Nacos服务发现
@ComponentScan(
    basePackages = {"com.vanky.im.gateway", "com.vanky.im.common.util", "com.vanky.im.common.model", "com.vanky.im.common.constant", "com.vanky.im.common.exception", "com.vanky.im.common.config", "com.vanky.im.common.service"},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.vanky\\.im\\.common\\.config\\.FeignConfig")
    }
)
@EnableScheduling
public class ImGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImGatewayApplication.class, args);
    }
}