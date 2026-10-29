package com.smartenergy.storage.controller;

import com.smartenergy.storage.config.RedisStreamProperties;
import com.smartenergy.storage.service.MeasurementPersistenceService;
import com.smartenergy.storage.service.RedisPendingMessageRecoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DiagnosticsExposureTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withBean(MeasurementPersistenceService.class, () -> mock(MeasurementPersistenceService.class))
            .withBean(RedisStreamProperties.class, RedisStreamProperties::new)
            .withBean(RedisPendingMessageRecoveryService.class, () -> mock(RedisPendingMessageRecoveryService.class))
            .withUserConfiguration(StorageStatusController.class);

    @Test
    void diagnosticsAreAbsentUnlessExplicitlyEnabled() {
        context.run(app -> assertThat(app).doesNotHaveBean(StorageStatusController.class));
        context.withPropertyValues("smartenergy.diagnostics.enabled=false")
                .run(app -> assertThat(app).doesNotHaveBean(StorageStatusController.class));
        context.withPropertyValues("smartenergy.diagnostics.enabled=true")
                .run(app -> assertThat(app).hasSingleBean(StorageStatusController.class));
    }
}
