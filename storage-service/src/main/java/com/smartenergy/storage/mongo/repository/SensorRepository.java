package com.smartenergy.storage.mongo.repository;

import com.smartenergy.storage.mongo.document.SensorDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository MongoDB des métadonnées de capteurs.
 *
 * <p>L'identifiant Spring Data est le {@code sensorId} métier, utilisé comme
 * {@code _id} dans la collection {@code sensors}.</p>
 */
@Repository
public interface SensorRepository extends MongoRepository<SensorDocument, String> {
}
