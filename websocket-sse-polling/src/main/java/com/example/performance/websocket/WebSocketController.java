package com.example.performance.websocket;

import com.example.performance.dto.BroadcastConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class WebSocketController {

    private final WebSocketService webSocketService;

    @PostMapping("/broadcast/start")
    public ResponseEntity<Void> startBroadcast(@RequestBody BroadcastConfig config) {
        webSocketService.broadcast(config);
        return ResponseEntity.noContent().build();
    }
}
