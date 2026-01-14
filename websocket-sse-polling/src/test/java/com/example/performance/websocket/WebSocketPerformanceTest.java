package com.example.performance.websocket;

import com.example.performance.dto.BroadcastConfig;
import com.example.performance.dto.LoadTestResponse;
import com.example.performance.websocket.utils.WebSocketBaseTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;

@Slf4j
class WebSocketPerformanceTest extends WebSocketBaseTest {

    private static Stream<Arguments> performanceTestScenarios() {
        return Stream.of(
                // 네트워크 환경: latency(ms), jitter(ms), clientCount, tps, duration(s), scenarioName
//                Arguments.of(0, 0, 10, 1L, 10L, "LAN: 10 clients, TPS=1 (no latency)"),
                Arguments.of(30, 5, 10, 1L, 10L, "Domestic: 10 clients, TPS=1 (30ms ± 5ms)")
//                Arguments.of(50, 10, 10, 1L, 10L, "Mobile 4G: 10 clients, TPS=1 (50ms ± 10ms)")
//                Arguments.of(30, 5, 100, 1L, 10L, "Domestic: 100 clients, TPS=1 (30ms ± 5ms)"),
//                Arguments.of(30, 5, 100, 10L, 10L, "Domestic: 100 clients, TPS=10 (30ms ± 5ms)"),
//                Arguments.of(30, 5, 100, 100L, 10L, "Domestic: 100 clients, TPS=100 (30ms ± 5ms)"),
//                Arguments.of(150, 20, 100, 1L, 10L, "Global: 100 clients, TPS=1 (150ms ± 20ms)")
        );
    }

    @ParameterizedTest(name = "{5}")
    @MethodSource("performanceTestScenarios")
    void testWebSocketPerformance(long latencyMs, long jitterMs, int clientCount, long tps, long duration, String scenarioName)
            throws InterruptedException, IOException {
        log.info("Starting scenario: {}", scenarioName);

        // 네트워크 지연 추가
        if (latencyMs > 0) {
            if (jitterMs > 0) {
                addLatency(latencyMs, jitterMs);
            } else {
                addLatency(latencyMs);
            }
        }
        final List<Long> latencies = new CopyOnWriteArrayList<>();
        final List<StompSession> sessions = new ArrayList<>();

        for (int i = 0; i < clientCount; i++) {
            StompSession session = createSession();
            sessions.add(session);
            session.subscribe("/topic/messages", new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return LoadTestResponse.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    if (payload instanceof LoadTestResponse response) {
                        long latency = Duration.between(response.timestamp(), Instant.now()).toMillis();
                        latencies.add(latency);
                    }
                }
            });
        }
        startBroadcast(new BroadcastConfig(100L, null, duration));

        Thread.sleep(TimeUnit.SECONDS.toMillis(duration) + 3000);

        LongSummaryStatistics stats = latencies.stream()
                .mapToLong(Long::longValue)
                .summaryStatistics();

        log.info("=== Performance Test Results: {} ===", scenarioName);
        log.info("Total messages received: {}", latencies.size());
        log.info("Average latency: {} ms", String.format("%.2f", stats.getAverage()));
        log.info("Min latency: {} ms", stats.getMin());
        log.info("Max latency: {} ms", stats.getMax());

        // 세션 정리
        sessions.forEach(s -> {
            if (s.isConnected()) {
                s.disconnect();
            }
        });

        // toxic 제거
        removeAllToxics();
    }

    private void startBroadcast(BroadcastConfig config) {
        RestAssured.given()
                .baseUri("http://" + host())
                .port(port())
                .contentType(ContentType.JSON)
                .body(config)
                .when()
                .post("/broadcast/start")
                .then()
                .statusCode(204);
    }
}
