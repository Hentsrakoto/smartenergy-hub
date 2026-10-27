package com.smartenergy.storage.mongo.repository;

import com.smartenergy.storage.mongo.document.DailySummaryDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository MongoDB des agrégats quotidiens.
 */
@Repository
public interface DailySummaryRepository extends MongoRepository<DailySummaryDocument, String> {

    Optional<DailySummaryDocument> findBySensorIdAndDate(String sensorId, String date);
}
