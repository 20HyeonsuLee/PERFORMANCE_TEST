package com.example.performance.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class WebSocketMetricService {

    private static final double NANOS_TO_MILLIS = 1_000_000.0;

    private final MeterRegistry meterRegistry;
    private final AtomicLong currentConnections = new AtomicLong(0);
    private final Map<String, Counter> failedCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> disconnectedCounters = new ConcurrentHashMap<>();

    private TimerSampleManager connectionSamples;
    private TimerSampleManager inboundMessageSamples;
    private TimerSampleManager outboundMessageSamples;
    private TimerSampleManager businessLogicSamples;

    private Timer connectionEstablishmentTimer;
    private Counter inboundMessageCounter;
    private Counter outboundMessageCounter;
    private Timer inboundMessageTimer;
    private Timer outboundMessageTimer;
    private Timer businessLogicTimer;

    public WebSocketMetricService(final MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void initializeMetrics() {
        initializeSampleManagers();
        registerGauges();
        registerTimers();
        registerCounters();
    }

    private void initializeSampleManagers() {
        this.connectionSamples = new TimerSampleManager(meterRegistry);
        this.inboundMessageSamples = new TimerSampleManager(meterRegistry);
        this.outboundMessageSamples = new TimerSampleManager(meterRegistry);
        this.businessLogicSamples = new TimerSampleManager(meterRegistry);
    }

    private void registerGauges() {
        Gauge.builder("websocket.connections.current", currentConnections, AtomicLong::get)
                .description("현재 웹소켓 연결 개수")
                .register(meterRegistry);
    }

    private void registerTimers() {
        this.connectionEstablishmentTimer = createTimer("websocket.connection.establishment.time", "웹소켓 연결 수립 시간");
        this.inboundMessageTimer = createTimer("websocket.message.inbound.time", "웹소켓 inbound 메시지 큐 대기 시간");
        this.outboundMessageTimer = createTimer("websocket.message.outbound.time", "웹소켓 outbound 메시지 처리 시간");
        this.businessLogicTimer = createTimer("websocket.message.business.logic.time", "웹소켓 inbound 메시지 비즈니스 로직 처리 시간");
    }

    private Timer createTimer(final String name, final String description) {
        return Timer.builder(name)
                .description(description)
                .publishPercentiles(0.95, 0.99)
                .register(meterRegistry);
    }

    private void registerCounters() {
        this.inboundMessageCounter = Counter.builder("websocket.messages.inbound.total")
                .description("인바운드 메시지 총 개수")
                .register(meterRegistry);
        this.outboundMessageCounter = Counter.builder("websocket.messages.outbound.total")
                .description("아웃바운드 메시지 총 개수")
                .register(meterRegistry);
    }

    public void startConnection(final String sessionId) {
        connectionSamples.start(sessionId);
    }

    public void completeConnection(final String sessionId) {
        currentConnections.incrementAndGet();
        connectionSamples.stop(sessionId, connectionEstablishmentTimer)
                .ifPresent(durationNanos -> {
                    final double durationMs = durationNanos / NANOS_TO_MILLIS;
                    log.info("WebSocket 연결 수립 완료: sessionId={}, duration={}ms", sessionId, durationMs);
                });
    }

    public void failConnection(final String sessionId, final String reason) {
        connectionSamples.remove(sessionId);
        incrementTaggedCounter(failedCounters, "websocket.connections.failed", "웹소켓 연결 실패 건수", reason);
    }

    public void recordDisconnection(final String sessionId, final String reason) {
        connectionSamples.remove(sessionId);
        incrementTaggedCounter(disconnectedCounters, "websocket.connections.disconnected", "웹소켓 연결 해제 건수", reason);
    }

    private void incrementTaggedCounter(
            final Map<String, Counter> counterCache,
            final String metricName,
            final String description,
            final String reason
    ) {
        final String key = metricName + "." + reason;
        final Counter counter = counterCache.computeIfAbsent(key, k ->
                Counter.builder(metricName)
                        .description(description)
                        .tag("reason", reason)
                        .register(meterRegistry)
        );
        counter.increment();
    }

    public void incrementInboundMessage() {
        inboundMessageCounter.increment();
    }

    public void incrementOutboundMessage() {
        outboundMessageCounter.increment();
    }

    public void startInboundMessageTimer(final String messageId) {
        inboundMessageSamples.start(messageId);
    }

    public void stopInboundMessageTimer(final String messageId) {
        inboundMessageSamples.stop(messageId, inboundMessageTimer);
    }

    public void startOutboundMessageTimer(final String messageId) {
        outboundMessageSamples.start(messageId);
    }

    public void stopOutboundMessageTimer(final String messageId) {
        outboundMessageSamples.stop(messageId, outboundMessageTimer);
    }

    public void startBusinessTimer(final String messageId) {
        businessLogicSamples.start(messageId);
    }

    public void stopBusinessTimer(final String messageId) {
        businessLogicSamples.stop(messageId, businessLogicTimer);
    }
}
