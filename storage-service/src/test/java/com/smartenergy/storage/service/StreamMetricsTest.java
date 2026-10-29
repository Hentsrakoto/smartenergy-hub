package com.smartenergy.storage.service;

import com.smartenergy.storage.config.StreamMetrics;
import com.smartenergy.storage.config.RedisStreamProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StreamMetricsTest {
    @Test
    void distinguishesEmptyPelFromRedisUnavailable() {
        var redis = mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS);
        var properties = new RedisStreamProperties();
        var registry = new SimpleMeterRegistry();
        var metrics = new StreamMetrics(redis, properties, registry);
        var summary = mock(PendingMessagesSummary.class);
        when(redis.opsForStream().pending(properties.getName(), properties.getGroup())).thenReturn(summary);
        when(summary.getTotalPendingMessages()).thenReturn(7L, 0L);
        metrics.update();
        assertThat(registry.get("smartenergy.redis.pending").gauge().value()).isEqualTo(7);
        metrics.update();
        assertThat(registry.get("smartenergy.redis.pending").gauge().value()).isZero();
        when(redis.opsForStream().pending(properties.getName(), properties.getGroup()))
                .thenThrow(new IllegalStateException("offline"));
        metrics.update();
        assertThat(registry.get("smartenergy.redis.pending").gauge().value()).isNaN();
    }
}
