package common;

import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.ToxiproxyContainer.ContainerProxy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

public abstract class ToxiproxyTest {

    private static final Network network = Network.newNetwork();

    @Container
    private static final ToxiproxyContainer toxiproxy = new ToxiproxyContainer(
            DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.5.0")).withNetwork(network);

    protected static ContainerProxy proxy;

    protected static Network network() {
        return network;
    }

    protected static ToxiproxyContainer toxiproxy() {
        return toxiproxy;
    }
}
