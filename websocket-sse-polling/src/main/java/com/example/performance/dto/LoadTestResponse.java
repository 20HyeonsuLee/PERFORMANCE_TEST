package com.example.performance.dto;

import java.time.Instant;
import java.util.List;

public record LoadTestResponse(
        Instant timestamp,
        List<InnerMessage> messages
) {

    private static final List<String> playerNames = java.util.List.of(
            "host",
            "guest1",
            "guest2",
            "guest3",
            "guest4",
            "guest5",
            "guest6",
            "guest7"
    );

    public static LoadTestResponse createTestMessage() {
        final List<InnerMessage> messages = playerNames.stream()
                .map(playerName -> new InnerMessage(playerName, 0, 0))
                .toList();
        return new LoadTestResponse(Instant.now(), messages);
    }

    private record InnerMessage(String playerName, int position, int speed) {
    }
}
