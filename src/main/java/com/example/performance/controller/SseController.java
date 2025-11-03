package com.example.performance.controller;

import com.example.performance.controller.dto.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@CrossOrigin(origins = "*")
public class SseController {

    private static final Logger logger = LoggerFactory.getLogger(SseController.class);
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L; // 30 minutes

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @GetMapping(value = "/sse/response", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {

        final String id = "client-" + System.currentTimeMillis();
        final SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitters.put(id, emitter);

        logger.info("SSE connection established: {}", id);

        emitter.onCompletion(() -> {
            logger.info("SSE connection completed: {}", id);
            emitters.remove(id);
        });

        emitter.onTimeout(() -> {
            logger.info("SSE connection timeout: {}", id);
            emitters.remove(id);
        });

        emitter.onError(throwable -> {
            logger.error("SSE connection error: {}", id, throwable);
            emitters.remove(id);
        });

        return emitter;
    }

    @PostMapping("/api/broadcast")
    public ResponseEntity<Message> broadcast() {

        final Message message = Message.createTestMessage();

        emitters.forEach((id, emitter) -> sendMessage(emitter, message));

        return ResponseEntity.ok(message);
    }

    @DeleteMapping("/api/remove-all")
    public ResponseEntity<Void> removeAll() {

        emitters.clear();

        return ResponseEntity.ok().build();
    }

    private static void sendMessage(SseEmitter emitter, Message message) {
        try {
            emitter.send(SseEmitter.event().data(message));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
