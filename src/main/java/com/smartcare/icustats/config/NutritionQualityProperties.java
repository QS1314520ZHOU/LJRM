package com.smartcare.icustats.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "icu-stats.nutrition-quality")
public class NutritionQualityProperties {

    private boolean enabled = true;
    private String collection = "";
    private String timezone = "Asia/Shanghai";
    private String validValue = "valid";
    private String defaultDeptCode = "0211";
    private String classPattern = "PatCnyyRestraintLjrmyy";
    private boolean autoDiscoverCollection = false;
    private Map<String, String> fields = new LinkedHashMap<>();

    public NutritionQualityProperties() {
        // Default field mappings
        fields.put("pid", "pid");
        fields.put("recordTime", "startTime");
        fields.put("route", "tj");
        fields.put("speed", "sd");
        fields.put("depth", "cd");
        fields.put("patency", "tcx");
        fields.put("gastricColor", "wyys");
        fields.put("flushing", "cg");
        fields.put("residualVolume", "wcyl");
        fields.put("toleranceScore", "zf");
        fields.put("scoreE", "e");
        fields.put("scoreF", "f");
        fields.put("scoreG", "g");
        fields.put("intervention", "cs");
        fields.put("interventionList", "csList");
        fields.put("targetVolume", "");
        fields.put("completedVolume", "");
        fields.put("mechanicalComplication", "");
        fields.put("gastrointestinalComplication", "");
        fields.put("metabolicComplication", "");
        fields.put("infectionComplication", "");
        fields.put("refeedingSyndrome", "");
        fields.put("pauseReason", "");
        fields.put("remark", "");
        fields.put("deptCode", "deptCode");
        fields.put("mrn", "mrn");
        fields.put("name", "name");
        fields.put("bed", "bed");
    }

    // Getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getCollection() { return collection; }
    public void setCollection(String collection) { this.collection = collection; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public String getValidValue() { return validValue; }
    public void setValidValue(String validValue) { this.validValue = validValue; }

    public String getDefaultDeptCode() { return defaultDeptCode; }
    public void setDefaultDeptCode(String defaultDeptCode) { this.defaultDeptCode = defaultDeptCode; }

    public String getClassPattern() { return classPattern; }
    public void setClassPattern(String classPattern) { this.classPattern = classPattern; }

    public boolean isAutoDiscoverCollection() { return autoDiscoverCollection; }
    public void setAutoDiscoverCollection(boolean autoDiscoverCollection) { this.autoDiscoverCollection = autoDiscoverCollection; }

    public Map<String, String> getFields() { return fields; }
    public void setFields(Map<String, String> fields) { this.fields = fields; }

    public String getField(String key) {
        return fields.getOrDefault(key, "");
    }

    public String getFieldOrDefault(String key, String defaultField) {
        String value = fields.get(key);
        return (value != null && !value.isEmpty()) ? value : defaultField;
    }
}
