package com.smartcare.icustats.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DateRangeUtilsTest {

    @Test
    void validateYearShouldAcceptValidYear() {
        assertDoesNotThrow(() -> DateRangeUtils.validateYear("2024"));
    }

    @Test
    void validateYearShouldRejectInvalidYear() {
        assertThrows(IllegalArgumentException.class, () -> DateRangeUtils.validateYear("1999"));
        assertThrows(IllegalArgumentException.class, () -> DateRangeUtils.validateYear("abc"));
    }

    @Test
    void validateMonthShouldAcceptValidMonth() {
        assertDoesNotThrow(() -> DateRangeUtils.validateMonth("2024-01", "测试月份"));
    }

    @Test
    void validateMonthShouldRejectInvalidMonth() {
        assertThrows(IllegalArgumentException.class, () -> DateRangeUtils.validateMonth("2024/01", "测试月份"));
        assertThrows(IllegalArgumentException.class, () -> DateRangeUtils.validateMonth("2024-13", "测试月份"));
    }

    @Test
    void buildMonthsShouldReturnCorrectRange() {
        List<String> months = DateRangeUtils.buildMonths("2024-01", "2024-03");
        assertEquals(3, months.size());
        assertEquals("2024-01", months.get(0));
        assertEquals("2024-02", months.get(1));
        assertEquals("2024-03", months.get(2));
    }

    @Test
    void buildMonthsShouldRejectStartAfterEnd() {
        assertThrows(IllegalArgumentException.class, () -> DateRangeUtils.buildMonths("2024-03", "2024-01"));
    }

    @Test
    void buildMonthsShouldRejectRangeOver36() {
        assertThrows(IllegalArgumentException.class, () -> DateRangeUtils.buildMonths("2020-01", "2024-02"));
    }

    @Test
    void getMonthRangeShouldReturnCorrectDates() {
        MonthRange range = DateRangeUtils.getMonthRange("2024-01");
        assertNotNull(range.getStartDate());
        assertNotNull(range.getEndDate());
        // startDate should be 2024-01-01 00:00:00 +08:00
        // endDate should be 2024-01-31 23:59:59.999 +08:00
    }

    @Test
    void getYearMonthsShouldReturn12Months() {
        List<String> months = DateRangeUtils.getYearMonths(2024);
        assertEquals(12, months.size());
        assertEquals("2024-01", months.get(0));
        assertEquals("2024-12", months.get(11));
    }

    @Test
    void buildDateListShouldReturnCorrectDates() {
        List<String> dates = DateRangeUtils.buildDateList("2024-01-01", "2024-01-03");
        assertEquals(3, dates.size());
        assertEquals("2024-01-01", dates.get(0));
        assertEquals("2024-01-03", dates.get(2));
    }

    @Test
    void buildDateListShouldRejectRangeOver366() {
        assertThrows(IllegalArgumentException.class, () -> DateRangeUtils.buildDateList("2024-01-01", "2025-12-31"));
    }

    @Test
    void crossYearMonthRange() {
        List<String> months = DateRangeUtils.buildMonths("2024-11", "2025-02");
        assertEquals(4, months.size());
        assertEquals("2024-11", months.get(0));
        assertEquals("2025-02", months.get(3));
    }
}
