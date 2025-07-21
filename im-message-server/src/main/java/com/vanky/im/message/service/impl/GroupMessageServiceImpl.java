package com.vanky.im.message.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vanky.im.message.entity.GroupMessage;
import com.vanky.im.message.mapper.GroupMessageMapper;
import com.vanky.im.message.service.GroupMessageService;
import org.springframework.stereotype.Service;

/**
* @author vanky
* @description 针对表【group_message】的数据库操作Service实现
* @createDate 2025-06-06
*/
@Service
public class GroupMessageServiceImpl extends ServiceImpl<GroupMessageMapper, GroupMessage>
    implements GroupMessageService{

} 