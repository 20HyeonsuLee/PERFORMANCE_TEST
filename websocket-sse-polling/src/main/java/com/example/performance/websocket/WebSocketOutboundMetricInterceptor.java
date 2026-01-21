package com.example.performance.websocket;

import com.example.performance.metrics.WebSocketMetricService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketOutboundMetricInterceptor implements ExecutorChannelInterceptor {

    private static final String MESSAGE_ID_HEADER = "messageId";

    private final WebSocketMetricService webSocketMetricService;

    @Override
    public Message<?> preSend(final Message<?> message, final MessageChannel channel) {
        if (isHeartbeat(message)) {
            return message;
        }

        if (!isMessage(message)) {
            return message;
        }

        return attachMessageIdAndStartTimer(message);
    }

    private Message<?> attachMessageIdAndStartTimer(final Message<?> message) {
        try {
            final var mutableAccessor = SimpMessageHeaderAccessor.getMutableAccessor(message);
            final String messageId = UUID.randomUUID().toString();
            mutableAccessor.setHeader(MESSAGE_ID_HEADER, messageId);

            final Message<?> headerMessage = MessageBuilder.createMessage(
                    message.getPayload(),
                    mutableAccessor.getMessageHeaders()
            );
            webSocketMetricService.startOutboundMessageTimer(messageId);
            return headerMessage;
        } catch (Exception exception) {
            log.error("WebSocket 아웃바운드 메시지 전송 시간 측정 시작 중 에러", exception);
            return message;
        }
    }

    @Override
    public void afterMessageHandled(
            final Message<?> message,
            final MessageChannel channel,
            final MessageHandler handler,
            final Exception exception
    ) {
        if (isHeartbeat(message)) {
            return;
        }

        if (isMessage(message)) {
            extractMessageId(message).ifPresent(this::stopOutboundTimer);
        }

        incrementOutboundCounter();
    }

    private void stopOutboundTimer(final String messageId) {
        try {
            webSocketMetricService.stopOutboundMessageTimer(messageId);
        } catch (Exception exception) {
            log.error("WebSocket 아웃바운드 메시지 전송 시간 측정 완료 중 에러", exception);
        }
    }

    private void incrementOutboundCounter() {
        try {
            webSocketMetricService.incrementOutboundMessage();
        } catch (Exception exception) {
            log.error("WebSocket 아웃바운드 메트릭 수집 중 에러", exception);
        }
    }

    private boolean isHeartbeat(final Message<?> message) {
        return SimpMessageType.HEARTBEAT.equals(getMessageType(message));
    }

    private boolean isMessage(final Message<?> message) {
        return SimpMessageType.MESSAGE.equals(getMessageType(message));
    }

    private SimpMessageType getMessageType(final Message<?> message) {
        return SimpMessageHeaderAccessor.getMessageType(message.getHeaders());
    }

    private Optional<String> extractMessageId(final Message<?> message) {
        return Optional.ofNullable(message.getHeaders().get(MESSAGE_ID_HEADER))
                .filter(String.class::isInstance)
                .map(String.class::cast);
    }
}
