package com.hendisantika.springbootprometheusgrafana;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"demo.memory.enabled=false", "demo.memory.limit-mib=32"})
class SpringBootPrometheusGrafanaApplicationTests {
    @Value("${local.server.port}") int port;
    @Autowired MemoryConsumer memory;
    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void endpointsAndMetrics() throws Exception {
        assertEquals(200, get("/api/ok").statusCode());
        assertEquals(404, get("/api/not-found").statusCode());
        assertEquals(500, get("/api/error").statusCode());
        assertTrue(get("/api/counted").body().contains("1.0"));
        assertTrue(get("/api/counted").body().contains("2.0"));
        HttpResponse<String> metrics = get("/actuator/prometheus");
        assertEquals(200, metrics.statusCode());
        assertTrue(metrics.body().contains("demo_controller_calls_total{application=\"memory-demo\"} 2.0"));
        assertTrue(metrics.body().contains("jvm_memory_used_bytes"));
        assertTrue(metrics.body().contains("status=\"500\""));
        assertTrue(get("/actuator/health").body().contains("UP"));
    }

    @Test
    void disabledMemoryAndRelease() {
        memory.consume();
        assertEquals(0, memory.retainedBytes());
        memory.release();
        assertEquals(0, memory.retainedBytes());
    }

    @Test
    void allocationIsBoundedAndReleaseStopsIt() {
        MemoryConsumer isolated = new MemoryConsumer(new io.micrometer.core.instrument.simple.SimpleMeterRegistry(), 32, true);
        for (int i = 0; i < 4; i++) isolated.consume();
        assertEquals(32L * 1024 * 1024, isolated.retainedBytes());
        isolated.release();
        isolated.consume();
        assertEquals(0, isolated.retainedBytes());
    }
}
