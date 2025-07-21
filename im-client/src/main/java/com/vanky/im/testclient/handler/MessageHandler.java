package com.vanky.im.testclient.handler;

import com.vanky.im.common.protocol.ChatMessage;


/**
 * 消息处理器接口
 */
public interface MessageHandler {
    
    /**
     * 处理接收到的消息
     * @param message 聊天消息
     */
    void handleMessage(ChatMessage message);
    
    /**
     * 处理连接状态变化
     * @param connected 是否已连接
     */
    default void onConnectionStatusChanged(boolean connected) {
        // 默认空实现
    }
    
    /**
     * 处理登录状态变化
     * @param loggedIn 是否已登录
     */
    default void onLoginStatusChanged(boolean loggedIn) {
        // 默认空实现
    }
    
    /**
     * 处理错误
     * @param error 错误信息
     */
    default void onError(String error) {
        // 默认空实现
    }
}