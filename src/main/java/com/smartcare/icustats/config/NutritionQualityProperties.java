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
    /** 并发症/管道通畅/冲管的"已发生"判定值，默认 "√" */
    private String checkedValue = "√";
    /** 喂养管类型编码映射: nasogastric/nasojejunal/gastrostomy/other → MongoDB tj 字段实际值 */
    private Map<String, String> routeValues = new LinkedHashMap<>();
    private Map<String, String> fields = new LinkedHashMap<>();

    public NutritionQualityProperties() {
        // Default field mappings — MongoDB 字段名
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
        fields.put("targetVolume", "mbl");
        fields.put("completedVolume", "wcl");
        fields.put("mechanicalComplication", "jxx");
        fields.put("gastrointestinalComplication", "wcd");
        fields.put("metabolicComplication", "dxx");
        fields.put("infectionComplication", "grx");
        fields.put("refeedingSyndrome", "zhz");
        fields.put("pauseReason", "ztyy");
        fields.put("pauseReasonList", "ztyyList");
        fields.put("remark", "bz");
        fields.put("deptCode", "deptCode");
        fields.put("mrn", "mrn");
        fields.put("name", "name");
        fields.put("bed", "bed");
    }

    // ── 基本属性 ──────────────────────────────────────────────────────

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

    // ── checked-value: "√" 判定 ──────────────────────────────────────

    public String getCheckedValue() { return checkedValue; }
    public void setCheckedValue(String checkedValue) { this.checkedValue = checkedValue; }

    // ── route-values: 营养通路编码映射 ────────────────────────────────

    public Map<String, String> getRouteValues() { return routeValues; }
    public void setRouteValues(Map<String, String> routeValues) { this.routeValues = routeValues; }

    // ── fields: 字段映射 ─────────────────────────────────────────────

    public Map<String, String> getFields() { return fields; }
    public void setFields(Map<String, String> fields) { this.fields = fields; }

    /**
     * 获取 fields 下的子字段值，例如 fields.pause-reason-list
     * YAML 结构: fields:\n  pause-reason-list: ztyyList
     */
    public String getNestedField(String section, String key) {
        // Spring Boot binding 会把 fields.pause-reason-list 映射到 fields map 中
        // key 在 map 中是 "pause-reason-list"
        String fullKey = section + "." + key;
        // 先尝试完整 key（Spring relaxed binding 可能已处理）
        String val = fields.get(fullKey);
        if (val != null) return val;
        // 再尝试直接 key
        val = fields.get(key);
        return val;
    }

    public String getField(String key) {
        return fields.getOrDefault(key, "");
    }

    public String getFieldOrDefault(String key, String defaultField) {
        String value = fields.get(key);
        return (value != null && !value.isEmpty()) ? value : defaultField;
    }
}
