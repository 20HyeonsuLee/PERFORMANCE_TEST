package com.example.performance.sse;

import com.example.performance.dto.BroadcastConfig;
import com.example.performance.dto.LoadTestResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE HTTP 엔드포인트 처리
 * - SSE 연결 엔드포인트
 * - 브로드캐스트 시작/중지 엔드포인트
 * - 연결 관리 엔드포인트
 */
@RestController
@Slf4j
@RequiredArgsConstructor
public class SseController {

    private final SseEmitterManager emitterManager;
    private final SseBroadcastService broadcastService;
    private final SseMetricsCollector metricsCollector;

    @PostConstruct
    public void init() {
        // 순환 참조 방지를 위해 초기화 시점에 Gauge 등록
        metricsCollector.registerActiveConnectionsGauge(emitterManager);
    }

    /**
     * SSE 연결 엔드포인트
     */
    @GetMapping(value = "/sse/response", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return emitterManager.createEmitter();
    }

    /**
     * 즉시 브로드캐스트 (한 번만)
     */
    @PostMapping("/api/broadcast")
    public ResponseEntity<LoadTestResponse> broadcast() {
        final LoadTestResponse response = broadcastService.broadcastOnce();
        return ResponseEntity.ok(response);
    }

    /**
     * 브로드캐스트 시작 (비동기)
     */
    @PostMapping("/sse/broadcast/start")
    public ResponseEntity<Void> startBroadcast(@RequestBody BroadcastConfig config) {
        broadcastService.broadcastAsync(config);
        return ResponseEntity.noContent().build();
    }

    /**
     * 모든 연결 제거
     */
    @DeleteMapping("/api/remove-all")
    public ResponseEntity<Void> removeAll() {
        emitterManager.removeAll();
        return ResponseEntity.ok().build();
    }

    /**
     * 전송된 메시지 총 개수 조회
     */
    public long getMessageCount() {
        return metricsCollector.getMessageCount();
    }

    /**
     * 현재 연결된 클라이언트 수 조회
     */
    public int getConnectedClientCount() {
        return emitterManager.getActiveConnectionCount();
    }
}
