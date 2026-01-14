package com.example.performance;

import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Slf4j
public abstract class ToxiproxyTest {

    @Container
    protected static ToxiproxyContainer toxiproxy = new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.5.0");

    protected Proxy proxy;

    @BeforeEach
    void setup() throws IOException {
        final ToxiproxyClient client = new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getControlPort());
        proxy = client.createProxy("was", "0.0.0.0:8666", "host.docker.internal:8080");
    }

    protected String host() {
        return toxiproxy.getHost();
    }

    protected Integer port() {
        return toxiproxy.getMappedPort(8666);
    }

    /**
     * 네트워크 지연(latency)을 추가합니다.
     * @param latencyMs 지연 시간 (밀리초)
     * @param jitterMs 지터 (밀리초, 지연 시간의 +/- 변동폭)
     */
    protected void addLatency(long latencyMs, long jitterMs) throws IOException {
        proxy.toxics()
                .latency("latency", ToxicDirection.DOWNSTREAM, latencyMs)
                .setJitter(jitterMs);
        log.info("Added latency: {}ms ± {}ms", latencyMs, jitterMs);
    }

    /**
     * 네트워크 지연(latency)을 추가합니다. (jitter 없음)
     * @param latencyMs 지연 시간 (밀리초)
     */
    protected void addLatency(long latencyMs) throws IOException {
        proxy.toxics()
                .latency("latency", ToxicDirection.DOWNSTREAM, latencyMs);
        log.info("Added latency: {}ms", latencyMs);
    }

    /**
     * 대역폭 제한을 추가합니다.
     * @param rateBytesPerSec 초당 전송 바이트 수 (예: 1024 = 1KB/s)
     */
    protected void addBandwidthLimit(long rateBytesPerSec) throws IOException {
        proxy.toxics()
                .bandwidth("bandwidth", ToxicDirection.DOWNSTREAM, rateBytesPerSec);
        log.info("Added bandwidth limit: {} bytes/sec", rateBytesPerSec);
    }

    /**
     * 모든 toxic을 제거합니다.
     */
    protected void removeAllToxics() throws IOException {
        proxy.toxics().getAll().forEach(toxic -> {
            try {
                toxic.remove();
                log.info("Removed toxic: {}", toxic.getName());
            } catch (IOException e) {
                log.error("Failed to remove toxic: {}", toxic.getName(), e);
            }
        });
    }
}
