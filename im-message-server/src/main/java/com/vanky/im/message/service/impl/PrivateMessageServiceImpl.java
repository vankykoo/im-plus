package com.vanky.im.message.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vanky.im.message.entity.PrivateMessage;
import com.vanky.im.message.mapper.PrivateMessageMapper;
import com.vanky.im.message.service.PrivateMessageService;
import org.springframework.stereotype.Service;

/**
* @author vanky
* @description 针对表【private_message】的数据库操作Service实现
* @createDate 2025-06-06
*/
@Service
public class PrivateMessageServiceImpl extends ServiceImpl<PrivateMessageMapper, PrivateMessage>
    implements PrivateMessageService{

} 