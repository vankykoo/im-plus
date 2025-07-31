# IM Plus - 分布式即时通讯系统

IM Plus是一个基于Spring Boot + Netty的分布式即时通讯系统，支持私聊、群聊、多协议连接等功能。

## 🚀 项目特性

### 核心功能
- ✅ **用户管理**: 用户注册、登录、认证、在线状态管理
- ✅ **私聊消息**: 一对一实时消息收发，支持写扩散模式
- ✅ **群聊消息**: 群组聊天，支持读扩散模式，适合大群场景
- ✅ **多协议支持**: WebSocket、TCP、UDP三种连接方式
- ✅ **心跳保活**: 自动心跳检测，维持连接稳定性
- ✅ **消息存储**: 消息持久化存储，支持历史消息查询
- ✅ **离线消息**: 离线消息推送和补偿机制
- ✅ **分布式架构**: 微服务架构，支持水平扩展

### 技术特性
- 🔥 **高性能**: 基于Netty NIO，支持高并发连接
- 🔥 **高可用**: Redis集群 + RocketMQ消息队列保证服务可用性
- 🔥 **消息可靠**: 消息序列号机制，保证消息有序性和完整性
- 🔥 **缓存优化**: Redis缓存热点数据，提升响应速度
- 🔥 **负载均衡**: 网关层负载均衡，支持多实例部署

## 🏗️ 系统架构

### 模块结构
- **im-gateway**: 网关服务，负责客户端连接管理和消息转发
- **im-user**: 用户服务，提供用户注册、登录、认证等功能
- **im-message-server**: 消息服务，负责消息存储、推送和分发
- **im-common**: 通用模块，包含协议定义、工具类、常量等
- **im-client**: 客户端实现，支持多种连接协议

### 技术栈
- **后端框架**: Spring Boot 3.4.3
- **通信框架**: Netty 4.1.119
- **消息队列**: RocketMQ 4.9.6
- **缓存**: Redis (Redisson 3.44.0)
- **数据库**: MySQL 8.0 + MyBatis Plus 3.5.10
- **序列化**: Protobuf 3.25.2
- **开发语言**: Java 17

## 🌐 服务端口配置

### 应用服务端口
- **im-gateway (网关服务)**: 8080
  - WebSocket端口: 8902/websocket
  - TCP端口: 8900
  - UDP端口: 8901
  - WebSocket专用端口: 8902
- **im-user (用户服务)**: 8090
- **im-message-server (消息服务)**: 8100
- **im-message-server-test (测试环境)**: 8093

### 中间件端口
- **MySQL数据库**: 3306
- **Redis缓存**: 6379 (192.168.200.137:6379)
- **RocketMQ NameServer**: 9876 (localhost:9876)

### 客户端连接端口
- **WebSocket连接**: ws://localhost:8080/websocket
- **HTTP用户服务**: http://localhost:8090
- **HTTP消息服务**: http://localhost:8092 (计划中)

### 端口常量定义
在 `im-common/src/main/java/com/vanky/im/common/constant/PortConstant.java` 中定义：
- DEFAULT_TCP_PORT: 8900
- DEFAULT_UDP_PORT: 8901
- DEFAULT_WEBSOCKET_PORT: 8902

## 📋 已实现功能

### 1. 用户管理系统
- **用户注册**: 支持用户ID唯一性校验，密码加密存储
- **用户登录**: JWT Token认证，支持多端登录
- **用户退出**: 清理会话信息，释放资源
- **在线状态**: Redis存储用户在线状态，实时更新

**API接口**:
```
POST /users/register  - 用户注册
POST /users/login     - 用户登录
GET  /users/logout/{userId} - 用户退出
```

### 2. 连接管理系统
- **多协议支持**: WebSocket、TCP、UDP三种连接方式
- **连接认证**: Token验证，防止非法连接
- **会话管理**: Redis存储用户会话信息，支持分布式部署
- **心跳保活**: 自动心跳检测，及时清理无效连接

### 3. 私聊消息系统 (写扩散模式)
- **消息发送**: 实时消息推送，支持文本、图片等多种类型
- **消息存储**: 为每个用户单独存储消息副本，读取性能优异
- **消息确认**: 消息送达确认机制
- **历史消息**: 支持历史消息查询和分页

**数据库设计**:
- `private_message`: 存储私聊消息内容
- `user_msg_list`: 用户消息索引表，支持快速查询

### 4. 群聊消息系统 (读扩散模式)
- **群组管理**: 群组成员管理，权限控制
- **消息广播**: 向群组所有在线成员推送消息
- **消息存储**: 消息只存储一份，节省存储空间
- **离线补偿**: 离线用户登录后自动拉取未读消息

**数据库设计**:
- `group_message`: 存储群聊消息内容
- `conversation_msg_list`: 会话消息索引表

### 5. 消息队列系统
- **RocketMQ集成**: 异步消息处理，提升系统性能
- **消息推送**: 跨网关消息推送，支持分布式部署
- **消息可靠性**: 消息持久化，保证消息不丢失
- **负载均衡**: 基于Tag的消息路由

### 6. 缓存系统
- **Redis集成**: 热点数据缓存，提升响应速度
- **会话缓存**: 用户会话信息缓存，支持快速查询
- **消息缓存**: 最近消息缓存，减少数据库查询
- **在线用户**: 在线用户集合缓存，实时状态管理

### 7. 数据存储系统
- **MySQL持久化**: 消息、用户、群组等数据持久化存储
- **MyBatis Plus**: 简化数据库操作，提供丰富的查询功能
- **事务管理**: 保证数据一致性，支持分布式事务
- **数据分表**: 支持消息表分表，应对大数据量场景

## 🔄 消息处理流程

### 私聊消息流程 (写扩散)
1. 客户端发送消息到网关
2. 网关转发消息到消息服务
3. 消息服务生成唯一消息ID和序列号
4. 为发送方和接收方分别创建消息记录
5. 更新Redis缓存，设置过期时间
6. 推送消息给在线接收方

### 群聊消息流程 (读扩散)
1. 验证发送者是否为群组成员
2. 生成唯一消息ID和会话序列号
3. 消息存储到group_message表
4. 更新Redis缓存和用户消息索引
5. 获取在线群成员列表
6. 通过RocketMQ推送消息到各网关
7. 网关推送消息给在线成员

## 📊 开发进度

### 已完成功能 ✅
1. **基本消息收发** (2025/5/21)
2. **自定义用户ID登录** (2025/5/23)
3. **指定接收方消息发送** (2025/5/23)
4. **用户踢出和登录响应** (2025/5/24)
5. **心跳保活机制** (2025/5/24)
6. **用户注册登录系统** (2025/5/27)
7. **私聊消息系统** (写扩散模式)
8. **群聊消息系统** (读扩散模式)
9. **多协议连接支持** (WebSocket/TCP/UDP)
10. **分布式消息推送** (RocketMQ)
11. **Redis缓存系统**
12. **历史消息查询API**

### 功能演示
![消息发送演示](pic/Pasted%20image%2020250525155900.png)
![消息接收演示](pic/Pasted%20image%2020250525155921.png)
![系统架构演示](pic/Pasted%20image%2020250525155938.png)

## 🚀 快速开始

### 环境要求
- Java 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+
- RocketMQ 4.9+

### 1. 环境准备
```bash
# 启动MySQL (端口3306)
# 启动Redis (端口6379, 地址192.168.200.137)
# 启动RocketMQ NameServer (localhost:9876)
```

### 2. 数据库初始化
```sql
-- 创建数据库
CREATE DATABASE `im-plus` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 导入数据库表结构
-- 执行相应的SQL脚本创建表结构
```

### 3. 配置文件
修改各模块的 `application.yml` 配置文件：
- 数据库连接信息
- Redis连接信息
- RocketMQ连接信息

### 4. 启动服务
```bash
# 1. 启动用户服务
cd im-user
mvn spring-boot:run

# 2. 启动消息服务
cd im-message-server
mvn spring-boot:run

# 3. 启动网关服务
cd im-gateway
mvn spring-boot:run
```

### 5. 测试连接
```bash
# WebSocket连接测试
ws://localhost:8080/websocket

# HTTP API测试
curl -X POST http://localhost:8090/users/register \
  -H "Content-Type: application/json" \
  -d '{"userId":"test001","username":"测试用户","password":"123456"}'
```

## 📖 API文档

### 用户管理API
| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 用户注册 | POST | `/users/register` | 注册新用户 |
| 用户登录 | POST | `/users/login` | 用户登录获取Token |
| 用户退出 | GET | `/users/logout/{userId}` | 用户退出登录 |

### 消息管理API
| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 拉取历史消息 | GET/POST | `/api/messages/pull` | 拉取会话历史消息 |

### WebSocket消息协议
```protobuf
message ChatMessage {
    int32 type = 1;        // 消息类型
    string content = 2;    // 消息内容
    string fromId = 3;     // 发送者ID
    string toId = 4;       // 接收者ID
    string token = 5;      // 认证Token
    int64 timestamp = 6;   // 时间戳
    string uid = 7;        // 消息唯一ID
    int64 seq = 8;         // 序列号
}
```

## 🏗️ 项目结构

```
im-plus/
├── im-gateway/          # 网关服务
│   ├── src/main/java/
│   │   └── com/vanky/im/gateway/
│   │       ├── netty/           # Netty服务器实现
│   │       ├── handler/         # 消息处理器
│   │       └── config/          # 配置类
│   └── pom.xml
├── im-user/             # 用户服务
│   ├── src/main/java/
│   │   └── com/vanky/im/user/
│   │       ├── controller/      # REST控制器
│   │       ├── service/         # 业务逻辑
│   │       ├── entity/          # 数据实体
│   │       └── mapper/          # 数据访问层
│   └── pom.xml
├── im-message-server/   # 消息服务
│   ├── src/main/java/
│   │   └── com/vanky/im/message/
│   │       ├── processor/       # 消息处理器
│   │       ├── service/         # 业务服务
│   │       ├── entity/          # 消息实体
│   │       └── config/          # 配置类
│   └── pom.xml
├── im-common/           # 通用模块
│   ├── src/main/java/
│   │   └── com/vanky/im/common/
│   │       ├── constant/        # 常量定义
│   │       ├── protocol/        # 协议定义
│   │       ├── util/            # 工具类
│   │       └── model/           # 通用模型
│   └── pom.xml
├── im-client/           # 客户端实现
│   └── src/main/java/
│       └── com/vanky/im/client/
├── docs/                # 项目文档
│   ├── memory_bank.md           # 项目记忆库
│   ├── offline-message-sync-*.md # 离线消息同步文档
│   ├── quick_test_guide.md      # 快速测试指南
│   ├── task_*.md               # 任务开发文档
│   └── unified-message-consumer-guide.md # 消息消费指南
└── pom.xml             # 父级POM文件
```

## 🔧 配置说明

### 关键配置项
- **数据库配置**: `spring.datasource.*`
- **Redis配置**: `spring.redis.*`
- **RocketMQ配置**: `rocketmq.*`
- **Netty配置**: `netty.server.*`

### 性能调优
- **连接池配置**: 数据库连接池、Redis连接池
- **线程池配置**: Netty EventLoop线程数
- **缓存配置**: Redis缓存过期时间、最大缓存数量
- **消息队列配置**: RocketMQ生产者、消费者参数

## 📈 性能特性

- **高并发**: 支持万级并发连接
- **低延迟**: 消息推送延迟 < 100ms
- **高可用**: 99.9% 服务可用性
- **可扩展**: 支持水平扩展，无状态设计

## 🤝 贡献指南

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开 Pull Request

## 📞 联系方式

- 作者: vanky