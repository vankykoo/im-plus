package com.vanky.im.gateway.timeout;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.gateway.timeout.model.TimeoutStats;

/**
 * 超时管理器接口
 * 
 * @author vanky
 * @create 2025/7/27
 * @description 定义超时任务管理的核心接口
 */
public interface TimeoutManager {
    
    /**
     * 添加超时任务
     * 
     * @param ackId 消息唯一ID，用于取消任务
     * @param message 完整的消息体，用于重发
     * @param userId 目标用户ID
     * @param timeoutMs 超时时间（毫秒）
     */
    void addTask(String ackId, ChatMessage message, String userId, long timeoutMs);
    
    /**
     * 添加超时任务（使用默认超时时间）
     * 
     * @param ackId 消息唯一ID
     * @param message 消息体
     * @param userId 目标用户ID
     */
    void addTask(String ackId, ChatMessage message, String userId);
    
    /**
     * 取消超时任务
     * 
     * @param ackId 消息唯一ID
     * @return true if cancelled successfully
     */
    boolean cancelTask(String ackId);
    
    /**
     * 获取统计信息
     * 
     * @return 统计信息
     */
    TimeoutStats getStats();
    
    /**
     * 检查是否启用
     * 
     * @return true if enabled
     */
    boolean isEnabled();
    
    /**
     * 启动超时管理器
     */
    void start();
    
    /**
     * 停止超时管理器
     */
    void stop();
}
