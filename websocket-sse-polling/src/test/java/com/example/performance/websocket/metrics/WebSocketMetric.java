package com.example.performance.websocket.metrics;

import io.prometheus.metrics.core.datapoints.DistributionDataPoint;
import io.prometheus.metrics.core.metrics.Histogram;
import io.prometheus.metrics.exporter.pushgateway.PushGateway;
import io.prometheus.metrics.model.snapshots.Unit;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public final class WebSocketMetric {

    private static final String PUSHGATEWAY_ADDRESS = "localhost:9091";
    private static final String JOB_NAME = "websocket-performance-test";
    private static final int PUSH_THRESHOLD = 10;
    private static final double MS_TO_SECONDS = 1000.0;

    private static final PushGateway PUSH_GATEWAY = PushGateway.builder()
            .address(PUSHGATEWAY_ADDRESS)
            .job(JOB_NAME)
            .build();

    private static final DistributionDataPoint HISTOGRAM = Histogram.builder()
            .name("websocket_message_client_latency")
            .help("웹소켓 메시지 레이턴시 (클라이언트 도달)")
            .unit(Unit.SECONDS)
            .register();

    private static final AtomicLong counter = new AtomicLong(0);
    private static final ReentrantLock pushLock = new ReentrantLock();

    private WebSocketMetric() {
    }

    public static void recordLatency(final long latencyMs) {
        HISTOGRAM.observe(latencyMs / MS_TO_SECONDS);
        incrementAndPushIfNeeded();
    }

    public static void push() {
        pushLock.lock();
        try {
            PUSH_GATEWAY.push();
            counter.set(0);
        } catch (IOException exception) {
            log.error("Failed to push metrics to gateway", exception);
            throw new MetricPushException(exception);
        } finally {
            pushLock.unlock();
        }
    }

    private static void incrementAndPushIfNeeded() {
        if (counter.incrementAndGet() < PUSH_THRESHOLD) {
            return;
        }

        if (!pushLock.tryLock()) {
            return;
        }

        try {
            if (counter.get() >= PUSH_THRESHOLD) {
                PUSH_GATEWAY.push();
                counter.set(0);
            }
        } catch (IOException exception) {
            log.warn("Failed to push metrics during threshold check", exception);
        } finally {
            pushLock.unlock();
        }
    }

    public static class MetricPushException extends RuntimeException {
        public MetricPushException(final Throwable cause) {
            super("Failed to push metrics to Pushgateway", cause);
        }
    }
}
