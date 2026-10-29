package com.smartenergy.storage.mongo.repository;

import com.smartenergy.storage.mongo.document.DailyReportDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DailyReportRepository extends MongoRepository<DailyReportDocument, String> {
}
