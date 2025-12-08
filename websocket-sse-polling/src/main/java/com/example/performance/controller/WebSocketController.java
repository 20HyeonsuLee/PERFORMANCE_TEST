package com.example.performance.controller;

import com.example.performance.controller.dto.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;

@Controller
public class WebSocketController {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketController.class);

    private final SimpMessagingTemplate messagingTemplate;

    private final SimpUserRegistry userRegistry;

    public WebSocketController(SimpMessagingTemplate messagingTemplate, SimpUserRegistry userRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.userRegistry = userRegistry;
    }

    @MessageMapping("/broadcast")
    @SendTo("/topic/response")
    public Message handleBroadcast(String message) {
        logger.info("Broadcasting message: {}", message);
        return Message.createTestMessage();
    }

    @DeleteMapping("/api/websocket/remove-all")
    public ResponseEntity<Void> disconnectAll() {

        final int activeConnections = userRegistry.getUserCount();

        logger.info("Disconnecting all WebSocket connections. Active connections: {}", activeConnections);

        messagingTemplate.convertAndSend("/topic/disconnect", "SERVER_SHUTDOWN");

        return ResponseEntity.ok().build();
    }
}
