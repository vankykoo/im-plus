package com.vanky.im.user.mapper;

import com.vanky.im.user.entity.Users;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author vanky
* @description 针对表【users(用户信息表)】的数据库操作Mapper
* @createDate 2025-05-25 22:55:08
* @Entity com.vanky.im.user.entity.Users
*/
@Mapper
public interface UsersMapper extends BaseMapper<Users> {

}




