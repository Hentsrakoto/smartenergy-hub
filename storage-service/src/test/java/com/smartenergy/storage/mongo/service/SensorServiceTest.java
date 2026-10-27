package com.smartenergy.storage.mongo.service;

import com.smartenergy.storage.mongo.document.SensorDocument;
import com.smartenergy.storage.mongo.dto.SensorRequest;
import com.smartenergy.storage.mongo.dto.SensorResponse;
import com.smartenergy.storage.mongo.exception.DuplicateSensorException;
import com.smartenergy.storage.mongo.exception.SensorNotFoundException;
import com.smartenergy.storage.mongo.repository.SensorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensorServiceTest {

    @Mock
    private SensorRepository repository;

    private SensorService service;

    @BeforeEach
    void setUp() {
        service = new SensorService(repository);
    }

    @Test
    @DisplayName("create() doit enregistrer le capteur avec les valeurs par défaut")
    void shouldCreateSensorWithDefaults() {
        SensorRequest request = request("SENSOR-001", "Compteur salle serveur",
                8.0, true, List.of("critical", "server-room"));
        when(repository.existsById("SENSOR-001")).thenReturn(false);
        when(repository.save(any(SensorDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SensorResponse response = service.create(request);

        assertThat(response.sensorId()).isEqualTo("SENSOR-001");
        assertThat(response.name()).isEqualTo("Compteur salle serveur");
        assertThat(response.building()).isEqualTo("Bâtiment A");
        assertThat(response.floor()).isEqualTo(1);
        assertThat(response.maxPowerKw()).isEqualTo(8.0);
        assertThat(response.active()).isTrue();
        assertThat(response.tags()).containsExactly("critical", "server-room");
        assertThat(response.minVoltage()).isEqualTo(150.0);
        assertThat(response.maxVoltage()).isEqualTo(300.0);
        assertThat(response.maxTemperature()).isEqualTo(125.0);
        assertThat(response.samplingIntervalSeconds()).isEqualTo(60);
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();

        verify(repository).save(any(SensorDocument.class));
    }

    @Test
    @DisplayName("create() doit refuser un identifiant déjà existant")
    void shouldRejectDuplicateSensor() {
        SensorRequest request = request("SENSOR-001", "Doublon", 8.0, true, List.of());
        when(repository.existsById("SENSOR-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateSensorException.class)
                .hasMessageContaining("existe déjà");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create() doit refuser un sensorId vide")
    void shouldRejectBlankSensorId() {
        SensorRequest request = request("   ", "Capteur sans id", 8.0, true, List.of());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sensorId est obligatoire");

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("create() doit refuser un identifiant que le Collector ne peut pas utiliser")
    void shouldRejectSensorIdOutsideMeasurementContract() {
        SensorRequest request = request("sensor/001", "Capteur", 8.0, true, List.of());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sensorId");

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("create() doit refuser les espaces autour d'un identifiant")
    void shouldRejectSensorIdWithSurroundingSpaces() {
        SensorRequest request = request(" SENSOR-001 ", "Capteur", 8.0, true, List.of());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sensorId");

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("create() doit convertir un conflit MongoDB en DuplicateSensorException")
    void shouldTranslateDuplicateKeyException() {
        SensorRequest request = request("SENSOR-001", "Conflit", 8.0, true, List.of());
        when(repository.existsById("SENSOR-001")).thenReturn(false);
        when(repository.save(any(SensorDocument.class))).thenThrow(new DuplicateKeyException("dup"));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateSensorException.class)
                .hasMessageContaining("existe déjà");
    }

    @Test
    @DisplayName("create() doit normaliser les tags")
    void shouldNormalizeTags() {
        SensorRequest request = request("SENSOR-002", "Tags", 8.0, true,
                List.of(" critical ", "critical", "", "server-room"));
        when(repository.existsById("SENSOR-002")).thenReturn(false);
        when(repository.save(any(SensorDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SensorResponse response = service.create(request);

        assertThat(response.tags()).containsExactly("critical", "server-room");
    }

    @Test
    @DisplayName("findAll() doit demander un tri par sensorId croissant")
    void shouldListSensorsSortedById() {
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(
                document("SENSOR-001"), document("SENSOR-002")));

        List<SensorResponse> result = service.findAll();

        assertThat(result).extracting(SensorResponse::sensorId)
                .containsExactly("SENSOR-001", "SENSOR-002");

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(repository).findAll(sortCaptor.capture());
        Sort.Order order = sortCaptor.getValue().getOrderFor("sensorId");
        assertThat(order).isNotNull();
        assertThat(order.isAscending()).isTrue();
    }

    @Test
    @DisplayName("findById() doit lever une exception 404 si le capteur est absent")
    void shouldThrowWhenSensorNotFound() {
        when(repository.findById("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById("UNKNOWN"))
                .isInstanceOf(SensorNotFoundException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    @DisplayName("update() doit mettre à jour les champs et préserver createdAt")
    void shouldUpdateSensorAndPreserveCreatedAt() {
        SensorDocument existing = document("SENSOR-001");
        Instant createdAt = Instant.parse("2026-10-01T10:00:00Z");
        existing.setCreatedAt(createdAt);
        existing.setUpdatedAt(createdAt);
        when(repository.findById("SENSOR-001")).thenReturn(Optional.of(existing));
        when(repository.save(any(SensorDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SensorRequest request = new SensorRequest(
                null, "Nouveau nom", "Bâtiment B", 2, 12.0, false,
                List.of("updated"), null, 240.0, 30.0, 30
        );

        SensorResponse response = service.update("SENSOR-001", request);

        assertThat(response.name()).isEqualTo("Nouveau nom");
        assertThat(response.building()).isEqualTo("Bâtiment B");
        assertThat(response.floor()).isEqualTo(2);
        assertThat(response.maxPowerKw()).isEqualTo(12.0);
        assertThat(response.active()).isFalse();
        assertThat(response.tags()).containsExactly("updated");
        assertThat(response.minVoltage()).isEqualTo(150.0);
        assertThat(response.maxVoltage()).isEqualTo(240.0);
        assertThat(response.maxTemperature()).isEqualTo(30.0);
        assertThat(response.samplingIntervalSeconds()).isEqualTo(30);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isAfter(createdAt);
    }

    @Test
    @DisplayName("update() doit refuser un sensorId de corps différent du chemin")
    void shouldRejectIdMismatchOnUpdate() {
        SensorDocument existing = document("SENSOR-001");
        when(repository.findById("SENSOR-001")).thenReturn(Optional.of(existing));

        SensorRequest request = request("SENSOR-999", "Nom", 8.0, true, List.of());

        assertThatThrownBy(() -> service.update("SENSOR-001", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("doit correspondre à l'identifiant du chemin");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update() doit refuser une plage de tension incohérente")
    void shouldRejectInvalidVoltageRange() {
        SensorDocument existing = document("SENSOR-001");
        when(repository.findById("SENSOR-001")).thenReturn(Optional.of(existing));

        SensorRequest request = new SensorRequest(
                null, "Nom", null, null, 8.0, true, null,
                null, 100.0, null, null
        );

        assertThatThrownBy(() -> service.update("SENSOR-001", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minVoltage doit être strictement inférieur à maxVoltage");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update() doit conserver les champs optionnels absents")
    void shouldKeepOptionalFieldsWhenNullOnUpdate() {
        SensorDocument existing = document("SENSOR-001");
        existing.setBuilding("Bâtiment A");
        existing.setFloor(1);
        existing.setTags(List.of("initial"));
        when(repository.findById("SENSOR-001")).thenReturn(Optional.of(existing));
        when(repository.save(any(SensorDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SensorRequest request = new SensorRequest(
                null, "Nom mis à jour", null, null, 9.0, true, null,
                null, null, null, null
        );

        SensorResponse response = service.update("SENSOR-001", request);

        assertThat(response.building()).isEqualTo("Bâtiment A");
        assertThat(response.floor()).isEqualTo(1);
        assertThat(response.tags()).containsExactly("initial");
        assertThat(response.minVoltage()).isEqualTo(150.0);
        assertThat(response.maxVoltage()).isEqualTo(300.0);
        assertThat(response.maxTemperature()).isEqualTo(125.0);
        assertThat(response.samplingIntervalSeconds()).isEqualTo(60);
    }

    private SensorRequest request(String sensorId, String name, Double maxPowerKw,
                                  Boolean active, List<String> tags) {
        return new SensorRequest(
                sensorId,
                name,
                "Bâtiment A",
                1,
                maxPowerKw,
                active,
                tags,
                null,
                null,
                null,
                null
        );
    }

    private SensorDocument document(String sensorId) {
        SensorDocument document = new SensorDocument();
        document.setSensorId(sensorId);
        document.setName("Capteur " + sensorId);
        document.setMaxPowerKw(8.0);
        document.setActive(true);
        return document;
    }
}
