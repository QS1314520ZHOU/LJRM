package com.smartcare.icustats.dto;

import java.util.List;
import java.util.Map;

/**
 * 营养质量指标年度/范围查询响应
 */
public class NutritionQualityResponse {

    private String startMonth;
    private String endMonth;
    private List<String> months;
    private List<NutritionQualityIndicator> indicators;
    private String dataStatus;
    private String message;

    public NutritionQualityResponse() {}

    // Getters and setters
    public String getStartMonth() { return startMonth; }
    public void setStartMonth(String startMonth) { this.startMonth = startMonth; }

    public String getEndMonth() { return endMonth; }
    public void setEndMonth(String endMonth) { this.endMonth = endMonth; }

    public List<String> getMonths() { return months; }
    public void setMonths(List<String> months) { this.months = months; }

    public List<NutritionQualityIndicator> getIndicators() { return indicators; }
    public void setIndicators(List<NutritionQualityIndicator> indicators) { this.indicators = indicators; }

    public String getDataStatus() { return dataStatus; }
    public void setDataStatus(String dataStatus) { this.dataStatus = dataStatus; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
