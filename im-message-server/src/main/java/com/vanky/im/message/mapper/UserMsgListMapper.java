package com.vanky.im.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vanky.im.message.entity.UserMsgList;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
* @author vanky
* @description 针对表【user_msg_list】的数据库操作Mapper
* @createDate 2025-06-06
* @Entity com.vanky.im.message.entity.UserMsgList
*/
public interface UserMsgListMapper extends BaseMapper<UserMsgList> {

    /**
     * 查询用户的最大全局序列号
     * 用于离线消息同步时判断是否有新消息
     *
     * @param userId 用户ID
     * @return 用户最大全局序列号，如果用户无消息则返回null
     */
    @Select("SELECT MAX(seq) FROM user_msg_list WHERE user_id = #{userId}")
    Long selectMaxSeqByUserId(@Param("userId") String userId);

    /**
     * 根据用户ID和序列号范围分页查询消息记录
     * 基于"持久化是第一原则"的设计理念，支持多端推送和重复拉取场景
     * 用于消息同步时批量拉取消息
     *
     * @param userId 用户ID
     * @param fromSeq 起始序列号（包含）
     * @param limit 查询数量限制
     * @return 用户消息记录列表，按seq升序排列
     */
    @Select("SELECT * FROM user_msg_list WHERE user_id = #{userId} AND seq >= #{fromSeq} ORDER BY seq ASC LIMIT #{limit}")
    List<UserMsgList> selectByUserIdAndSeqRange(@Param("userId") String userId,
                                               @Param("fromSeq") Long fromSeq,
                                               @Param("limit") Integer limit);

    /**
     * 根据用户ID和序列号范围分页查询消息记录
     * 基于"持久化是第一原则"的设计理念，消息的可见性基于用户的序列号范围
     * 支持多端推送和重复拉取场景，不再检查消息推送状态
     *
     * @param userId 用户ID
     * @param fromSeq 起始序列号（包含）
     * @param limit 查询数量限制
     * @return 用户消息记录列表，按seq升序排列
     */
    @Select("SELECT * FROM user_msg_list WHERE user_id = #{userId} AND seq >= #{fromSeq} ORDER BY seq ASC LIMIT #{limit}")
    List<UserMsgList> selectUndeliveredByUserIdAndSeqRange(@Param("userId") String userId,
                                                          @Param("fromSeq") Long fromSeq,
                                                          @Param("limit") Integer limit);
}