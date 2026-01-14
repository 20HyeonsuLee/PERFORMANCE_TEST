package com.example.performance.controller;

import com.example.performance.dto.BroadcastConfig;
import com.example.performance.dto.LoadTestResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class SseService {

    private final AtomicLong messageCount = new AtomicLong(0);

    @Async
    public void broadcastAsync(BroadcastConfig config, Map<String, SseEmitter> emitters) {
        System.out.println("=== SSE broadcast started: tps=" + config.tps() + ", duration=" + config.duration() + ", emitters=" + emitters.size());
        log.info("SSE broadcast started: tps={}, duration={}, emitters={}",
            config.tps(), config.duration(), emitters.size());

        messageCount.set(0);

        final long startTime = System.currentTimeMillis();
        final long durationMillis = config.duration() * 1000;
        final long endTime = startTime + durationMillis;

        System.out.println("=== SSE broadcast will run until: " + endTime + " (" + durationMillis + "ms)");
        log.info("SSE broadcast will run until: {} ({}ms)", endTime, durationMillis);

        if (config.tps() != null) {
            broadcastByTps(config.tps(), endTime, emitters);
        } else if (config.delay() != null) {
            broadcastByDelay(config.delay(), endTime, emitters);
        } else {
            throw new IllegalArgumentException("Either tps or delay must be provided");
        }

        System.out.println("=== SSE broadcast completed. Total messages sent: " + messageCount.get());
        log.info("SSE broadcast completed. Total messages sent: {}", messageCount.get());
    }

    private void broadcastByTps(long tps, long endTime, Map<String, SseEmitter> emitters) {
        final long intervalMillis = 1000 / tps;
        int count = 0;

        while (System.currentTimeMillis() < endTime) {
            final long iterationStart = System.currentTimeMillis();

            log.info("Broadcasting message #{} to {} emitters", ++count, emitters.size());
            broadcastToAll(emitters);

            final long elapsed = System.currentTimeMillis() - iterationStart;
            final long sleepTime = intervalMillis - elapsed;

            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("SSE broadcast interrupted", e);
                    break;
                }
            }
        }
    }

    private void broadcastByDelay(long delay, long endTime, Map<String, SseEmitter> emitters) {
        while (System.currentTimeMillis() < endTime) {
            broadcastToAll(emitters);

            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("SSE broadcast interrupted", e);
                break;
            }
        }
    }

    private void broadcastToAll(Map<String, SseEmitter> emitters) {
        final LoadTestResponse loadTestResponse = LoadTestResponse.createTestMessage();
        emitters.forEach((id, emitter) -> sendMessage(emitter, loadTestResponse));
        messageCount.incrementAndGet();
    }

    private void sendMessage(SseEmitter emitter, LoadTestResponse loadTestResponse) {
        try {
            emitter.send(SseEmitter.event().data(loadTestResponse));
        } catch (IOException e) {
            // Connection may be closed, log and continue
            log.error("Failed to send message to emitter: {}", e.getMessage());
        }
    }

    public long getMessageCount() {
        return messageCount.get();
    }
}
