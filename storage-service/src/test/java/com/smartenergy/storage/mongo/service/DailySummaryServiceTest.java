package com.smartenergy.storage.mongo.service;
import com.smartenergy.storage.client.AnalyticsClient;
import com.smartenergy.storage.mongo.dto.DailySummaryResponse;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class DailySummaryServiceTest {
 @Test void forwardsReadAndForcedRecomputeKeepingDocumentContract() {
  var client=mock(AnalyticsClient.class); var service=new DailySummaryService(client); var day=LocalDate.of(2026,11,1);
  var response=new DailySummaryResponse("sensor-001",day.toString(),2,3,4,1,5,1,Instant.EPOCH);
  when(client.get("/api/sensors/sensor-001/daily-summary",Map.of("date",day,"refresh",true),DailySummaryResponse.class)).thenReturn(response);
  when(client.recompute("sensor-001",day.toString(),DailySummaryResponse.class)).thenReturn(response);
  assertThat(service.getOrCompute("sensor-001",day,true)).isSameAs(response);
  var result=service.computeAndStore("sensor-001",day);
  assertThat(DailySummaryResponse.from(result)).isEqualTo(response);
  assertThat(result.getId()).isEqualTo("sensor-001:2026-11-01");
 }
}
