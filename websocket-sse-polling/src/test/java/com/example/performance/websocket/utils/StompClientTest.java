package com.example.performance.websocket.utils;

import com.example.performance.dto.BroadcastConfig;
import com.example.performance.websocket.metrics.WebSocketMetric;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
class StompClientTest extends WebSocketBaseTest {

    private static final String TOPIC_MESSAGES = "/topic/messages";
    private static final long BROADCAST_DELAY_MS = 100L;
    private static final long BROADCAST_DURATION_MS = 10000L;

    @AfterEach
    void tearDown() {
        WebSocketMetric.push();
    }

    @Test
    void shouldReceiveMessagesAndRecordLatency() throws InterruptedException {
        // given
        final StompSession session = createSession();
        final StompFrameHandler latencyRecordingHandler = createLatencyRecordingHandler(WebSocketMetric::recordLatency);

        // when
        session.subscribe(TOPIC_MESSAGES, latencyRecordingHandler);
        startBroadcast(new BroadcastConfig(BROADCAST_DELAY_MS, null, BROADCAST_DURATION_MS));

        // then
        Thread.sleep(BROADCAST_DURATION_MS);
    }

    private void startBroadcast(final BroadcastConfig config) {
        createHttpClient()
                .post()
                .uri("/websocket/broadcast/start")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(config)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    private WebClient createHttpClient() {
        return WebClient.builder()
                .baseUrl(String.format("http://%s:%d", HOST, PORT))
                .build();
    }
}
