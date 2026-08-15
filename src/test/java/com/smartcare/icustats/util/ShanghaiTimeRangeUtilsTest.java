package com.smartcare.icustats.util;

import com.smartcare.icustats.dto.BloodSugarTimeRange;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

class ShanghaiTimeRangeUtilsTest {

    // ════════════════════════════════════════════════════════════════════
    // currentNursingRange
    // ════════════════════════════════════════════════════════════════════

    @Test
    void currentNursingRange_after8am_returnsToday8amToTomorrow8am() {
        // Shanghai time: 2026-08-15 15:30 → today 08:00 to tomorrow 08:00
        Instant now = LocalDateTime.of(2026, 8, 15, 7, 30).toInstant(ZoneOffset.UTC); // 15:30 Shanghai

        BloodSugarTimeRange range = ShanghaiTimeRangeUtils.currentNursingRange(now);

        Instant expectedStart = LocalDateTime.of(2026, 8, 15, 0, 0).toInstant(ZoneOffset.UTC); // 08:00 Shanghai
        Instant expectedEnd = LocalDateTime.of(2026, 8, 16, 0, 0).toInstant(ZoneOffset.UTC); // 08:00 Shanghai next day

        assertEquals(expectedStart, range.getStartTime());
        assertEquals(expectedEnd, range.getEndTime());
        assertEquals("CURRENT_NURSING_DAY", range.getDefaultReason());
    }

    @Test
    void currentNursingRange_before8am_returnsYesterday8amToToday8am() {
        // Shanghai time: 2026-08-15 06:30 → yesterday 08:00 to today 08:00
        Instant now = LocalDateTime.of(2026, 8, 14, 22, 30).toInstant(ZoneOffset.UTC); // 06:30 Shanghai

        BloodSugarTimeRange range = ShanghaiTimeRangeUtils.currentNursingRange(now);

        Instant expectedStart = LocalDateTime.of(2026, 8, 14, 0, 0).toInstant(ZoneOffset.UTC); // 08:00 Shanghai
        Instant expectedEnd = LocalDateTime.of(2026, 8, 15, 0, 0).toInstant(ZoneOffset.UTC); // 08:00 Shanghai

        assertEquals(expectedStart, range.getStartTime());
        assertEquals(expectedEnd, range.getEndTime());
    }

    @Test
    void currentNursingRange_exactly8am_returnsToday8amToTomorrow8am() {
        // Shanghai time: 2026-08-15 08:00 → today 08:00 to tomorrow 08:00
        Instant now = LocalDateTime.of(2026, 8, 15, 0, 0).toInstant(ZoneOffset.UTC); // 08:00 Shanghai

        BloodSugarTimeRange range = ShanghaiTimeRangeUtils.currentNursingRange(now);

        Instant expectedStart = LocalDateTime.of(2026, 8, 15, 0, 0).toInstant(ZoneOffset.UTC);
        Instant expectedEnd = LocalDateTime.of(2026, 8, 16, 0, 0).toInstant(ZoneOffset.UTC);

        assertEquals(expectedStart, range.getStartTime());
        assertEquals(expectedEnd, range.getEndTime());
    }

    // ════════════════════════════════════════════════════════════════════
    // dischargedRange
    // ════════════════════════════════════════════════════════════════════

    @Test
    void dischargedRange_returnsAdmissionToDischarge() {
        Instant admission = LocalDateTime.of(2026, 8, 10, 6, 25).toInstant(ZoneOffset.UTC); // 14:25 Shanghai
        Instant discharge = LocalDateTime.of(2026, 8, 15, 1, 18).toInstant(ZoneOffset.UTC); // 09:18 Shanghai

        BloodSugarTimeRange range = ShanghaiTimeRangeUtils.dischargedRange(admission, discharge);

        assertEquals(admission, range.getStartTime());
        assertEquals(discharge, range.getEndTime());
        assertEquals("DISCHARGED_STAY", range.getDefaultReason());
    }

    // ════════════════════════════════════════════════════════════════════
    // parseInputToInstant
    // ════════════════════════════════════════════════════════════════════

    @Test
    void parseInputToInstant_validInput_returnsCorrectInstant() {
        Instant result = ShanghaiTimeRangeUtils.parseInputToInstant("2026-08-15T08:00");
        Instant expected = LocalDateTime.of(2026, 8, 15, 0, 0).toInstant(ZoneOffset.UTC); // 08:00 Shanghai = 00:00 UTC
        assertEquals(expected, result);
    }

    @Test
    void parseInputToInstant_invalidInput_returnsNull() {
        assertNull(ShanghaiTimeRangeUtils.parseInputToInstant("invalid"));
        assertNull(ShanghaiTimeRangeUtils.parseInputToInstant(null));
        assertNull(ShanghaiTimeRangeUtils.parseInputToInstant(""));
    }

    // ════════════════════════════════════════════════════════════════════
    // instantToInput
    // ════════════════════════════════════════════════════════════════════

    @Test
    void instantToInput_validInstant_returnsCorrectFormat() {
        Instant instant = LocalDateTime.of(2026, 8, 15, 0, 0).toInstant(ZoneOffset.UTC); // 08:00 Shanghai
        assertEquals("2026-08-15T08:00", ShanghaiTimeRangeUtils.instantToInput(instant));
    }

    @Test
    void instantToInput_null_returnsEmpty() {
        assertEquals("", ShanghaiTimeRangeUtils.instantToInput(null));
    }

    // ════════════════════════════════════════════════════════════════════
    // shiftRange
    // ════════════════════════════════════════════════════════════════════

    @Test
    void shiftRange_forwardOneDay() {
        Instant start = LocalDateTime.of(2026, 8, 15, 0, 0).toInstant(ZoneOffset.UTC);
        Instant end = LocalDateTime.of(2026, 8, 16, 0, 0).toInstant(ZoneOffset.UTC);
        BloodSugarTimeRange range = new BloodSugarTimeRange(start, end, "", "", "CURRENT_NURSING_DAY");

        BloodSugarTimeRange shifted = ShanghaiTimeRangeUtils.shiftRange(range, 1);

        Instant expectedStart = LocalDateTime.of(2026, 8, 16, 0, 0).toInstant(ZoneOffset.UTC);
        Instant expectedEnd = LocalDateTime.of(2026, 8, 17, 0, 0).toInstant(ZoneOffset.UTC);
        assertEquals(expectedStart, shifted.getStartTime());
        assertEquals(expectedEnd, shifted.getEndTime());
    }

    @Test
    void shiftRange_backwardOneDay() {
        Instant start = LocalDateTime.of(2026, 8, 15, 0, 0).toInstant(ZoneOffset.UTC);
        Instant end = LocalDateTime.of(2026, 8, 16, 0, 0).toInstant(ZoneOffset.UTC);
        BloodSugarTimeRange range = new BloodSugarTimeRange(start, end, "", "", "CURRENT_NURSING_DAY");

        BloodSugarTimeRange shifted = ShanghaiTimeRangeUtils.shiftRange(range, -1);

        Instant expectedStart = LocalDateTime.of(2026, 8, 14, 0, 0).toInstant(ZoneOffset.UTC);
        Instant expectedEnd = LocalDateTime.of(2026, 8, 15, 0, 0).toInstant(ZoneOffset.UTC);
        assertEquals(expectedStart, shifted.getStartTime());
        assertEquals(expectedEnd, shifted.getEndTime());
    }

    // ════════════════════════════════════════════════════════════════════
    // isValidRange
    // ════════════════════════════════════════════════════════════════════

    @Test
    void isValidRange_startBeforeEnd_returnsTrue() {
        Instant start = Instant.now();
        Instant end = start.plusSeconds(3600);
        assertTrue(ShanghaiTimeRangeUtils.isValidRange(start, end));
    }

    @Test
    void isValidRange_startEqualsEnd_returnsFalse() {
        Instant start = Instant.now();
        assertFalse(ShanghaiTimeRangeUtils.isValidRange(start, start));
    }

    @Test
    void isValidRange_startAfterEnd_returnsFalse() {
        Instant start = Instant.now();
        Instant end = start.minusSeconds(3600);
        assertFalse(ShanghaiTimeRangeUtils.isValidRange(start, end));
    }

    @Test
    void isValidRange_nullReturns_returnsFalse() {
        assertFalse(ShanghaiTimeRangeUtils.isValidRange(null, Instant.now()));
        assertFalse(ShanghaiTimeRangeUtils.isValidRange(Instant.now(), null));
        assertFalse(ShanghaiTimeRangeUtils.isValidRange(null, null));
    }

    // ════════════════════════════════════════════════════════════════════
    // formatShanghai
    // ════════════════════════════════════════════════════════════════════

    @Test
    void formatShanghai_validInstant_returnsCorrectFormat() {
        Instant instant = LocalDateTime.of(2026, 8, 15, 0, 0).toInstant(ZoneOffset.UTC); // 08:00 Shanghai
        assertEquals("2026-08-15 08:00", ShanghaiTimeRangeUtils.formatShanghai(instant));
    }

    @Test
    void formatShanghai_null_returnsEmpty() {
        assertEquals("", ShanghaiTimeRangeUtils.formatShanghai(null));
    }
}
