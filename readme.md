# 用户行为追踪中间件Demo（Spring Boot + Kafka + Protobuf + Schema Registry）

## 项目背景与目标

本系统为社交/推荐型平台中台开发的高可用、分布式、可扩展用户行为追踪Demo。
采用微服务架构与业界标准技术栈，实现端到端的事件采集、消息中转、落地存储与检索，满足企业实际对高可用性、分区容错性、最终一致性、高吞吐等多项中台需求。

---

## 核心需求与特性

- **多微服务架构**：Producer/Consumer分离，独立进程，支持横向扩容和容错。
- **高可用与分区性**：Kafka多分区、Consumer多实例、消费组自动分配。
- **最终一致性**：确保消费端重试与死信队列(Dead Letter Topic)机制，消息不丢失。
- **Schema治理**：全链路使用Protobuf，集成Confluent Schema Registry，类型安全且支持schema演进。
- **消息链路可观测**：可演示分区内顺序、group重平衡、幂等生产、位点提交、容错与故障转移。
- **自动化环境**：本地环境全部由testcontainers或docker-compose一键拉起，极易体验。

---

## 技术选型与组件说明

- **Spring Boot 3.x** —— 微服务架构基座，生态完整。
- **Spring Kafka** —— Kafka消息收发与监听，支持分区、重试、DLT特性。
- **Protobuf + Schema Registry** —— 严格类型约束、结构进化兼容。
- **PostgreSQL (Spring Data JPA)** —— 结构化事件存储。
- **Elasticsearch** —— 用户博文类事件的实时检索落地。
- **testcontainers/docker-compose** —— Kafka, Registry, 数据库, ES环境自动拉起。

---

## 核心系统架构

### 服务划分

```
definitive-guide/
├── event-common/                  # 公共模块，管理 protobuf schema 及通用工具
│   └── src/
│       ├── main/
│       │    └── proto/            # .proto 文件目录
│       │    └── java/             # Protobuf生成、公共工具/常量
│       └── test/
├── event-producer-service/        # 事件生产微服务（包含 REST API、Kafka Producer）
│   └── src/
│       ├── main/
│       │    └── java/com/lanlan/mock/ 
│       │    └── resources/
│       └── test/
├── event-consumer-service/        # 事件消费微服务（负责DB/ES落地、消费组管理等）
│   └── src/
│       ├── main/
│       │    └── java/com/lanlan/mock/ 
│       │    └── resources/
│       └── test/
├── infra-env/                     # 各类环境脚本（如docker-compose、初始化文档等，可选）
│   └── docker-compose.yml         # 如团队需要补充集中式Compose启动脚本
├── SOW.md
├── readme.md
├── pom.xml                        # 项目父Maven POM：统一依赖与插件管理
└── ...
```



- **event-producer-service**  
  - REST API事件入口，数据模拟/注入Kafka，Protobuf序列化&注册。
- **event-consumer-service**  
  - 消费Kafka事件：普通写DB，发帖写DB+ES，消费失败自动重试+DLT。
- **infra-env**  
  - 管理所有依赖环境（Kafka、Registry、DB、ES）。
- **event-dlt-monitor**（可选）  
  - 死信队列监控&补偿。

### Topic与Schema命名规范

- Topic: `user-behavior-events`（主） / `user-behavior-events-DLT`（死信）
- Schema 注册：`user-behavior-events-value` / `-key`
- Protobuf包、类名行业规范，字段明细见Schema定义。

---

## 用户行为事件结构（Protobuf定义）

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
- 支持标准行为，如 LOGIN、SWIPE、POST、VIEW、LIKE、BLOCK 等，eventType区分后按需路由入库/索引。

---

## ✅ 用户行为事件分类清单（适用于 Vibe 推荐系统）

| 类别           | 事件类型           | 描述                           | 推荐系统可用性                 |
|----------------|--------------------|-------------------------------|-------------------------------|
| 📣 内容行为     | post_created       | 用户发了一条动态或吐槽          | 🟢 提取关键词 / 标签建模       |
|                | comment_created    | 用户评论了他人内容              | 🟢 与他人互动 / 文本分析       |
|                | reply_created      | 回复了某条评论                  | 🟡 互动链深度分析              |
|                | delete_post        | 用户删除了内容                  | 🔴 通常不用于推荐, 可计入稳定性分析|
| ❤️ 互动行为    | post_liked         | 点赞某条帖子                    | 🟢 重要兴趣信号                |
|                | comment_liked      | 点赞某条评论                    | 🟡 更细粒度的兴趣标签          |
|                | post_shared        | 分享帖子给其他用户               | 🟢 “强兴趣”信号               |
|                | profile_viewed     | 查看某个用户主页                 | 🟢 潜在兴趣用户推荐源          |
|                | message_sent       | 私信了某个用户                   | 🟡 用户关系图建模（脱敏）      |
| 🔁 社交关系     | follow_user        | 关注了某个用户                   | 🟢 社交图结构核心数据          |
|                | unfollow_user      | 取消关注                         | 🟡 负反馈                      |
|                | block_user         | 拉黑某人                         | 🔴 应排除推荐                  |
| 🔍 兴趣探索     | search_tag         | 搜索了某个标签（如“迷幻音乐”）   | 🟢 tag 建模重要来源            |
|                | search_user        | 搜索了某个用户                   | 🟡 个体兴趣信号                |
|                | explore_clicked    | 点击了推荐页中的用户或帖子        | 🟢 推荐效果验证信号            |
| 🎨 个性设定     | update_profile     | 修改了个人资料/兴趣标签           | 🟢 主动标签、年龄、性别        |
|                | select_vibe_tags   | 选择了“vibe”关键词（如chill, geek, rave）  | 🟢 直接用于 vibe 分类 |



| 事件类型               | 描述           | Kafka Topic（建议）             | 推荐系统可用性             |
| ---------------------- | -------------- | ------------------------------- | -------------------------- |
| `user_registered`      | 新用户注册成功 | `user.identity.registered`      | 🟢 初始兴趣标签、注册渠道   |
| `user_logged_in`       | 用户成功登录   | `user.identity.login`           | 🟢 活跃度判断、推荐更新时机 |
| `user_logged_out`      | 用户主动登出   | `user.identity.logout`          | 🟡 会话时长分析（次要）     |
| `user_session_expired` | Session 失效   | `user.identity.session_expired` | 🔴 可选用于风控行为分析     |
| `user_account_deleted` | 注销账户       | `user.identity.deleted`         | 🔴 排除推荐候选用户         |

## ✅ 建议事件 Topic 命名规范（Kafka）

| Topic 名称                  | 事件范畴          |
|-----------------------------|-------------------|
| user.activity.content       | 发帖、评论、回复等|
| user.activity.interaction   | 点赞、分享、浏览等|
| user.activity.relation      | 关注、拉黑等      |
| user.activity.search        | 搜索行为          |
| user.activity.profile       | 修改资料          |
| user.vibe.recommendation    | 推荐输出流        |

---

> 说明：建议事件结构及Topic规划在Schema Registry和topic设计时同步纳入考虑，便于数据治理和推荐系统快速集成。

---

## 数据库存储与ES索引

- PostgreSQL：`user_behavior_event`（字段同上述Schema，extra_data存JSON）
- Elasticsearch：`user-post-events`（仅博文/内容类事件写入，字段映射与schema一致）

---

## 典型业务流程

1. 外部/测试系统通过REST API调用 event-producer-service，提交单条/批量用户行为事件。
2. Producer序列化Protobuf，注册schema后推送Kafka。
3. Kafka高可用分区存储，Consumer多实例、多组分区处理。
4. Consumer消费事件，普通事件写DB，发帖事件同步写入ES。
5. 消费失败触发自动重试、超限路由到DLT，保底保证不丢失。
6. 全链路可观测，健康检查与分区/分组/故障恢复可便捷测试。

---

## 开发与部署说明（摘要）

- 推荐工作流：严格按 SOW.md 任务推进，开发/测试中持续更新文档。
- 环境启动&一键复位：testcontainers或docker-compose，一步启停，保证测试隔离。
- 每个微服务均有独立README与接口开放。

---

## 更新指引

如架构或需求有调整，请同步修改本README与SOW.md，强调版本演进和变更记录，便于团队持续协作和知识沉淀。

---
