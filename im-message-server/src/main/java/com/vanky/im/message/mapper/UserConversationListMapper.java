package com.vanky.im.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vanky.im.message.dto.ConversationOverviewDTO;
import com.vanky.im.message.entity.UserConversationList;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
* @author vanky
* @description 针对表【user_conversation_list】的数据库操作Mapper
* @createDate 2025-06-06
* @Entity com.vanky.im.message.entity.UserConversationList
*/
public interface UserConversationListMapper extends BaseMapper<UserConversationList> {

    /**
     * 查询用户的会话概览列表（高效JOIN查询）
     *
     * @param userId 用户ID
     * @param limit 限制数量
     * @return 会话概览列表
     */
    List<ConversationOverviewDTO> selectConversationOverviews(@Param("userId") Long userId, @Param("limit") Integer limit);
}