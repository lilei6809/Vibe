package com.lanlan.mock;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Kafka 和 Schema Registry 的集成测试类
 * 使用 Testcontainers 自动启动和管理 Kafka 和 Schema Registry 容器
 * 
 * 注意：从 Kafka 3.0 开始，Kafka 不再依赖 Zookeeper，而是使用 KRaft（Kafka Raft）模式
 */
@SpringBootTest
public class KafkaWithSchemaRegistryTest {

    // 创建一个共享网络
    static Network network = Network.newNetwork();

    /**
     * 创建 Kafka 容器
     * 使用 Testcontainers 默认的 Kafka 镜像
     * 从 Kafka 3.0 开始，不再需要 Zookeeper，使用 KRaft 模式
     */
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.4.1")
    )
    .withNetwork(network) // 加入共享网络
    .withNetworkAliases("kafka-test"); // 设置固定的网络别名

    /**
     * 创建 Schema Registry 容器
     * 使用 Confluent 的 Schema Registry 镜像，版本 7.4.1
     */
    static GenericContainer<?> schemaRegistry;

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(KafkaWithSchemaRegistryTest.class);

    /**
     * 静态初始化块
     * 在类加载时启动 Kafka 容器
     */
    static {
        kafka.start();
        logger.info("Kafka container started. Bootstrap servers: {}", kafka.getBootstrapServers());
    }

    /**
     * 动态属性源
     * 在 Spring Boot 测试环境中动态注入 Kafka 和 Schema Registry 的配置
     * 这样 Spring Boot 应用就能自动连接到测试容器
     */
    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        logger.info("Configuring and starting Schema Registry container...");
        Slf4jLogConsumer schemaRegistryLogConsumer = new Slf4jLogConsumer(org.slf4j.LoggerFactory.getLogger("SchemaRegistryContainer"));

        schemaRegistry = new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:7.4.1"))
                .withExposedPorts(8081)
                // 使用固定的别名和Kafka内部端口连接
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "kafka-test:9092") 
                .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
                .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
                .withNetwork(network) // 加入与Kafka相同的共享网络
                .waitingFor(Wait.forHttp("/subjects")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(2)))
                .withLogConsumer(schemaRegistryLogConsumer);

        try {
            schemaRegistry.start();
            logger.info("Schema Registry container started. URL: http://{}:{}", schemaRegistry.getHost(), schemaRegistry.getFirstMappedPort());
        } catch (Exception e) {
            logger.error("Failed to start Schema Registry container.", e);
            if (schemaRegistry != null && schemaRegistry.isRunning()) {
                logger.error("Schema Registry logs upon failure:\\n{}", schemaRegistry.getLogs());
            } else if (schemaRegistry != null) {
                 logger.error("Schema Registry container object exists but is not running. Cannot get logs.");
            } else {
                logger.error("Schema Registry container object is null.");
            }
            throw e;
        }

        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.properties.schema.registry.url",
            () -> "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getFirstMappedPort());
        logger.info("Dynamic properties registered.");
    }

    /**
     * 基础测试方法
     * 验证容器是否正常启动，并打印连接信息
     * 可以在这里添加更多的测试用例
     */
    @Test
    void contextLoads() {
        if (schemaRegistry == null || !schemaRegistry.isRunning()) {
            logger.error("Schema Registry container in contextLoads: {}", schemaRegistry == null ? "null" : "not running");
            fail("Schema Registry container is not running or not initialized.");
        }

        String kafkaBootstrapServers = kafka.getBootstrapServers();
        String schemaRegistryUrl = "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getFirstMappedPort();

        logger.info("Kafka (from contextLoads): {}", kafkaBootstrapServers);
        logger.info("Schema Registry (from contextLoads): {}", schemaRegistryUrl);

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(20))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(schemaRegistryUrl + "/subjects"))
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();

            logger.info("Attempting to GET {}", request.uri());
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            logger.info("Schema Registry Response Code: {}", response.statusCode());
            logger.info("Schema Registry Response Body: {}", response.body());
            assertEquals(200, response.statusCode(), "Schema Registry /subjects endpoint should return 200. Body: " + response.body());

        } catch (java.io.IOException | InterruptedException e) {
            logger.error("Error accessing Schema Registry. Exception type: {}, Message: {}", e.getClass().getName(), e.getMessage(), e);
            fail("Error accessing Schema Registry: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("An unexpected error occurred while accessing Schema Registry. Exception type: {}, Message: {}", e.getClass().getName(), e.getMessage(), e);
            fail("Unexpected error during Schema Registry access: " + e.getMessage(), e);
        }
    }
}
