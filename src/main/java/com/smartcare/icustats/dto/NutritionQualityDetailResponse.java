package com.smartcare.icustats.dto;

import java.util.List;
import java.util.Map;

/**
 * 营养质量指标明细查询响应
 */
public class NutritionQualityDetailResponse {

    private String indicatorKey;
    private String indicatorName;
    private String startMonth;
    private String endMonth;
    private List<Map<String, String>> columns;
    private List<Map<String, Object>> rows;
    private String dataStatus;
    private String message;

    public NutritionQualityDetailResponse() {}

    // Getters and setters
    public String getIndicatorKey() { return indicatorKey; }
    public void setIndicatorKey(String indicatorKey) { this.indicatorKey = indicatorKey; }

    public String getIndicatorName() { return indicatorName; }
    public void setIndicatorName(String indicatorName) { this.indicatorName = indicatorName; }

    public String getStartMonth() { return startMonth; }
    public void setStartMonth(String startMonth) { this.startMonth = startMonth; }

    public String getEndMonth() { return endMonth; }
    public void setEndMonth(String endMonth) { this.endMonth = endMonth; }

    public List<Map<String, String>> getColumns() { return columns; }
    public void setColumns(List<Map<String, String>> columns) { this.columns = columns; }

    public List<Map<String, Object>> getRows() { return rows; }
    public void setRows(List<Map<String, Object>> rows) { this.rows = rows; }

    public String getDataStatus() { return dataStatus; }
    public void setDataStatus(String dataStatus) { this.dataStatus = dataStatus; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
