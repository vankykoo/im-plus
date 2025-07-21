package com.vanky.im.message.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vanky.im.message.entity.ConversationMsgList;
import com.vanky.im.message.mapper.ConversationMsgListMapper;
import com.vanky.im.message.service.ConversationMsgListService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
* @author vanky
* @description 针对表【conversation_msg_list】的数据库操作Service实现
* @createDate 2025-06-06
*/
@Slf4j
@Service
public class ConversationMsgListServiceImpl extends ServiceImpl<ConversationMsgListMapper, ConversationMsgList>
    implements ConversationMsgListService {

} 