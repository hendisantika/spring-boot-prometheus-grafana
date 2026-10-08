package com.hendisantika.springbootprometheusgrafana;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class MemoryConsumer {
    private final List<byte[]> blocks = new ArrayList<>();
    private final int limitMiB;
    private boolean active;
    private long retainedBytes;

    public MemoryConsumer(MeterRegistry registry,
            @Value("${demo.memory.limit-mib:256}") int limitMiB,
            @Value("${demo.memory.enabled:true}") boolean enabled) {
        if (limitMiB < 0 || limitMiB > 256) {
            throw new IllegalArgumentException("Memory limit must be between 0 and 256 MiB");
        }
        this.limitMiB = limitMiB;
        this.active = enabled;
        Gauge.builder("demo.memory.retained.bytes", this, MemoryConsumer::retainedBytes)
                .description("Bytes retained by the memory demonstration")
                .baseUnit("bytes").register(registry);
    }

    @Scheduled(fixedDelayString = "${demo.memory.interval-ms:1000}", initialDelay = 1000)
    public synchronized void consume() {
        if (!active) return;
        long remaining = limitMiB * 1024L * 1024L - retainedBytes;
        if (remaining <= 0) return;
        byte[] block = new byte[(int) Math.min(16L * 1024L * 1024L, remaining)];
        Arrays.fill(block, (byte) 1); // Touch pages so they consume physical memory.
        blocks.add(block); // Keep references to prevent garbage collection.
        retainedBytes += block.length;
    }

    public synchronized long retainedBytes() { return retainedBytes; }

    public synchronized void release() {
        active = false;
        blocks.clear();
        retainedBytes = 0;
    }
}
