package com.example.performance.websocket.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;

@Slf4j
public class SessionHandler extends StompSessionHandlerAdapter {

    @Override
    public void handleFrame(final StompHeaders headers, final Object payload) {
        log.debug("Received frame: {}", payload);
    }

    @Override
    public void handleTransportError(final StompSession session, final Throwable exception) {
        log.error("STOMP transport error: {}", exception.getMessage());
        throw new StompTransportException(exception);
    }

    @Override
    public void handleException(
            final StompSession session,
            final StompCommand command,
            final StompHeaders headers,
            final byte[] payload,
            final Throwable exception
    ) {
        log.error("STOMP exception on command {}: {}", command, exception.getMessage());
        throw new StompProtocolException(command, exception);
    }

    public static class StompTransportException extends RuntimeException {
        public StompTransportException(final Throwable cause) {
            super("STOMP transport error", cause);
        }
    }

    public static class StompProtocolException extends RuntimeException {
        public StompProtocolException(final StompCommand command, final Throwable cause) {
            super("STOMP protocol error on command: " + command, cause);
        }
    }
}
