package com.smartcare.icustats.dto;

import java.util.Map;

/**
 * 营养质量指标元数据和计算结果
 */
public class NutritionQualityIndicator {

    private int id;
    private String key;
    private String name;
    private String unit;
    private String formula;
    private String numeratorDefinition;
    private String denominatorDefinition;
    private String target;
    private String comparison; // LT, LE, GT, GE, EQ, DISPLAY_ONLY
    private String aggregationUnit;
    private NutritionQualityCell total;
    private Map<String, NutritionQualityCell> monthly;

    public NutritionQualityIndicator() {}

    // Static factory for mapping_required indicators
    public static NutritionQualityIndicator mappingRequired(int id, String key, String name, String missingField) {
        NutritionQualityIndicator indicator = new NutritionQualityIndicator();
        indicator.setId(id);
        indicator.setKey(key);
        indicator.setName(name);
        indicator.setUnit("%");
        indicator.setTotal(NutritionQualityCell.mappingRequired(missingField));
        indicator.setMonthly(new java.util.LinkedHashMap<>());
        return indicator;
    }

    // Getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getFormula() { return formula; }
    public void setFormula(String formula) { this.formula = formula; }

    public String getNumeratorDefinition() { return numeratorDefinition; }
    public void setNumeratorDefinition(String numeratorDefinition) { this.numeratorDefinition = numeratorDefinition; }

    public String getDenominatorDefinition() { return denominatorDefinition; }
    public void setDenominatorDefinition(String denominatorDefinition) { this.denominatorDefinition = denominatorDefinition; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public String getComparison() { return comparison; }
    public void setComparison(String comparison) { this.comparison = comparison; }

    public String getAggregationUnit() { return aggregationUnit; }
    public void setAggregationUnit(String aggregationUnit) { this.aggregationUnit = aggregationUnit; }

    public NutritionQualityCell getTotal() { return total; }
    public void setTotal(NutritionQualityCell total) { this.total = total; }

    public Map<String, NutritionQualityCell> getMonthly() { return monthly; }
    public void setMonthly(Map<String, NutritionQualityCell> monthly) { this.monthly = monthly; }
}
