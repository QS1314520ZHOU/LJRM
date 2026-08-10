package com.smartcare.icustats.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class StatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void indicatorsEndpointShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/stats/indicators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void yearEndpointMissingYearShouldReturn400() throws Exception {
        mockMvc.perform(get("/api/stats/year"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rangeEndpointMissingParamsShouldReturn400() throws Exception {
        mockMvc.perform(get("/api/stats/range"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detailEndpointMissingParamsShouldReturn400() throws Exception {
        mockMvc.perform(get("/api/stats/detail"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void qualityEndpointShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/stats/quality").param("year", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void nutritionYearEndpointMissingYearShouldReturn400() throws Exception {
        mockMvc.perform(get("/api/stats/nutrition/year"))
                .andExpect(status().isBadRequest());
    }
}
