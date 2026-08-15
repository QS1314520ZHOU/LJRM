package com.smartcare.icustats.service;

import com.smartcare.icustats.config.NutritionQualityProperties;
import com.smartcare.icustats.dto.NutritionQualityCell;
import com.smartcare.icustats.dto.NutritionQualityIndicator;
import com.smartcare.icustats.config.NutritionQualityIndicatorConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NutritionQualityServiceTest {

    // ════════════════════════════════════════════════════════════════════
    // NutritionQualityProperties
    // ════════════════════════════════════════════════════════════════════

    @Test
    void properties_defaultValues_correct() {
        NutritionQualityProperties properties = new NutritionQualityProperties();
        assertTrue(properties.isEnabled());
        assertEquals("Asia/Shanghai", properties.getTimezone());
        assertEquals("valid", properties.getValidValue());
        assertFalse(properties.isAutoDiscoverCollection());
        assertNotNull(properties.getFields());
        assertEquals("pid", properties.getFields().get("pid"));
        assertEquals("startTime", properties.getFields().get("recordTime"));
        assertEquals("tj", properties.getFields().get("route"));
        assertEquals("cs", properties.getFields().get("intervention"));
        assertEquals("csList", properties.getFields().get("interventionList"));
    }

    @Test
    void properties_settersWork() {
        NutritionQualityProperties properties = new NutritionQualityProperties();
        properties.setEnabled(false);
        assertFalse(properties.isEnabled());

        properties.setCollection("TestCollection");
        assertEquals("TestCollection", properties.getCollection());

        properties.setAutoDiscoverCollection(true);
        assertTrue(properties.isAutoDiscoverCollection());
    }

    // ════════════════════════════════════════════════════════════════════
    // NutritionQualityIndicatorConfig
    // ════════════════════════════════════════════════════════════════════

    @Test
    void indicatorConfig_hasEightIndicators() {
        assertEquals(8, NutritionQualityIndicatorConfig.INDICATORS.size());
    }

    @Test
    void indicatorConfig_keys_unique() {
        Set<String> uniqueKeys = new HashSet<>();
        for (var indicator : NutritionQualityIndicatorConfig.INDICATORS) {
            String key = (String) indicator.get("key");
            assertTrue(uniqueKeys.add(key), "Duplicate indicator key: " + key);
        }
        assertEquals(8, uniqueKeys.size());
    }

    @Test
    void indicatorConfig_allHaveRequiredFields() {
        String[] requiredKeys = {"id", "key", "name", "unit", "formula", "target", "comparison"};
        for (var indicator : NutritionQualityIndicatorConfig.INDICATORS) {
            for (String rk : requiredKeys) {
                assertTrue(indicator.containsKey(rk),
                        "Indicator missing '" + rk + "': " + indicator.get("key"));
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // NutritionQualityCell factories
    // ════════════════════════════════════════════════════════════════════

    @Test
    void cellOk_setsCorrectValues() {
        NutritionQualityCell cell = NutritionQualityCell.ok(5, 20, 25.0, true);
        assertEquals(5, cell.getNumerator());
        assertEquals(20, cell.getDenominator());
        assertEquals(25.0, cell.getValue());
        assertTrue(cell.getCompliant());
        assertEquals("ok", cell.getDataStatus());
        assertNull(cell.getMessage());
    }

    @Test
    void cellNoData_returnsCorrectStatus() {
        NutritionQualityCell cell = NutritionQualityCell.noData();
        assertEquals("no_data", cell.getDataStatus());
        assertNull(cell.getValue());
        assertNull(cell.getNumerator());
        assertNull(cell.getDenominator());
    }

    @Test
    void cellNoDenominator_returnsCorrectStatus() {
        NutritionQualityCell cell = NutritionQualityCell.noDenominator();
        assertEquals("no_denominator", cell.getDataStatus());
        assertNull(cell.getValue());
    }

    @Test
    void cellMappingRequired_returnsCorrectStatus() {
        NutritionQualityCell cell = NutritionQualityCell.mappingRequired("targetVolume");
        assertEquals("mapping_required", cell.getDataStatus());
        assertTrue(cell.getMessage().contains("targetVolume"));
    }

    @Test
    void cellCollectionNotConfigured_returnsCorrectStatus() {
        NutritionQualityCell cell = NutritionQualityCell.collectionNotConfigured();
        assertEquals("collection_not_configured", cell.getDataStatus());
    }

    @Test
    void cellCollectionNotFound_returnsCorrectStatus() {
        NutritionQualityCell cell = NutritionQualityCell.collectionNotFound();
        assertEquals("collection_not_found", cell.getDataStatus());
    }

    @Test
    void cellQueryError_returnsCorrectStatus() {
        NutritionQualityCell cell = NutritionQualityCell.queryError("test error");
        assertEquals("query_error", cell.getDataStatus());
        assertTrue(cell.getMessage().contains("test error"));
    }

    @Test
    void cellDisabled_returnsCorrectStatus() {
        NutritionQualityCell cell = NutritionQualityCell.disabled();
        assertEquals("disabled", cell.getDataStatus());
    }

    // ════════════════════════════════════════════════════════════════════
    // NutritionQualityIndicator
    // ════════════════════════════════════════════════════════════════════

    @Test
    void indicator_settersAndGetters_work() {
        NutritionQualityIndicator indicator = new NutritionQualityIndicator();
        indicator.setId(1);
        indicator.setKey("enteralInterruptionRate");
        indicator.setName("肠内营养中断率");
        indicator.setUnit("%");
        indicator.setFormula("...");
        indicator.setNumeratorDefinition("...");
        indicator.setDenominatorDefinition("...");
        indicator.setTarget("≤10");
        indicator.setComparison("less_equal");
        indicator.setAggregationUnit("patient_day");
        indicator.setTotal(NutritionQualityCell.noData());
        indicator.setMonthly(new HashMap<>());

        assertEquals(1, indicator.getId());
        assertEquals("enteralInterruptionRate", indicator.getKey());
        assertEquals("肠内营养中断率", indicator.getName());
        assertEquals("%", indicator.getUnit());
        assertEquals("no_data", indicator.getTotal().getDataStatus());
    }

    @Test
    void indicator_mappingRequired_staticFactory() {
        NutritionQualityIndicator indicator = NutritionQualityIndicator.mappingRequired(
                1, "testKey", "测试指标", "targetVolume");

        assertEquals(1, indicator.getId());
        assertEquals("testKey", indicator.getKey());
        assertEquals("测试指标", indicator.getName());
        assertEquals("mapping_required", indicator.getTotal().getDataStatus());
    }

    // ════════════════════════════════════════════════════════════════════
    // NutritionQualityCalculationService - edge cases
    // ════════════════════════════════════════════════════════════════════

    @Test
    void safeRate_roundsTo2Decimals() {
        NutritionQualityCell cell = NutritionQualityCalculationService.safeRate(1, 3, true);
        assertEquals(33.33, cell.getValue(), 0.01);
    }

    @Test
    void safeRatio_roundsTo2Decimals() {
        NutritionQualityCell cell = NutritionQualityCalculationService.safeRatio(10, 3, true);
        assertEquals(3.33, cell.getValue(), 0.01);
    }
}
