package com.example.performance.websocket.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import com.example.performance.dto.LoadTestResponse;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;

@Slf4j
public abstract class WebSocketBaseTest {

    protected static final String HOST = "43.200.188.232";
    protected static final int PORT = 8080;
    private static final String WS_URL = String.format("ws://%s:%d/ws", HOST, PORT);
    private static final int CONNECTION_TIMEOUT_SECONDS = 10;

    protected StompSession createSession() {
        final WebSocketStompClient stompClient = createStompClient();
        return connectWithTimeout(stompClient);
    }

    private WebSocketStompClient createStompClient() {
        final WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(createMessageConverter());
        return stompClient;
    }

    private MappingJackson2MessageConverter createMessageConverter() {
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        final MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter(objectMapper);
        converter.setStrictContentTypeMatch(false);
        return converter;
    }

    private StompSession connectWithTimeout(final WebSocketStompClient stompClient) {
        try {
            return stompClient.connectAsync(WS_URL, new SessionHandler())
                    .get(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WebSocketConnectionException("Connection interrupted", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new WebSocketConnectionException("Failed to connect to WebSocket", exception);
        }
    }

    protected StompFrameHandler createLatencyRecordingHandler(final Consumer<Long> latencyConsumer) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(final StompHeaders headers) {
                return LoadTestResponse.class;
            }

            @Override
            public void handleFrame(final StompHeaders headers, final Object payload) {
                if (payload instanceof LoadTestResponse response) {
                    final long latency = Duration.between(response.timestamp(), Instant.now()).abs().toMillis();
                    latencyConsumer.accept(latency);
                }
            }
        };
    }

    public static class WebSocketConnectionException extends RuntimeException {
        public WebSocketConnectionException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}
