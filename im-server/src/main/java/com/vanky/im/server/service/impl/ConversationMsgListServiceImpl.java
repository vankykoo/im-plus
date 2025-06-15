package com.vanky.im.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vanky.im.common.constant.ErrorConstants;
import com.vanky.im.common.exception.BusinessException;
import com.vanky.im.server.entity.ConversationMsgList;
import com.vanky.im.server.service.ConversationMsgListService;
import com.vanky.im.server.mapper.ConversationMsgListMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
* @author 86180
* @description 针对表【conversation_msg_list】的数据库操作Service实现
* @createDate 2025-06-03 21:43:35
*/
@Slf4j
@Service
public class ConversationMsgListServiceImpl extends ServiceImpl<ConversationMsgListMapper, ConversationMsgList>
    implements ConversationMsgListService{

    @Override
    @Transactional
    public Long getNextSeq(String conversationId) {
        try {
            // 查询该会话的最大序列号
            QueryWrapper<ConversationMsgList> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("conversation_id", conversationId)
                       .orderByDesc("seq")
                       .last("LIMIT 1");
            
            ConversationMsgList lastMsg = this.getOne(queryWrapper);
            
            if (lastMsg == null) {
                // 如果是第一条消息，从1开始
                return 1L;
            } else {
                // 返回下一个序列号
                return lastMsg.getSeq() + 1;
            }
        } catch (Exception e) {
            log.error("获取会话序列号失败 - 会话ID: {}, 错误: {}", conversationId, e.getMessage(), e);
            throw new BusinessException(ErrorConstants.CONVERSATION_SEQ_GET_FAILED_CODE, 
                                       ErrorConstants.CONVERSATION_SEQ_GET_FAILED_MSG, e);
        }
    }

    @Override
    @Transactional
    public boolean addMessageToConversation(String conversationId, Long msgId, Long seq) {
        try {
            ConversationMsgList msgList = new ConversationMsgList();
            msgList.setConversationId(conversationId);
            msgList.setMsgId(msgId);
            msgList.setSeq(seq);
            
            boolean saved = this.save(msgList);
            
            if (saved) {
                log.debug("消息添加到会话链成功 - 会话ID: {}, 消息ID: {}, 序列号: {}", 
                         conversationId, msgId, seq);
            } else {
                log.error("消息添加到会话链失败 - 会话ID: {}, 消息ID: {}, 序列号: {}", 
                         conversationId, msgId, seq);
            }
            
            return saved;
        } catch (Exception e) {
            log.error("添加消息到会话链时发生错误 - 会话ID: {}, 消息ID: {}, 序列号: {}, 错误: {}", 
                     conversationId, msgId, seq, e.getMessage(), e);
            throw new BusinessException(ErrorConstants.MSG_ADD_TO_CONVERSATION_FAILED_CODE, 
                                       ErrorConstants.MSG_ADD_TO_CONVERSATION_FAILED_MSG, e);
        }
    }
}




