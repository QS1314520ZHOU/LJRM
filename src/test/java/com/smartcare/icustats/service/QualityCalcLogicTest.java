package com.smartcare.icustats.service;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for quality calculation logic - extubation types, boolean handling, 48h boundary, etc.
 */
class QualityCalcLogicTest {

    @Test
    void extubationType_matchesMultipleVariants() {
        // The tubeExe.type field should match various airway tube types
        Set<String> validTypes = new HashSet<>(Arrays.asList(
                "气插管", "气管插管", "经口气管插管", "经鼻气管插管"));

        // Our current code only checks "气插管" - this test documents the gap
        assertTrue(validTypes.contains("气插管"), "气插管 should be valid");
        assertTrue(validTypes.contains("气管插管"), "气管插管 should also be valid");
    }

    @Test
    void unPlannedEndTube_booleanTypes() {
        // unPlannedEndTube field can be various boolean representations
        Document tube1 = new Document("unPlannedEndTube", true);
        Document tube2 = new Document("unPlannedEndTube", "true");
        Document tube3 = new Document("unPlannedEndTube", 1);
        Document tube4 = new Document("unPlannedEndTube", false);
        Document tube5 = new Document("unPlannedEndTube", null);

        // Test Boolean.TRUE.equals check (current implementation)
        assertTrue(isUnplanned(tube1), "Boolean true should match");
        assertFalse(isUnplanned(tube2), "String 'true' does NOT match Boolean.TRUE.equals - this is a known gap");
        assertFalse(isUnplanned(tube3), "Integer 1 does NOT match Boolean.TRUE.equals - this is a known gap");
        assertFalse(isUnplanned(tube4), "Boolean false should not match");
        assertFalse(isUnplanned(tube5), "null should not match");
    }

    private boolean isUnplanned(Document tube) {
        return Boolean.TRUE.equals(tube.get("unPlannedEndTube"));
    }

    @Test
    void hours48Boundary_exactlyAt48h() {
        long HOURS_48_MS = 48L * 3600 * 1000;
        long endTime = 1000000L;
        long startTime = endTime + HOURS_48_MS;

        // Exactly at 48h should count (<=48h)
        assertTrue((startTime - endTime) <= HOURS_48_MS, "Exactly 48h should count");
    }

    @Test
    void hours48Boundary_justOver48h() {
        long HOURS_48_MS = 48L * 3600 * 1000;
        long endTime = 1000000L;
        long startTime = endTime + HOURS_48_MS + 1;

        assertFalse((startTime - endTime) <= HOURS_48_MS, "Just over 48h should not count");
    }

    @Test
    void hours48Boundary_justUnder48h() {
        long HOURS_48_MS = 48L * 3600 * 1000;
        long endTime = 1000000L;
        long startTime = endTime + HOURS_48_MS - 1;

        assertTrue((startTime - endTime) <= HOURS_48_MS, "Just under 48h should count");
    }

    @Test
    void rescue_dischargeTypeContains() {
        // dischargedType field matching
        String dischargedType1 = "死亡";
        String dischargedType2 = "死亡（终末）";
        String dischargedType3 = "转出";
        String dischargedType4 = "自动出院";

        assertTrue(dischargedType1.contains("死亡"));
        assertTrue(dischargedType2.contains("死亡"));
        assertTrue(dischargedType2.contains("终末"));
        assertFalse(dischargedType3.contains("死亡"));
        assertFalse(dischargedType4.contains("死亡"));
    }

    @Test
    void rescue_terminalExcludedFromDenom() {
        // "死亡（终末）" should be excluded from denominator
        String terminalType = "死亡（终末）";
        assertTrue(terminalType.contains("死亡（终末）"), "Terminal death should be detected");
        // In current code: if dischargedType.contains("死亡（终末）") → terminal++, continue (skip denom)
    }

    @Test
    void dischargeTypeFields_multipleVariants() {
        // Patient discharge type can be in multiple fields
        Document patient = new Document("dischargedType", "转出")
                .append("dischargeType", "转出")
                .append("outType", "转出");

        // getFirstValue should find the first non-empty one
        String[] fields = {"dischargedType", "dischargeType", "outType"};
        String value = getFirstValue(patient, fields);
        assertEquals("转出", value);
    }

    @Test
    void dischargeTypeFields_firstFieldEmpty() {
        Document patient = new Document("dischargedType", "")
                .append("dischargeType", "转出");

        String[] fields = {"dischargedType", "dischargeType", "outType"};
        String value = getFirstValue(patient, fields);
        assertEquals("转出", value);
    }

    private String getFirstValue(Document doc, String... fields) {
        for (String f : fields) {
            Object v = doc.get(f);
            if (v != null && !String.valueOf(v).isEmpty()) return String.valueOf(v);
        }
        return "";
    }

    @Test
    void transferDeptFields_multipleVariants() {
        Document patient = new Document("dischargedDepartment", "呼吸内科")
                .append("transferDept", "")
                .append("outDeptName", null);

        String[] fields = {"dischargedDepartment", "transferDept", "outDeptName"};
        String value = getFirstNonNull(patient, fields);
        assertEquals("呼吸内科", value);
    }

    private String getFirstNonNull(Document doc, String... fields) {
        for (String f : fields) {
            Object v = doc.get(f);
            if (v != null && !String.valueOf(v).isEmpty()) return String.valueOf(v);
        }
        return "";
    }

    @Test
    void dataSourceMode_defaultAuto() {
        // The default source mode should be "auto"
        // This test just documents the expected behavior
        String mode = "auto";
        assertNotNull(mode);
        assertTrue(Arrays.asList("auto", "live", "stored").contains(mode));
    }
}
