package com.example.performance.websocket;

import com.example.performance.metrics.WebSocketMetricService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketInboundMetricInterceptor implements ExecutorChannelInterceptor {

    private static final String MESSAGE_ID_HEADER = "messageId";
    private static final Set<StompCommand> TRACKABLE_COMMANDS = Set.of(StompCommand.SEND, StompCommand.SUBSCRIBE);

    private final WebSocketMetricService webSocketMetricService;

    @Override
    public Message<?> preSend(final Message<?> message, final MessageChannel channel) {
        extractAccessor(message).ifPresent(this::handlePreSend);
        return message;
    }

    private void handlePreSend(final StompHeaderAccessor accessor) {
        if (!isTrackableCommand(accessor)) {
            return;
        }

        final String messageId = UUID.randomUUID().toString();
        accessor.setHeader(MESSAGE_ID_HEADER, messageId);
        recordInboundMetrics(messageId);
    }

    private boolean isTrackableCommand(final StompHeaderAccessor accessor) {
        return accessor.getCommand() instanceof StompCommand command && TRACKABLE_COMMANDS.contains(command);
    }

    private void recordInboundMetrics(final String messageId) {
        try {
            webSocketMetricService.startInboundMessageTimer(messageId);
            webSocketMetricService.incrementInboundMessage();
        } catch (Exception exception) {
            log.warn("WebSocket 인바운드 메트릭 수집 중 에러", exception);
        }
    }

    @Override
    public Message<?> beforeHandle(final Message<?> message, final MessageChannel channel, final MessageHandler handler) {
        extractMessageId(message).ifPresent(this::transitionToBusinessLogic);
        return message;
    }

    private void transitionToBusinessLogic(final String messageId) {
        try {
            webSocketMetricService.stopInboundMessageTimer(messageId);
            webSocketMetricService.startBusinessTimer(messageId);
        } catch (Exception exception) {
            log.warn("WebSocket 시간 측정 중 에러", exception);
        }
    }

    @Override
    public void afterMessageHandled(final Message<?> message, final MessageChannel channel, final MessageHandler handler, final Exception exception) {
        extractMessageId(message).ifPresent(this::completeBusinessLogic);
    }

    private void completeBusinessLogic(final String messageId) {
        try {
            webSocketMetricService.stopBusinessTimer(messageId);
        } catch (Exception exception) {
            log.warn("WebSocket 로직 처리 시간 측정 완료 중 에러", exception);
        }
    }

    private Optional<StompHeaderAccessor> extractAccessor(final Message<?> message) {
        return Optional.ofNullable(MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class));
    }

    private Optional<String> extractMessageId(final Message<?> message) {
        return extractAccessor(message)
                .map(accessor -> accessor.getHeader(MESSAGE_ID_HEADER))
                .filter(String.class::isInstance)
                .map(String.class::cast);
    }
}
