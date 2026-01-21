package com.example.performance.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BroadcastConfig(
        @Min(1) Long delay,
        @Min(1) Long tps,
        @NotNull @Min(1) Long duration
) {

    public boolean hasValidStrategy() {
        return (delay != null) != (tps != null);
    }

    public boolean isTpsStrategy() {
        return tps != null;
    }

    public boolean isDelayStrategy() {
        return delay != null;
    }

    public long calculateEndTime(final long startTime) {
        return startTime + (duration * 1000);
    }

    public long calculateIntervalMillis() {
        if (!isTpsStrategy()) {
            throw new IllegalStateException("TPS strategy is not configured");
        }
        return 1000 / tps;
    }
}
