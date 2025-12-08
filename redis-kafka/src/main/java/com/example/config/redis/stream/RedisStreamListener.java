package com.example.config.redis.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RedisStreamListener implements StreamListener<String, ObjectRecord<String, String>> {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisStreamListener(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void onMessage(ObjectRecord<String, String> message) {
        String streamKey = message.getStream();
        String messageId = message.getId().getValue();
        String value = message.getValue();

        log.debug("Received message from stream [{}] with ID [{}]: {}", streamKey, messageId, value);

        // Process message
        processMessage(value);

        // Acknowledge message
        redisTemplate.opsForStream()
                .acknowledge(RedisStreamConfig.CONSUMER_GROUP, message);
    }

    private void processMessage(String message) {
        // 성능 테스트를 위한 메시지 처리 로직
        // 실제 비즈니스 로직을 여기에 구현
    }
}