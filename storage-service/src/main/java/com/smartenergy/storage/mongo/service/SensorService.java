package com.smartenergy.storage.mongo.service;

import com.smartenergy.storage.mongo.document.SensorDocument;
import com.smartenergy.storage.mongo.dto.SensorRequest;
import com.smartenergy.storage.mongo.dto.SensorResponse;
import com.smartenergy.storage.mongo.exception.DuplicateSensorException;
import com.smartenergy.storage.mongo.exception.SensorNotFoundException;
import com.smartenergy.storage.mongo.repository.SensorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Service métier de gestion des métadonnées de capteurs dans MongoDB.
 *
 * <p>Le {@code sensorId} métier sert d'identifiant MongoDB ({@code _id}). Il est donc
 * unique et directement utilisable par les autres composants (Redis, Cassandra).</p>
 */
@Service
public class SensorService {

    private static final Logger log = LoggerFactory.getLogger(SensorService.class);

    private final SensorRepository repository;

    public SensorService(SensorRepository repository) {
        this.repository = repository;
    }

    /**
     * Crée un capteur. Échoue avec un conflit si l'identifiant existe déjà.
     */
    public SensorResponse create(SensorRequest request) {
        String sensorId = requireSensorId(request.sensorId());
        if (repository.existsById(sensorId)) {
            throw new DuplicateSensorException(sensorId);
        }

        SensorDocument document = new SensorDocument();
        document.setSensorId(sensorId);
        apply(document, request, true);

        Instant now = Instant.now();
        document.setCreatedAt(now);
        document.setUpdatedAt(now);

        try {
            repository.save(document);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateSensorException(sensorId);
        }

        log.info("Capteur créé dans MongoDB: sensorId='{}', name='{}'", sensorId, document.getName());
        return SensorResponse.from(document);
    }

    /**
     * Liste tous les capteurs par identifiant croissant.
     */
    public List<SensorResponse> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "sensorId"))
                .stream()
                .map(SensorResponse::from)
                .toList();
    }

    /**
     * Retourne un capteur ou lève une exception 404.
     */
    public SensorResponse findById(String sensorId) {
        return SensorResponse.from(findDocument(sensorId));
    }

    /**
     * Variante interne retournant le document complet, utilisée notamment par
     * le service d'agrégats pour récupérer les seuils configurés.
     */
    public SensorDocument findDocument(String sensorId) {
        String normalized = requireSensorId(sensorId);
        return repository.findById(normalized)
                .orElseThrow(() -> new SensorNotFoundException(normalized));
    }

    /**
     * Met à jour un capteur. L'identifiant du chemin est autoritaire.
     * Les champs optionnels absents du corps conservent leur valeur actuelle.
     */
    public SensorResponse update(String sensorId, SensorRequest request) {
        String normalized = requireSensorId(sensorId);
        SensorDocument document = findDocument(normalized);

        if (request.sensorId() != null
                && !normalized.equals(request.sensorId())) {
            throw new IllegalArgumentException(
                    "Le sensorId du corps ('" + request.sensorId()
                            + "') doit correspondre à l'identifiant du chemin ('" + normalized + "')");
        }

        apply(document, request, false);
        document.setUpdatedAt(Instant.now());
        repository.save(document);

        log.info("Capteur mis à jour dans MongoDB: sensorId='{}'", normalized);
        return SensorResponse.from(document);
    }

    private void apply(SensorDocument document, SensorRequest request, boolean creating) {
        if (request.maxPowerKw() == null) {
            throw new IllegalArgumentException("maxPowerKw est obligatoire");
        }
        if (request.maxPowerKw() < 0) {
            throw new IllegalArgumentException("maxPowerKw doit être positif ou nul");
        }
        if (request.active() == null) {
            throw new IllegalArgumentException("active est obligatoire");
        }

        document.setName(requireName(request.name()));
        document.setMaxPowerKw(request.maxPowerKw());
        document.setActive(request.active());

        if (request.building() != null) {
            document.setBuilding(trimToNull(request.building()));
        } else if (creating) {
            document.setBuilding(null);
        }

        if (request.floor() != null) {
            document.setFloor(request.floor());
        }

        if (request.tags() != null) {
            document.setTags(normalizeTags(request.tags()));
        } else if (creating) {
            document.setTags(List.of());
        }

        if (request.minVoltage() != null) {
            document.setMinVoltage(request.minVoltage());
        }
        if (request.maxVoltage() != null) {
            document.setMaxVoltage(request.maxVoltage());
        }
        if (request.maxTemperature() != null) {
            document.setMaxTemperature(request.maxTemperature());
        }
        if (request.samplingIntervalSeconds() != null) {
            document.setSamplingIntervalSeconds(request.samplingIntervalSeconds());
        }

        if (document.getMinVoltage() >= document.getMaxVoltage()) {
            throw new IllegalArgumentException("minVoltage doit être strictement inférieur à maxVoltage");
        }
    }

    private String requireSensorId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("sensorId est obligatoire");
        }
        if (!value.matches("^[a-zA-Z0-9_-]{3,50}$")) {
            throw new IllegalArgumentException(
                    "sensorId doit comporter entre 3 et 50 caractères alphanumériques, tirets ou underscores");
        }
        return value;
    }

    private String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("name est obligatoire");
        }
        String trimmed = value.trim();
        if (trimmed.length() > 120) {
            throw new IllegalArgumentException("name ne doit pas dépasser 120 caractères");
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> normalizeTags(List<String> tags) {
        return tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .toList();
    }
}
