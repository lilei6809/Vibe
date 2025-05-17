# 用户行为追踪中间件实现细节

## 1. 核心功能实现

### 1.1 事件处理流程
- **事件优先级处理**
  - 实现事件优先级队列
  - 高优先级事件优先处理
  - 优先级配置化

- **事件去重机制**
  - 基于event_id的去重
  - 基于业务键的去重
  - 去重窗口配置

- **事件幂等性保证**
  - 生产者幂等性配置
  - 消费者幂等性处理
  - 幂等性检查机制

- **事件顺序性保证**
  - 同一用户事件顺序保证
  - 同一会话事件顺序保证
  - 分区键设计

### 1.2 消息处理策略
- **批量处理机制**
  - 批量消息生产
  - 批量消息消费
  - 批量大小配置

- **消息压缩策略**
  - 压缩算法选择
  - 压缩级别配置
  - 压缩效果评估

- **消息大小限制**
  - 单条消息大小限制
  - 批量消息大小限制
  - 超限处理策略

- **消息格式版本控制**
  - Schema版本管理
  - 向后兼容性保证
  - 版本升级策略

## 2. 开发规范

### 2.1 代码结构
```
event-common/
├── src/main/
│   ├── proto/                    # Protobuf定义
│   ├── java/
│   │   ├── model/               # 数据模型
│   │   ├── constant/            # 常量定义
│   │   ├── util/                # 工具类
│   │   └── exception/           # 异常定义
│   └── resources/
│       └── application.yml      # 公共配置
```

### 2.2 异常处理
- **统一异常类型**
  ```java
  public class EventProcessingException extends RuntimeException {
      private String errorCode;
      private String errorMessage;
      // ...
  }
  ```

- **异常处理策略**
  - 业务异常处理
  - 系统异常处理
  - 重试策略

- **错误码规范**
  ```
  E001: 事件格式错误
  E002: 事件重复
  E003: 处理超时
  E004: 系统异常
  ```

## 3. 测试用例

### 3.1 单元测试
- **事件序列化/反序列化测试**
  ```java
  @Test
  public void testEventSerialization() {
      // 测试代码
  }
  ```

- **消息生产/消费测试**
  ```java
  @Test
  public void testMessageProduction() {
      // 测试代码
  }
  ```

- **异常处理测试**
  ```java
  @Test
  public void testExceptionHandling() {
      // 测试代码
  }
  ```

### 3.2 集成测试
- **端到端流程测试**
- **多分区消费测试**
- **重试机制测试**
- **死信队列测试**

## 4. 示例代码

### 4.1 Producer示例
```java
// 单条消息发送
public void sendSingleMessage(UserBehaviorEvent event) {
    kafkaTemplate.send("user-behavior-events", event.getUserId(), event);
}

// 批量消息发送
public void sendBatchMessages(List<UserBehaviorEvent> events) {
    events.forEach(event -> 
        kafkaTemplate.send("user-behavior-events", event.getUserId(), event));
}

// 带key的消息发送
public void sendMessageWithKey(String key, UserBehaviorEvent event) {
    kafkaTemplate.send("user-behavior-events", key, event);
}

// 异步发送
public void sendAsync(UserBehaviorEvent event) {
    kafkaTemplate.send("user-behavior-events", event.getUserId(), event)
        .addCallback(
            result -> log.info("Message sent successfully"),
            ex -> log.error("Failed to send message", ex)
        );
}
```

### 4.2 Consumer示例
```java
// 单条消息消费
@KafkaListener(topics = "user-behavior-events")
public void consumeMessage(UserBehaviorEvent event) {
    // 处理消息
}

// 批量消息消费
@KafkaListener(topics = "user-behavior-events", batch = "true")
public void consumeBatchMessages(List<UserBehaviorEvent> events) {
    // 批量处理消息
}

// 手动提交offset
@KafkaListener(topics = "user-behavior-events")
public void consumeWithManualCommit(
    @Payload UserBehaviorEvent event,
    Acknowledgment ack) {
    // 处理消息
    ack.acknowledge();
}

// 异常重试
@RetryableTopic(
    attempts = "3",
    backoff = @Backoff(delay = 1000, multiplier = 2.0),
    autoCreateTopics = "false",
    topicSuffixingStrategy = "APPEND_ATTEMPT_NUMBER"
)
@KafkaListener(topics = "user-behavior-events")
public void consumeWithRetry(UserBehaviorEvent event) {
    // 处理消息
}
```

## 5. 配置说明

### 5.1 Kafka配置
```yaml
spring:
  kafka:
    producer:
      bootstrap-servers: localhost:9092
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer
      properties:
        schema.registry.url: http://localhost:8081
    consumer:
      bootstrap-servers: localhost:9092
      group-id: user-behavior-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer
```

## 6. 使用示例

### 6.1 API调用示例
```bash
# 发送单条事件
curl -X POST http://localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -d '{
    "event_type": "post_created",
    "user_id": "user123",
    "target_user_id": "",
    "device_type": "mobile",
    "source_channel": "app"
  }'

# 发送批量事件
curl -X POST http://localhost:8080/api/events/batch \
  -H "Content-Type: application/json" \
  -d '[
    {
      "event_type": "post_created",
      "user_id": "user123",
      "target_user_id": "",
      "device_type": "mobile",
      "source_channel": "app"
    },
    {
      "event_type": "post_liked",
      "user_id": "user456",
      "target_user_id": "user123",
      "device_type": "web",
      "source_channel": "browser"
    }
  ]'
```

## 7. 开发环境

### 7.1 本地开发环境
- **Docker Compose配置**
  ```yaml
  version: '3'
  services:
    zookeeper:
      image: confluentinc/cp-zookeeper:latest
      environment:
        ZOOKEEPER_CLIENT_PORT: 2181
        ZOOKEEPER_TICK_TIME: 2000
      ports:
        - "2181:2181"

    kafka:
      image: confluentinc/cp-kafka:latest
      depends_on:
        - zookeeper
      ports:
        - "9092:9092"
      environment:
        KAFKA_BROKER_ID: 1
        KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
        KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
        KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
        KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
        KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1

    schema-registry:
      image: confluentinc/cp-schema-registry:latest
      depends_on:
        - kafka
      ports:
        - "8081:8081"
      environment:
        SCHEMA_REGISTRY_HOST_NAME: schema-registry
        SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS: kafka:29092
  ```

### 7.2 测试数据生成
- **测试数据生成器**
  ```java
  public class TestDataGenerator {
      public static UserBehaviorEvent generateRandomEvent() {
          // 生成随机测试数据
      }
  }
  ```

## 8. 文档

### 8.1 API文档
- **接口说明**
  - 事件发送接口
  - 批量事件发送接口
  - 事件查询接口

- **请求/响应示例**
  - 成功响应示例
  - 错误响应示例

- **错误码说明**
  - 系统错误码
  - 业务错误码

### 8.2 开发指南
- **环境搭建**
  - 开发环境配置
  - 依赖安装
  - 服务启动

- **开发流程**
  - 代码规范
  - 提交流程
  - 测试流程

- **测试方法**
  - 单元测试
  - 集成测试
  - 性能测试

- **常见问题**
  - 环境问题
  - 开发问题
  - 测试问题 