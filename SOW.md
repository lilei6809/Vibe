# SOW：基于 Spring Kafka + Protobuf + Schema Registry 的用户行为追踪中间件系统

---

## 一、项目目标与背景

本项目开发一套高可用、分布式、可扩展的用户行为追踪中间件Demo，采用 Spring Boot 微服务架构 + Kafka + Confluent Schema Registry（Protobuf格式） + PostgreSQL + Elasticsearch（均基于 testcontainers 自动拉起环境），用于演示标准中台事件流、容错机制、流式数据实时入库/检索等实际企业应用场景。

## 二、核心业务架构

### 服务划分与命名
- **event-producer-service**：提供REST API，模拟/接收用户行为，上报到Kafka，消息体采用Protobuf并注册到Schema Registry。
- **event-consumer-service**：消费Kafka Topic，落库（PostgreSQL）和写入ES（特定事件类型)，支持消费重试与死信队列（DLT），完整集成Protobuf Schema校验。
- **infra-env**：testcontainers/docker-compose自动启动 Kafka、Schema Registry、PostgreSQL、Elasticsearch。
- **event-dlt-monitor（可选）**：对死信队列进行监控及补偿。

### 核心链路流程
1. 通过API触发行为事件（支持单条/批量），REST接口由 event-producer-service 提供。
2. event-producer-service 使用 Protobuf 序列化事件，推送至 Kafka（topic: user-behavior-events），schema自动注册到Schema Registry。
3. event-consumer-service 基于Confluent Protobuf Kafka反序列化器消费消息，落库或写到ES；消费失败自动重试，持续失败入“user-behavior-events-DLT”主题。
4. 观测Kafka重平衡、分区漂移、消费顺序、幂等、ConsumerGroup数量等分布式核心特性。

### 主要Topic与Schema规范
- Topic：`user-behavior-events`（主），`user-behavior-events-DLT`（死信）
- Schema Registry subject命名：`user-behavior-events-value` / `-key`
- Protobuf文件统一管理（严格向后兼容）


## 三、数据模型示例

### Protobuf Schema
```protobuf
syntax = "proto3";
package events;
option java_package = "com.lanlan.mock.events";
option java_outer_classname = "UserBehaviorEventProtos";
message UserBehaviorEvent {
  string event_id = 1;
  string event_type = 2;
  string user_id = 3;
  string target_user_id = 4;
  int64 timestamp = 5;
  string device_type = 6;
  string source_channel = 7;
  map<string, string> extra_data = 8;
}
```

## 四、数据库与ES结构
- PostgreSQL：user_behavior_event（见前述需求分析）
- Elasticsearch：user-post-events（与Protobuf字段同步，分博文类写入）


## 五、详细任务清单（可打勾推进）

- [ ] 新建各微服务：event-producer-service, event-consumer-service（独立Spring Boot项目）
- [ ] infra-env testcontainers配置：Kafka、Schema Registry、Postgres、Elasticsearch一键启动
- [ ] 设计Protobuf schema，自动生成Java类，注册并测试schema registry兼容性
- [ ] event-producer-service 功能开发：
    - [ ] REST API `/api/events` 支持批量/单条POST
    - [ ] 数据生成/负载模拟脚本开发
    - [ ] Kafka Producer集成，分区策略、幂等性、Protobuf序列化、schema自动注册
    - [ ] 配置与Schema Registry对接
- [ ] event-consumer-service 功能开发：
    - [ ] Kafka Consumer Protobuf反序列化、基于Spring Kafka多实例/多组配置
    - [ ] 消息位点手动提交，分区分配、重平衡观测
    - [ ] 普通事件写入PostgreSQL，发帖类写入Postgres+ES
    - [ ] 消费端异常自动重试与DLT，DLT消息监控与处理
    - [ ] 运行状态/分区/Group REST接口/观测端开发
- [ ] DB与ES初始化脚本与映射
- [ ] 集成测试脚本（生产-消费全链路，重平衡，多分区，多组，多实例，重试与DLT）
- [ ] 文档完善：开发说明、接口说明、环境使用指引
- [ ] 项目readme、架构图、常见问题补充





- [ ] 下一步建议
    - 可以初始化根 pom.xml（多模块Maven工程），各子模块分别独立 `pom.xml`，所有服务依赖公共结构 `event-common`。
    - 下一步推荐优先：编写 `event-common` 的 protobuf schema 文件配置父pom与模块pom，集成protobuf代码生成插件与 testcontainers 依赖






## 六、架构与实现图示（若后续需要可补充类图/流程图）

```
(API)--> [producer-service] --protobuf--> [Kafka + Registry + DLT] --> [consumer-service(s)] --> DB/ES/DLT
```

---

> **说明：本SOW（Statement of Work）文件应随着项目推进随时更新进度及调整。每完成一项请将任务前方[ ]改为[x]，未完成项持续细化直至项目收敛。**
