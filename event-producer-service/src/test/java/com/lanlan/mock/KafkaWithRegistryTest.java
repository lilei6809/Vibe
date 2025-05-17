package com.lanlan.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class KafkaWithRegistryTest {

    static Network network = Network.newNetwork();

    @Container
    public static KafkaContainer kafka = new KafkaContainer("confluentinc/cp-kafka:7.4.1")
            .withNetwork(network)
            .withNetworkAliases("kafka");

    @Container
    public static GenericContainer<?> schemaRegistry = new GenericContainer<>("confluentinc/cp-schema-registry:7.4.1")
            .withNetwork(network)
            .withNetworkAliases("schema-registry")
            .withExposedPorts(8081)
            .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:9092");

    @Test
    void printEnvironment() {
        System.out.println("Kafka bootstrap servers: " + kafka.getBootstrapServers());
        System.out.println("Schema Registry: http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081));
    }
}

