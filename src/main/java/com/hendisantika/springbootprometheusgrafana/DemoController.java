package com.hendisantika.springbootprometheusgrafana;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DemoController {
    private final Counter calls;
    private final MemoryConsumer memory;

    public DemoController(MeterRegistry registry, MemoryConsumer memory) {
        this.memory = memory;
        calls = Counter.builder("demo.controller.calls")
                .description("Number of calls to the custom metric endpoint").register(registry);
    }

    @GetMapping("/ok")
    public ResponseEntity<String> ok() { return ResponseEntity.ok("OK"); }

    @GetMapping("/not-found")
    public ResponseEntity<String> notFound() { return ResponseEntity.status(404).body("Not found"); }

    @GetMapping("/error")
    public ResponseEntity<String> error() { return ResponseEntity.status(500).body("Demonstration error"); }

    @GetMapping("/counted")
    public Map<String, Double> counted() {
        calls.increment();
        return Map.of("calls", calls.count());
    }

    @GetMapping("/memory")
    public Map<String, Long> memory() { return Map.of("retainedBytes", memory.retainedBytes()); }

    @DeleteMapping("/memory")
    public Map<String, Long> release() {
        memory.release();
        return memory();
    }
}
