package com.smartcare.icustats.service;

import com.smartcare.icustats.config.NutritionQualityProperties;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    // getPid
    // ════════════════════════════════════════════════════════════════════

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
    void getPid_customFieldMapping_usesConfiguredField() {
        properties.getFields().put("pid", "patientId");
        Document doc = new Document("patientId", "custom123");
        assertEquals("custom123", adapter.getPid(doc));
    }

    // ════════════════════════════════════════════════════════════════════
    // getRecordTime
    // ════════════════════════════════════════════════════════════════════

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

    // ════════════════════════════════════════════════════════════════════
    // getIntervention
    // ════════════════════════════════════════════════════════════════════

    @Test
    void getIntervention_csField_returnsString() {
        Document doc = new Document("cs", "H");
        assertEquals("H", adapter.getIntervention(doc));
    }

    @Test
    void getIntervention_missing_returnsEmpty() {
        Document doc = new Document();
        assertEquals("", adapter.getIntervention(doc));
    }

    @Test
    void getIntervention_customFieldMapping_usesConfiguredField() {
        properties.getFields().put("intervention", "interventionCode");
        Document doc = new Document("interventionCode", "K");
        assertEquals("K", adapter.getIntervention(doc));
    }

    // ════════════════════════════════════════════════════════════════════
    // getInterventionList
    // ════════════════════════════════════════════════════════════════════

    @Test
    void getInterventionList_listField_returnsList() {
        Document doc = new Document("csList", Arrays.asList("H", "J"));
        assertEquals(Arrays.asList("H", "J"), adapter.getInterventionList(doc));
    }

    @Test
    void getInterventionList_singleString_returnsWrappedList() {
        Document doc = new Document("csList", "H");
        assertEquals(Collections.singletonList("H"), adapter.getInterventionList(doc));
    }

    @Test
    void getInterventionList_missing_returnsEmpty() {
        Document doc = new Document();
        assertTrue(adapter.getInterventionList(doc).isEmpty());
    }

    // ════════════════════════════════════════════════════════════════════
    // isValidRecord
    // ════════════════════════════════════════════════════════════════════

    @Test
    void isValidRecord_stringValid_returnsTrue() {
        Document doc = new Document("valid", "valid");
        assertTrue(adapter.isValidRecord(doc));
    }

    @Test
    void isValidRecord_stringInvalid_returnsFalse() {
        Document doc = new Document("valid", "invalid");
        assertFalse(adapter.isValidRecord(doc));
    }

    @Test
    void isValidRecord_booleanTrue_returnsTrue() {
        Document doc = new Document("valid", true);
        assertTrue(adapter.isValidRecord(doc));
    }

    @Test
    void isValidRecord_booleanFalse_returnsFalse() {
        Document doc = new Document("valid", false);
        assertFalse(adapter.isValidRecord(doc));
    }

    @Test
    void isValidRecord_integerOne_returnsTrue() {
        Document doc = new Document("valid", 1);
        assertTrue(adapter.isValidRecord(doc));
    }

    @Test
    void isValidRecord_integerZero_returnsFalse() {
        Document doc = new Document("valid", 0);
        assertFalse(adapter.isValidRecord(doc));
    }

    @Test
    void isValidRecord_null_returnsFalse() {
        Document doc = new Document();
        assertFalse(adapter.isValidRecord(doc));
    }

    // ════════════════════════════════════════════════════════════════════
    // hasPauseIntervention
    // ════════════════════════════════════════════════════════════════════

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

    @Test
    void hasPauseIntervention_empty_returnsFalse() {
        Document doc = new Document();
        assertFalse(adapter.hasPauseIntervention(doc));
    }

    // ════════════════════════════════════════════════════════════════════
    // isTargetVolumeMapped
    // ════════════════════════════════════════════════════════════════════

    @Test
    void isTargetVolumeMapped_notConfigured_returnsFalse() {
        assertFalse(adapter.isTargetVolumeMapped());
    }

    @Test
    void isTargetVolumeMapped_configured_returnsTrue() {
        properties.getFields().put("targetVolume", "ymNum");
        assertTrue(adapter.isTargetVolumeMapped());
    }

    @Test
    void isTargetVolumeMapped_emptyString_returnsFalse() {
        properties.getFields().put("targetVolume", "");
        assertFalse(adapter.isTargetVolumeMapped());
    }

    // ════════════════════════════════════════════════════════════════════
    // maskPid
    // ════════════════════════════════════════════════════════════════════

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

    @Test
    void maskPid_empty_returnsStars() {
        assertEquals("****", NutritionQualityRecordAdapter.maskPid(""));
    }

    // ════════════════════════════════════════════════════════════════════
    // getSpeed (numeric field)
    // ════════════════════════════════════════════════════════════════════

    @Test
    void getSpeed_integer_returnsDouble() {
        Document doc = new Document("sd", 50);
        assertEquals(50.0, adapter.getSpeed(doc));
    }

    @Test
    void getSpeed_double_returnsDouble() {
        Document doc = new Document("sd", 50.5);
        assertEquals(50.5, adapter.getSpeed(doc));
    }

    @Test
    void getSpeed_missing_returnsNull() {
        Document doc = new Document();
        assertNull(adapter.getSpeed(doc));
    }

    // ════════════════════════════════════════════════════════════════════
    // getRoute
    // ════════════════════════════════════════════════════════════════════

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

    // ════════════════════════════════════════════════════════════════════
    // isFieldMapped
    // ════════════════════════════════════════════════════════════════════

    @Test
    void isFieldMapped_defaultMapped_returnsTrue() {
        assertTrue(adapter.isFieldMapped("pid"));
        assertTrue(adapter.isFieldMapped("recordTime"));
        assertTrue(adapter.isFieldMapped("route"));
    }

    @Test
    void isFieldMapped_notMapped_returnsFalse() {
        assertFalse(adapter.isFieldMapped("targetVolume"));
    }

    @Test
    void isFieldMapped_customMapping_returnsTrue() {
        properties.getFields().put("targetVolume", "ymNum");
        assertTrue(adapter.isFieldMapped("targetVolume"));
    }
}
