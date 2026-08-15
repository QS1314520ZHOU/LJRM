package com.smartcare.icustats.dto;

import java.util.Map;

/**
 * 营养质量指标单元格数据
 */
public class NutritionQualityCell {

    private Integer numerator;
    private Integer denominator;
    private Double value;
    private Boolean compliant;
    private String dataStatus;
    private String message;

    public NutritionQualityCell() {}

    public NutritionQualityCell(Integer numerator, Integer denominator, Double value,
                                 Boolean compliant, String dataStatus, String message) {
        this.numerator = numerator;
        this.denominator = denominator;
        this.value = value;
        this.compliant = compliant;
        this.dataStatus = dataStatus;
        this.message = message;
    }

    // Static factory methods
    public static NutritionQualityCell ok(int numerator, int denominator, double value, boolean compliant) {
        return new NutritionQualityCell(numerator, denominator, value, compliant, "ok", null);
    }

    public static NutritionQualityCell noData() {
        return new NutritionQualityCell(null, null, null, null, "no_data", "暂无数据");
    }

    public static NutritionQualityCell noDenominator() {
        return new NutritionQualityCell(null, null, null, null, "no_denominator", "无分母");
    }

    public static NutritionQualityCell mappingRequired(String field) {
        return new NutritionQualityCell(null, null, null, null, "mapping_required",
                "字段映射待确认: " + field);
    }

    public static NutritionQualityCell collectionNotConfigured() {
        return new NutritionQualityCell(null, null, null, null, "collection_not_configured",
                "集合未配置");
    }

    public static NutritionQualityCell collectionNotFound() {
        return new NutritionQualityCell(null, null, null, null, "collection_not_found",
                "集合未找到");
    }

    public static NutritionQualityCell queryError(String message) {
        return new NutritionQualityCell(null, null, null, null, "query_error",
                "查询失败: " + message);
    }

    public static NutritionQualityCell disabled() {
        return new NutritionQualityCell(null, null, null, null, "disabled", "功能已禁用");
    }

    // Getters and setters
    public Integer getNumerator() { return numerator; }
    public void setNumerator(Integer numerator) { this.numerator = numerator; }

    public Integer getDenominator() { return denominator; }
    public void setDenominator(Integer denominator) { this.denominator = denominator; }

    public Double getValue() { return value; }
    public void setValue(Double value) { this.value = value; }

    public Boolean getCompliant() { return compliant; }
    public void setCompliant(Boolean compliant) { this.compliant = compliant; }

    public String getDataStatus() { return dataStatus; }
    public void setDataStatus(String dataStatus) { this.dataStatus = dataStatus; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
