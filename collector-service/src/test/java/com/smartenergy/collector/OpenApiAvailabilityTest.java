package com.smartenergy.collector;

import com.smartenergy.collector.controller.MeasurementController;
import com.smartenergy.collector.service.MeasurementPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
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
    @EnableAutoConfiguration
    @Import(MeasurementController.class)
    static class TestApplication {
    }

    @Autowired MockMvc mockMvc;
    @MockBean MeasurementPublisher publisher;

    @Test
    void publishesCollectorContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").isString())
                .andExpect(jsonPath("$.paths['/api/measurements'].post.responses['202']").exists())
                .andExpect(jsonPath("$.paths['/api/measurements'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/measurements'].post.responses['200']").doesNotExist());
    }

    @Test
    void servesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }
}
