package com.smartenergy.analytics.mongo.service;
import com.smartenergy.analytics.mongo.document.SensorDocument;
import com.smartenergy.analytics.mongo.exception.SensorNotFoundException;
import com.smartenergy.analytics.mongo.repository.SensorRepository;
import org.springframework.stereotype.Service;
@Service
public class SensorService {
    private final SensorRepository repository;
    public SensorService(SensorRepository repository) { this.repository = repository; }
    public SensorDocument findDocument(String id) {
        if (id == null || !id.matches("^[a-zA-Z0-9_-]{3,50}$"))
            throw new IllegalArgumentException("sensorId doit comporter entre 3 et 50 caractères alphanumériques, tirets ou underscores");
        return repository.findById(id).orElseThrow(() -> new SensorNotFoundException(id));
    }
}
