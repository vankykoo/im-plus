package com.vanky.im.gateway.timeout.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 消息超时重发配置类
 * 
 * @author vanky
 * @create 2025/7/27
 * @description 管理消息超时重发相关的配置参数
 */
@Data
@Component
@ConfigurationProperties(prefix = "message.timeout")
public class TimeoutConfig {
    
    /**
     * 是否启用超时重发机制
     */
    private boolean enabled = true;
    
    /**
     * 时间轮大小，必须是2的幂
     */
    private int wheelSize = 512;
    
    /**
     * 每个tick的时间间隔，单位毫秒
     */
    private long tickDuration = 100L;
    
    /**
     * 默认超时时间，单位毫秒
     */
    private long defaultTimeout = 5000L;
    
    /**
     * 最大重试次数
     */
    private int maxRetryCount = 3;
    
    /**
     * 重试退避基数
     */
    private int retryBackoffBase = 2;
    
    /**
     * 最大退避时间，单位毫秒
     */
    private long retryBackoffMax = 30000L;
    
    /**
     * 时间轮线程名称
     */
    private String tickerThreadName = "message-timeout-ticker";
    
    /**
     * 配置验证
     */
    @PostConstruct
    public void validateConfig() {
        if (!enabled) {
            return;
        }
        
        // 验证时间轮大小必须是2的幂
        if (wheelSize <= 0 || (wheelSize & (wheelSize - 1)) != 0) {
            throw new IllegalArgumentException("wheelSize must be a positive power of 2, but was: " + wheelSize);
        }
        
        // 验证tick间隔
        if (tickDuration <= 0) {
            throw new IllegalArgumentException("tickDuration must be positive, but was: " + tickDuration);
        }
        
        // 验证默认超时时间
        if (defaultTimeout <= 0) {
            throw new IllegalArgumentException("defaultTimeout must be positive, but was: " + defaultTimeout);
        }
        
        // 验证最大重试次数
        if (maxRetryCount < 0) {
            throw new IllegalArgumentException("maxRetryCount must be non-negative, but was: " + maxRetryCount);
        }
        
        // 验证退避基数
        if (retryBackoffBase < 1) {
            throw new IllegalArgumentException("retryBackoffBase must be at least 1, but was: " + retryBackoffBase);
        }
        
        // 验证最大退避时间
        if (retryBackoffMax <= 0) {
            throw new IllegalArgumentException("retryBackoffMax must be positive, but was: " + retryBackoffMax);
        }
        
        // 验证默认超时时间不能超过一轮的时间
        long maxSingleRoundTimeout = wheelSize * tickDuration;
        if (defaultTimeout > maxSingleRoundTimeout) {
            throw new IllegalArgumentException(
                String.format("defaultTimeout (%d ms) exceeds maximum single round timeout (%d ms). " +
                    "Consider increasing wheelSize or decreasing tickDuration.", 
                    defaultTimeout, maxSingleRoundTimeout));
        }
    }
    
    /**
     * 计算指定重试次数的退避时间
     * 
     * @param retryCount 重试次数
     * @return 退避时间，单位毫秒
     */
    public long calculateBackoffTimeout(int retryCount) {
        if (retryCount <= 0) {
            return defaultTimeout;
        }
        
        // 指数退避：base^retryCount * defaultTimeout
        long backoff = (long) (Math.pow(retryBackoffBase, retryCount) * defaultTimeout);
        return Math.min(backoff, retryBackoffMax);
    }
    
    /**
     * 获取时间轮掩码，用于快速计算槽位
     * 
     * @return 掩码值
     */
    public int getWheelMask() {
        return wheelSize - 1;
    }
}
