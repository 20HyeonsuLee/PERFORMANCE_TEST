package com.example.performance.controller;

import com.example.performance.dto.BroadcastConfig;
import com.example.performance.dto.LoadTestResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@Slf4j
@RequiredArgsConstructor
public class SseController {

    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final SseService sseService;

    @GetMapping(value = "/sse/response", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {

        final String id = "client-" + System.currentTimeMillis();
        final SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitters.put(id, emitter);

        log.info("SSE connection established: {}", id);

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
        });

        // Send initial connection message
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data("Connected"));
        } catch (IOException e) {
            log.error("Failed to send initial message", e);
            emitters.remove(id);
        }

        return emitter;
    }

    @PostMapping("/api/broadcast")
    public ResponseEntity<LoadTestResponse> broadcast() {
        final LoadTestResponse loadTestResponse = LoadTestResponse.createTestMessage();

        emitters.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event().data(loadTestResponse));
            } catch (IOException e) {
                log.error("Failed to send message: {}", e.getMessage());
            }
        });

        return ResponseEntity.ok(loadTestResponse);
    }

    @PostMapping("/sse/broadcast/start")
    public ResponseEntity<Void> startBroadcast(@RequestBody BroadcastConfig config) {
        sseService.broadcastAsync(config, emitters);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/remove-all")
    public ResponseEntity<Void> removeAll() {
        emitters.clear();
        return ResponseEntity.ok().build();
    }

    public long getMessageCount() {
        return sseService.getMessageCount();
    }

    public int getConnectedClientCount() {
        return emitters.size();
    }
}
