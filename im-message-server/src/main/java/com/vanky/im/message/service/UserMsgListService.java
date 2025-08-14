package com.vanky.im.message.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.vanky.im.message.entity.UserMsgList;

/**
* @author vanky
* @description 针对表【user_msg_list】的数据库操作Service
* @createDate 2025-06-06
*/
public interface UserMsgListService extends IService<UserMsgList> {

    /**
     * 保存写扩散消息记录
     * @param msgId 消息ID
     * @param conversationId 会话ID
     * @param seq 序列号
     * @param fromUserId 发送方用户ID
     * @param toUserId 接收方用户ID
     */
    void saveWriteExpandRecords(String msgId, String conversationId, Long seq, String fromUserId, String toUserId);

    /**
     * 为单个用户插入消息记录并获得用户级全局seq
     * @param userId 用户ID
     * @param msgId 消息ID
     * @param conversationId 会话ID
     * @return 用户级全局seq
     */
    Long saveUserMessageRecord(String userId, String msgId, String conversationId);

    /**
     * 获取指定用户的最大序列号
     * 用于序列号服务恢复时查询数据库中的最大序列号
     *
     * @param userId 用户ID
     * @return 用户最大序列号，如果用户无消息则返回0
     */
    Long getMaxSeqByUserId(String userId);
}