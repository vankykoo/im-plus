# IM Plus - 分布式即时通讯系统

IM Plus是一个基于Spring Boot + Netty的企业级分布式即时通讯系统，采用推拉结合的消息传输模式，支持私聊、群聊、多协议连接等功能。系统具备高性能、高可用、高可靠的特性，适用于企业内部通讯、在线客服、社交应用等场景。

## ✨ 核心亮点

### 🎯 架构创新
- 🚀 **推拉结合传输模式**: 推送保证实时性，拉取保证最终一致性，序列号驱动同步
- 🏗️ **统一消息表设计**: 私聊和群聊消息统一存储，简化架构，便于维护扩展
- ⚡ **混合扩散策略**: 私聊写扩散(高读性能) + 群聊读扩散(高写性能)，最优资源利用
- 🔄 **双重确认机制**: 发送回执 + 接收ACK，状态驱动的消息生命周期管理
- 🎯 **会话级串行化处理**: 网关层会话工作队列模型，保证同一会话消息严格顺序性

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
- **消息队列**: RocketMQ 4.9.6 - 分布式消息中间件，支持事务消息
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

### 私聊消息流程 (写扩散)
1. 客户端发送消息到网关
2. 网关进行权限校验（用户状态、好友关系、黑名单）
3. 网关转发消息到消息服务
4. 消息服务生成唯一消息ID和序列号
5. 为发送方和接收方分别创建消息记录
6. 更新Redis缓存，设置过期时间
7. 推送消息给在线接收方
8. 客户端接收消息后发送ACK确认
9. 如果超时未收到ACK，触发消息重发机制

### 群聊消息流程 (读扩散)
1. 验证发送者是否为群组成员
2. 网关进行权限校验（用户状态、群组成员关系）
3. 生成唯一消息ID和会话序列号
4. 消息存储到group_message表
5. 更新Redis缓存和用户消息索引
6. 获取在线群成员列表
7. 通过RocketMQ推送消息到各网关
8. 网关推送消息给在线成员
9. 客户端接收消息后发送ACK确认
10. 如果超时未收到ACK，触发消息重发机制

## 🔧 核心特性

### 消息可靠性保障
- **ACK确认机制**: 客户端接收消息后发送确认，确保消息送达
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
- `service/`: 消息业务服务，包括离线消息、会话管理等
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

## 🛠️ 开发指南

### 📝 开发环境搭建

#### IDE配置
推荐使用 IntelliJ IDEA 或 Eclipse：
```bash
# 导入项目
File -> Open -> 选择项目根目录的pom.xml

# 配置JDK
Project Structure -> Project -> Project SDK -> 选择Java 17

# 配置Maven
Settings -> Build Tools -> Maven -> 设置Maven路径
```

#### 代码规范
- **命名规范**: 驼峰命名法，类名首字母大写，方法和变量首字母小写
- **注释规范**: 类和方法必须有JavaDoc注释，复杂逻辑需要行内注释
- **包结构**: 按功能模块组织，controller/service/mapper分层清晰

#### 开发流程
1. **创建分支**: `git checkout -b feature/your-feature-name`
2. **编写代码**: 遵循代码规范，添加必要的测试
3. **本地测试**: 确保功能正常，无编译错误
4. **提交代码**: 使用清晰的commit message
5. **创建PR**: 详细描述功能和变更

### 🧪 测试指南

#### 单元测试
```bash
# 运行所有测试
mvn test

# 运行特定模块测试
cd im-message-server
mvn test
```

#### 集成测试
```bash
# 启动所有服务后运行客户端
cd im-client
mvn exec:java -Dexec.mainClass="com.vanky.im.client.UserWindow"
```

#### 性能测试
- 使用JMeter或自定义脚本测试并发连接
- 监控Redis和MySQL的性能指标
- 观察RocketMQ的消息堆积情况

### 🔧 调试技巧

#### 日志配置
在 `application.yml` 中调整日志级别：
```yaml
logging:
  level:
    com.vanky.im: DEBUG
    org.springframework: INFO
```

#### Redis调试
```bash
# 连接Redis查看数据
redis-cli -h 192.168.200.137 -p 6379 -a 123456

# 查看用户在线状态
SMEMBERS im:online:users

# 查看消息缓存
KEYS im:msg:*
```

#### RocketMQ调试
```bash
# 查看Topic列表
sh mqadmin topicList -n localhost:9876

# 查看消息堆积
sh mqadmin consumerProgress -n localhost:9876
```

### 📚 扩展开发

#### 添加新的消息类型
1. 在 `MessageTypeConstants` 中定义新的消息类型常量
2. 扩展 `ChatMessage.proto` 协议文件
3. 在相应的处理器中添加处理逻辑
4. 更新客户端的消息处理代码

#### 添加新的API接口
1. 在对应的Controller中添加新的端点
2. 实现相应的Service业务逻辑
3. 添加必要的数据访问层代码
4. 编写单元测试和集成测试

## 🤝 贡献指南

### 🎯 贡献方式
1. **Bug报告**: 在Issues中详细描述问题，包含复现步骤
2. **功能建议**: 提出新功能想法，说明使用场景和价值
3. **代码贡献**: Fork项目，开发功能，提交Pull Request
4. **文档改进**: 完善README、技术文档、代码注释

### 📋 提交规范
```bash
# 提交格式
git commit -m "type(scope): description"

# 示例
git commit -m "feat(message): 添加消息撤回功能"
git commit -m "fix(gateway): 修复WebSocket连接断开问题"
git commit -m "docs(readme): 更新安装指南"
```

### 🔄 Pull Request流程
1. Fork 项目到你的GitHub账号
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开 Pull Request，详细描述变更内容

### ✅ 代码审查标准
- 代码风格符合项目规范
- 功能完整，无明显Bug
- 包含必要的测试用例
- 文档和注释完善
- 不破坏现有功能

## 📞 联系方式

- **作者**: vanky
- **项目地址**: [GitHub Repository]
- **技术交流**: 欢迎提交Issue讨论技术问题
- **文档贡献**: 帮助完善项目文档和使用指南