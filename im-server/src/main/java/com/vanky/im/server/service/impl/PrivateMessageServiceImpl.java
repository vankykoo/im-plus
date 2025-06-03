package com.vanky.im.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vanky.im.server.entity.PrivateMessage;
import com.vanky.im.server.service.PrivateMessageService;
import com.vanky.im.server.mapper.PrivateMessageMapper;
import org.springframework.stereotype.Service;

/**
* @author 86180
* @description 针对表【private_message】的数据库操作Service实现
* @createDate 2025-06-03 21:43:35
*/
@Service
public class PrivateMessageServiceImpl extends ServiceImpl<PrivateMessageMapper, PrivateMessage>
    implements PrivateMessageService{

}




