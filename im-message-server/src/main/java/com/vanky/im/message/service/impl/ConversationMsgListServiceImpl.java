package com.vanky.im.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vanky.im.message.entity.ConversationMsgList;
import com.vanky.im.message.mapper.ConversationMsgListMapper;
import com.vanky.im.message.service.ConversationMsgListService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author vanky
* @description 针对表【conversation_msg_list】的数据库操作Service实现
* @createDate 2025-06-06
*/
@Slf4j
@Service
public class ConversationMsgListServiceImpl extends ServiceImpl<ConversationMsgListMapper, ConversationMsgList>
    implements ConversationMsgListService {

    @Override
    public List<ConversationMsgList> getMessagesAfterSeq(String conversationId, Long afterSeq, Integer limit) {
        // {{CHENGQI:
        // Action: Added; Timestamp: 2025-08-02 21:41:41 +08:00; Reason: 实现读扩散模式的消息查询方法;
        // }}
        // {{START MODIFICATIONS}}
        QueryWrapper<ConversationMsgList> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("conversation_id", conversationId)
                   .gt("seq", afterSeq)
                   .orderByAsc("seq");

        if (limit != null && limit > 0) {
            queryWrapper.last("LIMIT " + limit);
        }

        List<ConversationMsgList> result = list(queryWrapper);
        log.debug("查询会话消息完成 - 会话ID: {}, afterSeq: {}, limit: {}, 结果数量: {}",
                conversationId, afterSeq, limit, result.size());

        return result;
        // {{END MODIFICATIONS}}
    }

    @Override
    public Long getMaxSeq(String conversationId) {
        // {{CHENGQI:
        // Action: Added; Timestamp: 2025-08-02 21:41:41 +08:00; Reason: 实现获取会话最大seq的方法;
        // }}
        // {{START MODIFICATIONS}}
        QueryWrapper<ConversationMsgList> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("conversation_id", conversationId)
                   .orderByDesc("seq")
                   .last("LIMIT 1");

        ConversationMsgList latest = getOne(queryWrapper);
        Long maxSeq = latest != null ? latest.getSeq() : 0L;

        log.debug("获取会话最大seq - 会话ID: {}, 最大seq: {}", conversationId, maxSeq);
        return maxSeq;
        // {{END MODIFICATIONS}}
    }
}