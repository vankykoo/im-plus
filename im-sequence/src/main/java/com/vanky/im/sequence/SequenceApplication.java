package com.vanky.im.sequence;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * IM Sequence Service Application
 * 高性能序列号生成服务
 * 
 * @author vanky
 * @since 2025-08-11
 */
@SpringBootApplication
@EnableAsync
@MapperScan("com.vanky.im.sequence.mapper")
public class SequenceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SequenceApplication.class, args);
        System.out.println("IM Sequence Service started successfully on port 8084");
    }
}
