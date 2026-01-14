package com.example.performance.metrics;

import com.sun.management.OperatingSystemMXBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.List;

@Service
@Slf4j
public class MetricsService {

    private final OperatingSystemMXBean osBean;
    private final MemoryMXBean memoryBean;
    private final List<GarbageCollectorMXBean> gcBeans;

    public MetricsService() {
        this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
    }

    public PerformanceMetrics collectMetrics(long messagesSent, int connectedClients) {
        final long usedMemory = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        final long maxMemory = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
        final double cpuUsage = osBean.getCpuLoad() * 100;

        long totalGcCount = 0;
        long totalGcTime = 0;
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            totalGcCount += gcBean.getCollectionCount();
            totalGcTime += gcBean.getCollectionTime();
        }

        return PerformanceMetrics.builder()
                .totalMessagesSent(messagesSent)
                .connectedClients(connectedClients)
                .usedMemoryMB(usedMemory)
                .maxMemoryMB(maxMemory)
                .cpuUsage(cpuUsage)
                .gcCount(totalGcCount)
                .gcTime(totalGcTime)
                .build();
    }
}