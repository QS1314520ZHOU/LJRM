package com.smartcare.icustats.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Steroid dose conversion and IRI calculation utilities.
 *
 * Steroid factor = hydrocortisone equivalent dose (mg) within an 8-8 window.
 * 8-8 window: [D 08:00:00 Shanghai, D+1 08:00:00 Shanghai) for natural day D.
 *
 * Conversion formulas:
 *   Hydrocortisone:  equivalent = doseMg × 1
 *   Methylprednisolone: equivalent = doseMg × 5   (4mg ≈ 20mg hydrocortisone)
 *   Dexamethasone:   equivalent = doseMg × 26.6666666667  (0.75mg ≈ 20mg hydrocortisone)
 *
 * IRI = bloodSugar.result × (insulin + 0.5) × correctionFactor
 *   correctionFactor:
 *     steroidFactor > 200  → 0.6
 *     steroidFactor >= 100 → 0.8
 *     steroidFactor < 100  → 0.85
 */
public class SteroidDoseUtils {

    /** Scale for internal BigDecimal calculations */
    private static final int CALC_SCALE = 10;

    /** Final output scale for IRI and equivalents */
    private static final int OUTPUT_SCALE = 2;

    /** Methylprednisolone multiplier: 4mg ≈ 20mg hydrocortisone → ×5 */
    public static final BigDecimal METHYLPREDNISOLONE_FACTOR = new BigDecimal("5");

    /** Dexamethasone multiplier: 0.75mg ≈ 20mg hydrocortisone → ×(20/0.75) */
    public static final BigDecimal DEXAMETHASONE_FACTOR = new BigDecimal("20").divide(
            new BigDecimal("0.75"), CALC_SCALE, RoundingMode.HALF_UP);

    /** Hydrocortisone multiplier: ×1 */
    public static final BigDecimal HYDROCORTISONE_FACTOR = BigDecimal.ONE;

    private SteroidDoseUtils() {}

    /**
     * Convert dose to mg based on unit string.
     * Supported: mg, g, μg, ug, mcg
     *
     * @return dose in mg, or null if unit is unrecognized
     */
    public static BigDecimal convertToMg(BigDecimal dose, String unit) {
        if (dose == null) return null;
        if (unit == null || unit.trim().isEmpty()) return null;

        String normalizedUnit = unit.trim().toLowerCase();
        switch (normalizedUnit) {
            case "mg":
                return dose;
            case "g":
                return dose.multiply(new BigDecimal("1000"));
            case "μg":
            case "ug":
            case "mcg":
                return dose.divide(new BigDecimal("1000"), CALC_SCALE, RoundingMode.HALF_UP);
            default:
                return null; // Unrecognized unit
        }
    }

    /**
     * Calculate hydrocortisone equivalent from dose in mg and drug type.
     *
     * @param doseMg   dose in milligrams
     * @param drugName drug name (contains-based matching)
     * @return hydrocortisone equivalent in mg, or null if drug not recognized
     */
    public static BigDecimal toHydrocortisoneEquivalent(BigDecimal doseMg, String drugName) {
        if (doseMg == null || drugName == null) return null;

        String name = drugName.trim();
        if (name.contains("甲泼尼龙")) {
            return doseMg.multiply(METHYLPREDNISOLONE_FACTOR);
        } else if (name.contains("氢化可的松")) {
            return doseMg.multiply(HYDROCORTISONE_FACTOR);
        } else if (name.contains("地塞米松")) {
            return doseMg.multiply(DEXAMETHASONE_FACTOR);
        }
        return null; // Not a target steroid
    }

    /**
     * Determine correction factor based on steroid factor.
     *
     * @param steroidFactor total hydrocortisone equivalent for the 8-8 window
     * @return correction factor
     */
    public static BigDecimal getCorrectionFactor(BigDecimal steroidFactor) {
        if (steroidFactor == null) return new BigDecimal("0.85");
        if (steroidFactor.compareTo(new BigDecimal("200")) > 0) {
            return new BigDecimal("0.6");
        }
        if (steroidFactor.compareTo(new BigDecimal("100")) >= 0) {
            return new BigDecimal("0.8");
        }
        return new BigDecimal("0.85");
    }

    /**
     * Calculate IRI = result × (insulin + 0.5) × correctionFactor
     *
     * @param result          blood glucose result (mmol/L)
     * @param insulin         insulin dose (U)
     * @param correctionFactor correction factor from steroid level
     * @return IRI value rounded to 2 decimal places, or null if result is null
     */
    public static BigDecimal calculateIri(BigDecimal result, BigDecimal insulin, BigDecimal correctionFactor) {
        if (result == null) return null;

        BigDecimal safeInsulin = insulin != null ? insulin : BigDecimal.ZERO;
        BigDecimal safeCorrection = correctionFactor != null ? correctionFactor : new BigDecimal("0.85");

        // IRI = result × (insulin + 0.5) × correctionFactor
        BigDecimal iri = result
                .multiply(safeInsulin.add(new BigDecimal("0.5")))
                .multiply(safeCorrection);

        return iri.setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Safe parse of a numeric value from MongoDB.
     * Handles Number, String, null, empty, NaN, Infinity.
     *
     * @return parsed BigDecimal, or null if not a valid finite number
     */
    public static BigDecimal safeParseBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal) {
            BigDecimal bd = (BigDecimal) value;
            return bd.stripTrailingZeros();
        }
        if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) return null;
            return BigDecimal.valueOf(d).stripTrailingZeros();
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) return null;
        try {
            BigDecimal bd = new BigDecimal(s);
            // Check for NaN/Infinity in string form
            return bd.stripTrailingZeros();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Safe parse that returns 0 for invalid values (for insulin).
     */
    public static BigDecimal safeParseInsulin(Object value) {
        BigDecimal result = safeParseBigDecimal(value);
        return result != null ? result : BigDecimal.ZERO;
    }

    /**
     * Check if a drug name is a target steroid.
     */
    public static boolean isTargetSteroid(String drugName) {
        if (drugName == null) return false;
        return drugName.contains("甲泼尼龙")
                || drugName.contains("氢化可的松")
                || drugName.contains("地塞米松");
    }
}
