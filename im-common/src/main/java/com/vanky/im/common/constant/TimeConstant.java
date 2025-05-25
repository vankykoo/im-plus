package com.vanky.im.common.constant;

/**
 * @author vanky
 * @create 2025/5/25 11:30
 * @description 时间相关常量
 */
public interface TimeConstant {

    /**
     * 心跳间隔时间，单位秒
     */
    int HEARTBEAT_INTERVAL = 60;
    
    /**
     * 服务端读空闲超时时间，单位秒
     */
    int SERVER_READ_IDLE_TIMEOUT = 180;
    
    /**
     * 写空闲和所有类型空闲不检测
     */
    int IDLE_TIME_DISABLE = 0;
    
} 