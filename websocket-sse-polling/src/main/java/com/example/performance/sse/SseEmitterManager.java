package com.example.performance.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE Emitter 생명주기 관리
 * - Emitter 생성, 등록, 제거
 * - 연결 콜백 설정
 * - 초기 연결 메시지 전송
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SseEmitterManager {

    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final SseMetricsCollector metricsCollector;

    /**
     * 새로운 SSE 연결 생성
     */
    public SseEmitter createEmitter() {
        final String id = generateEmitterId();
        final SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        registerEmitter(id, emitter);
        setupCallbacks(id, emitter);
        sendInitialMessage(id, emitter);

        metricsCollector.incrementConnectionTotal();
        log.info("SSE connection established: {}", id);

        return emitter;
    }

    /**
     * 모든 활성 emitter 조회
     */
    public Collection<SseEmitter> getAllEmitters() {
        return emitters.values();
    }

    /**
     * 활성 연결 수 조회
     */
    public int getActiveConnectionCount() {
        return emitters.size();
    }

    /**
     * 모든 연결 제거
     */
    public void removeAll() {
        emitters.clear();
        log.info("All SSE connections removed");
    }

    private String generateEmitterId() {
        return "client-" + System.currentTimeMillis();
    }

    private void registerEmitter(String id, SseEmitter emitter) {
        emitters.put(id, emitter);
    }

    private void setupCallbacks(String id, SseEmitter emitter) {
        emitter.onCompletion(() -> {
            log.info("SSE connection completed: {}", id);
            emitters.remove(id);
        });

        emitter.onTimeout(() -> {
            log.info("SSE connection timeout: {}", id);
            emitters.remove(id);
        });

        emitter.onError(throwable -> {
            log.error("SSE connection error: {}", id, throwable);
            emitters.remove(id);
            metricsCollector.incrementConnectionError();
        });
    }

    private void sendInitialMessage(String id, SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data("Connected"));
        } catch (IOException e) {
            log.error("Failed to send initial message to {}", id, e);
            emitters.remove(id);
            metricsCollector.incrementConnectionError();
        }
    }
}
