package com.smartcare.icustats.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class NumberUtilsTest {

    @Test
    void safeNumberShouldConvertValidNumber() {
        assertEquals(123.0, NumberUtils.safeNumber("123"));
        assertEquals(45.67, NumberUtils.safeNumber(45.67));
        assertEquals(0.0, NumberUtils.safeNumber(null));
        assertEquals(0.0, NumberUtils.safeNumber("abc"));
    }

    @Test
    void normalizeTextShouldTrimAndHandleNull() {
        assertEquals("", NumberUtils.normalizeText(null));
        assertEquals("hello", NumberUtils.normalizeText("  hello  "));
        assertEquals("中文", NumberUtils.normalizeText("中文"));
    }

    @Test
    void calcAgeShouldReturnFormattedAge() {
        // Test with explicit age field
        java.util.Map<String, Object> patient1 = new java.util.HashMap<>();
        patient1.put("age", 65);
        assertEquals("65岁", NumberUtils.calcAge(patient1));

        java.util.Map<String, Object> patient2 = new java.util.HashMap<>();
        patient2.put("age", "72岁");
        assertEquals("72岁", NumberUtils.calcAge(patient2));

        // Test with no age and no birthday
        java.util.Map<String, Object> patient3 = new java.util.HashMap<>();
        assertEquals("", NumberUtils.calcAge(patient3));
    }

    @Test
    void calcIcuDaysShouldReturnCorrectDays() {
        java.util.Map<String, Object> patient = new java.util.HashMap<>();
        // Admission on 2024-01-01, discharge on 2024-01-03 = 3 days
        java.time.ZonedDateTime admission = java.time.ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0,
                DateRangeUtils.SHANGHAI_ZONE);
        java.time.ZonedDateTime discharge = java.time.ZonedDateTime.of(2024, 1, 3, 23, 59, 0, 0,
                DateRangeUtils.SHANGHAI_ZONE);
        patient.put("icuAdmissionTime", java.util.Date.from(admission.toInstant()));
        patient.put("icuDischargeTime", java.util.Date.from(discharge.toInstant()));
        assertEquals("3天", NumberUtils.calcIcuDays(patient));
    }

    @Test
    void trimTrailingZerosShouldWork() {
        assertEquals("1.5", NumberUtils.trimTrailingZeros(new BigDecimal("1.50")));
        assertEquals("1", NumberUtils.trimTrailingZeros(new BigDecimal("1.00")));
        assertEquals("0", NumberUtils.trimTrailingZeros(BigDecimal.ZERO));
    }

    @Test
    void safePercentShouldReturnFormattedPercent() {
        assertEquals("50.00%", NumberUtils.safePercent(1, 2));
        assertEquals("0.00%", NumberUtils.safePercent(0, 0));
        assertEquals("100.00%", NumberUtils.safePercent(5, 5));
    }
}
