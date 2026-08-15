package com.smartcare.icustats.util;

import com.smartcare.icustats.service.BloodSugarService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class SteroidDoseUtilsTest {

    // ===== convertToMg =====

    @Test
    void convertToMg_mg_returnsSame() {
        BigDecimal result = SteroidDoseUtils.convertToMg(new BigDecimal("40"), "mg");
        assertEquals(0, new BigDecimal("40").compareTo(result));
    }

    @Test
    void convertToMg_g_multipliesBy1000() {
        BigDecimal result = SteroidDoseUtils.convertToMg(new BigDecimal("0.5"), "g");
        assertEquals(0, new BigDecimal("500").compareTo(result));
    }

    @Test
    void convertToMg_ug_dividesBy1000() {
        BigDecimal result = SteroidDoseUtils.convertToMg(new BigDecimal("500"), "μg");
        assertNotNull(result);
        assertEquals(0, new BigDecimal("0.5").compareTo(result.stripTrailingZeros()));
    }

    @Test
    void convertToMg_mcg_dividesBy1000() {
        BigDecimal result = SteroidDoseUtils.convertToMg(new BigDecimal("1000"), "mcg");
        assertEquals(0, new BigDecimal("1").compareTo(result.stripTrailingZeros()));
    }

    @Test
    void convertToMg_nullDose_returnsNull() {
        assertNull(SteroidDoseUtils.convertToMg(null, "mg"));
    }

    @Test
    void convertToMg_nullUnit_returnsNull() {
        assertNull(SteroidDoseUtils.convertToMg(new BigDecimal("10"), null));
    }

    @Test
    void convertToMg_emptyUnit_returnsNull() {
        assertNull(SteroidDoseUtils.convertToMg(new BigDecimal("10"), "  "));
    }

    @Test
    void convertToMg_unknownUnit_returnsNull() {
        assertNull(SteroidDoseUtils.convertToMg(new BigDecimal("10"), "ml"));
    }

    // ===== toHydrocortisoneEquivalent =====

    @Test
    void toHydrocortisoneEquivalent_hydrocortisone_x1() {
        BigDecimal result = SteroidDoseUtils.toHydrocortisoneEquivalent(new BigDecimal("100"), "氢化可的松");
        assertEquals(0, new BigDecimal("100").compareTo(result));
    }

    @Test
    void toHydrocortisoneEquivalent_methylprednisolone_x5() {
        // 4mg methylprednisolone ≈ 20mg hydrocortisone → ×5
        BigDecimal result = SteroidDoseUtils.toHydrocortisoneEquivalent(new BigDecimal("40"), "注射用甲泼尼龙琥珀酸钠");
        assertEquals(0, new BigDecimal("200").compareTo(result));
    }

    @Test
    void toHydrocortisoneEquivalent_dexamethasone_x26_67() {
        // 0.75mg dexamethasone ≈ 20mg hydrocortisone → ×(20/0.75)
        BigDecimal result = SteroidDoseUtils.toHydrocortisoneEquivalent(new BigDecimal("3"), "地塞米松磷酸钠注射液");
        assertNotNull(result);
        // 3 × 26.6667 ≈ 80
        assertTrue(result.compareTo(new BigDecimal("79")) > 0);
        assertTrue(result.compareTo(new BigDecimal("81")) < 0);
    }

    @Test
    void toHydrocortisoneEquivalent_unknownDrug_returnsNull() {
        assertNull(SteroidDoseUtils.toHydrocortisoneEquivalent(new BigDecimal("10"), "头孢曲松"));
    }

    @Test
    void toHydrocortisoneEquivalent_nullDose_returnsNull() {
        assertNull(SteroidDoseUtils.toHydrocortisoneEquivalent(null, "氢化可的松"));
    }

    @Test
    void toHydrocortisoneEquivalent_nullName_returnsNull() {
        assertNull(SteroidDoseUtils.toHydrocortisoneEquivalent(new BigDecimal("10"), null));
    }

    // ===== getCorrectionFactor =====

    @Test
    void getCorrectionFactor_gt200_returns0_6() {
        BigDecimal cf = SteroidDoseUtils.getCorrectionFactor(new BigDecimal("201"));
        assertEquals(0, new BigDecimal("0.6").compareTo(cf));
    }

    @Test
    void getCorrectionFactor_eq200_returns0_8() {
        BigDecimal cf = SteroidDoseUtils.getCorrectionFactor(new BigDecimal("200"));
        assertEquals(0, new BigDecimal("0.8").compareTo(cf));
    }

    @Test
    void getCorrectionFactor_100to200_returns0_8() {
        BigDecimal cf = SteroidDoseUtils.getCorrectionFactor(new BigDecimal("150"));
        assertEquals(0, new BigDecimal("0.8").compareTo(cf));
    }

    @Test
    void getCorrectionFactor_eq100_returns0_8() {
        BigDecimal cf = SteroidDoseUtils.getCorrectionFactor(new BigDecimal("100"));
        assertEquals(0, new BigDecimal("0.8").compareTo(cf));
    }

    @Test
    void getCorrectionFactor_lt100_returns0_85() {
        BigDecimal cf = SteroidDoseUtils.getCorrectionFactor(new BigDecimal("50"));
        assertEquals(0, new BigDecimal("0.85").compareTo(cf));
    }

    @Test
    void getCorrectionFactor_zero_returns0_85() {
        BigDecimal cf = SteroidDoseUtils.getCorrectionFactor(BigDecimal.ZERO);
        assertEquals(0, new BigDecimal("0.85").compareTo(cf));
    }

    @Test
    void getCorrectionFactor_null_returns0_85() {
        BigDecimal cf = SteroidDoseUtils.getCorrectionFactor(null);
        assertEquals(0, new BigDecimal("0.85").compareTo(cf));
    }

    // ===== calculateIri =====

    @Test
    void calculateIri_normalCase() {
        // result=8, insulin=10, correctionFactor=0.8
        // IRI = 8 × (10 + 0.5) × 0.8 = 67.2
        BigDecimal iri = SteroidDoseUtils.calculateIri(
                new BigDecimal("8"), new BigDecimal("10"), new BigDecimal("0.8"));
        assertEquals(0, new BigDecimal("67.20").compareTo(iri));
    }

    @Test
    void calculateIri_noInsulin() {
        // result=7, insulin=null, correctionFactor=0.85
        // IRI = 7 × (0 + 0.5) × 0.85 = 2.975 → 2.98
        BigDecimal iri = SteroidDoseUtils.calculateIri(
                new BigDecimal("7"), null, new BigDecimal("0.85"));
        assertEquals(0, new BigDecimal("2.98").compareTo(iri));
    }

    @Test
    void calculateIri_zeroInsulin() {
        // result=10, insulin=0, correctionFactor=0.6
        // IRI = 10 × (0 + 0.5) × 0.6 = 3
        BigDecimal iri = SteroidDoseUtils.calculateIri(
                new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("0.6"));
        assertEquals(0, new BigDecimal("3.00").compareTo(iri));
    }

    @Test
    void calculateIri_nullCorrectionFactor_usesDefault0_85() {
        // result=6, insulin=5, correctionFactor=null → default 0.85
        // IRI = 6 × (5 + 0.5) × 0.85 = 28.05
        BigDecimal iri = SteroidDoseUtils.calculateIri(
                new BigDecimal("6"), new BigDecimal("5"), null);
        assertEquals(0, new BigDecimal("28.05").compareTo(iri));
    }

    @Test
    void calculateIri_nullResult_returnsNull() {
        assertNull(SteroidDoseUtils.calculateIri(null, new BigDecimal("5"), new BigDecimal("0.8")));
    }

    @Test
    void calculateIri_roundsTo2Decimals() {
        // result=7.3, insulin=8, correctionFactor=0.85
        // IRI = 7.3 × (8 + 0.5) × 0.85 = 7.3 × 8.5 × 0.85 = 52.7425 → 52.74
        BigDecimal iri = SteroidDoseUtils.calculateIri(
                new BigDecimal("7.3"), new BigDecimal("8"), new BigDecimal("0.85"));
        assertEquals(0, new BigDecimal("52.74").compareTo(iri));
    }

    // ===== safeParseBigDecimal =====

    @Test
    void safeParseBigDecimal_number_returnsBigDecimal() {
        assertEquals(0, new BigDecimal("42").compareTo(SteroidDoseUtils.safeParseBigDecimal(42)));
        assertEquals(0, new BigDecimal("3.14").compareTo(SteroidDoseUtils.safeParseBigDecimal(3.14)));
    }

    @Test
    void safeParseBigDecimal_string_returnsBigDecimal() {
        assertEquals(0, new BigDecimal("100").compareTo(SteroidDoseUtils.safeParseBigDecimal("100")));
        assertEquals(0, new BigDecimal("0.5").compareTo(SteroidDoseUtils.safeParseBigDecimal("0.5")));
    }

    @Test
    void safeParseBigDecimal_null_returnsNull() {
        assertNull(SteroidDoseUtils.safeParseBigDecimal(null));
    }

    @Test
    void safeParseBigDecimal_emptyString_returnsNull() {
        assertNull(SteroidDoseUtils.safeParseBigDecimal(""));
        assertNull(SteroidDoseUtils.safeParseBigDecimal("  "));
    }

    @Test
    void safeParseBigDecimal_invalidString_returnsNull() {
        assertNull(SteroidDoseUtils.safeParseBigDecimal("abc"));
    }

    @Test
    void safeParseBigDecimal_bigDecimalInput_returnsSame() {
        BigDecimal input = new BigDecimal("99.9");
        BigDecimal result = SteroidDoseUtils.safeParseBigDecimal(input);
        assertEquals(0, input.compareTo(result));
    }

    // ===== safeParseInsulin =====

    @Test
    void safeParseInsulin_validNumber_returnsValue() {
        assertEquals(0, new BigDecimal("10").compareTo(SteroidDoseUtils.safeParseInsulin("10")));
    }

    @Test
    void safeParseInsulin_null_returnsZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(SteroidDoseUtils.safeParseInsulin(null)));
    }

    @Test
    void safeParseInsulin_invalid_returnsZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(SteroidDoseUtils.safeParseInsulin("abc")));
    }

    // ===== isTargetSteroid =====

    @Test
    void isTargetSteroid_methylprednisolone_returnsTrue() {
        assertTrue(SteroidDoseUtils.isTargetSteroid("注射用甲泼尼龙琥珀酸钠"));
    }

    @Test
    void isTargetSteroid_hydrocortisone_returnsTrue() {
        assertTrue(SteroidDoseUtils.isTargetSteroid("氢化可的松注射液"));
    }

    @Test
    void isTargetSteroid_dexamethasone_returnsTrue() {
        assertTrue(SteroidDoseUtils.isTargetSteroid("地塞米松磷酸钠注射液"));
    }

    @Test
    void isTargetSteroid_nonSteroid_returnsFalse() {
        assertFalse(SteroidDoseUtils.isTargetSteroid("头孢曲松"));
    }

    @Test
    void isTargetSteroid_null_returnsFalse() {
        assertFalse(SteroidDoseUtils.isTargetSteroid(null));
    }

    // ===== get8_8Window =====

    @Test
    void get8_8Window_after8am_sameDayToNextDay() {
        // 2024-01-15 10:00 → window [2024-01-15 08:00, 2024-01-16 08:00)
        java.time.ZonedDateTime time = java.time.ZonedDateTime.of(2024, 1, 15, 10, 0, 0, 0,
                DateRangeUtils.SHANGHAI_ZONE);
        java.util.Date date = java.util.Date.from(time.toInstant());

        java.util.Date[] window = BloodSugarService.get8_8Window(date);
        assertNotNull(window[0]);
        assertNotNull(window[1]);

        java.time.ZonedDateTime expectedStart = java.time.ZonedDateTime.of(2024, 1, 15, 8, 0, 0, 0,
                DateRangeUtils.SHANGHAI_ZONE);
        java.time.ZonedDateTime expectedEnd = java.time.ZonedDateTime.of(2024, 1, 16, 8, 0, 0, 0,
                DateRangeUtils.SHANGHAI_ZONE);

        assertEquals(expectedStart.toInstant(), window[0].toInstant());
        assertEquals(expectedEnd.toInstant(), window[1].toInstant());
    }

    @Test
    void get8_8Window_before8am_prevDayToSameDay() {
        // 2024-01-15 06:00 → window [2024-01-14 08:00, 2024-01-15 08:00)
        java.time.ZonedDateTime time = java.time.ZonedDateTime.of(2024, 1, 15, 6, 0, 0, 0,
                DateRangeUtils.SHANGHAI_ZONE);
        java.util.Date date = java.util.Date.from(time.toInstant());

        java.util.Date[] window = BloodSugarService.get8_8Window(date);
        assertNotNull(window[0]);
        assertNotNull(window[1]);

        java.time.ZonedDateTime expectedStart = java.time.ZonedDateTime.of(2024, 1, 14, 8, 0, 0, 0,
                DateRangeUtils.SHANGHAI_ZONE);
        java.time.ZonedDateTime expectedEnd = java.time.ZonedDateTime.of(2024, 1, 15, 8, 0, 0, 0,
                DateRangeUtils.SHANGHAI_ZONE);

        assertEquals(expectedStart.toInstant(), window[0].toInstant());
        assertEquals(expectedEnd.toInstant(), window[1].toInstant());
    }

    @Test
    void get8_8Window_exactly8am_sameDayToNextDay() {
        // 2024-01-15 08:00:00 → window [2024-01-15 08:00, 2024-01-16 08:00)
        java.time.ZonedDateTime time = java.time.ZonedDateTime.of(2024, 1, 15, 8, 0, 0, 0,
                DateRangeUtils.SHANGHAI_ZONE);
        java.util.Date date = java.util.Date.from(time.toInstant());

        java.util.Date[] window = BloodSugarService.get8_8Window(date);
        assertNotNull(window[0]);
        assertNotNull(window[1]);

        java.time.ZonedDateTime expectedStart = java.time.ZonedDateTime.of(2024, 1, 15, 8, 0, 0, 0,
                DateRangeUtils.SHANGHAI_ZONE);
        java.time.ZonedDateTime expectedEnd = java.time.ZonedDateTime.of(2024, 1, 16, 8, 0, 0, 0,
                DateRangeUtils.SHANGHAI_ZONE);

        assertEquals(expectedStart.toInstant(), window[0].toInstant());
        assertEquals(expectedEnd.toInstant(), window[1].toInstant());
    }

    @Test
    void get8_8Window_nullTime_returnsNullArray() {
        java.util.Date[] window = BloodSugarService.get8_8Window(null);
        assertNull(window[0]);
        assertNull(window[1]);
    }

    @Test
    void get8_8Window_isLeftClosedRightOpen() {
        // Verify window is [start, end) - left closed, right open
        java.time.ZonedDateTime time = java.time.ZonedDateTime.of(2024, 1, 15, 14, 0, 0, 0,
                DateRangeUtils.SHANGHAI_ZONE);
        java.util.Date date = java.util.Date.from(time.toInstant());

        java.util.Date[] window = BloodSugarService.get8_8Window(date);

        // Start should be before time
        assertTrue(window[0].before(date) || window[0].equals(date));
        // End should be after time
        assertTrue(window[1].after(date));
    }
}
