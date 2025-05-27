-- 创建数据库
CREATE DATABASE IF NOT EXISTS im_plus DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;

USE im_plus;

-- 创建用户表
CREATE TABLE IF NOT EXISTS `users` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '用户唯一ID，自增主键',
  `user_id` varchar(20) NOT NULL COMMENT '用户自定义ID，用于登录和显示',
  `username` varchar(50) NOT NULL COMMENT '用户昵称',
  `password` varchar(100) NOT NULL COMMENT '加密后的用户密码',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '用户状态：1-正常，2-禁用',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表'; 