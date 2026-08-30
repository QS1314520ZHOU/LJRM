package com.smartcare.icustats.service;

import com.smartcare.icustats.config.NutritionQualityProperties;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class NutritionQualityRecordAdapterTest {

    private NutritionQualityProperties properties;
    private NutritionQualityRecordAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new NutritionQualityProperties();
        adapter = new NutritionQualityRecordAdapter(properties);
    }

    // ════════════════════════════════════════════════════════════════════
    // isChecked — 统一 "√" 判定
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class IsCheckedTest {
        @Test
        void checkmark_returnsTrue() {
            assertTrue(adapter.isChecked("√"));
        }

        @Test
        void checkmarkWithSpaces_returnsTrue() {
            assertTrue(adapter.isChecked(" √ "));
        }

        @Test
        void null_returnsFalse() {
            assertFalse(adapter.isChecked(null));
        }

        @Test
        void emptyString_returnsFalse() {
            assertFalse(adapter.isChecked(""));
        }

        @Test
        void cross_returnsFalse() {
            assertFalse(adapter.isChecked("×"));
        }

        @Test
        void chineseNo_returnsFalse() {
            assertFalse(adapter.isChecked("否"));
        }

        @Test
        void falseString_returnsFalse() {
            assertFalse(adapter.isChecked("false"));
        }

        @Test
        void zeroString_returnsFalse() {
            assertFalse(adapter.isChecked("0"));
        }

        @Test
        void booleanTrue_returnsFalse() {
            // Boolean.parseBoolean("√") returns false, so we must not use it
            assertFalse(adapter.isChecked(true));
        }

        @Test
        void customCheckedValue_works() {
            properties.setCheckedValue("是");
            assertTrue(adapter.isChecked("是"));
            assertFalse(adapter.isChecked("√"));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // toDecimal — 安全数值转换
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class ToDecimalTest {
        @Test
        void stringNumber_parses() {
            assertEquals(new BigDecimal("24"), NutritionQualityRecordAdapter.toDecimal("24"));
        }

        @Test
        void decimalString_parses() {
            assertEquals(new BigDecimal("6.5"), NutritionQualityRecordAdapter.toDecimal("6.5"));
        }

        @Test
        void integer_parses() {
            assertEquals(new BigDecimal("24"), NutritionQualityRecordAdapter.toDecimal(24));
        }

        @Test
        void integerAsMongoInt32_parses() {
            // Simulate MongoDB Int32
            assertEquals(new BigDecimal("24"), NutritionQualityRecordAdapter.toDecimal(Integer.valueOf(24)));
        }

        @Test
        void long_parses() {
            assertEquals(new BigDecimal("100"), NutritionQualityRecordAdapter.toDecimal(100L));
        }

        @Test
        void double_parses() {
            assertEquals(new BigDecimal("6.5"), NutritionQualityRecordAdapter.toDecimal(6.5));
        }

        @Test
        void float_parses() {
            assertEquals(new BigDecimal("3.14"), NutritionQualityRecordAdapter.toDecimal(3.14f));
        }

        @Test
        void bigDecimal_passthrough() {
            BigDecimal val = new BigDecimal("99.99");
            assertSame(val, NutritionQualityRecordAdapter.toDecimal(val));
        }

        @Test
        void decimal128_parses() {
            Decimal128 d128 = new Decimal128(42);
            assertEquals(new BigDecimal("42"), NutritionQualityRecordAdapter.toDecimal(d128));
        }

        @Test
        void null_returnsNull() {
            assertNull(NutritionQualityRecordAdapter.toDecimal(null));
        }

        @Test
        void emptyString_returnsNull() {
            assertNull(NutritionQualityRecordAdapter.toDecimal(""));
        }

        @Test
        void illegalString_returnsNull() {
            assertNull(NutritionQualityRecordAdapter.toDecimal("abc"));
        }

        @Test
        void slash_returnsNull() {
            assertNull(NutritionQualityRecordAdapter.toDecimal("/"));
        }

        @Test
        void dash_returnsNull() {
            assertNull(NutritionQualityRecordAdapter.toDecimal("-"));
        }

        @Test
        void stringWithSpaces_parses() {
            assertEquals(new BigDecimal("24"), NutritionQualityRecordAdapter.toDecimal(" 24 "));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 五类并发症判断
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class ComplicationTest {
        @Test
        void mechanicalComplication_checked_returnsTrue() {
            Document doc = new Document("jxx", "√");
            assertTrue(adapter.hasMechanicalComplication(doc));
        }

        @Test
        void mechanicalComplication_unchecked_returnsFalse() {
            Document doc = new Document("jxx", "×");
            assertFalse(adapter.hasMechanicalComplication(doc));
        }

        @Test
        void gastrointestinalComplication_checked_returnsTrue() {
            Document doc = new Document("wcd", "√");
            assertTrue(adapter.hasGastrointestinalComplication(doc));
        }

        @Test
        void metabolicComplication_checked_returnsTrue() {
            Document doc = new Document("dxx", "√");
            assertTrue(adapter.hasMetabolicComplication(doc));
        }

        @Test
        void infectionComplication_checked_returnsTrue() {
            Document doc = new Document("grx", "√");
            assertTrue(adapter.hasInfectionComplication(doc));
        }

        @Test
        void refeedingSyndrome_checked_returnsTrue() {
            Document doc = new Document("zhz", "√");
            assertTrue(adapter.hasRefeedingSyndrome(doc));
        }

        @Test
        void complication_null_returnsFalse() {
            Document doc = new Document();
            assertFalse(adapter.hasMechanicalComplication(doc));
            assertFalse(adapter.hasGastrointestinalComplication(doc));
            assertFalse(adapter.hasMetabolicComplication(doc));
            assertFalse(adapter.hasInfectionComplication(doc));
            assertFalse(adapter.hasRefeedingSyndrome(doc));
        }

        @Test
        void complication_emptyString_returnsFalse() {
            Document doc = new Document("jxx", "");
            assertFalse(adapter.hasMechanicalComplication(doc));
        }

        @Test
        void hasAnyComplication_oneTrue_returnsTrue() {
            Document doc = new Document("jxx", "√")
                    .append("wcd", "×")
                    .append("dxx", "×")
                    .append("grx", "×")
                    .append("zhz", "×");
            assertTrue(adapter.hasAnyComplication(doc));
        }

        @Test
        void hasAnyComplication_allFalse_returnsFalse() {
            Document doc = new Document("jxx", "×")
                    .append("wcd", "×")
                    .append("dxx", "×")
                    .append("grx", "×")
                    .append("zhz", "×");
            assertFalse(adapter.hasAnyComplication(doc));
        }

        @Test
        void complication_unmappedField_returnsFalse() {
            // Fields not mapped in properties default to empty string
            Document doc = new Document();
            assertFalse(adapter.hasMechanicalComplication(doc));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 管道通畅和冲管
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class PatencyAndFlushingTest {
        @Test
        void patency_checked_returnsTrue() {
            Document doc = new Document("tcx", "√");
            assertTrue(adapter.isPatent(doc));
        }

        @Test
        void patency_unchecked_returnsFalse() {
            Document doc = new Document("tcx", "×");
            assertFalse(adapter.isPatent(doc));
        }

        @Test
        void flushing_checked_returnsTrue() {
            Document doc = new Document("cg", "√");
            assertTrue(adapter.isFlushed(doc));
        }

        @Test
        void flushing_unchecked_returnsFalse() {
            Document doc = new Document("cg", "");
            assertFalse(adapter.isFlushed(doc));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 有效性判断
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class ValidityTest {
        @Test
        void validString_valid_returnsTrue() {
            Document doc = new Document("valid", "valid");
            assertTrue(adapter.isValidRecord(doc));
        }

        @Test
        void validString_invalid_returnsFalse() {
            Document doc = new Document("valid", "invalid");
            assertFalse(adapter.isValidRecord(doc));
        }

        @Test
        void validNull_returnsFalse() {
            Document doc = new Document();
            assertFalse(adapter.isValidRecord(doc));
        }

        @Test
        void validWithSpaces_trimmedCorrectly() {
            Document doc = new Document("valid", " valid ");
            assertTrue(adapter.isValidRecord(doc));
        }

        @Test
        void customValidValue_works() {
            properties.setValidValue("active");
            Document doc = new Document("valid", "active");
            assertTrue(adapter.isValidRecord(doc));

            Document doc2 = new Document("valid", "valid");
            assertFalse(adapter.isValidRecord(doc2));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 耐受性判断
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class ToleranceTest {
        @Test
        void zfZero_tolerant() {
            Document doc = new Document("zf", 0);
            assertEquals("tolerant", adapter.classifyTolerance(doc));
        }

        @Test
        void zfPositive_intolerant() {
            Document doc = new Document("zf", 3);
            assertEquals("intolerant", adapter.classifyTolerance(doc));
        }

        @Test
        void zfNull_unassessed() {
            Document doc = new Document();
            assertEquals("unassessed", adapter.classifyTolerance(doc));
        }

        @Test
        void zfString_parsesCorrectly() {
            Document doc = new Document("zf", "0");
            assertEquals("tolerant", adapter.classifyTolerance(doc));
        }

        @Test
        void zfStringPositive_intolerant() {
            Document doc = new Document("zf", "5");
            assertEquals("intolerant", adapter.classifyTolerance(doc));
        }

        @Test
        void scoreE_positive() {
            Document doc = new Document("e", "3");
            assertTrue(adapter.isScoreEPositive(doc));
        }

        @Test
        void scoreE_zero() {
            Document doc = new Document("e", "0");
            assertFalse(adapter.isScoreEPositive(doc));
        }

        @Test
        void scoreF_positive() {
            Document doc = new Document("f", "2");
            assertTrue(adapter.isScoreFPositive(doc));
        }

        @Test
        void scoreG_positive() {
            Document doc = new Document("g", "1");
            assertTrue(adapter.isScoreGPositive(doc));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 目标量和完成量
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class VolumeTest {
        @Test
        void targetVolume_string_parses() {
            Document doc = new Document("mbl", "1");
            assertEquals(new BigDecimal("1"), adapter.getTargetVolume(doc));
        }

        @Test
        void completedVolume_string_parses() {
            Document doc = new Document("wcl", "2");
            assertEquals(new BigDecimal("2"), adapter.getCompletedVolume(doc));
        }

        @Test
        void targetVolume_integer_parses() {
            Document doc = new Document("mbl", 100);
            assertEquals(new BigDecimal("100"), adapter.getTargetVolume(doc));
        }

        @Test
        void targetVolume_null_returnsNull() {
            Document doc = new Document();
            assertNull(adapter.getTargetVolume(doc));
        }

        @Test
        void targetVolume_zeroTarget_noDivisionByZero() {
            Document doc = new Document("mbl", "0").append("wcl", "10");
            Map<String, Object> info = adapter.calcCompletionInfo(doc);
            assertNull(info.get("completionRate"));
            assertEquals("目标量无效", info.get("mappingStatus"));
        }

        @Test
        void completionRate_over100_notTruncated() {
            Document doc = new Document("mbl", "100").append("wcl", "150");
            Map<String, Object> info = adapter.calcCompletionInfo(doc);
            assertEquals(new BigDecimal("150.00"), info.get("completionRate"));
            assertEquals(true, info.get("targetReached"));
        }

        @Test
        void completionRate_exactTarget() {
            Document doc = new Document("mbl", "100").append("wcl", "100");
            Map<String, Object> info = adapter.calcCompletionInfo(doc);
            assertEquals(new BigDecimal("100.00"), info.get("completionRate"));
            assertEquals(true, info.get("targetReached"));
        }

        @Test
        void completionRate_belowTarget() {
            Document doc = new Document("mbl", "100").append("wcl", "80");
            Map<String, Object> info = adapter.calcCompletionInfo(doc);
            assertEquals(new BigDecimal("80.00"), info.get("completionRate"));
            assertEquals(false, info.get("targetReached"));
        }

        @Test
        void completionRate_unmappedField_returnsMappingRequired() {
            // Default properties has targetVolume=mbl, completedVolume=wcl
            // so they are mapped
            Document doc = new Document();
            Map<String, Object> info = adapter.calcCompletionInfo(doc);
            assertNull(info.get("completionRate"));
            assertNotNull(info.get("mappingStatus"));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 营养通路分类
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class RouteClassificationTest {
        @Test
        void tubeTypeA_nasogastric() {
            Document doc = new Document("tj", "A");
            assertEquals("nasogastric", adapter.classifyRoute(doc));
        }

        @Test
        void tubeTypeB_nasojejunal() {
            Document doc = new Document("tj", "B");
            assertEquals("nasojejunal", adapter.classifyRoute(doc));
        }

        @Test
        void tubeTypeC_gastrostomy() {
            Document doc = new Document("tj", "C");
            assertEquals("gastrostomy", adapter.classifyRoute(doc));
        }

        @Test
        void tubeTypeD_other() {
            Document doc = new Document("tj", "D");
            assertEquals("other", adapter.classifyRoute(doc));
        }

        @Test
        void unknownTubeType_returnsUnknown() {
            Document doc = new Document("tj", "X");
            assertEquals("unknown", adapter.classifyRoute(doc));
        }

        @Test
        void customRouteValues_work() {
            properties.getRouteValues().put("nasogastric", "NG");
            Document doc = new Document("tj", "NG");
            assertEquals("nasogastric", adapter.classifyRoute(doc));
        }

        @Test
        void emptyRoute_returnsUnknown() {
            Document doc = new Document();
            assertEquals("unknown", adapter.classifyRoute(doc));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 暂停原因列表
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class PauseReasonTest {
        @Test
        void ztyyList_preferred_overZtyy() {
            Document doc = new Document("ztyyList", Arrays.asList("手术", "患者要求"))
                    .append("ztyy", "暂停原因：其他");
            List<String> reasons = adapter.getPauseReasonList(doc);
            assertEquals(Arrays.asList("手术", "患者要求"), reasons);
        }

        @Test
        void ztyyList_empty_fallbackToZtyy() {
            Document doc = new Document("ztyyList", Collections.emptyList())
                    .append("ztyy", "暂停原因：手术");
            List<String> reasons = adapter.getPauseReasonList(doc);
            assertEquals(1, reasons.size());
            assertTrue(reasons.get(0).contains("手术"));
        }

        @Test
        void ztyyList_missing_fallbackToZtyy() {
            Document doc = new Document("ztyy", "暂停原因：手术");
            List<String> reasons = adapter.getPauseReasonList(doc);
            assertEquals(1, reasons.size());
        }

        @Test
        void bothMissing_returnsEmpty() {
            Document doc = new Document();
            List<String> reasons = adapter.getPauseReasonList(doc);
            assertTrue(reasons.isEmpty());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 干预措施
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class InterventionTest {
        @Test
        void hasPauseIntervention_csListContainsJ_returnsTrue() {
            Document doc = new Document("csList", Arrays.asList("H", "J"));
            assertTrue(adapter.hasPauseIntervention(doc));
        }

        @Test
        void hasPauseIntervention_csListContainsLowercaseJ_returnsFalse() {
            Document doc = new Document("csList", Arrays.asList("H", "j"));
            assertFalse(adapter.hasPauseIntervention(doc));
        }

        @Test
        void hasPauseIntervention_csFieldJ_returnsTrue() {
            Document doc = new Document("cs", "J");
            assertTrue(adapter.hasPauseIntervention(doc));
        }

        @Test
        void hasPauseIntervention_noJ_returnsFalse() {
            Document doc = new Document("csList", Arrays.asList("H", "I", "K"));
            assertFalse(adapter.hasPauseIntervention(doc));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 基础字段读取
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class BasicFieldTest {
        @Test
        void getPid_stringField_returnsString() {
            Document doc = new Document("pid", "abc123");
            assertEquals("abc123", adapter.getPid(doc));
        }

        @Test
        void getPid_missingField_returnsEmpty() {
            Document doc = new Document();
            assertEquals("", adapter.getPid(doc));
        }

        @Test
        void getRecordTime_dateField_returnsDate() {
            Date now = new Date();
            Document doc = new Document("startTime", now);
            assertEquals(now, adapter.getRecordTime(doc));
        }

        @Test
        void getRecordTime_null_returnsNull() {
            Document doc = new Document();
            assertNull(adapter.getRecordTime(doc));
        }

        @Test
        void getRoute_present_returnsValue() {
            Document doc = new Document("tj", "EN");
            assertEquals("EN", adapter.getRoute(doc));
        }

        @Test
        void getRoute_missing_returnsEmpty() {
            Document doc = new Document();
            assertEquals("", adapter.getRoute(doc));
        }

        @Test
        void getSpeed_integer_returnsDouble() {
            Document doc = new Document("sd", 50);
            assertEquals(50.0, adapter.getSpeed(doc));
        }

        @Test
        void getSpeed_missing_returnsNull() {
            Document doc = new Document();
            assertNull(adapter.getSpeed(doc));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 脱敏
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class MaskPidTest {
        @Test
        void maskPid_longId_correctlyMasked() {
            assertEquals("6a7b****d292", NutritionQualityRecordAdapter.maskPid("6a7b2f90c618e735eb79d292"));
        }

        @Test
        void maskPid_exactlyEight_showsFirst4Last4() {
            assertEquals("abcd****efgh", NutritionQualityRecordAdapter.maskPid("abcdefgh"));
        }

        @Test
        void maskPid_sevenChars_returnsStars() {
            assertEquals("****", NutritionQualityRecordAdapter.maskPid("abcdefg"));
        }

        @Test
        void maskPid_null_returnsStars() {
            assertEquals("****", NutritionQualityRecordAdapter.maskPid(null));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 字段映射检查
    // ════════════════════════════════════════════════════════════════════

    @Nested
    class FieldMappingTest {
        @Test
        void isFieldMapped_defaultMapped_returnsTrue() {
            assertTrue(adapter.isFieldMapped("pid"));
            assertTrue(adapter.isFieldMapped("recordTime"));
            assertTrue(adapter.isFieldMapped("route"));
            assertTrue(adapter.isFieldMapped("mechanicalComplication"));
            assertTrue(adapter.isFieldMapped("targetVolume"));
        }

        @Test
        void isFieldMapped_notMapped_returnsFalse() {
            // All fields now have defaults, so check a non-existent key
            assertFalse(adapter.isFieldMapped("nonExistentKey"));
        }

        @Test
        void isTargetVolumeMapped_default_returnsTrue() {
            assertTrue(adapter.isTargetVolumeMapped());
        }

        @Test
        void isCompletedVolumeMapped_default_returnsTrue() {
            assertTrue(adapter.isCompletedVolumeMapped());
        }

        @Test
        void isComplicationFieldMapped_default_returnsTrue() {
            assertTrue(adapter.isComplicationFieldMapped("mechanicalComplication"));
            assertTrue(adapter.isComplicationFieldMapped("gastrointestinalComplication"));
            assertTrue(adapter.isComplicationFieldMapped("metabolicComplication"));
            assertTrue(adapter.isComplicationFieldMapped("infectionComplication"));
            assertTrue(adapter.isComplicationFieldMapped("refeedingSyndrome"));
        }
    }
}
