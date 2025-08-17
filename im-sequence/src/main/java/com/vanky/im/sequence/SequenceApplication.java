package com.vanky.im.sequence;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * IM Sequence Service Application
 * 高性能序列号生成服务
 *
 * @author vanky
 * @since 2025-08-11
 * @updated 2025-08-14 - 添加 Feign 客户端支持
 * @updated 2025-08-16 - 启用Nacos服务发现，支持分布式部署
 */
@SpringBootApplication
@EnableDiscoveryClient  // 启用Nacos服务发现
@EnableAsync
@EnableFeignClients(basePackages = {"com.vanky.im.sequence.client"})
@ComponentScan(basePackages = {"com.vanky.im.sequence", "com.vanky.im.common"})
@MapperScan("com.vanky.im.sequence.mapper")
public class SequenceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SequenceApplication.class, args);
        System.out.println("IM Sequence Service started successfully on port 8084");
    }
}
