package com.example.performance.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class TimerSampleManager {

    private final Map<String, Sample> samples = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;

    public TimerSampleManager(final MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void start(final String id) {
        final Sample sample = Timer.start(meterRegistry);
        samples.put(id, sample);
    }

    public Optional<Long> stop(final String id, final Timer timer) {
        final Sample sample = samples.remove(id);
        if (sample == null) {
            return Optional.empty();
        }
        final long durationNanos = sample.stop(timer);
        return Optional.of(durationNanos);
    }

    public void remove(final String id) {
        samples.remove(id);
    }
}