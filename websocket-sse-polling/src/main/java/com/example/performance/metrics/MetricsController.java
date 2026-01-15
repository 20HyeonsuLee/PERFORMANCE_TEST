package com.example.performance.metrics;

import com.example.performance.sse.SseController;
import com.example.performance.websocket.WebSocketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsService metricsService;
    private final WebSocketService webSocketService;
    private final SseController sseController;
    private final SimpUserRegistry simpUserRegistry;

    @GetMapping("/websocket")
    public ResponseEntity<PerformanceMetrics> getWebSocketMetrics() {
        final long messageCount = webSocketService.getMessageCount();
        final int connectedClients = simpUserRegistry.getUserCount();

        final PerformanceMetrics metrics = metricsService.collectMetrics(messageCount, connectedClients);

        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/sse")
    public ResponseEntity<PerformanceMetrics> getSseMetrics() {
        final long messageCount = sseController.getMessageCount();
        final int connectedClients = sseController.getConnectedClientCount();

        final PerformanceMetrics metrics = metricsService.collectMetrics(messageCount, connectedClients);

        return ResponseEntity.ok(metrics);
    }
}
