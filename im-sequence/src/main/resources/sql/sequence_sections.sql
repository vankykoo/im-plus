-- IM Plus 序列号分段表
-- 用于持久化序列号分段信息，支持高性能序列号生成
-- 采用单一表设计，通过 section_key 前缀区分不同业务类型

CREATE TABLE `sequence_section` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `section_key` varchar(255) NOT NULL COMMENT '分段业务唯一键, 例如 u_{section_id} 或 c_{section_id}',
  `max_seq` bigint(20) NOT NULL DEFAULT '0' COMMENT '该分段已分配的序列号上限',
  `step` int(11) NOT NULL DEFAULT '10000' COMMENT '每次持久化的步长',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `version` bigint(20) NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_section_key` (`section_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='序列号分段持久化表';

-- 业务键说明：
-- 用户序列号分段: u_{section_id} (例如: u_123)
-- 会话序列号分段: c_{section_id} (例如: c_456)
-- 未来扩展: s_{section_id} (系统消息序列号)

-- 插入初始化数据示例
-- INSERT INTO sequence_section (section_key, max_seq, step) VALUES ('u_0', 0, 10000);
-- INSERT INTO sequence_section (section_key, max_seq, step) VALUES ('c_0', 0, 10000);
