package com.example.performance.dto;

public record BroadcastConfig(
        Long delay,
        Long tps,
        Long duration
) {

}
