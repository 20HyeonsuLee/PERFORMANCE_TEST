dependencies {
    implementation("org.springframework.boot:spring-boot-starter-validation")

    testImplementation("org.testcontainers:testcontainers:1.19.0")
    testImplementation("org.testcontainers:junit-jupiter:1.19.0")
    testImplementation("org.testcontainers:toxiproxy:1.19.0")
    testImplementation("eu.rekawek.toxiproxy:toxiproxy-java:2.1.11")
    testImplementation("io.rest-assured:rest-assured:5.3.0")

    implementation("io.prometheus:prometheus-metrics-core:1.3.0")
    implementation("io.prometheus:prometheus-metrics-exporter-pushgateway:1.3.0")
}
