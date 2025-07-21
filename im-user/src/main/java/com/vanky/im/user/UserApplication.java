package com.vanky.im.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author vanky
 * @date 2025/5/25
 * @description 用户服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.vanky.im.user", "com.vanky.im.common"})
@EnableTransactionManagement
@MapperScan("com.vanky.im.user.mapper")
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}