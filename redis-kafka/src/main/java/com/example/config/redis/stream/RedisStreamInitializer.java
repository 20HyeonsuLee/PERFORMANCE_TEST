package com.example.config.redis.stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStreamInitializer implements CommandLineRunner {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void run(String... args) {
        try {
            // Check if stream exists
            if (Boolean.FALSE.equals(redisTemplate.hasKey(RedisStreamConfig.STREAM_KEY))) {
                log.info("Creating Redis Stream: {}", RedisStreamConfig.STREAM_KEY);
                // Create stream by adding a dummy message
                redisTemplate.opsForStream().add(RedisStreamConfig.STREAM_KEY, java.util.Map.of("init", "true"));
            }

            // Check if consumer group exists
            try {
                StreamInfo.XInfoGroups groups = redisTemplate.opsForStream()
                        .groups(RedisStreamConfig.STREAM_KEY);

                boolean groupExists = groups.stream()
                        .anyMatch(group -> RedisStreamConfig.CONSUMER_GROUP.equals(group.groupName()));

                if (!groupExists) {
                    log.info("Creating consumer group: {}", RedisStreamConfig.CONSUMER_GROUP);
                    redisTemplate.opsForStream().createGroup(
                            RedisStreamConfig.STREAM_KEY,
                            RedisStreamConfig.CONSUMER_GROUP
                    );
                }
            } catch (Exception e) {
                log.info("Creating consumer group: {}", RedisStreamConfig.CONSUMER_GROUP);
                redisTemplate.opsForStream().createGroup(
                        RedisStreamConfig.STREAM_KEY,
                        RedisStreamConfig.CONSUMER_GROUP
                );
            }

            log.info("Redis Stream initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Redis Stream", e);
        }
    }
}