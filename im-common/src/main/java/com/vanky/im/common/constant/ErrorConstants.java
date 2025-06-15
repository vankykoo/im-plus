package com.vanky.im.common.constant;

/**
 * 异常常量类
 * @author vanky
 * @date 2024-12-06
 */
public class ErrorConstants {

    // ========== 消息相关异常 ==========
    
    /**
     * 消息保存失败
     */
    public static final int MSG_SAVE_FAILED_CODE = 5001;
    public static final String MSG_SAVE_FAILED_MSG = "消息保存失败";
    
    /**
     * 获取会话序列号失败
     */
    public static final int CONVERSATION_SEQ_GET_FAILED_CODE = 5002;
    public static final String CONVERSATION_SEQ_GET_FAILED_MSG = "获取会话序列号失败";
    
    /**
     * 消息添加到会话链失败
     */
    public static final int MSG_ADD_TO_CONVERSATION_FAILED_CODE = 5003;
    public static final String MSG_ADD_TO_CONVERSATION_FAILED_MSG = "消息添加到会话链失败";
    
    // ========== 用户相关异常 ==========
    
    /**
     * 用户不存在
     */
    public static final int USER_NOT_FOUND_CODE = 4001;
    public static final String USER_NOT_FOUND_MSG = "用户不存在";
    
    /**
     * 用户状态异常
     */
    public static final int USER_STATUS_ERROR_CODE = 4002;
    public static final String USER_STATUS_ERROR_MSG = "用户状态异常";
    
    /**
     * 密码错误
     */
    public static final int PASSWORD_ERROR_CODE = 4003;
    public static final String PASSWORD_ERROR_MSG = "密码错误";
    
    // ========== 会话相关异常 ==========
    
    /**
     * 会话创建失败
     */
    public static final int CONVERSATION_CREATE_FAILED_CODE = 6001;
    public static final String CONVERSATION_CREATE_FAILED_MSG = "会话创建失败";
    
    /**
     * 会话不存在
     */
    public static final int CONVERSATION_NOT_FOUND_CODE = 6002;
    public static final String CONVERSATION_NOT_FOUND_MSG = "会话不存在";
}