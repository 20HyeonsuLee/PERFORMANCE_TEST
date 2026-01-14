//package com.example.performance.websocket.utils;
//
//import static org.awaitility.Awaitility.await;
//
//import com.example.performance.dto.BroadcastConfig;
//import com.example.performance.dto.LoadTestResponse;
//import java.lang.reflect.Type;
//import java.util.concurrent.TimeUnit;
//import lombok.extern.slf4j.Slf4j;
//import org.junit.jupiter.api.Test;
//import org.springframework.http.MediaType;
//import org.springframework.messaging.simp.stomp.StompFrameHandler;
//import org.springframework.messaging.simp.stomp.StompHeaders;
//import org.springframework.messaging.simp.stomp.StompSession;
//import org.springframework.web.reactive.function.client.WebClient;
//
//@Slf4j
//class StompClientTest extends WebSocketBaseTest {
//
//    @Test
//    void stompClientTest() {
//        final StompSession session = createSession();
//
//        session.subscribe("/topic/messages", new StompFrameHandler() {
//            @Override
//            public Type getPayloadType(StompHeaders headers) {
//                return LoadTestResponse.class;
//            }
//
//            @Override
//            public void handleFrame(StompHeaders headers, Object payload) {
//                log.info(payload.toString());
//            }
//        });
//
//        startBroadcast(new BroadcastConfig(100L, null, 1000L));
//
//        await()
//                .atMost(1, TimeUnit.SECONDS)
//                .pollInterval(100, TimeUnit.MILLISECONDS);
//    }
//
//    private void startBroadcast(BroadcastConfig config) {
//        WebClient httpClient = WebClient.builder()
//                .baseUrl("http://" + host() + ":" + port())
//                .build();
//
//        httpClient.post()
//                .uri("/broadcast/start")
//                .contentType(MediaType.APPLICATION_JSON)
//                .bodyValue(config)
//                .retrieve()
//                .bodyToMono(Void.class)
//                .block();
//    }
//}
