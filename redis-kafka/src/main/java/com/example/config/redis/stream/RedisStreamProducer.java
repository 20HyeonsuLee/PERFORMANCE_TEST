package com.example.config.redis.stream;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisStreamProducer {

    private final RedisTemplate<String, Object> redisTemplate;

    public void publish(String message) {
        ObjectRecord<String, String> record = StreamRecords.newRecord()
                .in(RedisStreamConfig.STREAM_KEY)
                .ofObject(message);

        redisTemplate.opsForStream().add(record);
    }

    public void publishWithKey(String key, String value) {
        ObjectRecord<String, String> record = StreamRecords.newRecord()
                .in(RedisStreamConfig.STREAM_KEY)
                .ofObject(value)
                .withId(key);

        redisTemplate.opsForStream().add(record);
    }
}