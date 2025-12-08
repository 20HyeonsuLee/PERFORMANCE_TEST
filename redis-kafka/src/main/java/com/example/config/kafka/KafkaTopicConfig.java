package com.example.config.kafka;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaTopicConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    @Bean
    public NewTopic performanceTestTopic() {
        return TopicBuilder.name("performance-test-topic")
                .partitions(3)
                .replicas(1)
                .config("compression.type", "lz4")
                .config("retention.ms", "604800000") // 7 days
                .build();
    }

    @Bean
    public NewTopic redisEventTopic() {
        return TopicBuilder.name("redis-event-topic")
                .partitions(3)
                .replicas(1)
                .config("compression.type", "lz4")
                .config("retention.ms", "604800000") // 7 days
                .build();
    }
}