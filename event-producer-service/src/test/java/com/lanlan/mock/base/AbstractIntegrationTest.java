package com.lanlan.mock.base;


import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

// @SpringBootTest 可以在这里，也可以在子类中，取决于你是否希望所有子类都加载Spring上下文
// 如果不是所有子类都需要Spring上下文，可以考虑不在这里加，或者提供不同版本的基类
@SpringBootTest
// 可选: 如果希望 @BeforeAll 和 @AfterAll 是非静态的，可以配合 TestInstance.Lifecycle.PER_CLASS
// @TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractIntegrationTest {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractIntegrationTest.class);

    protected static Network network = Network.newNetwork();

    protected static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.4.1")
    )
            .withNetwork(network)
            .withNetworkAliases("kafka-test");

    protected static GenericContainer<?> schemaRegistry;

    static {
        logger.info("Starting Kafka container from AbstractIntegrationTest...");
        kafka.start();
        logger.info("Kafka container started. Bootstrap servers: {}", kafka.getBootstrapServers());
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        logger.info("Configuring and starting Schema Registry container from AbstractIntegrationTest...");
        Slf4jLogConsumer schemaRegistryLogConsumer = new Slf4jLogConsumer(LoggerFactory.getLogger("SchemaRegistryContainerBase"));

        schemaRegistry = new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:7.4.1"))
                .withExposedPorts(8081)
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "kafka-test:9092")
                .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
                .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
                .withNetwork(network)
                .waitingFor(Wait.forHttp("/subjects")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(2)))
                .withLogConsumer(schemaRegistryLogConsumer);

        try {
            schemaRegistry.start();
            logger.info("Schema Registry container started. URL: http://{}:{}", schemaRegistry.getHost(), schemaRegistry.getFirstMappedPort());
        } catch (Exception e) {
            logger.error("Failed to start Schema Registry container in base.", e);
            // ... (错误处理) ...
            throw e;
        }

        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.properties.schema.registry.url",
                () -> "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getFirstMappedPort());
        logger.info("Dynamic properties registered from AbstractIntegrationTest.");
    }

    // 你还可以添加一些通用的辅助方法，比如注册 schema 的方法，如果很多测试都需要的话
    // protected void registerSchema(String subject, String schemaString) { ... }
}
