package com.vanky.im.message.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.vanky.im.message.entity.Message;

import java.util.List;

/**
 * 统一消息服务接口
 * 
 * @author vanky
 * @description 针对表【message】的数据库操作Service
 * @createDate 2025-07-28
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-07-28 22:32:26 +08:00; Reason: 创建MessageService接口，提供统一的消息服务;
// }}
// {{START MODIFICATIONS}}
public interface MessageService extends IService<Message> {

    /**
     * 根据消息ID查询消息
     * @param msgId 消息ID
     * @return 消息实体
     */
    Message getByMsgId(String msgId);

    /**
     * 根据消息ID查询消息（Long类型）
     * @param msgId 消息ID
     * @return 消息实体
     */
    Message getByMsgId(Long msgId);

    /**
     * 根据会话ID和消息类型查询消息列表
     * @param conversationId 会话ID
     * @param msgType 消息类型
     * @param limit 限制数量
     * @return 消息列表
     */
    List<Message> getByConversationIdAndType(String conversationId, Integer msgType, Integer limit);

    /**
     * 根据会话ID、消息类型和序列号范围查询消息
     * @param conversationId 会话ID
     * @param msgType 消息类型
     * @param startSeq 起始序列号
     * @param endSeq 结束序列号
     * @param limit 限制数量
     * @return 消息列表
     */
    List<Message> getByConversationIdAndTypeAndSeqRange(String conversationId, Integer msgType, 
                                                        Long startSeq, Long endSeq, Integer limit);

    /**
     * 更新消息状态
     * @param msgId 消息ID
     * @param status 新状态
     * @return 是否更新成功
     */
    boolean updateMessageStatus(String msgId, Integer status);

    /**
     * 更新消息状态（Long类型）
     * @param msgId 消息ID
     * @param status 新状态
     * @return 是否更新成功
     */
    boolean updateMessageStatus(Long msgId, Integer status);

    /**
     * 根据发送者ID和消息类型查询消息列表
     * @param senderId 发送者ID
     * @param msgType 消息类型
     * @param limit 限制数量
     * @return 消息列表
     */
    List<Message> getBySenderIdAndType(Long senderId, Integer msgType, Integer limit);

    /**
     * 根据消息ID列表批量查询消息
     * @param msgIds 消息ID列表
     * @return 消息列表
     */
    List<Message> listByMsgIds(List<Long> msgIds);
}
// {{END MODIFICATIONS}}
