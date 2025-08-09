# IM Plus - 分布式即时通讯系统

IM Plus是一个基于Spring Boot + Netty的企业级分布式即时通讯系统，采用推拉结合的消息传输模式，支持私聊、群聊、多协议连接等功能。系统具备高性能、高可用、高可靠的特性，适用于企业内部通讯、在线客服、社交应用等场景。

## ✨ 核心亮点

### 🎯 架构创新
- 🚀 **推拉结合传输模式**: 推送保证实时性，拉取保证最终一致性，序列号驱动同步
- 🏗️ **统一消息表设计**: 私聊和群聊消息统一存储，简化架构，便于维护扩展
- ⚡ **混合扩散策略**: 私聊写扩散(高读性能) + 群聊读扩散(高写性能)，最优资源利用
- 🔄 **双重确认机制**: 发送回执 + 接收ACK，状态驱动的消息生命周期管理
- 🎯 **会话级串行化处理**: 网关层会话工作队列模型，保证同一会话消息严格顺序性
- 🔀 **消息分离架构**: ACK消息与业务消息分离处理，统一分发器模式，避免竞争条件

### 🛠️ 核心功能
- ✅ **用户管理**: 用户注册、登录、认证、在线状态管理、好友关系管理
- ✅ **私聊消息**: 一对一实时消息收发，支持写扩散模式，包含权限校验和黑名单机制
- ✅ **群聊消息**: 群组聊天，支持读扩散模式，适合大群场景，轻量级通知推送
- ✅ **多协议支持**: WebSocket、TCP、UDP三种连接方式，满足不同场景需求
- ✅ **心跳保活**: 自动心跳检测，维持连接稳定性，及时清理无效连接
- ✅ **消息存储**: 统一消息表设计，支持私聊和群聊消息统一管理
- ✅ **离线消息**: 完整的离线消息拉取和会话同步机制，支持断点续传
- ✅ **消息确认**: 完整的ACK确认机制，确保消息可靠投递
- ✅ **超时重发**: 基于时间轮算法的消息超时重发机制，指数退避策略
- ✅ **会话管理**: 会话列表同步，未读数管理，最新消息预览
- ✅ **分布式架构**: 微服务架构，支持水平扩展，无状态设计

### 🔥 技术特性
- ⚡ **高性能**: 基于Netty NIO，支持万级并发连接，消息推送延迟 < 100ms
- 🛡️ **高可用**: Redis + RocketMQ消息队列保证服务可用性，99.9% SLA
- 🔒 **消息可靠**: 序列号机制 + 双重确认 + 超时重发 + 幂等性保证，确保消息不丢失不重复
- 📋 **顺序保证**: RocketMQ顺序消费 + 会话级串行化，确保消息严格按发送顺序处理
- 🔀 **架构分离**: ACK消息与业务消息分离处理，统一分发器模式，避免竞争条件
- 🚀 **缓存优化**: Redis缓存热点数据，智能过期策略，提升响应速度
- ⚖️ **负载均衡**: 网关层负载均衡，支持多实例部署，自动故障转移
- 🎯 **智能路由**: 基于RocketMQ的分布式消息路由，支持跨网关推送

## 🏗️ 系统架构

### 📦 模块结构
```
im-plus/
├── im-gateway/          # 🌐 网关服务 - 连接管理和消息路由
├── im-user/             # 👤 用户服务 - 用户管理和认证
├── im-message-server/   # 💬 消息服务 - 消息处理和存储
├── im-common/           # 🔧 通用模块 - 协议定义和工具类
├── im-client/           # 📱 客户端 - 多协议客户端实现
└── docs/               # 📚 项目文档 - 技术文档和指南
```

#### 核心模块职责
- **im-gateway**: 网关服务，负责客户端连接管理、消息路由转发、负载均衡、协议适配、会话级串行化处理
- **im-user**: 用户服务，提供用户注册、登录、认证、好友关系管理、在线状态管理
- **im-message-server**: 消息服务，负责消息存储、推送分发、离线消息处理、会话管理
- **im-common**: 通用模块，包含协议定义、工具类、常量、共享模型、Redis配置等
- **im-client**: 客户端实现，支持WebSocket、TCP、UDP三种连接协议，Redis存储模拟

### 🛠️ 技术栈

#### 核心框架
- **后端框架**: Spring Boot 3.4.3 - 企业级微服务框架
- **通信框架**: Netty 4.1.119.Final - 高性能异步网络通信
- **开发语言**: Java 17 - 现代化Java开发

#### 中间件
- **消息队列**: RocketMQ 4.9.6 - 分布式消息中间件，支持事务消息和顺序消费
  - TOPIC_CONVERSATION_MESSAGE: 业务消息Topic，支持顺序消费
  - TOPIC_MESSAGE_ACK: ACK消息Topic，支持并发处理
  - TOPIC_PUSH_TO_GATEWAY: 网关推送Topic，支持跨网关消息路由
- **缓存系统**: Redis + Redisson 3.44.0 - 分布式缓存，支持集群模式
- **数据库**: MySQL 8.0 + MyBatis Plus 3.5.10.1 - 关系型数据库持久化

#### 通信协议
- **序列化**: Protobuf 3.25.2 - 高效二进制序列化
- **连接协议**: WebSocket、TCP、UDP - 多协议支持
- **消息协议**: 自定义ChatMessage协议 - 支持扩展字段

#### 架构模式
- **消息传输**: 推拉结合模式 - 实时性 + 最终一致性
- **存储模式**: 私聊写扩散 + 群聊读扩散 - 性能最优化
- **可靠性**: 双重确认 + 时间轮超时重发 + 幂等性保证
- **会话管理**: 统一消息表 + 会话列表同步 + Redis缓存

## 🌐 服务端口配置

### 🚀 应用服务端口
| 服务 | 端口 | 协议 | 说明 |
|------|------|------|------|
| **im-gateway** | 8080 | HTTP | 网关服务主端口 |
| | 8900 | TCP | TCP长连接端口 |
| | 8901 | UDP | UDP连接端口 |
| | 8902 | WebSocket | WebSocket连接端口 |
| **im-user** | 8090 | HTTP | 用户服务API端口 |
| **im-message-server** | 8100 | HTTP | 消息服务API端口 |

### 🗄️ 中间件配置
| 组件 | 地址 | 说明 |
|------|------|------|
| **MySQL数据库** | localhost:3306 | 数据库名: im-plus |
| **Redis缓存** | 192.168.200.137:6379 | 密码: 123456 |
| **RocketMQ NameServer** | localhost:9876 | 消息队列服务 |

### 📱 客户端连接地址
```bash
# WebSocket连接
ws://localhost:8080/websocket

# HTTP API服务
http://localhost:8090  # 用户服务
http://localhost:8100  # 消息服务

# TCP/UDP连接
tcp://localhost:8900   # TCP长连接
udp://localhost:8901   # UDP连接
```

### ⚙️ 端口常量定义
在 `im-common/src/main/java/com/vanky/im/common/constant/PortConstant.java` 中定义：
```java
DEFAULT_TCP_PORT = 8900        // TCP连接端口
DEFAULT_UDP_PORT = 8901        // UDP连接端口
DEFAULT_WEBSOCKET_PORT = 8902  // WebSocket连接端口
```

## 📋 已实现功能

### 1. 用户管理系统
- **用户注册**: 支持用户ID唯一性校验，密码加密存储
- **用户登录**: JWT Token认证，支持多端登录
- **用户退出**: 清理会话信息，释放资源
- **在线状态**: Redis存储用户在线状态，实时更新
- **好友关系**: 好友关系校验，黑名单机制
- **用户状态**: 用户封禁、禁言状态检查

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
- **消息确认**: ACK确认机制，确保消息可靠投递
- **超时重发**: 基于时间轮算法的消息超时重发机制
- **历史消息**: 支持历史消息查询和分页
- **权限校验**: 用户状态、好友关系、黑名单检查
- **会话管理**: 维护会话列表，更新未读数和最新消息

**数据库设计**:
- `message`: 统一存储私聊和群聊消息内容
- `user_msg_list`: 用户消息索引表，支持快速查询
- `user_conversation_list`: 用户会话列表，包含未读数和最新消息ID

### 4. 群聊消息系统 (读扩散模式)
- **群组管理**: 群组成员管理，权限控制
- **消息广播**: 向群组所有在线成员推送消息
- **消息存储**: 消息只存储一份，节省存储空间
- **离线补偿**: 离线用户登录后自动拉取未读消息
- **消息确认**: ACK确认机制，确保消息可靠投递
- **超时重发**: 基于时间轮算法的消息超时重发机制
- **会话管理**: 维护群聊会话列表，更新未读数和最新消息

**数据库设计**:
- `message`: 统一存储私聊和群聊消息内容
- `conversation_msg_list`: 会话消息索引表
- `user_conversation_list`: 用户会话列表，包含未读数和最新消息ID

### 5. 消息队列系统
- **RocketMQ集成**: 异步消息处理，提升系统性能
- **消息推送**: 跨网关消息推送，支持分布式部署
- **消息可靠性**: 消息持久化，保证消息不丢失
- **负载均衡**: 基于Topic的消息路由
- **消息分类**: 私聊消息和群聊消息使用不同的Topic
- **消息确认**: 消息ACK确认机制，确保消息可靠投递

### 6. 缓存系统
- **Redis集成**: 热点数据缓存，提升响应速度
- **会话缓存**: 用户会话信息缓存，支持快速查询
- **消息缓存**: 最近消息缓存，减少数据库查询
- **在线用户**: 在线用户集合缓存，实时状态管理
- **序列号生成**: 基于Redis的全局序列号和用户级序列号生成
- **会话列表**: 会话列表缓存，支持快速渲染首屏

### 7. 数据存储系统
- **MySQL持久化**: 消息、用户、群组等数据持久化存储
- **MyBatis Plus**: 简化数据库操作，提供丰富的查询功能
- **事务管理**: 保证数据一致性，支持分布式事务
- **统一消息表**: 私聊和群聊消息统一存储在message表中
- **会话管理**: 用户会话列表表，支持未读数和最新消息管理
- **索引优化**: 用户消息索引表，支持高效的消息查询

## 🔄 消息处理流程

### 私聊消息完整流程（写扩散模式）

#### 阶段1：客户端发送消息
1. **消息构建**：客户端构建ChatMessage协议对象，包含发送方ID、接收方ID、消息内容、客户端序列号等
2. **会话ID生成**：客户端生成会话ID（格式：`private_小ID_大ID`），确保同一对用户的会话ID一致
3. **协议封装**：将消息封装为Protobuf格式，通过WebSocket/TCP连接发送到网关

#### 阶段2：网关层处理（im-gateway）
4. **会话级串行化处理**：
   - ConversationDispatcher根据conversationId将消息路由到对应的会话工作队列
   - ConversationWorkerPool使用哈希分配策略确保同一会话的消息串行处理
   - 不同会话之间保持并发处理，整体吞吐量不受影响
5. **消息ID生成**：使用雪花算法生成全局唯一的消息ID，替换客户端临时ID
6. **消息预处理**：验证消息格式，补充会话ID（如客户端未提供则兜底生成）
7. **RocketMQ投递**：
   - 将消息投递到TOPIC_CONVERSATION_MESSAGE主题，标签为"private"
   - 使用conversationId作为消息Key，确保同一会话消息的顺序投递
   - 异步发送并处理投递结果，向客户端返回发送状态

#### 阶段3：消息服务层处理（im-message-server）
8. **消息消费**：PrivateMessageConsumer从RocketMQ消费私聊消息
9. **完整的6步处理流程**：
   - **步骤1 - 关系与权限校验**：
     - 验证发送方和接收方的用户状态（是否被禁用）
     - 检查好友关系和黑名单状态
     - 业务异常（如被拉黑）不重试，直接返回成功避免重复处理
   - **步骤2 - 消息持久化**：
     - 保存消息主体到统一的`message`表，设置消息类型为私聊（1）
     - 为发送方创建`user_msg_list`记录，生成用户级全局序列号
     - 处理会话信息，创建或更新`conversation`表记录
   - **步骤3 - 消息下推**：
     - 查询接收方在线状态，获取所在网关节点信息
     - **在线场景**：为接收方创建`user_msg_list`记录，通过RocketMQ推送到目标网关
     - **离线场景**：将消息ID添加到离线消息队列，不生成接收方的全局序列号
   - **步骤4 - 维护会话列表**：
     - 更新发送方和接收方的`user_conversation_list`表
     - 更新未读消息数、最新消息ID、最后更新时间等字段
   - **步骤5 - 数据缓存**：
     - 将消息内容缓存到Redis（TTL 1天）
     - 更新用户消息链缓存（ZSet结构，msgId -> seq映射）
     - 限制缓存大小，自动清理旧消息
   - **步骤6 - 最终确认**：
     - 记录幂等性结果（基于客户端序列号）
     - 发送投递回执给发送方
     - 事务提交，确保数据一致性

#### 阶段4：消息推送和确认
10. **网关推送**：目标网关接收到推送消息，通过WebSocket/TCP连接推送给在线客户端
11. **客户端确认**：客户端接收消息后发送ACK确认到专门的TOPIC_MESSAGE_ACK主题
12. **ACK消息分离处理**：MessageAckConsumer使用并发模式处理ACK消息，通过统一分发器路由到MessageAckProcessor
13. **消息状态更新**：ACK处理器更新消息状态为"推送成功"，确保消息生命周期完整
14. **超时重发**：基于时间轮算法的超时重发机制，确保消息可靠送达

**涉及的数据表操作**：
- `message`表：插入1条消息记录
- `user_msg_list`表：插入2条记录（发送方和接收方各1条）
- `user_conversation_list`表：更新2条记录（发送方和接收方的会话信息）
- `conversation`表：创建或更新1条会话记录

### 群聊消息完整流程（读扩散模式）

#### 阶段1：客户端发送群聊消息
1. **群聊消息构建**：客户端构建ChatMessage，设置发送方ID、群组ID、消息内容
2. **群聊会话ID**：客户端生成或使用群聊会话ID（格式：`group_群组ID`）
3. **消息发送**：通过网络连接发送到网关层

#### 阶段2：网关层处理（im-gateway）
4. **会话级串行化**：同私聊流程，确保同一群聊会话内消息的顺序性
5. **群聊消息预处理**：
   - 生成全局唯一消息ID
   - 验证群组ID和会话ID的一致性
   - 补充缺失的会话ID信息
6. **RocketMQ投递**：投递到TOPIC_CONVERSATION_MESSAGE主题，标签为"group"

#### 阶段3：消息服务层处理（im-message-server）
7. **群聊消息消费**：GroupMessageConsumer从RocketMQ消费群聊消息
8. **读扩散模式处理流程**：
   - **群成员验证**：验证发送方是否为群组成员，检查群组状态
   - **会话序列号生成**：为群聊会话生成递增的序列号，保证消息顺序
   - **消息存储**（读扩散核心）：
     - 保存消息主体到统一的`message`表，设置消息类型为群聊（2）
     - 仅保存1条记录到`conversation_msg_list`表，建立消息ID与会话序列号的映射
     - **不为每个群成员创建user_msg_list记录**，避免写扩散的存储开销
   - **群聊会话维护**：
     - 更新`conversation`表的成员数量和最后消息时间
     - 为所有群成员更新`user_conversation_list`表的会话信息
   - **消息缓存**：
     - 缓存消息内容到Redis，便于快速读取
     - 仅为发送方添加消息索引到用户消息链缓存

#### 阶段4：群成员消息分发
9. **轻量级通知机制**（读扩散核心）：
   - 获取在线群成员列表及其所在网关节点
   - **推送通知而非完整消息**：向在线成员推送包含消息ID和会话序列号的轻量级通知
   - 客户端收到通知后，主动调用消息拉取API获取完整消息内容
10. **离线成员处理**：
    - 离线成员不接收实时通知
    - 成员上线后通过会话同步机制发现新消息并主动拉取

#### 阶段5：消息读取和确认
11. **主动拉取**：客户端基于会话序列号主动拉取群聊消息
    - 查询`conversation_msg_list`表获取消息ID列表
    - 根据消息ID从`message`表或Redis缓存获取完整消息内容
12. **群聊ACK确认**：客户端发送GROUP_CONVERSATION_ACK到专门的TOPIC_MESSAGE_ACK主题
    - ACK消息格式：conversationId:seq（如"group_123:5"）
    - MessageAckConsumer并发处理ACK消息，更新Redis中的用户会话同步点
13. **已读状态更新**：客户端更新本地已读序列号，服务端同步更新`user_conversation_list`表

**涉及的数据表操作**：
- `message`表：插入1条消息记录
- `conversation_msg_list`表：插入1条记录（消息ID与会话序列号映射）
- `user_conversation_list`表：更新N条记录（所有群成员的会话信息）
- `conversation`表：更新1条群聊会话记录

### 写扩散 vs 读扩散对比

| 对比维度 | 私聊（写扩散） | 群聊（读扩散） |
|---------|---------------|---------------|
| **存储策略** | 为每个用户创建消息副本 | 消息只存储一份 |
| **写入成本** | O(用户数) | O(1) |
| **读取成本** | O(1) | O(消息数) |
| **存储空间** | 高（冗余存储） | 低（单份存储） |
| **适用场景** | 用户数少，读取频繁 | 用户数多，读取相对较少 |
| **数据表** | user_msg_list | conversation_msg_list |

### 消息状态生命周期
1. **0-已发送**：消息已保存到数据库，等待推送
2. **1-推送成功**：客户端已确认接收消息
3. **2-已读**：用户已读取消息内容
4. **3-撤回**：消息被发送方撤回
5. **4-推送失败**：多次重试后仍无法推送成功

## 🔧 核心特性

### 消息可靠性保障
- **ACK确认机制**: 客户端接收消息后发送确认，确保消息送达
- **消息分离处理**: ACK消息与业务消息分离到不同Topic，避免竞争条件
- **顺序消费保证**: 业务消息使用MessageListenerOrderly确保严格顺序处理
- **统一分发器**: ImMessageHandler提供统一的消息路由、异常处理和监控功能
- **超时重发**: 基于时间轮算法的消息超时重发机制
- **消息状态跟踪**: 消息发送、送达、已读状态完整跟踪
- **离线消息补偿**: 用户上线后自动拉取未读消息

### 高性能架构
- **写扩散模式**: 私聊消息采用写扩散，读取性能优异
- **读扩散模式**: 群聊消息采用读扩散，存储空间节省
- **统一消息存储**: 私聊和群聊消息统一存储，简化架构
- **会话列表管理**: 维护用户会话列表，支持快速渲染首屏

### 消息顺序保障
- **会话级串行化**: 网关层实现会话工作队列模型，确保同一会话内消息严格按发送顺序处理
- **跨会话并发**: 不同会话之间保持并发处理，整体吞吐量不受影响
- **RocketMQ顺序消费**: 使用MessageListenerOrderly接口，确保同一会话内消息严格按顺序处理
- **消息分离架构**: ACK消息与业务消息分离到不同Topic，避免竞争条件影响顺序性
- **统一分发器**: ImMessageHandler统一消息分发器，提供消息路由、异常处理和监控功能
- **资源可控**: 固定线程池大小(默认16个)，队列容量限制(默认1000条)，最大会话数限制(默认10000个)
- **优雅降级**: 配置化启用/禁用，未启用时自动回退到原有同步处理方式
- **完整监控**: 统计信息、处理延迟监控、详细日志记录

### 分布式支持
- **多协议支持**: WebSocket、TCP、UDP多协议接入
- **负载均衡**: 支持多网关实例，自动负载均衡
- **消息路由**: 基于RocketMQ的分布式消息路由
- **缓存一致性**: Redis缓存与数据库数据一致性保障





## 📁 项目结构

```
im-plus/
├── 📦 im-gateway/              # 🌐 网关服务 - 连接管理和消息路由
│   ├── src/main/java/com/vanky/im/gateway/
│   │   ├── netty/              # Netty服务器实现 (TCP/UDP/WebSocket)
│   │   ├── handler/            # 消息处理器和协议适配
│   │   ├── consumer/           # RocketMQ消息消费者
│   │   ├── service/            # 业务服务层
│   │   └── config/             # 配置类和Bean定义
│   └── src/main/resources/
│       └── application.yml     # 网关服务配置
│
├── 📦 im-user/                 # 👤 用户服务 - 用户管理和认证
│   ├── src/main/java/com/vanky/im/user/
│   │   ├── controller/         # REST API控制器
│   │   ├── service/            # 用户业务逻辑
│   │   ├── entity/             # 用户数据实体
│   │   └── mapper/             # MyBatis数据访问层
│   └── src/main/resources/
│       ├── application.yml     # 用户服务配置
│       └── mapper/             # MyBatis XML映射文件
│
├── 📦 im-message-server/       # 💬 消息服务 - 消息处理和存储
│   ├── src/main/java/com/vanky/im/message/
│   │   ├── processor/          # 消息处理器 (私聊/群聊)
│   │   ├── consumer/           # RocketMQ消息消费者
│   │   ├── service/            # 消息业务服务
│   │   ├── controller/         # 消息API控制器
│   │   ├── entity/             # 消息数据实体
│   │   └── mapper/             # 数据访问层
│   └── src/main/resources/
│       ├── application.yml     # 消息服务配置
│       └── mapper/             # MyBatis XML映射文件
│
├── 📦 im-common/               # 🔧 通用模块 - 协议定义和工具类
│   ├── src/main/java/com/vanky/im/common/
│   │   ├── constant/           # 常量定义 (消息类型/Redis Key等)
│   │   ├── protocol/           # Protobuf协议定义
│   │   ├── model/              # 通用数据模型
│   │   ├── util/               # 工具类和帮助方法
│   │   └── config/             # 通用配置类
│   └── src/main/resources/
│       └── proto/              # Protobuf协议文件
│
├── 📦 im-client/               # 📱 客户端 - 多协议客户端实现
│   └── src/main/java/com/vanky/im/client/
│       ├── ui/                 # Swing用户界面
│       ├── network/            # 网络通信层 (WebSocket/TCP)
│       ├── storage/            # 本地存储 (Redis模拟)
│       ├── sync/               # 离线消息同步
│       └── message/            # 消息处理和确认
│
├── 📚 docs/                    # 📖 项目文档
│   ├── memory_bank.md          # 项目记忆库 - 完整的项目历史
│   ├── offline-message-sync-*.md # 离线消息同步技术文档
│   ├── quick_test_guide.md     # 快速测试指南
│   ├── task_*.md              # 开发任务文档
│   └── *-guide.md             # 各种技术指南
│
├── 🖼️ pic/                     # 图片资源
└── 📄 pom.xml                  # Maven父级配置文件
```

### 🔍 关键目录说明

#### 网关服务 (im-gateway)
- `netty/`: Netty服务器实现，支持TCP、UDP、WebSocket三种协议
- `handler/`: 消息处理器，负责协议解析和消息路由，支持会话级串行化处理
- `consumer/`: 消费RocketMQ消息，推送给客户端
- `conversation/`: 会话级串行化处理组件，包含ConversationDispatcher、ConversationWorkerPool等核心类

#### 消息服务 (im-message-server)
- `processor/`: 核心消息处理器，实现私聊写扩散和群聊读扩散
- `consumer/`: RocketMQ消息消费者，支持顺序消费和并发消费
  - ConversationMessageConsumer: 业务消息顺序消费者
  - MessageAckConsumer: ACK消息并发消费者
- `handler/`: 统一消息分发器，ImMessageHandler提供消息路由和异常处理
- `service/`: 消息业务服务，包括离线消息、会话管理、消息状态管理等
- `controller/`: REST API，提供消息拉取和同步接口

#### 客户端 (im-client)
- `network/`: 网络通信层，支持WebSocket和TCP连接
- `storage/`: 本地存储，使用Redis模拟本地文件存储
- `sync/`: 离线消息同步，实现推拉结合模式

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

## 🗄️ 数据库结构

### 数据库设计概述
IM Plus采用MySQL作为主要数据存储，数据库名为`im-plus`，字符集为`utf8mb4`。系统采用统一消息表设计，将私聊和群聊消息合并存储，通过索引表实现高效查询。

### 核心数据表

#### 1. users - 用户信息表
```sql
CREATE TABLE `users` (
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
```

**字段说明：**
- `id`: 数据库自增主键，内部使用
- `user_id`: 用户自定义ID，用于登录和对外显示，全局唯一
- `username`: 用户昵称，可修改
- `password`: 加密后的密码，使用BCrypt等安全算法
- `status`: 用户状态，1-正常，2-禁用
- `last_login_time`: 最后登录时间，用于统计和安全检查
- `deleted`: 软删除标记，支持数据恢复

#### 2. message - 统一消息表
```sql
CREATE TABLE `message` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `msg_id` bigint(20) NOT NULL COMMENT '全局唯一的消息ID (由雪花算法等生成)',
  `conversation_id` varchar(100) NOT NULL COMMENT '会话ID',
  `sender_id` bigint(20) NOT NULL COMMENT '发送者用户ID',
  `msg_type` tinyint(4) NOT NULL COMMENT '消息类型：1-私聊，2-群聊',
  `content_type` tinyint(4) NOT NULL COMMENT '内容类型：1-文本，2-图片，3-文件，4-语音，5-视频，6-位置，99-系统',
  `content` text NOT NULL COMMENT '消息内容',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '消息状态：0-已发送，1-推送成功，2-已读，3-撤回，4-推送失败',
  `send_time` datetime NOT NULL COMMENT '发送时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_id` (`msg_id`),
  KEY `idx_conversation_id` (`conversation_id`),
  KEY `idx_sender_id` (`sender_id`),
  KEY `idx_send_time` (`send_time`),
  KEY `idx_msg_type` (`msg_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一消息表';
```

**字段说明：**
- `msg_id`: 全局唯一消息ID，使用雪花算法生成，保证分布式环境下的唯一性
- `conversation_id`: 会话ID，私聊格式为"private_小ID_大ID"，群聊格式为"group_群组ID"
- `sender_id`: 发送者用户ID，关联users表
- `msg_type`: 消息类型，1-私聊，2-群聊
- `content_type`: 内容类型，支持文本、图片、文件、语音、视频、位置、系统消息
- `content`: 消息内容，文本消息直接存储，多媒体消息存储URL或路径
- `status`: 消息状态，用于消息确认和已读回执机制

#### 3. conversation - 会话表
```sql
CREATE TABLE `conversation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `conversation_id` varchar(100) NOT NULL COMMENT '会话ID',
  `type` int(11) NOT NULL COMMENT '会话类型：0-私聊，1-群聊',
  `member_count` int(11) DEFAULT '0' COMMENT '会话成员数',
  `last_msg_time` datetime DEFAULT NULL COMMENT '最后一条消息时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(20) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(20) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conversation_id` (`conversation_id`),
  KEY `idx_type` (`type`),
  KEY `idx_last_msg_time` (`last_msg_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';
```

**字段说明：**
- `conversation_id`: 会话唯一标识，与message表关联
- `type`: 会话类型，0-私聊，1-群聊
- `member_count`: 会话成员数量，群聊时使用
- `last_msg_time`: 最后一条消息时间，用于会话列表排序

#### 4. user_conversation_list - 用户会话列表表
```sql
CREATE TABLE `user_conversation_list` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `conversation_id` varchar(100) NOT NULL COMMENT '会话ID',
  `last_read_seq` bigint(20) DEFAULT '0' COMMENT '此会话中用户已读的最后一条消息seq',
  `unread_count` int(11) DEFAULT '0' COMMENT '未读消息数',
  `last_msg_id` bigint(20) DEFAULT NULL COMMENT '此会话最新一条消息的ID',
  `last_update_time` datetime DEFAULT NULL COMMENT '会话最后更新时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_conversation` (`user_id`, `conversation_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_conversation_id` (`conversation_id`),
  KEY `idx_last_update_time` (`last_update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户会话列表表';
```

**字段说明：**
- `user_id`: 用户ID，关联users表
- `conversation_id`: 会话ID，关联conversation表
- `last_read_seq`: 用户在此会话中已读的最后一条消息序列号
- `unread_count`: 未读消息数，用于显示红点提醒
- `last_msg_id`: 最新消息ID，用于显示会话列表中的最后一条消息摘要
- `last_update_time`: 会话最后更新时间，用于会话列表排序

#### 5. user_msg_list - 用户消息索引表
```sql
CREATE TABLE `user_msg_list` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `msg_id` bigint(20) NOT NULL COMMENT '消息ID',
  `conversation_id` varchar(100) NOT NULL COMMENT '会话ID',
  `seq` bigint(20) NOT NULL COMMENT '消息序号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_seq` (`user_id`, `seq`),
  KEY `idx_user_conversation` (`user_id`, `conversation_id`),
  KEY `idx_msg_id` (`msg_id`),
  KEY `idx_seq` (`seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户消息索引表';
```

**字段说明：**
- `user_id`: 用户ID，支持私聊写扩散模式
- `msg_id`: 消息ID，关联message表
- `conversation_id`: 会话ID，用于按会话查询
- `seq`: 用户级全局序列号，用于离线消息同步和顺序保证

#### 6. conversation_msg_list - 会话消息索引表
```sql
CREATE TABLE `conversation_msg_list` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `conversation_id` varchar(100) NOT NULL COMMENT '会话ID',
  `msg_id` bigint(20) NOT NULL COMMENT '消息ID',
  `seq` bigint(20) NOT NULL COMMENT '消息序号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conversation_seq` (`conversation_id`, `seq`),
  KEY `idx_conversation_id` (`conversation_id`),
  KEY `idx_msg_id` (`msg_id`),
  KEY `idx_seq` (`seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话消息索引表';
```

**字段说明：**
- `conversation_id`: 会话ID，支持群聊读扩散模式
- `msg_id`: 消息ID，关联message表
- `seq`: 会话级序列号，用于群聊消息的顺序查询

### 表关系说明

#### 核心关系
- **users** ↔ **message**: 一对多关系，一个用户可以发送多条消息
- **conversation** ↔ **message**: 一对多关系，一个会话包含多条消息
- **users** ↔ **user_conversation_list**: 一对多关系，一个用户可以参与多个会话
- **message** ↔ **user_msg_list**: 一对多关系，一条消息可以对应多个用户记录（写扩散）
- **message** ↔ **conversation_msg_list**: 一对一关系，每条消息在会话中有唯一序列号

#### 索引设计原则
1. **查询优化**: 基于常用查询场景设计复合索引
2. **唯一性约束**: 确保业务唯一性，如用户ID、消息ID等
3. **排序优化**: 为时间字段和序列号字段建立索引
4. **外键关联**: 为关联查询建立适当索引

### 数据一致性保证
- **事务管理**: 使用Spring事务管理确保数据一致性
- **乐观锁**: 通过version字段或时间戳实现乐观锁控制
- **分布式锁**: 使用Redis分布式锁处理并发场景
- **数据校验**: 在应用层和数据库层双重校验数据完整性