package com.example.performance.sse;

import static org.awaitility.Awaitility.await;

import com.example.performance.dto.BroadcastConfig;
import com.example.performance.dto.LoadTestResponse;
import com.example.performance.sse.metrics.SseMetric;
import com.example.performance.websocket.metrics.WebSocketMetric;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

class SsePerformenceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @AfterEach
    void tearDown() {
        SseMetric.push();
    }

    private LoadTestResponse convertResponse(String payload) {
        try {
            return objectMapper.readValue(payload, LoadTestResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testSse() throws InterruptedException {
        WebClient webClient = WebClient.create("http://43.200.188.232:8080");

        webClient.get()
                .uri("/sse/response")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(data -> !data.startsWith("Connected"))
                .map(this::convertResponse)
                .doOnNext(response -> {
                    long duration = Duration.between(response.timestamp(), Instant.now()).abs().toMillis();
                    System.out.println("Latency: " + duration + "ms");
                    SseMetric.recordLatency(duration);
                })
                .doOnError(error -> System.err.println("에러 발생: " + error))
                .doOnComplete(() -> System.out.println("모든 스트림 수신 완료"))
                .subscribe();

        startBroadcast(new BroadcastConfig(100L, null, 10000L));

        Thread.sleep(10000L);
//        await()
//                .atMost(10, TimeUnit.SECONDS)
//                .pollInterval(10000, TimeUnit.MILLISECONDS);
    }

    private void startBroadcast(BroadcastConfig config) {
        WebClient httpClient = WebClient.builder()
                .baseUrl(String.format("http://%s:%d", "43.200.188.232", 8080))
                .build();

        httpClient.post()
                .uri("/sse/broadcast/start")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(config)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }
}
