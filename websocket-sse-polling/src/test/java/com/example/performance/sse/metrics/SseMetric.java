package com.example.performance.sse.metrics;

import io.prometheus.metrics.core.datapoints.DistributionDataPoint;
import io.prometheus.metrics.core.metrics.Histogram;
import io.prometheus.metrics.exporter.pushgateway.PushGateway;
import io.prometheus.metrics.model.snapshots.Unit;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

public class SseMetric {

    private static PushGateway pushGateway = PushGateway.builder()
            .address("localhost:9091")
            .job("sse-performance-test")
            .build();

    private static final DistributionDataPoint histogram = Histogram.builder()
            .name("sse_message_client_latency")
            .help("sse 메시지 레이턴시 (클라이언트 도달)")
            .unit(Unit.SECONDS)
            .register();

    private static final AtomicLong counter = new AtomicLong(0);
    private static final int PUSH_THRESHOLD = 10;

    public static void recordLatency(long latencyMs) {
        try {
            histogram.observe(latencyMs / 1000.0);
            incrementAndPush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void push() {
        try {
            pushGateway.push();
            counter.set(0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void incrementAndPush() throws IOException {
        if (counter.incrementAndGet() >= PUSH_THRESHOLD) {
            synchronized (SseMetric.class) {
                if (counter.get() >= PUSH_THRESHOLD) {
                    push();
                }
            }
        }
    }
}
