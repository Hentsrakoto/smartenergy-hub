package com.smartenergy.analytics.mongo.repository;
import com.smartenergy.analytics.mongo.document.SensorDocument;
import org.springframework.data.repository.Repository;
import java.util.Optional;
public interface SensorRepository extends Repository<SensorDocument, String> {
    Optional<SensorDocument> findById(String id);
}
