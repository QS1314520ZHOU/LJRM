package com.smartcare.icustats.util;

import com.smartcare.icustats.dto.BloodSugarTimeRange;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Time range utilities for blood sugar statistics.
 * All calculations use Asia/Shanghai timezone.
 */
public class ShanghaiTimeRangeUtils {

    public static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalTime NURSING_BOUNDARY = LocalTime.of(8, 0);
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private ShanghaiTimeRangeUtils() {}

    /**
     * Calculate current nursing day range for in-ICU patients.
     * If current time >= 08:00: today 08:00 to tomorrow 08:00
     * If current time < 08:00: yesterday 08:00 to today 08:00
     */
    public static BloodSugarTimeRange currentNursingRange(Instant now) {
        ZonedDateTime localNow = now.atZone(SHANGHAI_ZONE);
        LocalDate nursingDate = localNow.toLocalTime().isBefore(NURSING_BOUNDARY)
                ? localNow.toLocalDate().minusDays(1)
                : localNow.toLocalDate();

        Instant start = nursingDate.atTime(NURSING_BOUNDARY).atZone(SHANGHAI_ZONE).toInstant();
        Instant end = nursingDate.plusDays(1).atTime(NURSING_BOUNDARY).atZone(SHANGHAI_ZONE).toInstant();

        return new BloodSugarTimeRange(start, end,
                formatShanghai(start), formatShanghai(end), "CURRENT_NURSING_DAY");
    }

    /**
     * Calculate discharged patient range (admission to discharge).
     */
    public static BloodSugarTimeRange dischargedRange(Instant admissionTime, Instant dischargeTime) {
        return new BloodSugarTimeRange(admissionTime, dischargeTime,
                formatShanghai(admissionTime), formatShanghai(dischargeTime), "DISCHARGED_STAY");
    }

    /**
     * Create range from requested times.
     */
    public static BloodSugarTimeRange requestedRange(Instant startTime, Instant endTime) {
        return new BloodSugarTimeRange(startTime, endTime,
                formatShanghai(startTime), formatShanghai(endTime), "REQUESTED_RANGE");
    }

    /**
     * Format Instant to Shanghai display string.
     */
    public static String formatShanghai(Instant instant) {
        if (instant == null) return "";
        return instant.atZone(SHANGHAI_ZONE).format(DISPLAY_FORMATTER);
    }

    /**
     * Parse Shanghai datetime-local input value to Instant.
     * Input format: "2026-08-15T08:00"
     */
    public static Instant parseInputToInstant(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            LocalDateTime ldt = LocalDateTime.parse(value, INPUT_FORMATTER);
            return ldt.atZone(SHANGHAI_ZONE).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Convert Instant to datetime-local input value.
     */
    public static String instantToInput(Instant instant) {
        if (instant == null) return "";
        return instant.atZone(SHANGHAI_ZONE).format(INPUT_FORMATTER);
    }

    /**
     * Shift range by N Shanghai calendar days.
     */
    public static BloodSugarTimeRange shiftRange(BloodSugarTimeRange range, int days) {
        ZonedDateTime startZdt = range.getStartTime().atZone(SHANGHAI_ZONE);
        ZonedDateTime endZdt = range.getEndTime().atZone(SHANGHAI_ZONE);

        Instant newStart = startZdt.plusDays(days).toInstant();
        Instant newEnd = endZdt.plusDays(days).toInstant();

        return new BloodSugarTimeRange(newStart, newEnd,
                formatShanghai(newStart), formatShanghai(newEnd), range.getDefaultReason());
    }

    /**
     * Check if shifting forward is allowed (endTime must not exceed maxTime).
     */
    public static boolean canShiftForward(BloodSugarTimeRange range, Instant maxTime, int days) {
        if (maxTime == null) return true;
        ZonedDateTime endZdt = range.getEndTime().atZone(SHANGHAI_ZONE);
        Instant newEnd = endZdt.plusDays(days).toInstant();
        return !newEnd.isAfter(maxTime);
    }

    /**
     * Check if shifting backward is allowed (startTime must not be before minTime).
     */
    public static boolean canShiftBackward(BloodSugarTimeRange range, Instant minTime, int days) {
        if (minTime == null) return true;
        ZonedDateTime startZdt = range.getStartTime().atZone(SHANGHAI_ZONE);
        Instant newStart = startZdt.plusDays(days).toInstant();
        return !newStart.isBefore(minTime);
    }

    /**
     * Validate range: start must be before end.
     */
    public static boolean isValidRange(Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null) return false;
        return startTime.isBefore(endTime);
    }
}
