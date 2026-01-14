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

    private final SimpMessagingTemplate messagingTemplate;
    private final AtomicLong messageCount = new AtomicLong(0);

    @Async
    public void broadcast(BroadcastConfig config) {
        messageCount.set(0);

        final long startTime = System.currentTimeMillis();
        final long endTime = startTime + (config.duration() * 1000);

        if (config.tps() != null) {
            broadcastByTps(config.tps(), endTime);
        } else if (config.delay() != null) {
            broadcastByDelay(config.delay(), endTime);
        } else {
            throw new IllegalArgumentException("Either tps or delay must be provided");
        }

        log.info("WebSocket broadcast completed. Total messages sent: {}", messageCount.get());
    }

    private void broadcastByTps(long tps, long endTime) {
        final long intervalMillis = 1000 / tps;

        while (System.currentTimeMillis() < endTime) {
            final long iterationStart = System.currentTimeMillis();

            sendMessage();

            final long elapsed = System.currentTimeMillis() - iterationStart;
            final long sleepTime = intervalMillis - elapsed;

            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Broadcast interrupted", e);
                    break;
                }
            }
        }
    }

    private void broadcastByDelay(long delay, long endTime) {
        while (System.currentTimeMillis() < endTime) {
            sendMessage();

            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Broadcast interrupted", e);
                break;
            }
        }
    }

    private void sendMessage() {
        final LoadTestResponse loadTestResponse = LoadTestResponse.createTestMessage();
        messagingTemplate.convertAndSend("/topic/messages", loadTestResponse);
        messageCount.incrementAndGet();
    }

    public long getMessageCount() {
        return messageCount.get();
    }
}
