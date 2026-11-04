package com.smartenergy.storage;

import com.smartenergy.storage.cassandra.service.CassandraMeasurementService;
import com.smartenergy.storage.controller.MeasurementHistoryController;
import com.smartenergy.storage.controller.SensorController;
import com.smartenergy.storage.mongo.service.DailySummaryService;
import com.smartenergy.storage.mongo.service.SensorService;
import com.smartenergy.storage.service.LatestMeasurementService;
import com.smartenergy.storage.config.OpenApiExamples;
import com.smartenergy.storage.controller.AnalyticsController;
import com.smartenergy.storage.controller.DailyReportController;
import com.smartenergy.storage.service.AnalyticsService;
import com.smartenergy.storage.mongo.service.DailyReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration;
import org.springframework.boot.autoconfigure.data.cassandra.CassandraDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.cassandra.CassandraRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = OpenApiAvailabilityTest.TestApplication.class)
@AutoConfigureMockMvc
class OpenApiAvailabilityTest {

    @Configuration
    @EnableAutoConfiguration(exclude = {
            CassandraAutoConfiguration.class, CassandraDataAutoConfiguration.class,
            CassandraRepositoriesAutoConfiguration.class, MongoAutoConfiguration.class,
            MongoDataAutoConfiguration.class, MongoRepositoriesAutoConfiguration.class,
            RedisAutoConfiguration.class
    })
    @Import({SensorController.class, MeasurementHistoryController.class, AnalyticsController.class,
            DailyReportController.class, OpenApiExamples.class})
    static class TestApplication {
    }

    @Autowired MockMvc mockMvc;
    @MockBean SensorService sensorService;
    @MockBean DailySummaryService dailySummaryService;
    @MockBean CassandraMeasurementService cassandraService;
    @MockBean LatestMeasurementService latestMeasurementService;
    @MockBean AnalyticsService analyticsService;
    @MockBean DailyReportService dailyReportService;

    @Test
    void documentsConcreteRequestsResultsAndErrors() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/sensors'].post.requestBody.content['application/json'].examples.cours.value.maxPowerKw").value(5))
                .andExpect(jsonPath("$.paths['/api/sensors'].post.responses['201'].content['*/*'].examples.cours.value.sensorId").value("sensor-demo"))
                .andExpect(jsonPath("$.paths['/api/analytics/{sensorId}/summary'].get.responses['200'].content['*/*'].examples.cours.value.energyKwh").value(0.166667))
                .andExpect(jsonPath("$.paths['/api/analytics/{sensorId}/anomalies'].get.responses['200'].content['*/*'].examples.cours.value[0].reasons[0]").value("POWER_ABOVE_MAX"))
                .andExpect(jsonPath("$.paths['/api/sensors/{id}/daily-report'].get.responses['200'].content['*/*'].examples.cours.value.status").value("ALERT"))
                .andExpect(jsonPath("$.paths['/api/sensors'].post.responses['409'].content['application/json'].examples.cours.value.status").value(409));
    }

    @Test
    void publishesStorageContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").isString())
                .andExpect(jsonPath("$.paths['/api/sensors'].get").exists())
                .andExpect(jsonPath("$.paths['/api/sensors'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/sensors'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/measurements/{sensorId}/history'].get.responses['200'].content['*/*'].schema.type").value("array"))
                .andExpect(jsonPath("$.paths['/api/measurements/{sensorId}/latest'].get.responses['200'].content['*/*'].schema['$ref']").value("#/components/schemas/MeasurementEntity"));
    }

    @Test
    void servesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }
}
