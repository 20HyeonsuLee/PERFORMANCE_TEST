package com.example.performance.websocket.utils;

import static org.awaitility.Awaitility.await;

import com.example.performance.dto.BroadcastConfig;
import com.example.performance.dto.LoadTestResponse;
import com.example.performance.sse.metrics.SseMetric;
import com.example.performance.websocket.metrics.WebSocketMetric;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.graphql.GraphQlProperties.Websocket;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
class StompClientTest extends WebSocketBaseTest {

    @AfterEach
    void teerDown() {
        WebSocketMetric.push();
    }

    @Test
    void stompClientTest() throws InterruptedException {
        final StompSession session = createSession();

        session.subscribe("/topic/messages", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return LoadTestResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                LoadTestResponse response = (LoadTestResponse) payload;
                long duration = Duration.between(response.timestamp(), Instant.now()).abs().toMillis();
                System.out.println("Latency: " + duration + "ms");
                WebSocketMetric.recordLatency(duration);
            }
        });

        startBroadcast(new BroadcastConfig(100L, null, 10000L));
        Thread.sleep(10000L);
//        await()
//                .atMost(1, TimeUnit.SECONDS)
//                .pollInterval(100, TimeUnit.MILLISECONDS);
    }

    private void startBroadcast(BroadcastConfig config) {
        WebClient httpClient = WebClient.builder()
                .baseUrl(String.format("http://%s:%d", host, port))
                .build();

        httpClient.post()
                .uri("/broadcast/start")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(config)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }
}
