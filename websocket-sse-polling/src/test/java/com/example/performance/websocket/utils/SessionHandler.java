package com.example.performance.websocket.utils;

import java.lang.reflect.Type;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;

@Slf4j
public class SessionHandler extends StompSessionHandlerAdapter {

    public SessionHandler() {
        super();
    }

    @Override
    public Type getPayloadType(StompHeaders headers) {
        return super.getPayloadType(headers);
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
        log.info("Received frame {}", payload);
        super.handleFrame(headers, payload);
    }

    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
        super.afterConnected(session, connectedHeaders);
    }

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
        log.error("STOMP TRANSPORT ERROR: " + exception.getMessage());
        throw new RuntimeException(exception);
    }

    @Override
    public void handleException(
            StompSession session,
            StompCommand command,
            StompHeaders headers,
            byte[] payload,
            Throwable exception
    ) {
        log.error("STOMP EXCEPTION: " + exception.getMessage());
        throw new RuntimeException(exception);
    }

}
