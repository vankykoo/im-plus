package com.vanky.im.gateway.config;

import com.vanky.im.gateway.timeout.MessageTimeoutManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

/**
 * 超时管理器配置类
 * 
 * @author vanky
 * @create 2025/7/27
 * @description 负责启动超时管理器
 */
@Slf4j
@Configuration
public class TimeoutManagerConfig implements ApplicationRunner {
    
    @Autowired
    private MessageTimeoutManager timeoutManager;
    
    /**
     * 应用启动后自动启动超时管理器
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (timeoutManager.isEnabled()) {
            timeoutManager.start();
            log.info("超时管理器启动完成");
        } else {
            log.info("超时管理器已禁用，跳过启动");
        }
    }
}
