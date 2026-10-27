package com.smartenergy.storage.cassandra;

import com.smartenergy.storage.cassandra.entity.MeasurementEntity;
import com.smartenergy.storage.cassandra.entity.MeasurementKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.cassandra.core.mapping.CassandraMappingContext;
import org.springframework.data.cassandra.core.mapping.CassandraPersistentEntity;
import org.springframework.data.cassandra.core.mapping.CassandraPersistentProperty;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie le mapping Spring Data Cassandra sans nécessiter de serveur Cassandra.
 *
 * <p>Ce test garantit que la clé primaire composite est correctement interprétée :
 * deux colonnes de partition ({@code sensor_id}, {@code day}) et une colonne de
 * clustering ({@code measured_at}).</p>
 */
class MeasurementEntityMappingTest {

    private CassandraMappingContext mappingContext;

    @BeforeEach
    void setUp() {
        mappingContext = new CassandraMappingContext();
        mappingContext.afterPropertiesSet();
    }

    @Test
    @DisplayName("La clé composite doit déclarer (sensor_id, day) en partition et measured_at en clustering")
    void shouldMapCompositePrimaryKey() {
        CassandraPersistentEntity<?> keyEntity =
                mappingContext.getRequiredPersistentEntity(MeasurementKey.class);

        assertThat(keyEntity.isCompositePrimaryKey()).isTrue();

        List<CassandraPersistentProperty> properties = new ArrayList<>();
        keyEntity.forEach(properties::add);

        assertThat(properties)
                .filteredOn(CassandraPersistentProperty::isPartitionKeyColumn)
                .extracting(property -> property.getColumnName().asInternal())
                .containsExactlyInAnyOrder("sensor_id", "day");

        assertThat(properties)
                .filteredOn(CassandraPersistentProperty::isClusterKeyColumn)
                .extracting(property -> property.getColumnName().asInternal())
                .containsExactly("measured_at");
    }

    @Test
    @DisplayName("L'entité doit cibler la table measurements_by_sensor_day avec une clé composite")
    void shouldMapEntityTableAndPrimaryKey() {
        CassandraPersistentEntity<?> entity =
                mappingContext.getRequiredPersistentEntity(MeasurementEntity.class);

        assertThat(entity.getTableName().asInternal()).isEqualTo("measurements_by_sensor_day");
        assertThat(entity.getRequiredPersistentProperty("key").getType())
                .isEqualTo(MeasurementKey.class);
    }
}
