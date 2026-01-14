//package com.example.performance.sse;
//
//import com.example.performance.dto.BroadcastConfig;
//import com.example.performance.dto.LoadTestResponse;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.extern.slf4j.Slf4j;
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.params.ParameterizedTest;
//import org.junit.jupiter.params.provider.Arguments;
//import org.junit.jupiter.params.provider.MethodSource;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.test.web.server.LocalServerPort;
//import org.springframework.http.MediaType;
//import org.springframework.http.codec.ServerSentEvent;
//import org.springframework.web.reactive.function.client.WebClient;
//import reactor.core.Disposable;
//import reactor.core.publisher.Flux;
//
//import java.lang.management.ManagementFactory;
//import java.lang.management.MemoryMXBean;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicLong;
//import java.util.stream.Stream;
//
//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
//@Slf4j
//public class SsePerformanceTest {
//
//    @LocalServerPort
//    private int port;
//
//    private List<Disposable> subscriptions;
//    private WebClient webClient;
//    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
//    private final com.sun.management.OperatingSystemMXBean osBean =
//        (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
//
//    @BeforeEach
//    void setUp() {
//        subscriptions = new ArrayList<>();
//        webClient = WebClient.builder()
//            .baseUrl("http://localhost:" + port)
//            .build();
//    }
//
//    @AfterEach
//    void tearDown() {
//        for (Disposable subscription : subscriptions) {
//            if (!subscription.isDisposed()) {
//                subscription.dispose();
//            }
//        }
//        subscriptions.clear();
//    }
//
//    static Stream<Arguments> performanceTestScenarios() {
//        return Stream.of(
////                Arguments.of(10, 1L, 10L, "10 clients, TPS=1")
////                Arguments.of(100, 1L, 10L, "100 clients, TPS=1"),
////                Arguments.of(100, 10L, 10L, "100 clients, TPS=10")
//                Arguments.of(100, 100L, 10L, "100 clients, TPS=100")
//        );
//    }
//
//    @ParameterizedTest(name = "{3}")
//    @MethodSource("performanceTestScenarios")
//    void testSsePerformance(int clientCount, long tps, long duration, String scenarioName) throws Exception {
//        log.info("Starting test: {}", scenarioName);
//
//        // Warm-up
//        System.gc();
//        Thread.sleep(2000);
//
//        // Metrics before test
//        long memoryBefore = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
//
//        // Connect clients
//        CountDownLatch connectLatch = new CountDownLatch(clientCount);
//        AtomicLong receivedMessages = new AtomicLong(0);
//        List<Long> latencies = new ArrayList<>();
//
//        for (int i = 0; i < clientCount; i++) {
//            connectClient(connectLatch, receivedMessages, latencies);
//        }
//
//        boolean connected = connectLatch.await(30, TimeUnit.SECONDS);
//        if (!connected) {
//            log.error("Failed to connect all clients. Connected: {}/{}",
//                clientCount - connectLatch.getCount(), clientCount);
//        }
//
//        log.info("All clients connected: {}", subscriptions.size());
//
//        // Wait for server-side connections to establish
//        int attempts = 0;
//        while (attempts < 50) {
//            try {
//                Integer connectedCount = webClient.get()
//                    .uri("/metrics/sse")
//                    .retrieve()
//                    .bodyToMono(String.class)
//                    .map(body -> {
//                        // Parse connectedClients from JSON
//                        try {
//                            return Integer.parseInt(body.split("\"connectedClients\":")[1].split(",")[0]);
//                        } catch (Exception e) {
//                            return 0;
//                        }
//                    })
//                    .block();
//
//                if (connectedCount != null && connectedCount >= clientCount) {
//                    log.info("Server confirmed {} clients connected", connectedCount);
//                    break;
//                }
//            } catch (Exception e) {
//                log.debug("Waiting for server connections...");
//            }
//
//            Thread.sleep(100);
//            attempts++;
//        }
//
//        // Start broadcasting
//        BroadcastConfig config = new BroadcastConfig(null, tps, duration);
//        startBroadcast(config);
//
//        // Wait for test to complete
//        Thread.sleep((duration + 2) * 1000);
//
//        // Metrics after test
//        long memoryAfter = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
//        double cpuUsage = osBean.getCpuLoad() * 100;
//
//        // Calculate statistics
//        long expectedMessages = tps * duration;
//        long totalExpectedMessages = expectedMessages * clientCount;
//        double successRate = (receivedMessages.get() / (double) totalExpectedMessages) * 100;
//
//        long avgLatency = latencies.isEmpty() ? 0 :
//            latencies.stream().mapToLong(Long::longValue).sum() / latencies.size();
//
//        // Print results
//        log.info("========================================");
//        log.info("Test Results: {}", scenarioName);
//        log.info("========================================");
//        log.info("Connected Clients: {}", subscriptions.size());
//        log.info("Expected Messages per client: {}", expectedMessages);
//        log.info("Total Received Messages: {}", receivedMessages.get());
//        log.info("Success Rate: {}", String.format("%.2f%%", successRate));
//        log.info("Average Latency: {} ms", avgLatency);
//        log.info("Memory Before: {} MB", memoryBefore);
//        log.info("Memory After: {} MB", memoryAfter);
//        log.info("Memory Used: {} MB", memoryAfter - memoryBefore);
//        log.info("CPU Usage: {}", String.format("%.2f%%", cpuUsage));
//        log.info("========================================");
//
//        // Cool down
//        Thread.sleep(3000);
//    }
//
//    private void connectClient(CountDownLatch connectLatch, AtomicLong receivedMessages, List<Long> latencies) {
//        Flux<ServerSentEvent<String>> eventStream = webClient.get()
//            .uri("/sse/response")
//            .accept(MediaType.TEXT_EVENT_STREAM)
//            .retrieve()
//            .bodyToFlux(new org.springframework.core.ParameterizedTypeReference<ServerSentEvent<String>>() {});
//
//        Disposable subscription = eventStream.subscribe(
//            event -> {
//                // Skip initial connection message
//                if ("connected".equals(event.event())) {
//                    if (connectLatch.getCount() > 0) {
//                        connectLatch.countDown();
//                    }
//                    return;
//                }
//
//                if (connectLatch.getCount() > 0) {
//                    connectLatch.countDown();
//                }
//
//                receivedMessages.incrementAndGet();
//
//                try {
//                    String data = (String) event.data();
//                    ObjectMapper mapper = new ObjectMapper();
//                    mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
//                    LoadTestResponse loadTestResponse = mapper.readValue(data, LoadTestResponse.class);
//
//                    long latency = System.currentTimeMillis() - loadTestResponse.timestamp().toEpochMilli();
//                    synchronized (latencies) {
//                        latencies.add(latency);
//                    }
//                } catch (Exception e) {
//                    log.error("Failed to parse message", e);
//                }
//            },
//            error -> {
//                log.error("SSE error", error);
//            },
//            () -> {
//                log.info("SSE connection completed");
//            }
//        );
//
//        subscriptions.add(subscription);
//    }
//
//    private void startBroadcast(BroadcastConfig config) {
//        try {
//            log.info("Calling broadcast API with config: tps={}, duration={}", config.tps(), config.duration());
//            webClient.post()
//                .uri("/sse/broadcast/start")
//                .contentType(MediaType.APPLICATION_JSON)
//                .bodyValue(config)
//                .retrieve()
//                .bodyToMono(Void.class)
//                .block();
//
//            log.info("Broadcast API call completed");
//
//            // Give broadcast thread time to start
//            Thread.sleep(1000);
//        } catch (Exception e) {
//            log.error("Failed to start broadcast", e);
//        }
//    }
//}
