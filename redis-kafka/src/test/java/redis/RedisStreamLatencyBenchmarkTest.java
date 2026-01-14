package redis;

import static org.assertj.core.api.Assertions.*;

import common.ToxiproxyTest;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import io.lettuce.core.Consumer;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.XReadArgs.StreamOffset;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RedisStreamLatencyBenchmarkTest extends ToxiproxyTest {

    private static final String STREAM_KEY = "latency-test-stream";
    private static final String CONSUMER_GROUP = "latency-test-group";
    private static final String CONSUMER_NAME = "consumer-1";

    @Container
    private static final GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379)
            .withNetwork(network());

    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private RedisCommands<String, String> commands;

    @BeforeEach
    void setUp() throws ExecutionException, InterruptedException {
        // Toxiproxy 설정
        proxy = toxiproxy().getProxy(redis, 6379);
        String redisHost = proxy.getContainerIpAddress();
        int redisPort = proxy.getProxyPort();

        RedisURI redisURI = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .build();

        redisClient = RedisClient.create(redisURI);
        connection = redisClient.connect();
        commands = connection.sync();

        // Stream과 Consumer Group 생성
        try {
            // Stream이 없으면 생성
            Map<String, String> initialMessage = new HashMap<>();
            initialMessage.put("init", "true");
            commands.xadd(STREAM_KEY, initialMessage);

            // Consumer Group 생성
            commands.xgroupCreate(StreamOffset.latest(STREAM_KEY), CONSUMER_GROUP);
        } catch (Exception e) {
            // Stream이나 Group이 이미 존재하면 무시
            System.out.println(">>> Stream or consumer group already exists (ignored)");
        }

        System.out.println(">>> [Redis Stream] 초기화 완료");
    }

    @AfterEach
    void tearDown() {
        if (connection != null) {
            connection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }

    @Test
    @DisplayName("Real: Warm-up 및 비동기 수신을 포함한 정밀 측정 (Redis Stream)")
    void preciseLatencyBenchmark() throws Exception {
        // 1. 네트워크 지연 설정 (1ms)
        proxy.toxics()
                .latency("latency-toxic", ToxicDirection.DOWNSTREAM, 1)
                .setJitter(1);

        // ==========================================
        // [Step 1] Warm-up (초기화 비용 제거용)
        // ==========================================
        System.out.println(">>> Warm-up 시작...");
        for (int i = 0; i < 5; i++) {
            Map<String, String> warmupMessage = new HashMap<>();
            warmupMessage.put("timestamp", String.valueOf(System.nanoTime()));
            warmupMessage.put("type", "warmup");
            commands.xadd(STREAM_KEY, warmupMessage);
        }

        // Consumer도 워밍업
        int warmupCount = 0;
        while (warmupCount < 5) {
            List<StreamMessage<String, String>> messages = commands.xreadgroup(
                    Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                    XReadArgs.Builder.block(Duration.ofMillis(100)),
                    StreamOffset.lastConsumed(STREAM_KEY)
            );

            if (messages != null) {
                warmupCount += messages.size();
                for (StreamMessage<String, String> message : messages) {
                    commands.xack(STREAM_KEY, CONSUMER_GROUP, message.getId());
                }
            }
        }
        System.out.println(">>> Warm-up 완료! (이제 진짜 측정을 시작합니다)");

        // ==========================================
        // [Step 2] 진짜 측정 (Consumer 스레드 분리)
        // ==========================================
        int messageCount = 10; // 평균을 내기 위해 10개 측정
        CountDownLatch latch = new CountDownLatch(messageCount);
        AtomicLong totalLatencyNanos = new AtomicLong(0);

        // 1. Consumer를 별도 스레드에서 미리 돌립니다. (Real-world 시뮬레이션)
        Thread consumerThread = new Thread(() -> {
            while (latch.getCount() > 0) {
                List<StreamMessage<String, String>> messages = commands.xreadgroup(
                        Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                        XReadArgs.Builder.block(Duration.ofMillis(10)),
                        StreamOffset.lastConsumed(STREAM_KEY)
                );

                if (messages != null) {
                    for (StreamMessage<String, String> message : messages) {
                        String timestampStr = message.getBody().get("timestamp");
                        String type = message.getBody().get("type");

                        // warmup 메시지는 건너뜀
                        if ("real".equals(type) && timestampStr != null) {
                            long sendTimeNano = Long.parseLong(timestampStr);
                            long receiveTimeNano = System.nanoTime();
                            totalLatencyNanos.addAndGet(receiveTimeNano - sendTimeNano);
                            latch.countDown();
                        }

                        // ACK
                        commands.xack(STREAM_KEY, CONSUMER_GROUP, message.getId());
                    }
                }
            }
        });
        consumerThread.start();

        // 2. 잠시 대기 (Consumer 스레드가 확실히 읽기 시작하도록)
        Thread.sleep(500);

        // 3. Producer 전송
        System.out.println(">>> 메시지 전송 시작...");
        for (int i = 0; i < messageCount; i++) {
            Map<String, String> messageBody = new HashMap<>();
            messageBody.put("timestamp", String.valueOf(System.nanoTime()));
            messageBody.put("type", "real");
            messageBody.put("index", String.valueOf(i));

            commands.xadd(STREAM_KEY, messageBody);

            // 너무 빨리 쏘면 배치로 묶일 수 있으니 미세한 텀을 줌 (선택사항)
            Thread.sleep(1);
        }

        // 4. 완료 대기
        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        // ==========================================
        // [Step 3] 결과 출력
        // ==========================================
        double avgLatencyNs = totalLatencyNanos.get() / (double) messageCount;
        double avgLatencyMs = avgLatencyNs / 1_000_000.0;

        System.out.println("==========================================");
        System.out.println(" [Redis Stream] Network Latency Setting: 1ms (+/- 1ms)");
        System.out.println(" [Redis Stream] Measured Average Latency: " + String.format("%.3f", avgLatencyMs) + " ms");
        System.out.println("==========================================");
    }
}
