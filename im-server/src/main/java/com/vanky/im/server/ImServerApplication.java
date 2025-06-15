package com.vanky.im.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author vanky
 * @create 2025/6/5
 * @description IM服务器启动类
 */
@SpringBootApplication(scanBasePackages = {
        "com.vanky.im.server",
        "com.vanky.im.common"
})
@MapperScan("com.vanky.im.server.mapper")
public class ImServerApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ImServerApplication.class, args);
    }
}