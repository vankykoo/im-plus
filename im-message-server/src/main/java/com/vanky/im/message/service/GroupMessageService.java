package com.vanky.im.message.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.vanky.im.message.entity.GroupMessage;

/**
* @author vanky
* @description 针对表【group_message】的数据库操作Service
* @createDate 2025-06-06
*/
public interface GroupMessageService extends IService<GroupMessage> {

    /**
     * 根据消息ID查询群聊消息
     * @param msgId 消息ID
     * @return 群聊消息实体
     */
    GroupMessage getByMsgId(String msgId);
}