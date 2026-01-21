package com.example.performance.websocket.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@Slf4j
public abstract class WebSocketBaseTest {

    protected static final String host = "43.200.188.232";
    protected static final int port = 8080;

    protected static final String WS_URL = String.format("ws://%s:%d/ws", host, port);

    protected StompSession createSession() {
        final WebSocketClient webSocketClient = new StandardWebSocketClient();
        final WebSocketStompClient stompClient = new WebSocketStompClient(webSocketClient);

        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        final MappingJackson2MessageConverter messageConverter = new MappingJackson2MessageConverter(objectMapper);

        messageConverter.setStrictContentTypeMatch(false);
        stompClient.setMessageConverter(messageConverter);

        try {
            return stompClient.connectAsync(WS_URL, new SessionHandler()).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }
}
