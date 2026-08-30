package com.smartcare.icustats.service;

import com.smartcare.icustats.config.NutritionQualityProperties;
import com.smartcare.icustats.dto.NutritionQualityCell;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class NutritionQualityCalculationServiceTest {

    private NutritionQualityProperties properties;
    private NutritionQualityRecordAdapter adapter;
    private NutritionQualityCalculationService calcService;

    @BeforeEach
    void setUp() {
        properties = new NutritionQualityProperties();
        adapter = new NutritionQualityRecordAdapter(properties);
        calcService = new NutritionQualityCalculationService(properties, adapter);
    }

    // ════════════════════════════════════════════════════════════════════
    // safeRate
    // ════════════════════════════════════════════════════════════════════

    @Test
    void safeRate_positiveDenominator_returnsCorrectRate() {
        NutritionQualityCell cell = NutritionQualityCalculationService.safeRate(5, 20, true);
        assertEquals(25.0, cell.getValue());
        assertEquals(5, cell.getNumerator());
        assertEquals(20, cell.getDenominator());
        assertTrue(cell.getCompliant());
        assertEquals("ok", cell.getDataStatus());
    }

    @Test
    void safeRate_zeroDenominator_returnsNoDenominator() {
        NutritionQualityCell cell = NutritionQualityCalculationService.safeRate(0, 0, false);
        assertEquals("no_denominator", cell.getDataStatus());
        assertNull(cell.getValue());
    }

    @Test
    void safeRate_zeroNumerator_returnsZeroRate() {
        NutritionQualityCell cell = NutritionQualityCalculationService.safeRate(0, 10, true);
        assertEquals(0.0, cell.getValue());
        assertTrue(cell.getCompliant());
    }

    @Test
    void safeRate_100Percent() {
        NutritionQualityCell cell = NutritionQualityCalculationService.safeRate(10, 10, true);
        assertEquals(100.0, cell.getValue());
    }

    @Test
    void safeRate_roundsTo2Decimals() {
        NutritionQualityCell cell = NutritionQualityCalculationService.safeRate(1, 3, true);
        assertEquals(33.33, cell.getValue(), 0.01);
    }

    // ════════════════════════════════════════════════════════════════════
    // safeRatio
    // ════════════════════════════════════════════════════════════════════

    @Test
    void safeRatio_validValues_returnsCorrectRatio() {
        NutritionQualityCell cell = NutritionQualityCalculationService.safeRatio(10, 5, true);
        assertEquals(2.0, cell.getValue());
        assertTrue(cell.getCompliant());
    }

    @Test
    void safeRatio_zeroDenominator_returnsNoDenominator() {
        NutritionQualityCell cell = NutritionQualityCalculationService.safeRatio(10, 0, false);
        assertEquals("no_denominator", cell.getDataStatus());
    }

    // ════════════════════════════════════════════════════════════════════
    // toShanghaiMonth / toShanghaiDate — UTC→Asia/Shanghai
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class TimezoneTest {
        @Test
        void toShanghaiMonth_utcDate_returnsCorrectMonth() {
            // 2026-08-30T05:37:00Z → Shanghai 2026-08-30 13:37 → 2026-08
            Date utcDate = Date.from(java.time.Instant.parse("2026-08-30T05:37:00Z"));
            assertEquals("2026-08", calcService.toShanghaiMonth(utcDate));
        }

        @Test
        void toShanghaiDate_utcDate_returnsCorrectDate() {
            Date utcDate = Date.from(java.time.Instant.parse("2026-08-30T05:37:00Z"));
            assertEquals("2026-08-30", calcService.toShanghaiDate(utcDate));
        }

        @Test
        void toShanghaiMonth_nearMidnight_handlesCrossDay() {
            // UTC 2026-08-31T23:30:00Z → Shanghai 2026-09-01 07:30 → 2026-09
            Date utcDate = Date.from(java.time.Instant.parse("2026-08-31T23:30:00Z"));
            assertEquals("2026-09", calcService.toShanghaiMonth(utcDate));
        }

        @Test
        void toShanghaiMonth_nearMonthEnd_handlesCrossMonth() {
            // UTC 2026-07-31T16:00:00Z → Shanghai 2026-08-01 00:00 → 2026-08
            Date utcDate = Date.from(java.time.Instant.parse("2026-07-31T16:00:00Z"));
            assertEquals("2026-08", calcService.toShanghaiMonth(utcDate));
        }

        @Test
        void toShanghaiMonth_null_returnsEmpty() {
            assertEquals("", calcService.toShanghaiMonth(null));
        }

        @Test
        void toShanghaiDate_null_returnsEmpty() {
            assertEquals("", calcService.toShanghaiDate(null));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // selectLatestDailyAssessment
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class DailyAssessmentTest {
        @Test
        void emptyList_returnsEmpty() {
            List<Document> result = calcService.selectLatestDailyAssessment(Collections.emptyList());
            assertTrue(result.isEmpty());
        }

        @Test
        void samePatientSameDay_keepsLatest() {
            // Both on same Shanghai date: 2026-08-30
            Date morning = Date.from(java.time.Instant.parse("2026-08-30T03:00:00Z")); // 11:00 Shanghai
            Date evening = Date.from(java.time.Instant.parse("2026-08-30T11:00:00Z")); // 19:00 Shanghai

            Document early = new Document("pid", "p1")
                    .append("startTime", morning)
                    .append("valid", "valid");
            Document late = new Document("pid", "p1")
                    .append("startTime", evening)
                    .append("valid", "valid");

            List<Document> result = calcService.selectLatestDailyAssessment(Arrays.asList(early, late));
            assertEquals(1, result.size());
            assertEquals(evening, result.get(0).get("startTime"));
        }

        @Test
        void differentPatients_bothKept() {
            Date time = Date.from(java.time.Instant.parse("2026-08-30T05:00:00Z"));
            Document r1 = new Document("pid", "p1").append("startTime", time).append("valid", "valid");
            Document r2 = new Document("pid", "p2").append("startTime", time).append("valid", "valid");

            List<Document> result = calcService.selectLatestDailyAssessment(Arrays.asList(r1, r2));
            assertEquals(2, result.size());
        }

        @Test
        void invalidRecords_filtered() {
            Document invalid = new Document("pid", "p1")
                    .append("startTime", new Date())
                    .append("valid", "invalid");

            List<Document> result = calcService.selectLatestDailyAssessment(Collections.singletonList(invalid));
            assertTrue(result.isEmpty());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // calcInterruptionRate
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class InterruptionRateTest {
        @Test
        void emptyRecords_returnsNoData() {
            NutritionQualityCell cell = calcService.calcInterruptionRate(Collections.emptyList());
            assertEquals("no_data", cell.getDataStatus());
        }

        @Test
        void withPauseIntervention_calculatesCorrectly() {
            Date time = Date.from(java.time.Instant.parse("2026-08-30T05:00:00Z"));
            Document withPause = new Document("pid", "p1")
                    .append("startTime", time)
                    .append("valid", "valid")
                    .append("csList", Arrays.asList("J"));
            Document withoutPause = new Document("pid", "p2")
                    .append("startTime", time)
                    .append("valid", "valid")
                    .append("csList", Arrays.asList("H"));

            NutritionQualityCell cell = calcService.calcInterruptionRate(Arrays.asList(withPause, withoutPause));
            assertEquals("ok", cell.getDataStatus());
            assertEquals(1, cell.getNumerator());
            assertEquals(2, cell.getDenominator());
            assertEquals(50.0, cell.getValue());
            assertFalse(cell.getCompliant()); // 50% > 10%
        }

        @Test
        void csFieldOnly_recognizesPause() {
            Date time = Date.from(java.time.Instant.parse("2026-08-30T05:00:00Z"));
            Document withPause = new Document("pid", "p1")
                    .append("startTime", time)
                    .append("valid", "valid")
                    .append("cs", "J");

            NutritionQualityCell cell = calcService.calcInterruptionRate(Collections.singletonList(withPause));
            assertEquals(1, cell.getNumerator());
            assertFalse(cell.getCompliant()); // 100% > 10%
        }

        @Test
        void noInterruption_compliant() {
            Date time = Date.from(java.time.Instant.parse("2026-08-30T05:00:00Z"));
            Document doc = new Document("pid", "p1")
                    .append("startTime", time)
                    .append("valid", "valid")
                    .append("csList", Arrays.asList("H", "I"));

            NutritionQualityCell cell = calcService.calcInterruptionRate(Collections.singletonList(doc));
            assertEquals(0, cell.getNumerator());
            assertEquals(0.0, cell.getValue());
            assertTrue(cell.getCompliant());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // calcFeedingIntoleranceRate — 使用 assessedCount 作为分母
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class FeedingIntoleranceRateTest {
        @Test
        void emptyRecords_returnsNoData() {
            NutritionQualityCell cell = calcService.calcFeedingIntoleranceRate(Collections.emptyList());
            assertEquals("no_data", cell.getDataStatus());
        }

        @Test
        void highScore_inNumerator() {
            Date time = Date.from(java.time.Instant.parse("2026-08-30T05:00:00Z"));
            Document intolerant = new Document("pid", "p1")
                    .append("startTime", time)
                    .append("valid", "valid")
                    .append("zf", 3);
            Document tolerant = new Document("pid", "p2")
                    .append("startTime", time)
                    .append("valid", "valid")
                    .append("zf", 0);

            NutritionQualityCell cell = calcService.calcFeedingIntoleranceRate(Arrays.asList(intolerant, tolerant));
            assertEquals("ok", cell.getDataStatus());
            assertEquals(1, cell.getNumerator());
            assertEquals(2, cell.getDenominator());
            assertEquals(50.0, cell.getValue());
            assertFalse(cell.getCompliant()); // 50% > 20%
        }

        @Test
        void jIntervention_inNumerator() {
            Date time = Date.from(java.time.Instant.parse("2026-08-30T05:00:00Z"));
            Document withJ = new Document("pid", "p1")
                    .append("startTime", time)
                    .append("valid", "valid")
                    .append("zf", 0)
                    .append("csList", Arrays.asList("J"));

            NutritionQualityCell cell = calcService.calcFeedingIntoleranceRate(Collections.singletonList(withJ));
            assertEquals(1, cell.getNumerator());
            assertFalse(cell.getCompliant());
        }

        @Test
        void allTolerant_compliant() {
            Date time = Date.from(java.time.Instant.parse("2026-08-30T05:00:00Z"));
            Document doc = new Document("pid", "p1")
                    .append("startTime", time)
                    .append("valid", "valid")
                    .append("zf", 0);

            NutritionQualityCell cell = calcService.calcFeedingIntoleranceRate(Collections.singletonList(doc));
            assertEquals(0, cell.getNumerator());
            assertTrue(cell.getCompliant());
        }

        @Test
        void unassessed_notCountedInDenominator() {
            Date time = Date.from(java.time.Instant.parse("2026-08-30T05:00:00Z"));
            Document assessed = new Document("pid", "p1")
                    .append("startTime", time)
                    .append("valid", "valid")
                    .append("zf", 0);
            // p2 has no zf → unassessed
            Document unassessed = new Document("pid", "p2")
                    .append("startTime", time)
                    .append("valid", "valid");

            NutritionQualityCell cell = calcService.calcFeedingIntoleranceRate(Arrays.asList(assessed, unassessed));
            assertEquals("ok", cell.getDataStatus());
            // denominator should be 1 (only assessed), not 2
            assertEquals(1, cell.getDenominator());
            assertEquals(0, cell.getNumerator());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // calcPlanCompletionRate — BigDecimal 精确计算
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class PlanCompletionRateTest {
        @Test
        void noFieldMapping_returnsMappingRequired() {
            // Unmap the fields to simulate missing configuration
            properties.getFields().put("targetVolume", "");
            properties.getFields().put("completedVolume", "");
            NutritionQualityCell cell = calcService.calcPlanCompletionRate(Collections.emptyList());
            assertEquals("mapping_required", cell.getDataStatus());
        }

        @Test
        void withMapping_calculatesCorrectly() {
            Date time = Date.from(java.time.Instant.parse("2026-08-30T05:00:00Z"));
            Document completed = new Document("pid", "p1")
                    .append("startTime", time)
                    .append("valid", "valid")
                    .append("mbl", "100")
                    .append("wcl", "120");
            Document notCompleted = new Document("pid", "p2")
                    .append("startTime", time)
                    .append("valid", "valid")
                    .append("mbl", "100")
                    .append("wcl", "50");

            NutritionQualityCell cell = calcService.calcPlanCompletionRate(Arrays.asList(completed, notCompleted));
            assertEquals("ok", cell.getDataStatus());
            assertEquals(1, cell.getNumerator());
            assertEquals(2, cell.getDenominator());
            assertEquals(50.0, cell.getValue());
            assertFalse(cell.getCompliant()); // 50% < 80%
        }

        @Test
        void targetZero_excludedFromDenominator() {
            Date time = Date.from(java.time.Instant.parse("2026-08-30T05:00:00Z"));
            Document zeroTarget = new Document("pid", "p1")
                    .append("startTime", time)
                    .append("valid", "valid")
                    .append("mbl", "0")
                    .append("wcl", "100");

            NutritionQualityCell cell = calcService.calcPlanCompletionRate(Collections.singletonList(zeroTarget));
            assertEquals("no_data", cell.getDataStatus());
        }

        @Test
        void completionOver100_notTruncated() {
            Date time = Date.from(java.time.Instant.parse("2026-08-30T05:00:00Z"));
            Document over = new Document("pid", "p1")
                    .append("startTime", time)
                    .append("valid", "valid")
                    .append("mbl", "100")
                    .append("wcl", "150");

            NutritionQualityCell cell = calcService.calcPlanCompletionRate(Collections.singletonList(over));
            assertEquals("ok", cell.getDataStatus());
            assertEquals(1, cell.getNumerator());
            assertEquals(1, cell.getDenominator());
            assertTrue(cell.getCompliant()); // 100% >= 80%
        }

        @Test
        void stringNumbers_parsedCorrectly() {
            Date time = Date.from(java.time.Instant.parse("2026-08-30T05:00:00Z"));
            // completed (100) >= target (80) → 计入分子
            Document doc = new Document("pid", "p1")
                    .append("startTime", time)
                    .append("valid", "valid")
                    .append("mbl", "80")
                    .append("wcl", "100");

            NutritionQualityCell cell = calcService.calcPlanCompletionRate(Collections.singletonList(doc));
            assertEquals("ok", cell.getDataStatus());
            // 1/1 * 100 = 100.00%
            assertEquals(100.0, cell.getValue(), 0.01);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // calcTubeBlockageRate — 使用 isChecked
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class TubeBlockageRateTest {
        @Test
        void checkmarkComplication_counted() {
            Date time = Date.from(java.time.Instant.parse("2026-08-30T05:00:00Z"));
            Document withBlockage = new Document("pid", "p1")
                    .append("startTime", time)
                    .append("valid", "valid")
                    .append("jxx", "√");
            Document without = new Document("pid", "p2")
                    .append("startTime", time)
                    .append("valid", "valid")
                    .append("jxx", "×");

            NutritionQualityCell cell = calcService.calcTubeBlockageRate(Arrays.asList(withBlockage, without));
            assertEquals("ok", cell.getDataStatus());
            assertEquals(1, cell.getNumerator());
            assertEquals(2, cell.getDenominator());
        }

        @Test
        void noFieldMapping_returnsMappingRequired() {
            properties.getFields().put("mechanicalComplication", "");
            NutritionQualityCell cell = calcService.calcTubeBlockageRate(Collections.emptyList());
            assertEquals("mapping_required", cell.getDataStatus());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // calcUnplannedRemovalRate — 备注含"拔管" + 机械性并发症
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class UnplannedRemovalRateTest {
        @Test
        void emptyRecords_returnsNoData() {
            NutritionQualityCell cell = calcService.calcUnplannedRemovalRate(Collections.emptyList());
            assertEquals("no_data", cell.getDataStatus());
        }

        @Test
        void remarkWith拔管AndComplication_counted() {
            Date time = Date.from(java.time.Instant.parse("2026-08-30T05:00:00Z"));
            Document doc = new Document("pid", "p1")
                    .append("startTime", time)
                    .append("valid", "valid")
                    .append("jxx", "√")
                    .append("bz", "非计划拔管");

            NutritionQualityCell cell = calcService.calcUnplannedRemovalRate(Collections.singletonList(doc));
            assertEquals("ok", cell.getDataStatus());
            assertEquals(1, cell.getNumerator());
            assertEquals(1, cell.getDenominator());
        }

        @Test
        void remarkWith拔管NoComplication_notCounted() {
            Date time = Date.from(java.time.Instant.parse("2026-08-30T05:00:00Z"));
            Document doc = new Document("pid", "p1")
                    .append("startTime", time)
                    .append("valid", "valid")
                    .append("jxx", "×")
                    .append("bz", "非计划拔管");

            NutritionQualityCell cell = calcService.calcUnplannedRemovalRate(Collections.singletonList(doc));
            assertEquals(0, cell.getNumerator());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // calcSkinProblemRate — 备注含"皮肤" + 机械性并发症
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class SkinProblemRateTest {
        @Test
        void emptyRecords_returnsNoData() {
            NutritionQualityCell cell = calcService.calcSkinProblemRate(Collections.emptyList());
            assertEquals("no_data", cell.getDataStatus());
        }

        @Test
        void remarkWith皮肤AndComplication_counted() {
            Date time = Date.from(java.time.Instant.parse("2026-08-30T05:00:00Z"));
            Document doc = new Document("pid", "p1")
                    .append("startTime", time)
                    .append("valid", "valid")
                    .append("jxx", "√")
                    .append("bz", "皮肤发红");

            NutritionQualityCell cell = calcService.calcSkinProblemRate(Collections.singletonList(doc));
            assertEquals("ok", cell.getDataStatus());
            assertEquals(1, cell.getNumerator());
        }

        @Test
        void remarkWith皮肤NoComplication_notCounted() {
            Date time = Date.from(java.time.Instant.parse("2026-08-30T05:00:00Z"));
            Document doc = new Document("pid", "p1")
                    .append("startTime", time)
                    .append("valid", "valid")
                    .append("jxx", "×")
                    .append("bz", "皮肤发红");

            NutritionQualityCell cell = calcService.calcSkinProblemRate(Collections.singletonList(doc));
            assertEquals(0, cell.getNumerator());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // calcAspirationRate — 备注含"误吸" + 机械性并发症
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class AspirationRateTest {
        @Test
        void emptyRecords_returnsNoData() {
            NutritionQualityCell cell = calcService.calcAspirationRate(Collections.emptyList());
            assertEquals("no_data", cell.getDataStatus());
        }

        @Test
        void remarkWith误吸AndComplication_counted() {
            Date time = Date.from(java.time.Instant.parse("2026-08-30T05:00:00Z"));
            Document doc = new Document("pid", "p1")
                    .append("startTime", time)
                    .append("valid", "valid")
                    .append("jxx", "√")
                    .append("bz", "发生误吸");

            NutritionQualityCell cell = calcService.calcAspirationRate(Collections.singletonList(doc));
            assertEquals("ok", cell.getDataStatus());
            assertEquals(1, cell.getNumerator());
        }

        @Test
        void remarkWith误吸NoComplication_notCounted() {
            Date time = Date.from(java.time.Instant.parse("2026-08-30T05:00:00Z"));
            Document doc = new Document("pid", "p1")
                    .append("startTime", time)
                    .append("valid", "valid")
                    .append("jxx", "×")
                    .append("bz", "发生误吸");

            NutritionQualityCell cell = calcService.calcAspirationRate(Collections.singletonList(doc));
            assertEquals(0, cell.getNumerator());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // calcEnteralParenteralRatio
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class EnteralParenteralRatioTest {
        @Test
        void validValues_returnsCorrectRatio() {
            NutritionQualityCell cell = calcService.calcEnteralParenteralRatio(20, 10);
            assertEquals("ok", cell.getDataStatus());
            assertEquals(2.0, cell.getValue());
            assertTrue(cell.getCompliant()); // 2.0 >= 2.0
        }

        @Test
        void zeroParenteral_returnsNoDenominator() {
            NutritionQualityCell cell = calcService.calcEnteralParenteralRatio(20, 0);
            assertEquals("no_denominator", cell.getDataStatus());
        }

        @Test
        void belowTarget_notCompliant() {
            NutritionQualityCell cell = calcService.calcEnteralParenteralRatio(5, 10);
            assertEquals(0.5, cell.getValue());
            assertFalse(cell.getCompliant()); // 0.5 < 2.0
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 患者级去重
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class PatientDedupTest {
        @Test
        void uniquePids_multipleRecords_returnsUniquePids() {
            Document r1 = new Document("pid", "p1").append("valid", "valid");
            Document r2 = new Document("pid", "p2").append("valid", "valid");
            Document r3 = new Document("pid", "p1").append("valid", "valid");

            Set<String> pids = calcService.uniquePids(Arrays.asList(r1, r2, r3));
            assertEquals(2, pids.size());
            assertTrue(pids.contains("p1"));
            assertTrue(pids.contains("p2"));
        }

        @Test
        void samePidMultipleRecords_onlyCountedOnceForComplication() {
            Date time1 = Date.from(java.time.Instant.parse("2026-08-30T05:00:00Z"));
            Date time2 = Date.from(java.time.Instant.parse("2026-08-31T05:00:00Z"));

            // Same patient, two records — both with complication
            Document r1 = new Document("pid", "p1")
                    .append("startTime", time1)
                    .append("valid", "valid")
                    .append("jxx", "√");
            Document r2 = new Document("pid", "p1")
                    .append("startTime", time2)
                    .append("valid", "valid")
                    .append("jxx", "√");

            Map<String, Object> stats = calcService.calcComplicationStats(
                    Arrays.asList(r1, r2), "mechanical");
            // assessedPids should only have 1 unique pid
            assertEquals(1, stats.get("assessedCount"));
            // affectedPids should also only have 1 unique pid
            assertEquals(1, stats.get("affectedCount"));
        }

        @Test
        void samePidOneComplicationOneNot_affectedCountOne() {
            Date time1 = Date.from(java.time.Instant.parse("2026-08-30T05:00:00Z"));
            Date time2 = Date.from(java.time.Instant.parse("2026-08-31T05:00:00Z"));

            Document r1 = new Document("pid", "p1")
                    .append("startTime", time1)
                    .append("valid", "valid")
                    .append("jxx", "√");
            Document r2 = new Document("pid", "p1")
                    .append("startTime", time2)
                    .append("valid", "valid")
                    .append("jxx", "×");

            Map<String, Object> stats = calcService.calcComplicationStats(
                    Arrays.asList(r1, r2), "mechanical");
            assertEquals(1, stats.get("assessedCount"));
            // At least one record has "√", so patient is counted as affected
            assertEquals(1, stats.get("affectedCount"));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // calcToleranceStats
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class ToleranceStatsTest {
        @Test
        void mixedScores_calculatedCorrectly() {
            Date time = Date.from(java.time.Instant.parse("2026-08-30T05:00:00Z"));
            Document tolerant = new Document("pid", "p1")
                    .append("startTime", time)
                    .append("valid", "valid")
                    .append("zf", 0);
            Document intolerant = new Document("pid", "p2")
                    .append("startTime", time)
                    .append("valid", "valid")
                    .append("zf", 3);
            Document unassessed = new Document("pid", "p3")
                    .append("startTime", time)
                    .append("valid", "valid");

            Map<String, Object> stats = calcService.calcToleranceStats(
                    Arrays.asList(tolerant, intolerant, unassessed));
            assertEquals(2, stats.get("assessedCount"));
            assertEquals(1, stats.get("tolerantCount"));
            assertEquals(1, stats.get("intolerantCount"));
            assertEquals(1, stats.get("unassessedCount"));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // extractUniquePids
    // ════════════════════════════════════════════════════════════════════

    @Test
    void extractUniquePids_emptyList_returnsEmpty() {
        assertTrue(calcService.extractUniquePids(Collections.emptyList()).isEmpty());
    }

    // ════════════════════════════════════════════════════════════════════
    // groupByMonth
    // ════════════════════════════════════════════════════════════════════

    @Test
    void groupByMonth_multipleRecords_groupsCorrectly() {
        Date jan = Date.from(java.time.Instant.parse("2026-01-15T05:00:00Z"));
        Date feb = Date.from(java.time.Instant.parse("2026-02-15T05:00:00Z"));

        Document r1 = new Document("pid", "p1").append("startTime", jan).append("valid", "valid");
        Document r2 = new Document("pid", "p2").append("startTime", feb).append("valid", "valid");
        Document r3 = new Document("pid", "p3").append("startTime", jan).append("valid", "valid");

        Map<String, List<Document>> groups = calcService.groupByMonth(Arrays.asList(r1, r2, r3));
        assertEquals(2, groups.size());
        assertTrue(groups.containsKey("2026-01"));
        assertTrue(groups.containsKey("2026-02"));
        assertEquals(2, groups.get("2026-01").size());
        assertEquals(1, groups.get("2026-02").size());
    }

    // ════════════════════════════════════════════════════════════════════
    // buildJudgmentReason
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class JudgmentReasonTest {
        @Test
        void interruptionRate_withPause_reasonContainsJ() {
            Date time = Date.from(java.time.Instant.parse("2026-08-30T05:00:00Z"));
            Document doc = new Document("pid", "p1")
                    .append("startTime", time)
                    .append("valid", "valid")
                    .append("csList", Arrays.asList("J"));

            String reason = calcService.buildJudgmentReason("enteralInterruptionRate", doc, true, true);
            assertTrue(reason.contains("J"));
        }

        @Test
        void feedingIntoleranceRate_unassessed_reasonContainsNull() {
            Date time = Date.from(java.time.Instant.parse("2026-08-30T05:00:00Z"));
            Document doc = new Document("pid", "p1")
                    .append("startTime", time)
                    .append("valid", "valid");
            // No zf field → unassessed

            String reason = calcService.buildJudgmentReason("feedingIntoleranceRate", doc, false, false);
            assertTrue(reason.contains("未评估") || reason.contains("null"));
        }

        @Test
        void planCompletionRate_targetNull_reasonContainsEmpty() {
            Date time = Date.from(java.time.Instant.parse("2026-08-30T05:00:00Z"));
            Document doc = new Document("pid", "p1")
                    .append("startTime", time)
                    .append("valid", "valid");
            // No mbl field → target is null

            String reason = calcService.buildJudgmentReason("enteralPlanCompletionRate", doc, false, false);
            assertTrue(reason.contains("目标量为空") || reason.contains("不计入分母"));
        }
    }
}
