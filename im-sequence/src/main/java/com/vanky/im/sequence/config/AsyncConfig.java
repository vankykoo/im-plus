package com.vanky.im.sequence.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步配置类
 * 配置序列号持久化专用的线程池
 * 
 * @author vanky
 * @since 2025-08-11
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    @Autowired
    private SequenceConfig sequenceConfig;

    /**
     * 序列号持久化线程池
     * 
     * @return 线程池执行器
     */
    @Bean("sequencePersistenceExecutor")
    public Executor sequencePersistenceExecutor() {
        SequenceConfig.Persistence persistence = sequenceConfig.getPersistence();
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数
        executor.setCorePoolSize(persistence.getCorePoolSize());
        
        // 最大线程数
        executor.setMaxPoolSize(persistence.getMaxPoolSize());
        
        // 队列容量
        executor.setQueueCapacity(persistence.getQueueCapacity());
        
        // 线程名前缀
        executor.setThreadNamePrefix(persistence.getThreadNamePrefix());
        
        // 拒绝策略：由调用线程执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 线程空闲时间（秒）
        executor.setKeepAliveSeconds(60);
        
        // 允许核心线程超时
        executor.setAllowCoreThreadTimeOut(true);
        
        // 等待任务完成后关闭
        executor.setWaitForTasksToCompleteOnShutdown(true);
        
        // 等待时间（秒）
        executor.setAwaitTerminationSeconds(30);
        
        // 初始化
        executor.initialize();
        
        log.info("Sequence persistence thread pool initialized: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                persistence.getCorePoolSize(), persistence.getMaxPoolSize(), persistence.getQueueCapacity());
        
        return executor;
    }
}
