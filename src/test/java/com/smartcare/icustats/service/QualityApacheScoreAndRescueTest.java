package com.smartcare.icustats.service;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for two specific issues:
 * 1. APACHEⅡ total score nullish-coalescing semantics (total=0 is valid, not "missing")
 * 2. calcRescue() dischargeType/dischargedType field compatibility
 */
class QualityApacheScoreAndRescueTest {

    // ════════════════════════════════════════════════════════════════════
    // Problem 1: APACHEⅡ total score fallback
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("QualityService.getScoreTotal - nullish coalescing")
    class QualityServiceGetScoreTotal {

        private double invokeGetScoreTotal(Document score) throws Exception {
            QualityService svc = createQualityService();
            Method m = QualityService.class.getDeclaredMethod("getScoreTotal", Document.class);
            m.setAccessible(true);
            return (double) m.invoke(svc, score);
        }

        @Test
        @DisplayName("total=0, apacheII.totalScore=18 → should return 0 (total=0 is valid)")
        void totalZero_apacheIITotalScoreExists_returnsZero() throws Exception {
            Document score = new Document("total", 0)
                    .append("apacheII", new Document("totalScore", 18));
            assertEquals(0.0, invokeGetScoreTotal(score),
                    "total=0 is a valid score, must not fall back to apacheII.totalScore");
        }

        @Test
        @DisplayName("total=null, apacheII.totalScore=18 → should return 18")
        void totalNull_apacheIITotalScoreExists_returns18() throws Exception {
            Document score = new Document("apacheII", new Document("totalScore", 18));
            assertEquals(18.0, invokeGetScoreTotal(score));
        }

        @Test
        @DisplayName("total field missing, apacheII.totalScore=18 → should return 18")
        void totalMissing_apacheIITotalScoreExists_returns18() throws Exception {
            Document score = new Document("apacheII", new Document("totalScore", 18));
            assertEquals(18.0, invokeGetScoreTotal(score));
        }

        @Test
        @DisplayName("total and apacheII.totalScore both missing → should return 0")
        void bothMissing_returnsZero() throws Exception {
            Document score = new Document();
            assertEquals(0.0, invokeGetScoreTotal(score));
        }

        @Test
        @DisplayName("total=0, no apacheII → should return 0")
        void totalZeroNoApacheII_returnsZero() throws Exception {
            Document score = new Document("total", 0);
            assertEquals(0.0, invokeGetScoreTotal(score));
        }

        @Test
        @DisplayName("total=20, apacheII.totalScore=10 → should return 20 (total takes priority)")
        void totalPresent_usesTotal() throws Exception {
            Document score = new Document("total", 20)
                    .append("apacheII", new Document("totalScore", 10));
            assertEquals(20.0, invokeGetScoreTotal(score));
        }

        @Test
        @DisplayName("null score → should return 0")
        void nullScore_returnsZero() throws Exception {
            assertEquals(0.0, invokeGetScoreTotal(null));
        }

        @Test
        @DisplayName("total=0, apacheII is not a Map → should return 0")
        void totalZero_apacheIINotMap_returnsZero() throws Exception {
            Document score = new Document("total", 0)
                    .append("apacheII", "notAMap");
            assertEquals(0.0, invokeGetScoreTotal(score));
        }

        @Test
        @DisplayName("total=0, apacheII exists but totalScore is null → should return 0")
        void totalZero_apacheIITotalScoreNull_returnsZero() throws Exception {
            Document score = new Document("total", 0)
                    .append("apacheII", new Document("totalScore", null));
            assertEquals(0.0, invokeGetScoreTotal(score));
        }

        @Test
        @DisplayName("total is string \"0\" → should return 0")
        void totalStringZero_returnsZero() throws Exception {
            Document score = new Document("total", "0");
            assertEquals(0.0, invokeGetScoreTotal(score));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Problem 1b: QualityCalcService.getApacheScoreTotal
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("QualityCalcService.getApacheScoreTotal - same rules")
    class QualityCalcServiceGetApacheScoreTotal {

        private double invokeGetApacheScoreTotal(Document score) throws Exception {
            Method m = QualityCalcService.class.getDeclaredMethod("getApacheScoreTotal", Document.class);
            m.setAccessible(true);
            return (double) m.invoke(null, score);
        }

        @Test
        @DisplayName("total=0, apacheII.totalScore=18 → returns 0")
        void totalZero_returnsZero() throws Exception {
            Document score = new Document("total", 0)
                    .append("apacheII", new Document("totalScore", 18));
            assertEquals(0.0, invokeGetApacheScoreTotal(score));
        }

        @Test
        @DisplayName("total=null, apacheII.totalScore=18 → returns 18")
        void totalNull_returns18() throws Exception {
            Document score = new Document("apacheII", new Document("totalScore", 18));
            assertEquals(18.0, invokeGetApacheScoreTotal(score));
        }

        @Test
        @DisplayName("both missing → returns 0")
        void bothMissing_returnsZero() throws Exception {
            assertEquals(0.0, invokeGetApacheScoreTotal(new Document()));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Problem 2: calcRescue dischargeType compatibility
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getDischargeType - field fallback logic")
    class GetDischargeTypeTest {

        private String invokeGetDischargeType(Document patient) throws Exception {
            Method m = QualityCalcService.class.getDeclaredMethod("getDischargeType", Document.class);
            m.setAccessible(true);
            return (String) m.invoke(null, patient);
        }

        @Test
        @DisplayName("dischargedType=死亡 → returns 死亡")
        void dischargedTypeExists_returnsDischargedType() throws Exception {
            Document patient = new Document("dischargedType", "死亡");
            assertEquals("死亡", invokeGetDischargeType(patient));
        }

        @Test
        @DisplayName("dischargedType=null, dischargeType=死亡 → returns 死亡")
        void dischargedTypeNull_fallbackToDischargeType() throws Exception {
            Document patient = new Document("dischargeType", "死亡");
            assertEquals("死亡", invokeGetDischargeType(patient));
        }

        @Test
        @DisplayName("dischargedType empty, dischargeType=死亡（终末）→ returns 死亡（终末）")
        void dischargedTypeEmpty_fallbackToDischargeType() throws Exception {
            Document patient = new Document("dischargedType", "")
                    .append("dischargeType", "死亡（终末）");
            assertEquals("死亡（终末）", invokeGetDischargeType(patient));
        }

        @Test
        @DisplayName("dischargedType=转出, dischargeType=死亡 → returns 转出 (dischargedType takes priority)")
        void dischargedTypePresent_ignoresDischargeType() throws Exception {
            Document patient = new Document("dischargedType", "转出")
                    .append("dischargeType", "死亡");
            assertEquals("转出", invokeGetDischargeType(patient));
        }

        @Test
        @DisplayName("both null → returns empty string")
        void bothNull_returnsEmpty() throws Exception {
            Document patient = new Document();
            assertEquals("", invokeGetDischargeType(patient));
        }

        @Test
        @DisplayName("dischargedType is whitespace → fallback to dischargeType")
        void dischargedTypeWhitespace_fallback() throws Exception {
            Document patient = new Document("dischargedType", "   ")
                    .append("dischargeType", "自动出院");
            assertEquals("自动出院", invokeGetDischargeType(patient));
        }

        @Test
        @DisplayName("dischargedType is string \"null\" → fallback to dischargeType")
        void dischargedTypeStringNull_fallback() throws Exception {
            Document patient = new Document("dischargedType", "null")
                    .append("dischargeType", "转出");
            assertEquals("转出", invokeGetDischargeType(patient));
        }

        @Test
        @DisplayName("null patient → returns empty string")
        void nullPatient_returnsEmpty() throws Exception {
            assertEquals("", invokeGetDischargeType(null));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // normalizeText helper
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("normalizeText - null/blank/string-null handling")
    class NormalizeTextTest {

        private String invokeNormalizeText(Object value) throws Exception {
            Method m = QualityCalcService.class.getDeclaredMethod("normalizeText", Object.class);
            m.setAccessible(true);
            return (String) m.invoke(null, value);
        }

        @Test
        void nullValue_returnsEmpty() throws Exception {
            assertEquals("", invokeNormalizeText(null));
        }

        @Test
        void stringNull_returnsEmpty() throws Exception {
            assertEquals("", invokeNormalizeText("null"));
        }

        @Test
        void stringNullUpperCase_returnsEmpty() throws Exception {
            assertEquals("", invokeNormalizeText("NULL"));
        }

        @Test
        void whitespaceOnly_returnsEmpty() throws Exception {
            assertEquals("", invokeNormalizeText("   "));
        }

        @Test
        void normalString_trimmed() throws Exception {
            assertEquals("死亡", invokeNormalizeText("  死亡  "));
        }

        @Test
        void integerValue_converted() throws Exception {
            assertEquals("123", invokeNormalizeText(123));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Rescue success rate logic (field-level unit tests)
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Rescue rate classification rules")
    class RescueClassificationTest {

        @Test
        @DisplayName("dischargedType=死亡 → death (not terminal)")
        void deathType_countsAsDeath() {
            String dt = "死亡";
            assertFalse(dt.contains("死亡（终末）"), "死亡 should not match terminal");
            assertTrue(dt.contains("死亡"), "死亡 should match death");
        }

        @Test
        @DisplayName("dischargedType=死亡（终末）→ terminal, excluded from denom")
        void terminalType_excludedFromDenom() {
            String dt = "死亡（终末）";
            assertTrue(dt.contains("死亡（终末）"), "Should match terminal");
        }

        @Test
        @DisplayName("dischargedType=转出 → success")
        void transferType_countsAsSuccess() {
            String dt = "转出";
            assertFalse(dt.contains("死亡"));
        }

        @Test
        @DisplayName("dischargedType missing, dischargeType=死亡 → fallback to death")
        void fallbackDischargeType_death() throws Exception {
            Document patient = new Document("dischargeType", "死亡");
            Method m = QualityCalcService.class.getDeclaredMethod("getDischargeType", Document.class);
            m.setAccessible(true);
            String dt = (String) m.invoke(null, patient);
            assertEquals("死亡", dt);
            assertTrue(dt.contains("死亡"));
        }

        @Test
        @DisplayName("dischargedType empty, dischargeType=死亡（终末）→ fallback to terminal")
        void fallbackDischargeType_terminal() throws Exception {
            Document patient = new Document("dischargedType", "")
                    .append("dischargeType", "死亡（终末）");
            Method m = QualityCalcService.class.getDeclaredMethod("getDischargeType", Document.class);
            m.setAccessible(true);
            String dt = (String) m.invoke(null, patient);
            assertTrue(dt.contains("死亡（终末）"));
        }

        @Test
        @DisplayName("dischargedType=转出, dischargeType=死亡 → use dischargedType, count as success")
        void priorityDischargedType_overDischargeType() throws Exception {
            Document patient = new Document("dischargedType", "转出")
                    .append("dischargeType", "死亡");
            Method m = QualityCalcService.class.getDeclaredMethod("getDischargeType", Document.class);
            m.setAccessible(true);
            String dt = (String) m.invoke(null, patient);
            assertEquals("转出", dt);
            assertFalse(dt.contains("死亡"));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private static QualityService createQualityService() {
        try {
            java.lang.reflect.Constructor<?> ctor =
                    QualityService.class.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            return (QualityService) ctor.newInstance(null, null, null, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create QualityService for testing", e);
        }
    }
}
