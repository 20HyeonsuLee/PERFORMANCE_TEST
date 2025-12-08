package com.example.config.redis.pubsub;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RedisMessageSubscriber implements MessageListener {

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String messageBody = new String(message.getBody());
        String channel = new String(message.getChannel());

        log.debug("Received message from channel [{}]: {}", channel, messageBody);

        // Process message here
        processMessage(messageBody);
    }

    private void processMessage(String message) {
        // 성능 테스트를 위한 메시지 처리 로직
        // 실제 비즈니스 로직을 여기에 구현
    }
}