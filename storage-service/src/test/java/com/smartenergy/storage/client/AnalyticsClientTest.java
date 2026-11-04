package com.smartenergy.storage.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartenergy.storage.mongo.dto.DailySummaryResponse;
import com.smartenergy.storage.mongo.exception.SensorNotFoundException;
import com.smartenergy.storage.mongo.service.DailySummaryService;
import com.smartenergy.storage.cassandra.service.CassandraMeasurementService;
import com.smartenergy.storage.service.CassandraPersistenceService;
import com.smartenergy.storage.model.EnergyMeasurementEvent;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.time.*;
import java.util.Map;
import java.net.SocketTimeoutException;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyticsClientTest {
 RestTemplate http; MockRestServiceServer server; AnalyticsClient client;
 static final String URL="http://analytics/internal/sensors/sensor-001/daily-summary?date=2026-11-01";
 static final String JSON="{\"sensorId\":\"sensor-001\",\"date\":\"2026-11-01\",\"avgPowerKw\":2,\"maxPowerKw\":3,\"energyKwh\":4,\"anomalyCount\":1,\"measurementCount\":5,\"minPowerKw\":1,\"updatedAt\":\"2026-11-01T00:00:00Z\"}";
 @BeforeEach void setup() { http=new RestTemplate();server=MockRestServiceServer.bindTo(http).build(); client=new AnalyticsClient(http,new ObjectMapper().findAndRegisterModules(),"http://analytics"); }
 @AfterEach void verifyServer() { server.verify(); }
 @Test void anomalyJsonPreservesFlatMeasurementIdentityAndNullTemperature() {
  String body="[{\"measurement\":{\"sensorId\":\"sensor-001\",\"day\":\"2026-11-01\",\"measuredAt\":\"2026-11-01T00:00:00Z\",\"powerKw\":2,\"voltage\":230,\"temperature\":null},\"reasons\":[\"POWER_ABOVE_MAX\"]}]";
  server.expect(requestTo("http://analytics/api/anomalies")).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
  var result=client.get("/api/anomalies",Map.of(),com.smartenergy.storage.service.MeasurementAnomaly[].class);
  assertThat(result[0].measurement().getSensorId()).isEqualTo("sensor-001");
  assertThat(result[0].measurement().getMeasuredAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
  assertThat(result[0].measurement().getDay()).isEqualTo(LocalDate.of(2026,11,1));
  assertThat(result[0].measurement().getTemperature()).isNull();
  assertThat(result[0].reasons()).containsExactly("POWER_ABOVE_MAX");
 }
 @Test void reportJsonPreservesSnapshotFields() {
  String body="{\"sensorId\":\"sensor-001\",\"sensorName\":\"Cuisine\",\"date\":\"2026-11-01\",\"measurementCount\":5,\"energyKwh\":4,\"anomalyCount\":1,\"status\":\"ALERT\",\"generatedAt\":\"2026-11-01T00:00:00Z\"}";
  server.expect(requestTo("http://analytics/api/report")).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
  var result=client.get("/api/report",Map.of(),com.smartenergy.storage.mongo.dto.DailyReportResponse.class);
  assertThat(result.sensorName()).isEqualTo("Cuisine"); assertThat(result.status()).isEqualTo("ALERT");
  assertThat(result.generatedAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
 }
 @Test void postRecomputesAndDecodesJson() {
  server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(JSON,MediaType.APPLICATION_JSON));
  var response=client.recompute("sensor-001","2026-11-01",DailySummaryResponse.class);
  assertThat(response.energyKwh()).isEqualTo(4); assertThat(response.updatedAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
 }
 @Test void onlyExactTopLevelCodeAllowsMissingSensor() {
  server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"code\":\"SENSOR_NOT_FOUND\"}").contentType(MediaType.APPLICATION_JSON));
  assertThatThrownBy(()->client.recompute("sensor-001","2026-11-01",DailySummaryResponse.class)).isInstanceOf(SensorNotFoundException.class);
 }
 @Test void exactMissingSensorAllowsRawPersistence() {
  server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"code\":\"SENSOR_NOT_FOUND\"}").contentType(MediaType.APPLICATION_JSON));
  var persistence=new CassandraPersistenceService(mock(CassandraMeasurementService.class),new DailySummaryService(client),new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
  persistence.persist(new EnergyMeasurementEvent("1-0","sensor-001",Instant.parse("2026-11-01T00:00:00Z"),2,230,null));
  assertThat(persistence.getPersistedCount()).isEqualTo(1);
 }
 @Test void exactCodeOnOtherStatusCannotAllowAcknowledgement() {
  server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"code\":\"SENSOR_NOT_FOUND\"}").contentType(MediaType.APPLICATION_JSON));
  assertUnavailable();
 }
 @Test void arbitrary404LeavesPersistenceFailed() {
  server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"message\":\"missing route\"}").contentType(MediaType.APPLICATION_JSON));
  var persistence=new CassandraPersistenceService(mock(CassandraMeasurementService.class),new DailySummaryService(client),new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
  assertThatThrownBy(()->persistence.persist(new EnergyMeasurementEvent("1-0","sensor-001",Instant.parse("2026-11-01T00:00:00Z"),2,230,null))).isInstanceOf(AnalyticsHttpException.class);
  assertThat(persistence.getPersistedCount()).isZero(); assertThat(persistence.getPersistedEvents()).isEmpty();
 }
 @Test void nestedCodeCannotAllowAcknowledgement() {
  server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"error\":{\"code\":\"SENSOR_NOT_FOUND\"}}").contentType(MediaType.APPLICATION_JSON));
  assertUnavailable();
 }
 @Test void wrongCodeCannotAllowAcknowledgement() {
  server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"code\":\"NO_MEASUREMENTS\"}").contentType(MediaType.APPLICATION_JSON));
  assertUnavailable();
 }
 @Test void readFailureIsUnavailable() {
  server.expect(requestTo("http://analytics/api/test")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("secret host"));
  assertThatThrownBy(()->client.get("/api/test",Map.of(),DailySummaryResponse.class)).isInstanceOfSatisfying(AnalyticsHttpException.class,e->{assertThat(e.status()).isEqualTo(503);assertThat(e.body()).isNull();});
 }
 @Test void upstreamFailureIsUnavailable() { server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));assertUnavailable(); }
 @Test void timeoutIsUnavailable() { server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("secret host")));assertUnavailable(); }
 @Test void malformedJsonIsUnavailable() { server.expect(requestTo(URL)).andRespond(withSuccess("{",MediaType.APPLICATION_JSON));assertUnavailable(); }
 @Test void trailingGarbageCannotAllowAcknowledgement() {
  server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"code\":\"SENSOR_NOT_FOUND\"}garbage").contentType(MediaType.APPLICATION_JSON));
  assertUnavailable();
 }
 @Test void secondJsonDocumentCannotAllowAcknowledgement() {
  server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"code\":\"SENSOR_NOT_FOUND\"} {}").contentType(MediaType.APPLICATION_JSON));
  assertUnavailable();
 }
 @Test void trailingContentAfterSuccessfulSummaryIsUnavailable() {
  server.expect(requestTo(URL)).andRespond(withSuccess(JSON + " {}",MediaType.APPLICATION_JSON));
  assertUnavailable();
 }
 @Test void summaryForAnotherSensorIsUnavailable() {
  server.expect(requestTo(URL)).andRespond(withSuccess(JSON.replace("sensor-001","sensor-002"),MediaType.APPLICATION_JSON));
  assertUnavailable();
 }
 @Test void missingFieldsAreUnavailable() { server.expect(requestTo(URL)).andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));assertUnavailable(); }
 @Test void badRequestRetainsStatusAndJson() { relayError(HttpStatus.BAD_REQUEST); }
 @Test void read404RetainsStatusAndJson() { relayError(HttpStatus.NOT_FOUND); }
 void relayError(HttpStatus status) {
  server.expect(requestTo("http://analytics/api/test?date=2026-11-01")).andRespond(withStatus(status).body("{\"message\":\"date invalide\"}").contentType(MediaType.APPLICATION_JSON));
  assertThatThrownBy(()->client.get("/api/test",Map.of("date","2026-11-01"),DailySummaryResponse.class)).isInstanceOfSatisfying(AnalyticsHttpException.class,e->{assertThat(e.status()).isEqualTo(status.value());assertThat(e.body().path("message").asText()).isEqualTo("date invalide");});
 }
 void assertUnavailable() { assertThatThrownBy(()->client.recompute("sensor-001","2026-11-01",DailySummaryResponse.class)).isInstanceOfSatisfying(AnalyticsHttpException.class,e->{assertThat(e.status()).isEqualTo(503);assertThat(e.body()).isNull();}); }
}
