package com.smartcare.icustats.service;

import com.smartcare.icustats.config.IcuStatsProperties;
import com.smartcare.icustats.util.PatientUtils;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for department filter behavior controlled by ENABLE_DEPT_FILTER.
 */
class QualityDepartmentFilterTest {

    @Test
    void whenDeptFilterDisabled_noDeptConditionsAdded() {
        List<Document> deptOr = PatientUtils.buildDepartmentOr("重症医学科", false);
        assertTrue(deptOr.isEmpty(), "When enableDeptFilter=false, no department conditions should be added");
    }

    @Test
    void whenDeptFilterEnabled_deptConditionsAdded() {
        List<Document> deptOr = PatientUtils.buildDepartmentOr("重症医学科", true);
        assertFalse(deptOr.isEmpty(), "When enableDeptFilter=true, department conditions should be added");
        // Should contain conditions for all department fields
        assertTrue(deptOr.size() >= 6, "Should have conditions for multiple department fields");
    }

    @Test
    void whenDeptFilterDisabled_patientWithNoDeptIncluded() {
        // Patient with no department fields at all
        Document filter = PatientUtils.buildPatientFilter(null, "重症医学科", false);
        // Filter should only have status != invalid, no department conditions
        assertFalse(filter.containsKey("$and"), "No $and wrapper when dept filter disabled");
        assertFalse(filter.containsKey("$or"), "No $or when dept filter disabled");
        assertEquals(new Document("$ne", "invalid"), filter.get("status"));
    }

    @Test
    void whenDeptFilterEnabled_patientWithDeptFiltered() {
        Document filter = PatientUtils.buildPatientFilter(null, "重症医学科", true);
        // Should have $and wrapper with department conditions
        assertTrue(filter.containsKey("$and"), "Should have $and wrapper when dept filter enabled");
    }

    @Test
    void whenDeptFilterDisabled_nullDepartment_noConditions() {
        List<Document> deptOr = PatientUtils.buildDepartmentOr(null, false);
        assertTrue(deptOr.isEmpty());
    }

    @Test
    void whenDeptFilterEnabled_emptyDepartment_noConditions() {
        List<Document> deptOr = PatientUtils.buildDepartmentOr("", true);
        assertTrue(deptOr.isEmpty(), "Empty department should not add conditions even when filter enabled");
    }

    @Test
    void monthlyOverlapFilter_disabledDeptFilter_noDeptConditions() {
        Document filter = PatientUtils.buildMonthlyOverlapFilter(
                new java.util.Date(), new java.util.Date(), "重症医学科", false);
        // Should have $or for discharge time but no $and for department
        assertTrue(filter.containsKey("$or"), "Should have discharge time $or");
        assertFalse(filter.containsKey("$and"), "Should NOT have $and when dept filter disabled");
    }

    @Test
    void monthlyOverlapFilter_enabledDeptFilter_hasDeptConditions() {
        Document filter = PatientUtils.buildMonthlyOverlapFilter(
                new java.util.Date(), new java.util.Date(), "重症医学科", true);
        assertTrue(filter.containsKey("$and"), "Should have $and when dept filter enabled");
    }

    @Test
    void propertiesDefault_deptFilterDisabled() {
        IcuStatsProperties props = new IcuStatsProperties();
        assertFalse(props.isEnableDeptFilter(), "Default ENABLE_DEPT_FILTER should be false");
    }

    @Test
    void propertiesDefault_sourceModeAuto() {
        IcuStatsProperties props = new IcuStatsProperties();
        assertEquals("auto", props.getQualitySourceMode(), "Default source mode should be auto");
    }
}
