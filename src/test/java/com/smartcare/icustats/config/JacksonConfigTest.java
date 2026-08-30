package com.smartcare.icustats.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcare.icustats.dto.BloodSugarPageData;
import com.smartcare.icustats.dto.BloodSugarTimeRange;
import com.smartcare.icustats.dto.PatientSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class JacksonConfigTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldSerializeInstantAsIso8601() throws Exception {
        BloodSugarTimeRange range = new BloodSugarTimeRange(
                Instant.parse("2026-08-30T00:00:00Z"),
                Instant.parse("2026-08-31T00:00:00Z"),
                "2026-08-30 08:00",
                "2026-08-31 08:00",
                "CURRENT_NURSING_DAY"
        );

        String json = objectMapper.writeValueAsString(range);

        assertThat(json)
                .contains("\"startTime\":\"2026-08-30T00:00:00Z\"")
                .contains("\"endTime\":\"2026-08-31T00:00:00Z\"")
                .doesNotContain("\"startTime\":178");
    }

    @Test
    void shouldNotSerializeInstantAsTimestamp() throws Exception {
        BloodSugarTimeRange range = new BloodSugarTimeRange(
                Instant.parse("2026-08-30T00:00:00Z"),
                Instant.parse("2026-08-31T00:00:00Z"),
                "2026-08-30 08:00",
                "2026-08-31 08:00",
                "CURRENT_NURSING_DAY"
        );

        String json = objectMapper.writeValueAsString(range);

        assertThat(json).doesNotMatch(".*\"startTime\"\\s*:\\s*\\d{10,}.*");
        assertThat(json).doesNotMatch(".*\"endTime\"\\s*:\\s*\\d{10,}.*");
    }

    @Test
    void shouldSerializeBloodSugarTimeRange() throws Exception {
        BloodSugarTimeRange range = new BloodSugarTimeRange(
                Instant.parse("2026-08-30T00:00:00Z"),
                Instant.parse("2026-08-31T00:00:00Z"),
                "2026-08-30 08:00",
                "2026-08-31 08:00",
                "CURRENT_NURSING_DAY"
        );

        String json = objectMapper.writeValueAsString(range);

        assertThat(json).contains("\"startTimeShanghai\":\"2026-08-30 08:00\"");
        assertThat(json).contains("\"timezone\":\"Asia/Shanghai\"");
        assertThat(json).contains("\"defaultReason\":\"CURRENT_NURSING_DAY\"");
    }

    @Test
    void shouldSerializeNestedBloodSugarPageData() throws Exception {
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

        String json = objectMapper.writeValueAsString(pageData);

        assertThat(json).contains("\"patient\":");
        assertThat(json).contains("\"range\":");
        assertThat(json).contains("\"startTime\":\"2026-08-30T00:00:00Z\"");
        assertThat(json).contains("\"endTime\":\"2026-08-31T00:00:00Z\"");
        assertThat(json).contains("\"rows\":[]");
    }
}
