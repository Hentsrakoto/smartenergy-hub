package com.smartenergy.storage.service;

import com.smartenergy.storage.cassandra.service.CassandraMeasurementService;
import com.smartenergy.storage.model.EnergyMeasurementEvent;
import com.smartenergy.storage.mongo.exception.SensorNotFoundException;
import com.smartenergy.storage.mongo.service.DailySummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CassandraPersistenceServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 26);

    @Mock private CassandraMeasurementService cassandraService;
    @Mock private DailySummaryService dailySummaryService;

    private CassandraPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new CassandraPersistenceService(cassandraService, dailySummaryService,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    @Test
    void storesMeasurementBeforeRefreshingDailySummaryForUtcDay() {
        EnergyMeasurementEvent event = event("100-0");

        service.persist(event);

        InOrder order = inOrder(cassandraService, dailySummaryService);
        order.verify(cassandraService).save(event);
        order.verify(dailySummaryService).computeAndStore("sensor-001", DAY);
        assertThat(service.getPersistedCount()).isEqualTo(1);
    }

    @Test
    void treatsUnregisteredSensorAsRawMeasurementOnly() {
        EnergyMeasurementEvent event = event("101-0");
        doThrow(new SensorNotFoundException("sensor-001"))
                .when(dailySummaryService).computeAndStore("sensor-001", DAY);

        service.persist(event);

        verify(cassandraService).save(event);
        assertThat(service.getPersistedCount()).isEqualTo(1);
    }

    @Test
    void leavesProcessingFailedWhenMongoRefreshFailsSoRedisCanRetry() {
        EnergyMeasurementEvent event = event("102-0");
        RuntimeException failure = new RuntimeException("MongoDB unavailable");
        doThrow(failure).when(dailySummaryService).computeAndStore("sensor-001", DAY);

        assertThatThrownBy(() -> service.persist(event)).isSameAs(failure);

        verify(cassandraService).save(event);
        assertThat(service.getPersistedCount()).isZero();
        assertThat(service.getPersistedEvents()).isEmpty();
    }

    @Test
    void doesNotRefreshSummaryIfCassandraWriteFails() {
        EnergyMeasurementEvent event = event("103-0");
        RuntimeException failure = new RuntimeException("Cassandra unavailable");
        doThrow(failure).when(cassandraService).save(event);

        assertThatThrownBy(() -> service.persist(event)).isSameAs(failure);

        verifyNoInteractions(dailySummaryService);
        assertThat(service.getPersistedCount()).isZero();
    }

    @Test
    void refreshesSummaryAgainWhenTheSameEventIsReplayed() {
        EnergyMeasurementEvent event = event("104-0");

        service.persist(event);
        service.persist(event);

        verify(cassandraService, times(2)).save(event);
        verify(dailySummaryService, times(2)).computeAndStore("sensor-001", DAY);
    }

    @Test
    void serializesConcurrentWritesForTheSameSensorAndDay() throws Exception {
        CountDownLatch firstSaveEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstSave = new CountDownLatch(1);
        CountDownLatch secondCallStarted = new CountDownLatch(1);
        CountDownLatch overlappingSave = new CountDownLatch(1);
        AtomicInteger activeWrites = new AtomicInteger();
        AtomicInteger maximumActiveWrites = new AtomicInteger();
        doAnswer(invocation -> {
            int active = activeWrites.incrementAndGet();
            maximumActiveWrites.accumulateAndGet(active, Math::max);
            if (active > 1) {
                overlappingSave.countDown();
            }
            if (firstSaveEntered.getCount() > 0) {
                firstSaveEntered.countDown();
                if (!releaseFirstSave.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("La première écriture n'a pas été libérée");
                }
            }
            activeWrites.decrementAndGet();
            return null;
        }).when(cassandraService).save(any());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> service.persist(event("200-0")));
            assertThat(firstSaveEntered.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> second = executor.submit(() -> {
                secondCallStarted.countDown();
                service.persist(event("201-0"));
            });
            assertThat(secondCallStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(overlappingSave.await(200, TimeUnit.MILLISECONDS)).isFalse();
            releaseFirstSave.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            releaseFirstSave.countDown();
            executor.shutdownNow();
        }

        assertThat(maximumActiveWrites.get()).isEqualTo(1);
    }

    private EnergyMeasurementEvent event(String recordId) {
        return new EnergyMeasurementEvent(recordId, "sensor-001",
                Instant.parse("2026-10-26T23:59:59Z"), 2.0, 230.0, 25.0);
    }
}
