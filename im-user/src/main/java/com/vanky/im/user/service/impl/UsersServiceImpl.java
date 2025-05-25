package com.vanky.im.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vanky.im.user.entity.Users;
import com.vanky.im.user.service.UsersService;
import com.vanky.im.user.mapper.UsersMapper;
import org.springframework.stereotype.Service;

/**
* @author vanky
* @description 针对表【users(用户信息表)】的数据库操作Service实现
* @createDate 2025-05-25 22:55:08
*/
@Service
public class UsersServiceImpl extends ServiceImpl<UsersMapper, Users>
    implements UsersService{

}




