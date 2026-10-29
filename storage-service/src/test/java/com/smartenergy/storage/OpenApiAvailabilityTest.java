package com.smartenergy.storage;

import com.smartenergy.storage.cassandra.service.CassandraMeasurementService;
import com.smartenergy.storage.controller.MeasurementHistoryController;
import com.smartenergy.storage.controller.SensorController;
import com.smartenergy.storage.mongo.service.DailySummaryService;
import com.smartenergy.storage.mongo.service.SensorService;
import com.smartenergy.storage.service.LatestMeasurementService;
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
    @Import({SensorController.class, MeasurementHistoryController.class})
    static class TestApplication {
    }

    @Autowired MockMvc mockMvc;
    @MockBean SensorService sensorService;
    @MockBean DailySummaryService dailySummaryService;
    @MockBean CassandraMeasurementService cassandraService;
    @MockBean LatestMeasurementService latestMeasurementService;

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
