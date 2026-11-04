package com.smartenergy.storage.service;
import com.smartenergy.storage.client.AnalyticsClient;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class AnalyticsServiceTest {
 @Test void forwardsSummaryAndAnomaliesWithoutLocalCalculation() {
  var client=mock(AnalyticsClient.class); var service=new AnalyticsService(client); var time=Instant.EPOCH;
  var summary=new AnalyticsSummary("sensor-001",time,time,0,0,0,0,0,0);
  when(client.get("/api/analytics/sensor-001/summary",Map.of("from",time,"to",time),AnalyticsSummary.class)).thenReturn(summary);
  when(client.get("/api/analytics/sensor-001/anomalies",Map.of("from",time,"to",time),MeasurementAnomaly[].class)).thenReturn(new MeasurementAnomaly[0]);
  assertThat(service.summarize("sensor-001",time,time)).isSameAs(summary);
  assertThat(service.findAnomalies("sensor-001",time,time)).isEmpty();
 }
}
