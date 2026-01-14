package com.example.performance.metrics;

import lombok.Builder;

@Builder
public record PerformanceMetrics(
        long totalMessagesSent,
        int connectedClients,
        long usedMemoryMB,
        long maxMemoryMB,
        double cpuUsage,
        long gcCount,
        long gcTime
) {
}