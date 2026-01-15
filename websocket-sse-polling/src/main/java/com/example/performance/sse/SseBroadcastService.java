package com.example.performance.sse;

import com.example.performance.dto.BroadcastConfig;
import com.example.performance.dto.LoadTestResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * SSE 브로드캐스트 로직 전담
 * - TPS/Delay 기반 메시지 브로드캐스트
 * - 단일 브로드캐스트 수행
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SseBroadcastService {

    private final SseEmitterManager emitterManager;
    private final SseMetricsCollector metricsCollector;

    /**
     * 비동기 브로드캐스트 시작
     */
    @Async
    public void broadcastAsync(BroadcastConfig config) {
        log.info("SSE broadcast started: tps={}, duration={}, emitters={}",
            config.tps(), config.duration(), emitterManager.getActiveConnectionCount());

        metricsCollector.resetMessageCount();

        final long endTime = calculateEndTime(config.duration());

        if (config.tps() != null) {
            broadcastByTps(config.tps(), endTime);
        } else if (config.delay() != null) {
            broadcastByDelay(config.delay(), endTime);
        } else {
            throw new IllegalArgumentException("Either tps or delay must be provided");
        }

        log.info("SSE broadcast completed. Total messages sent: {}", metricsCollector.getMessageCount());
    }

    /**
     * 즉시 브로드캐스트 (한 번만)
     */
    public LoadTestResponse broadcastOnce() {
        final LoadTestResponse message = LoadTestResponse.createTestMessage();
        broadcastToAll(message);
        return message;
    }

    private long calculateEndTime(long durationSeconds) {
        return System.currentTimeMillis() + (durationSeconds * 1000);
    }

    private void broadcastByTps(long tps, long endTime) {
        final long intervalMillis = 1000 / tps;
        int count = 0;

        while (System.currentTimeMillis() < endTime) {
            final long iterationStart = System.currentTimeMillis();

            log.debug("Broadcasting message #{} to {} emitters",
                ++count, emitterManager.getActiveConnectionCount());
            broadcastOnce();

            sleepIfNeeded(intervalMillis, iterationStart);
        }
    }

    private void broadcastByDelay(long delayMillis, long endTime) {
        while (System.currentTimeMillis() < endTime) {
            broadcastOnce();
            sleep(delayMillis);
        }
    }

    private void broadcastToAll(LoadTestResponse message) {
        metricsCollector.recordBroadcast(() -> {
            emitterManager.getAllEmitters()
                .forEach(emitter -> sendMessage(emitter, message));
        });
    }

    private void sendMessage(SseEmitter emitter, LoadTestResponse message) {
        try {
            emitter.send(SseEmitter.event().data(message));
            metricsCollector.recordMessageSent();
        } catch (IOException e) {
            log.debug("Failed to send message to emitter: {}", e.getMessage());
            metricsCollector.recordMessageFailed();
        }
    }

    private void sleepIfNeeded(long intervalMillis, long iterationStart) {
        final long elapsed = System.currentTimeMillis() - iterationStart;
        final long sleepTime = intervalMillis - elapsed;

        if (sleepTime > 0) {
            sleep(sleepTime);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Broadcast interrupted", e);
        }
    }
}
