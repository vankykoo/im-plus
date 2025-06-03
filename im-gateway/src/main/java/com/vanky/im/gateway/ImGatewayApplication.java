package com.vanky.im.gateway;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author vanky
 * @create 2025/6/5
 * @description IM 网关应用程序入口
 * 
 * 这是 IM 网关的唯一启动类，负责启动 Spring Boot 应用和 Netty 服务器。
 * Netty 服务器（TCP、UDP、WebSocket）的启动由 NettyServerRunner 负责，
 * 它实现了 ApplicationRunner 接口，在 Spring Boot 应用启动完成后初始化并启动 Netty 服务器。
 */
@SpringBootApplication
@MapperScan("com.vanky.im.gateway.mapper")
public class ImGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImGatewayApplication.class, args);
    }
}