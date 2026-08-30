package com.smartcare.icustats.service;

import com.smartcare.icustats.config.NutritionQualityProperties;
import com.smartcare.icustats.dto.NutritionQualityCell;
import com.smartcare.icustats.dto.NutritionQualityIndicator;
import com.smartcare.icustats.config.NutritionQualityIndicatorConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NutritionQualityServiceTest {

    // ════════════════════════════════════════════════════════════════════
    // NutritionQualityProperties
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class PropertiesTest {
        @Test
        void defaultValues_correct() {
            NutritionQualityProperties properties = new NutritionQualityProperties();
            assertTrue(properties.isEnabled());
            assertEquals("Asia/Shanghai", properties.getTimezone());
            assertEquals("valid", properties.getValidValue());
            assertEquals("√", properties.getCheckedValue());
            assertFalse(properties.isAutoDiscoverCollection());
            assertNotNull(properties.getFields());
            assertEquals("pid", properties.getFields().get("pid"));
            assertEquals("startTime", properties.getFields().get("recordTime"));
            assertEquals("tj", properties.getFields().get("route"));
            assertEquals("cs", properties.getFields().get("intervention"));
            assertEquals("csList", properties.getFields().get("interventionList"));
        }

        @Test
        void defaultFieldMappings_matchRealData() {
            NutritionQualityProperties properties = new NutritionQualityProperties();
            // 并发症字段
            assertEquals("jxx", properties.getFields().get("mechanicalComplication"));
            assertEquals("wcd", properties.getFields().get("gastrointestinalComplication"));
            assertEquals("dxx", properties.getFields().get("metabolicComplication"));
            assertEquals("grx", properties.getFields().get("infectionComplication"));
            assertEquals("zhz", properties.getFields().get("refeedingSyndrome"));
            // 目标量/完成量
            assertEquals("mbl", properties.getFields().get("targetVolume"));
            assertEquals("wcl", properties.getFields().get("completedVolume"));
            // 暂停原因
            assertEquals("ztyy", properties.getFields().get("pauseReason"));
            assertEquals("ztyyList", properties.getFields().get("pauseReasonList"));
            // 备注
            assertEquals("bz", properties.getFields().get("remark"));
        }

        @Test
        void checkedValue_defaultIsCheckmark() {
            NutritionQualityProperties properties = new NutritionQualityProperties();
            assertEquals("√", properties.getCheckedValue());
        }

        @Test
        void routeValues_defaultIsEmpty() {
            NutritionQualityProperties properties = new NutritionQualityProperties();
            assertNotNull(properties.getRouteValues());
            assertTrue(properties.getRouteValues().isEmpty());
        }

        @Test
        void settersWork() {
            NutritionQualityProperties properties = new NutritionQualityProperties();
            properties.setEnabled(false);
            assertFalse(properties.isEnabled());

            properties.setCollection("TestCollection");
            assertEquals("TestCollection", properties.getCollection());

            properties.setAutoDiscoverCollection(true);
            assertTrue(properties.isAutoDiscoverCollection());

            properties.setCheckedValue("是");
            assertEquals("是", properties.getCheckedValue());
        }

        @Test
        void routeValues_setAndGet() {
            NutritionQualityProperties properties = new NutritionQualityProperties();
            properties.getRouteValues().put("enteral", "A");
            properties.getRouteValues().put("parenteral", "B");
            assertEquals("A", properties.getRouteValues().get("enteral"));
            assertEquals("B", properties.getRouteValues().get("parenteral"));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // NutritionQualityIndicatorConfig
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class IndicatorConfigTest {
        @Test
        void hasEightIndicators() {
            assertEquals(8, NutritionQualityIndicatorConfig.INDICATORS.size());
        }

        @Test
        void keys_unique() {
            Set<String> uniqueKeys = new HashSet<>();
            for (var indicator : NutritionQualityIndicatorConfig.INDICATORS) {
                String key = (String) indicator.get("key");
                assertTrue(uniqueKeys.add(key), "Duplicate indicator key: " + key);
            }
            assertEquals(8, uniqueKeys.size());
        }

        @Test
        void allHaveRequiredFields() {
            String[] requiredKeys = {"id", "key", "name", "unit", "formula", "target", "comparison"};
            for (var indicator : NutritionQualityIndicatorConfig.INDICATORS) {
                for (String rk : requiredKeys) {
                    assertTrue(indicator.containsKey(rk),
                            "Indicator missing '" + rk + "': " + indicator.get("key"));
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // NutritionQualityCell factories
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class CellTest {
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
        void cellMappingRequired_returnsCorrectStatus() {
            NutritionQualityCell cell = NutritionQualityCell.mappingRequired("targetVolume");
            assertEquals("mapping_required", cell.getDataStatus());
            assertTrue(cell.getMessage().contains("targetVolume"));
        }

        @Test
        void cellDisabled_returnsCorrectStatus() {
            NutritionQualityCell cell = NutritionQualityCell.disabled();
            assertEquals("disabled", cell.getDataStatus());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // NutritionQualityIndicator
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class IndicatorTest {
        @Test
        void settersAndGetters_work() {
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
        void mappingRequired_staticFactory() {
            NutritionQualityIndicator indicator = NutritionQualityIndicator.mappingRequired(
                    1, "testKey", "测试指标", "targetVolume");

            assertEquals(1, indicator.getId());
            assertEquals("testKey", indicator.getKey());
            assertEquals("测试指标", indicator.getName());
            assertEquals("mapping_required", indicator.getTotal().getDataStatus());
        }
    }
}
