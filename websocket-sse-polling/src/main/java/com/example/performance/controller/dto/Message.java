package com.example.performance.controller.dto;

import java.util.List;

public record Message(List<InnerMessage> messages) {

    private static final List<String> playerNames = List.of(
            "host",
            "guest1",
            "guest2",
            "guest3",
            "guest4",
            "guest5",
            "guest6",
            "guest7"
    );

    public static Message createTestMessage() {
        final List<InnerMessage> messages = playerNames.stream()
                .map(playerName -> new InnerMessage(playerName, 0, 0))
                .toList();
        return new Message(messages);
    }

    private record InnerMessage(String playerName, int position, int speed) {
    }
}
