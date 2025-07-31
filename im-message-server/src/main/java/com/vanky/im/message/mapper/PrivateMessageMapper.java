package com.vanky.im.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vanky.im.message.entity.PrivateMessage;

/**
* @author vanky
* @description 针对表【private_message】的数据库操作Mapper
* @deprecated 该Mapper已被统一的 MessageMapper 替代，请使用 com.vanky.im.message.mapper.MessageMapper
* @createDate 2025-06-06
* @Entity com.vanky.im.message.entity.PrivateMessage
*/
@Deprecated
public interface PrivateMessageMapper extends BaseMapper<PrivateMessage> {

}