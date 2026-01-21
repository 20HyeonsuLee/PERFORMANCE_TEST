package com.example.performance.websocket;

import com.example.performance.dto.BroadcastConfig;
import com.example.performance.dto.LoadTestResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketService {

    private static final String TOPIC_MESSAGES = "/topic/messages";

    private final SimpMessagingTemplate messagingTemplate;
    private final AtomicLong messageCount = new AtomicLong(0);

    @Async
    public void broadcast(final BroadcastConfig config) {
        validateConfig(config);
        messageCount.set(0);
        log.info("broadcast config: {}", config);

        final long endTime = config.calculateEndTime(System.currentTimeMillis());

        if (config.isTpsStrategy()) {
            broadcastByTps(config.calculateIntervalMillis(), endTime);
            return;
        }

        broadcastByDelay(config.delay(), endTime);
        log.info("WebSocket broadcast completed. Total messages sent: {}", messageCount.get());
    }

    private void validateConfig(final BroadcastConfig config) {
        if (!config.hasValidStrategy()) {
            throw new IllegalArgumentException("Either tps or delay must be provided, but not both");
        }
    }

    private void broadcastByTps(final long intervalMillis, final long endTime) {
        while (System.currentTimeMillis() < endTime) {
            final long iterationStart = System.currentTimeMillis();
            sendMessage();
            sleepForRemainingInterval(intervalMillis, iterationStart);
        }
    }

    private void sleepForRemainingInterval(final long intervalMillis, final long iterationStart) {
        final long elapsed = System.currentTimeMillis() - iterationStart;
        final long sleepTime = intervalMillis - elapsed;

        if (sleepTime <= 0) {
            return;
        }

        sleepQuietly(sleepTime);
    }

    private void broadcastByDelay(final long delay, final long endTime) {
        while (System.currentTimeMillis() < endTime) {
            sendMessage();
            sleepQuietly(delay);
        }
    }

    private void sleepQuietly(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Broadcast interrupted");
        }
    }

    private void sendMessage() {
        final LoadTestResponse response = LoadTestResponse.createTestMessage();
        messagingTemplate.convertAndSend(TOPIC_MESSAGES, response);
        messageCount.incrementAndGet();
    }

    public long getMessageCount() {
        return messageCount.get();
    }
}
