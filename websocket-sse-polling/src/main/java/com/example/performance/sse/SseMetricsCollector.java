package com.example.performance.sse;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * SSE 메트릭 수집 전담
 * - 메시지 전송/실패 카운트
 * - 브로드캐스트 소요 시간
 * - 연결 관련 메트릭
 */
@Component
public class SseMetricsCollector {

    private final AtomicLong messageCount = new AtomicLong(0);

    // Micrometer metrics
    private final Counter messagesSentCounter;
    private final Counter messagesFailedCounter;
    private final Timer broadcastTimer;
    private final Counter connectionsTotal;
    private final Counter connectionsErrors;

    private final MeterRegistry meterRegistry;

    public SseMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.messagesSentCounter = Counter.builder("sse.messages.sent")
            .description("Total number of SSE messages sent")
            .register(meterRegistry);

        this.messagesFailedCounter = Counter.builder("sse.messages.failed")
            .description("Total number of SSE messages failed to send")
            .register(meterRegistry);

        this.broadcastTimer = Timer.builder("sse.broadcast.duration")
            .description("Time taken to broadcast messages to all clients")
            .register(meterRegistry);

        this.connectionsTotal = Counter.builder("sse.connections.total")
            .description("Total number of SSE connections")
            .register(meterRegistry);

        this.connectionsErrors = Counter.builder("sse.connections.errors")
            .description("Total number of SSE connection errors")
            .register(meterRegistry);
    }

    /**
     * 활성 연결 수 Gauge 등록 (순환 참조 방지를 위해 별도 메서드)
     */
    public void registerActiveConnectionsGauge(SseEmitterManager emitterManager) {
        meterRegistry.gauge("sse.connections.active", emitterManager,
            SseEmitterManager::getActiveConnectionCount);
    }

    /**
     * 메시지 전송 성공 기록
     */
    public void recordMessageSent() {
        messagesSentCounter.increment();
        messageCount.incrementAndGet();
    }

    /**
     * 메시지 전송 실패 기록
     */
    public void recordMessageFailed() {
        messagesFailedCounter.increment();
    }

    /**
     * 브로드캐스트 소요 시간 기록
     */
    public void recordBroadcast(Runnable broadcastTask) {
        broadcastTimer.record(broadcastTask);
    }

    /**
     * 연결 성공 기록
     */
    public void incrementConnectionTotal() {
        connectionsTotal.increment();
    }

    /**
     * 연결 오류 기록
     */
    public void incrementConnectionError() {
        connectionsErrors.increment();
    }

    /**
     * 총 메시지 수 조회
     */
    public long getMessageCount() {
        return messageCount.get();
    }

    /**
     * 메시지 카운트 초기화
     */
    public void resetMessageCount() {
        messageCount.set(0);
    }
}
