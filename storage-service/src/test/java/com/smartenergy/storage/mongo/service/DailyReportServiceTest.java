package com.smartenergy.storage.mongo.service;
import com.smartenergy.storage.client.AnalyticsClient;
import com.smartenergy.storage.mongo.dto.DailyReportResponse;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Map;
import static org.mockito.Mockito.*;
class DailyReportServiceTest {
 @Test void delegatesReportToAnalytics() {
  var client=mock(AnalyticsClient.class); var day=LocalDate.of(2026,11,1);
  new DailyReportService(client).getOrCreate("sensor-001",day);
  verify(client).get("/api/sensors/sensor-001/daily-report",Map.of("date",day),DailyReportResponse.class);
 }
}
