package com.smartenergy.storage.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Snapshot PEL sampled outside HTTP metric reads; NaN means unavailable. */
@Component
public class StreamMetrics {
    private final StringRedisTemplate redis;
    private final RedisStreamProperties properties;
    private volatile double pending = Double.NaN;

    public StreamMetrics(StringRedisTemplate redis, RedisStreamProperties properties, MeterRegistry registry) {
        this.redis = redis;
        this.properties = properties;
        registry.gauge("smartenergy.redis.pending", this, value -> value.pending);
    }

    @Scheduled(fixedDelayString = "${smartenergy.metrics.stream-interval-ms:10000}")
    public void update() {
        try {
            var summary = redis.opsForStream().pending(properties.getName(), properties.getGroup());
            pending = summary == null ? Double.NaN : summary.getTotalPendingMessages();
        } catch (RuntimeException exception) {
            pending = Double.NaN;
        }
    }
}
