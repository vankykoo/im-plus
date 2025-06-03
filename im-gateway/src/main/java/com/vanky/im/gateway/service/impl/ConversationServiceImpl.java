package com.vanky.im.gateway.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vanky.im.gateway.entity.Conversation;
import com.vanky.im.gateway.service.ConversationService;
import com.vanky.im.gateway.mapper.ConversationMapper;
import org.springframework.stereotype.Service;

/**
* @author vanky
* @description 针对表【conversation】的数据库操作Service实现
* @createDate 2025-06-03 21:37:27
*/
@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation>
    implements ConversationService{

}




