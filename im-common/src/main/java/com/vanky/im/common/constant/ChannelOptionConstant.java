package com.vanky.im.common.constant;

/**
 * @author vanky
 * @create 2025/5/25 11:32
 * @description 通道选项相关常量
 */
public interface ChannelOptionConstant {

    /**
     * 等待连接队列的最大长度
     */
    int SO_BACKLOG = 128;
    
    /**
     * 接收缓冲区大小
     */
    int SO_RCVBUF = 1024 * 1024;
    
    /**
     * 发送缓冲区大小
     */
    int SO_SNDBUF = 1024 * 1024;
    
    /**
     * HTTP消息聚合最大长度
     */
    int MAX_CONTENT_LENGTH = 8192;
    
} 