package com.smartcare.icustats.service;

import com.smartcare.icustats.config.NutritionQualityProperties;
import com.smartcare.icustats.dto.NutritionQualityCell;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @Test
    void safeRatio_equalValues_returnsOne() {
        NutritionQualityCell cell = NutritionQualityCalculationService.safeRatio(5, 5, true);
        assertEquals(1.0, cell.getValue());
    }

    // ════════════════════════════════════════════════════════════════════
    // toShanghaiMonth / toShanghaiDate
    // ════════════════════════════════════════════════════════════════════

    @Test
    void toShanghaiMonth_utcDate_returnsCorrectMonth() {
        // 2024-01-15T11:00:00Z → Shanghai 2024-01-15 19:00 → 2024-01
        Date utcDate = new Date(1705316400000L);
        assertEquals("2024-01", calcService.toShanghaiMonth(utcDate));
    }

    @Test
    void toShanghaiDate_utcDate_returnsCorrectDate() {
        Date utcDate = new Date(1705316400000L);
        assertEquals("2024-01-15", calcService.toShanghaiDate(utcDate));
    }

    @Test
    void toShanghaiMonth_null_returnsEmpty() {
        assertEquals("", calcService.toShanghaiMonth(null));
    }

    @Test
    void toShanghaiDate_null_returnsEmpty() {
        assertEquals("", calcService.toShanghaiDate(null));
    }

    @Test
    void toShanghaiMonth_nearMidnight_handlesCorrectly() {
        // UTC 2024-01-31T23:30:00Z → Shanghai 2024-02-01 07:30 → 2024-02
        Date utcDate = new Date(1706747400000L);
        assertEquals("2024-02", calcService.toShanghaiMonth(utcDate));
    }

    // ════════════════════════════════════════════════════════════════════
    // selectLatestDailyAssessment
    // ════════════════════════════════════════════════════════════════════

    @Test
    void selectLatestDailyAssessment_emptyList_returnsEmpty() {
        List<Document> result = calcService.selectLatestDailyAssessment(Collections.emptyList());
        assertTrue(result.isEmpty());
    }

    @Test
    void selectLatestDailyAssessment_samePatientSameDay_keepsLatest() {
        // Both on same Shanghai date: 2024-01-15
        Date morning = new Date(1705291200000L); // 2024-01-15T04:00:00Z → 12:00 Shanghai
        Date evening = new Date(1705320000000L); // 2024-01-15T12:00:00Z → 20:00 Shanghai

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
    void selectLatestDailyAssessment_differentPatientsBothKept() {
        Date time = new Date(1705316400000L);
        Document r1 = new Document("pid", "p1").append("startTime", time).append("valid", "valid");
        Document r2 = new Document("pid", "p2").append("startTime", time).append("valid", "valid");

        List<Document> result = calcService.selectLatestDailyAssessment(Arrays.asList(r1, r2));
        assertEquals(2, result.size());
    }

    @Test
    void selectLatestDailyAssessment_invalidRecords_filtered() {
        Document invalid = new Document("pid", "p1")
                .append("startTime", new Date())
                .append("valid", "invalid");

        List<Document> result = calcService.selectLatestDailyAssessment(Collections.singletonList(invalid));
        assertTrue(result.isEmpty());
    }

    // ════════════════════════════════════════════════════════════════════
    // extractUniquePids
    // ════════════════════════════════════════════════════════════════════

    @Test
    void extractUniquePids_multipleRecords_returnsUniquePids() {
        Document r1 = new Document("pid", "p1").append("valid", "valid");
        Document r2 = new Document("pid", "p2").append("valid", "valid");
        Document r3 = new Document("pid", "p1").append("valid", "valid");

        List<String> pids = calcService.extractUniquePids(Arrays.asList(r1, r2, r3));
        assertEquals(2, pids.size());
        assertTrue(pids.contains("p1"));
        assertTrue(pids.contains("p2"));
    }

    @Test
    void extractUniquePids_emptyList_returnsEmpty() {
        assertTrue(calcService.extractUniquePids(Collections.emptyList()).isEmpty());
    }

    // ════════════════════════════════════════════════════════════════════
    // groupByMonth
    // ════════════════════════════════════════════════════════════════════

    @Test
    void groupByMonth_multipleRecords_groupsCorrectly() {
        Date jan = new Date(1705316400000L); // 2024-01
        Date feb = new Date(1708008000000L); // 2024-02

        Document r1 = new Document("pid", "p1").append("startTime", jan).append("valid", "valid");
        Document r2 = new Document("pid", "p2").append("startTime", feb).append("valid", "valid");
        Document r3 = new Document("pid", "p3").append("startTime", jan).append("valid", "valid");

        Map<String, List<Document>> groups = calcService.groupByMonth(Arrays.asList(r1, r2, r3));
        assertEquals(2, groups.size());
        assertTrue(groups.containsKey("2024-01"));
        assertTrue(groups.containsKey("2024-02"));
        assertEquals(2, groups.get("2024-01").size());
        assertEquals(1, groups.get("2024-02").size());
    }

    @Test
    void groupByMonth_emptyList_returnsEmpty() {
        assertTrue(calcService.groupByMonth(Collections.emptyList()).isEmpty());
    }

    // ════════════════════════════════════════════════════════════════════
    // calcInterruptionRate
    // ════════════════════════════════════════════════════════════════════

    @Test
    void calcInterruptionRate_emptyRecords_returnsNoData() {
        NutritionQualityCell cell = calcService.calcInterruptionRate(Collections.emptyList());
        assertEquals("no_data", cell.getDataStatus());
    }

    @Test
    void calcInterruptionRate_withPauseIntervention_calculatesCorrectly() {
        Date time = new Date(1705316400000L);
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
    void calcInterruptionRate_csFieldOnly_recognizesPause() {
        Date time = new Date(1705316400000L);
        Document withPause = new Document("pid", "p1")
                .append("startTime", time)
                .append("valid", "valid")
                .append("cs", "J");

        NutritionQualityCell cell = calcService.calcInterruptionRate(Collections.singletonList(withPause));
        assertEquals(1, cell.getNumerator());
        assertFalse(cell.getCompliant()); // 100% > 10%
    }

    @Test
    void calcInterruptionRate_noInterruption_compliant() {
        Date time = new Date(1705316400000L);
        Document doc = new Document("pid", "p1")
                .append("startTime", time)
                .append("valid", "valid")
                .append("csList", Arrays.asList("H", "I"));

        NutritionQualityCell cell = calcService.calcInterruptionRate(Collections.singletonList(doc));
        assertEquals(0, cell.getNumerator());
        assertEquals(0.0, cell.getValue());
        assertTrue(cell.getCompliant());
    }

    // ════════════════════════════════════════════════════════════════════
    // calcFeedingIntoleranceRate
    // ════════════════════════════════════════════════════════════════════

    @Test
    void calcFeedingIntoleranceRate_emptyRecords_returnsNoData() {
        NutritionQualityCell cell = calcService.calcFeedingIntoleranceRate(Collections.emptyList());
        assertEquals("no_data", cell.getDataStatus());
    }

    @Test
    void calcFeedingIntoleranceRate_highScore_inNumerator() {
        Date time = new Date(1705316400000L);
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
    void calcFeedingIntoleranceRate_jIntervention_inNumerator() {
        Date time = new Date(1705316400000L);
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
    void calcFeedingIntoleranceRate_allTolerant_compliant() {
        Date time = new Date(1705316400000L);
        Document doc = new Document("pid", "p1")
                .append("startTime", time)
                .append("valid", "valid")
                .append("zf", 0);

        NutritionQualityCell cell = calcService.calcFeedingIntoleranceRate(Collections.singletonList(doc));
        assertEquals(0, cell.getNumerator());
        assertTrue(cell.getCompliant());
    }

    // ════════════════════════════════════════════════════════════════════
    // calcPlanCompletionRate
    // ════════════════════════════════════════════════════════════════════

    @Test
    void calcPlanCompletionRate_noFieldMapping_returnsMappingRequired() {
        NutritionQualityCell cell = calcService.calcPlanCompletionRate(Collections.emptyList());
        assertEquals("mapping_required", cell.getDataStatus());
    }

    @Test
    void calcPlanCompletionRate_withMapping_calculatesCorrectly() {
        properties.getFields().put("targetVolume", "ymNum");
        properties.getFields().put("completedVolume", "wcyl");

        Date time = new Date(1705316400000L);
        Document completed = new Document("pid", "p1")
                .append("startTime", time)
                .append("valid", "valid")
                .append("ymNum", 100)
                .append("wcyl", 120);
        Document notCompleted = new Document("pid", "p2")
                .append("startTime", time)
                .append("valid", "valid")
                .append("ymNum", 100)
                .append("wcyl", 50);

        NutritionQualityCell cell = calcService.calcPlanCompletionRate(Arrays.asList(completed, notCompleted));
        assertEquals("ok", cell.getDataStatus());
        assertEquals(1, cell.getNumerator());
        assertEquals(2, cell.getDenominator());
        assertEquals(50.0, cell.getValue());
        assertFalse(cell.getCompliant()); // 50% < 80%
    }

    // ════════════════════════════════════════════════════════════════════
    // calcEnteralParenteralRatio
    // ════════════════════════════════════════════════════════════════════

    @Test
    void calcEnteralParenteralRatio_validValues_returnsCorrectRatio() {
        NutritionQualityCell cell = calcService.calcEnteralParenteralRatio(20, 10);
        assertEquals("ok", cell.getDataStatus());
        assertEquals(2.0, cell.getValue());
        assertTrue(cell.getCompliant()); // 2.0 >= 2.0
    }

    @Test
    void calcEnteralParenteralRatio_zeroParenteral_returnsNoDenominator() {
        NutritionQualityCell cell = calcService.calcEnteralParenteralRatio(20, 0);
        assertEquals("no_denominator", cell.getDataStatus());
    }

    @Test
    void calcEnteralParenteralRatio_bothZero_returnsNoDenominator() {
        NutritionQualityCell cell = calcService.calcEnteralParenteralRatio(0, 0);
        assertEquals("no_denominator", cell.getDataStatus());
    }

    @Test
    void calcEnteralParenteralRatio_belowTarget_notCompliant() {
        NutritionQualityCell cell = calcService.calcEnteralParenteralRatio(5, 10);
        assertEquals(0.5, cell.getValue());
        assertFalse(cell.getCompliant()); // 0.5 < 2.0
    }
}
