package com.smartenergy.analytics.mongo.repository;

import com.smartenergy.analytics.mongo.document.DailyReportDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DailyReportRepository extends MongoRepository<DailyReportDocument, String> {
}
