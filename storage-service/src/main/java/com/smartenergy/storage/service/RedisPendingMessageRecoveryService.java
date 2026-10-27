package com.smartenergy.storage.service;

import com.smartenergy.storage.config.RedisStreamProperties;
import com.smartenergy.storage.model.EnergyMeasurementEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Service de reprise des messages non acquittés (Pending Entries List - PEL).
 * Garantit la sémantique de livraison "At-Least-Once" en rejouant les messages
 * non acquittés suite à un arrêt inattendu ou un échec temporaire de persistance.
 */
@Service
public class RedisPendingMessageRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(RedisPendingMessageRecoveryService.class);

    private final StringRedisTemplate redisTemplate;
    private final MeasurementPersistenceService persistenceService;
    private final RedisStreamProperties properties;

    public RedisPendingMessageRecoveryService(
            StringRedisTemplate redisTemplate,
            MeasurementPersistenceService persistenceService,
            RedisStreamProperties properties) {
        this.redisTemplate = redisTemplate;
        this.persistenceService = persistenceService;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Démarrage de la vérification initiale de la PEL (Pending Entries List)...");
        recoverPendingMessages();
    }

    @Scheduled(fixedDelayString = "${smartenergy.stream.recovery-interval-ms:10000}")
    public void scheduledPendingCheck() {
        recoverPendingMessages();
    }

    /**
     * Inspecte la PEL et tente de traiter et acquitter les messages en souffrance.
     *
     * @return le nombre de messages récupérés et acquittés avec succès
     */
    @SuppressWarnings("unchecked")
    public synchronized int recoverPendingMessages() {
        String stream = properties.getName();
        String group = properties.getGroup();
        String consumerName = properties.getConsumerName();

        try {
            PendingMessagesSummary summary = redisTemplate.opsForStream().pending(stream, group);
            if (summary == null || summary.getTotalPendingMessages() == 0) {
                log.trace("Aucun message en attente dans la PEL pour '{}' / '{}'", stream, group);
                return 0;
            }

            log.info("PEL détectée pour '{}' / '{}' : {} message(s) en attente.",
                    stream, group, summary.getTotalPendingMessages());

            // 1. Lire les messages en attente pour le consommateur courant
            List<MapRecord<String, Object, Object>> pendingRecords = redisTemplate.opsForStream().read(
                    Consumer.from(group, consumerName),
                    StreamReadOptions.empty().count(100),
                    StreamOffset.create(stream, ReadOffset.from("0"))
            );

            int recovered = 0;
            if (pendingRecords != null && !pendingRecords.isEmpty()) {
                for (MapRecord<String, Object, Object> record : pendingRecords) {
                    if (processAndAcknowledge(record)) {
                        recovered++;
                    }
                }
            }

            // 2. Réclamer les messages des anciens consommateurs seulement après 5 secondes d'inactivité.
            PendingMessages pendingList = redisTemplate.opsForStream().pending(
                    stream,
                    group,
                    Range.unbounded(),
                    100
            );

            if (pendingList != null && !pendingList.isEmpty()) {
                for (PendingMessage pm : pendingList) {
                    // Si le message a passé plus de 5s sans être acquitté par son consommateur
                    if (!consumerName.equals(pm.getConsumerName())
                            && pm.getElapsedTimeSinceLastDelivery().compareTo(Duration.ofSeconds(5)) > 0) {
                        log.info("Message orphelin détecté dans la PEL (id: {}, idle: {} ms, consumer: {}). Réclamation et traitement...",
                                pm.getIdAsString(), pm.getElapsedTimeSinceLastDelivery().toMillis(), pm.getConsumerName());

                        // XCLAIM transfère la propriété PEL avant traitement (XRANGE ne le ferait pas).
                        List<MapRecord<String, Object, Object>> claimedRecords = redisTemplate.opsForStream().claim(
                                stream, group, consumerName, Duration.ofSeconds(5), pm.getId()
                        );

                        if (claimedRecords != null && !claimedRecords.isEmpty()) {
                            for (MapRecord<String, Object, Object> record : claimedRecords) {
                                if (processAndAcknowledge(record)) {
                                    recovered++;
                                }
                            }
                        }
                    }
                }
            }

            if (recovered > 0) {
                log.info("Reprise PEL réussie : {} message(s) ont été traités et acquittés.", recovered);
            }
            return recovered;

        } catch (Exception e) {
            log.warn("Notice lors de l'inspection de la PEL : {}", e.getMessage());
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean processAndAcknowledge(MapRecord<String, ?, ?> record) {
        String recordId = record.getId().getValue();
        EnergyMeasurementEvent event;
        try {
            event = EnergyMeasurementEvent.fromMap(recordId, (Map<Object, Object>) record.getValue());
        } catch (RuntimeException e) {
            log.error("Message en attente '{}' illisible/invalide: {}. NON acquitté, conservé dans la PEL pour inspection.",
                    recordId, e.getMessage());
            return false;
        }
        try {
            persistenceService.persist(event);

            redisTemplate.opsForStream().acknowledge(
                    properties.getName(),
                    properties.getGroup(),
                    record.getId()
            );

            log.info("Message en attente '{}' ré-exécuté et acquitté (XACK) pour le capteur '{}'",
                    recordId, event.sensorId());
            return true;
        } catch (Exception e) {
            log.error("Impossible de récupérer/acquitter le message '{}' de la PEL : {}", recordId, e.getMessage());
            return false;
        }
    }
}
