package com.smartcare.icustats.controller;

import com.smartcare.icustats.dto.BloodSugarPageData;
import com.smartcare.icustats.dto.BloodSugarTimeRange;
import com.smartcare.icustats.dto.PatientSummary;
import com.smartcare.icustats.service.BloodSugarService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BloodSugarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BloodSugarService bloodSugarService;

    @Test
    void shouldReturnTimeRangeWithInstant() throws Exception {
        PatientSummary patient = new PatientSummary();
        patient.setId("test123");

        BloodSugarTimeRange range = new BloodSugarTimeRange(
                Instant.parse("2026-08-30T00:00:00Z"),
                Instant.parse("2026-08-31T00:00:00Z"),
                "2026-08-30 08:00",
                "2026-08-31 08:00",
                "CURRENT_NURSING_DAY"
        );

        BloodSugarPageData pageData = new BloodSugarPageData();
        pageData.setPatient(patient);
        pageData.setRange(range);
        pageData.setRows(Collections.emptyList());

        when(bloodSugarService.getPageData(eq("test123"), any(Instant.class), any(Instant.class)))
                .thenReturn(pageData);

        mockMvc.perform(get("/api/blood-sugar/patient/test123")
                        .param("startTime", "2026-08-30T00:00:00.000Z")
                        .param("endTime", "2026-08-31T00:00:00.000Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.range.startTime").value("2026-08-30T00:00:00Z"))
                .andExpect(jsonPath("$.data.range.endTime").value("2026-08-31T00:00:00Z"))
                .andExpect(jsonPath("$.data.patient.id").value("test123"))
                .andExpect(jsonPath("$.data.rows").isArray());
    }

    @Test
    void shouldReturn400WhenOnlyStartTimeProvided() throws Exception {
        mockMvc.perform(get("/api/blood-sugar/patient/test123")
                        .param("startTime", "2026-08-30T00:00:00.000Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void shouldReturn400WhenStartTimeAfterEndTime() throws Exception {
        mockMvc.perform(get("/api/blood-sugar/patient/test123")
                        .param("startTime", "2026-08-31T00:00:00.000Z")
                        .param("endTime", "2026-08-30T00:00:00.000Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void shouldReturn400WhenPidEmpty() throws Exception {
        mockMvc.perform(get("/api/blood-sugar/patient/ ")
                        .param("startTime", "2026-08-30T00:00:00.000Z")
                        .param("endTime", "2026-08-31T00:00:00.000Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void shouldReturnValidJsonWhenServiceThrowsException() throws Exception {
        when(bloodSugarService.getPageData(anyString(), any(Instant.class), any(Instant.class)))
                .thenThrow(new RuntimeException("test error"));

        mockMvc.perform(get("/api/blood-sugar/patient/test123")
                        .param("startTime", "2026-08-30T00:00:00.000Z")
                        .param("endTime", "2026-08-31T00:00:00.000Z"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("服务器内部错误"));
    }
}
