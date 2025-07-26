package com.vanky.im.message.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.vanky.im.message.entity.PrivateMessage;

/**
* @author vanky
* @description 针对表【private_message】的数据库操作Service
* @createDate 2025-06-06
*/
public interface PrivateMessageService extends IService<PrivateMessage> {

    /**
     * 根据消息ID查询私聊消息
     * @param msgId 消息ID
     * @return 私聊消息实体
     */
    PrivateMessage getByMsgId(String msgId);
}