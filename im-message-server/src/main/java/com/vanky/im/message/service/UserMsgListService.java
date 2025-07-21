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
}