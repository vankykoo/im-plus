package com.vanky.im.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vanky.im.server.entity.GroupMessage;
import com.vanky.im.server.service.GroupMessageService;
import com.vanky.im.server.mapper.GroupMessageMapper;
import org.springframework.stereotype.Service;

/**
* @author 86180
* @description 针对表【group_message】的数据库操作Service实现
* @createDate 2025-06-03 21:43:35
*/
@Service
public class GroupMessageServiceImpl extends ServiceImpl<GroupMessageMapper, GroupMessage>
    implements GroupMessageService{

}




