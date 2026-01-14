package kafka;

import static org.assertj.core.api.Assertions.*;

import common.ToxiproxyTest;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@Slf4j
class KafkaLatencyBenchmarkTest extends ToxiproxyTest {

    private static final String TOPIC_NAME = "latency-test";

    @Container
    private static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
            .withNetwork(network());

    private KafkaProducer<String, String> producer;
    private KafkaConsumer<String, String> consumer;
    private static String bootstrapServers;

    @BeforeEach
    void setUp() throws ExecutionException, InterruptedException {
        // Toxiproxy 설정
        proxy = toxiproxy().getProxy(kafka, 9093);
        bootstrapServers = "PLAINTEXT://" + proxy.getContainerIpAddress() + ":" + proxy.getProxyPort();

        // 토픽 생성
        try (AdminClient adminClient = AdminClient.create(
                Collections.singletonMap("bootstrap.servers", bootstrapServers))) {
            adminClient.createTopics(List.of(new NewTopic(TOPIC_NAME, 1, (short) 1))).all().get();
        }

        // [Producer 설정] Redis-like 튜닝 적용
        Properties prodProps = new Properties();
        prodProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        prodProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        prodProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // 1. acks = 1 (Leader 기록 시 즉시 응답, Redis Master 쓰기와 유사)
        prodProps.put(ProducerConfig.ACKS_CONFIG, "1");

        // 2. linger.ms = 0 (배치 대기 없이 즉시 전송, Latency 최우선)
        prodProps.put(ProducerConfig.LINGER_MS_CONFIG, "0");

        // (선택) TCP 버퍼 크기를 줄여서 더 빨리 쏘도록 유도할 수도 있으나, 일단 위 두 개가 핵심입니다.

        producer = new KafkaProducer<>(prodProps);

        // [Consumer 설정]
        Properties consProps = new Properties();
        consProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consProps.put(ConsumerConfig.GROUP_ID_CONFIG, "latency-group-" + UUID.randomUUID());
        consProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        // (선택) Consumer도 최대한 빨리 가져오도록 fetch.min.bytes 등을 조정할 수 있습니다.
        // consProps.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "1"); // 기본값이 1이라 생략 가능

        consumer = new KafkaConsumer<>(consProps);
        consumer.subscribe(Collections.singletonList(TOPIC_NAME));

        // 파티션 할당 대기
        while (consumer.assignment().isEmpty()) {
            consumer.poll(Duration.ofMillis(100));
        }
        System.out.println(">>> [Consumer] 파티션 할당 완료: " + consumer.assignment());
    }

    @AfterEach
    void tearDown() {
        if (producer != null) {
            producer.close();
        }
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    @DisplayName("Real: Warm-up 및 비동기 수신을 포함한 정밀 측정")
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
            producer.send(new ProducerRecord<>(TOPIC_NAME, "warmup-" + i, "warmup"));
        }
        // 컨슈머도 워밍업
        int warmupCount = 0;
        while (warmupCount < 5) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            warmupCount += records.count();
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
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(10)); // 짧게 자주 폴링
                for (ConsumerRecord<String, String> record : records) {
                    long sendTimeNano = Long.parseLong(record.value());
                    long receiveTimeNano = System.nanoTime();
                    totalLatencyNanos.addAndGet(receiveTimeNano - sendTimeNano);
                    latch.countDown();
                }
            }
        });
        consumerThread.start();

        // 2. 잠시 대기 (Consumer 스레드가 확실히 poll() 루프에 진입하도록)
        Thread.sleep(500);

        // 3. Producer 전송
        System.out.println(">>> 메시지 전송 시작...");
        for (int i = 0; i < messageCount; i++) {
            String payload = String.valueOf(System.nanoTime()); // 나노초 단위
            // acks=1, linger.ms=0 이므로 거의 즉시 전송됨
            producer.send(new ProducerRecord<>(TOPIC_NAME, "real-key-" + i, payload));
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
        System.out.println(" Network Latency Setting: 1ms (+/- 1ms)");
        System.out.println(" Measured Average Latency: " + String.format("%.3f", avgLatencyMs) + " ms");
        System.out.println("==========================================");
    }
}
