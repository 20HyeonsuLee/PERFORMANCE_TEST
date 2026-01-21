package com.example.performance.websocket;

import com.example.performance.dto.BroadcastConfig;
import com.example.performance.websocket.utils.WebSocketBaseTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompSession;
import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Slf4j
@Disabled("성능 테스트는 수동으로 실행")
class WebSocketPerformanceTest extends WebSocketBaseTest {

    private static final String TOPIC_MESSAGES = "/topic/messages";
    private static final long EXTRA_WAIT_MS = 3000L;

    private final List<StompSession> sessions = new ArrayList<>();

    private static Stream<Arguments> performanceTestScenarios() {
        return Stream.of(
                Arguments.of(10, 1L, 10L, "10 clients, delay=1ms, 10s")
        );
    }

    @AfterEach
    void tearDown() {
        disconnectAllSessions();
    }

    @ParameterizedTest(name = "{3}")
    @MethodSource("performanceTestScenarios")
    void shouldMeasureWebSocketLatency(
            final int clientCount,
            final long delayMs,
            final long durationSeconds,
            final String scenarioName
    ) throws InterruptedException {
        // given
        log.info("Starting scenario: {}", scenarioName);
        final List<Long> latencies = new CopyOnWriteArrayList<>();
        final StompFrameHandler latencyHandler = createLatencyRecordingHandler(latencies::add);

        // when
        connectClientsAndSubscribe(clientCount, latencyHandler);
        startBroadcast(new BroadcastConfig(delayMs, null, durationSeconds));
        waitForBroadcastCompletion(durationSeconds);

        // then
        logPerformanceResults(scenarioName, latencies);
    }

    private void connectClientsAndSubscribe(final int clientCount, final StompFrameHandler handler) {
        for (int i = 0; i < clientCount; i++) {
            final StompSession session = createSession();
            sessions.add(session);
            session.subscribe(TOPIC_MESSAGES, handler);
        }
    }

    private void waitForBroadcastCompletion(final long durationSeconds) throws InterruptedException {
        Thread.sleep(TimeUnit.SECONDS.toMillis(durationSeconds) + EXTRA_WAIT_MS);
    }

    private void logPerformanceResults(final String scenarioName, final List<Long> latencies) {
        final LongSummaryStatistics stats = latencies.stream()
                .mapToLong(Long::longValue)
                .summaryStatistics();

        log.info("=== Performance Test Results: {} ===", scenarioName);
        log.info("Total messages received: {}", latencies.size());
        log.info("Average latency: {} ms", String.format("%.2f", stats.getAverage()));
        log.info("Min latency: {} ms", stats.getMin());
        log.info("Max latency: {} ms", stats.getMax());
    }

    private void disconnectAllSessions() {
        sessions.stream()
                .filter(StompSession::isConnected)
                .forEach(StompSession::disconnect);
        sessions.clear();
    }

    private void startBroadcast(final BroadcastConfig config) {
        RestAssured.given()
                .baseUri("http://" + HOST)
                .port(PORT)
                .contentType(ContentType.JSON)
                .body(config)
                .when()
                .post("/websocket/broadcast/start")
                .then()
                .statusCode(204);
    }
}
