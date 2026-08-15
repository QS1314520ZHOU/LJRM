package com.smartcare.icustats.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for icu-stats.
 * Maps to "icu-stats.*" in application.yml and environment variables.
 */
@Component
@ConfigurationProperties(prefix = "icu-stats")
public class IcuStatsProperties {

    /**
     * Whether to filter patients by department.
     * When false, all ICU patients are included regardless of department field.
     * Env: ENABLE_DEPT_FILTER (default false)
     */
    private boolean enableDeptFilter = false;

    /**
     * Quality data source mode: auto, live, stored.
     * - live: always calculate in real-time, ignore stored data
     * - stored: use persisted doctorQuality/doctorQC data
     * - auto: use stored if complete, fallback to live if invalid
     * Env: QUALITY_SOURCE_MODE (default auto)
     */
    private String qualitySourceMode = "auto";

    /**
     * Whether the quality diagnostic endpoint is enabled.
     * Env: QUALITY_DIAGNOSTIC_ENABLED (default false)
     */
    private boolean qualityDiagnosticEnabled = false;

    /**
     * Whether the quality rebuild runner is enabled.
     * Env: QUALITY_REBUILD_ENABLED (default false)
     */
    private boolean qualityRebuildEnabled = false;

    /**
     * Start month for quality rebuild (yyyy-MM).
     * Env: QUALITY_REBUILD_START_MONTH
     */
    private String qualityRebuildStartMonth;

    /**
     * End month for quality rebuild (yyyy-MM).
     * Env: QUALITY_REBUILD_END_MONTH
     */
    private String qualityRebuildEndMonth;

    /**
     * Department code for quality rebuild.
     * Env: QUALITY_REBUILD_DEPT_CODE (default 0211)
     */
    private String qualityRebuildDeptCode = "0211";

    /**
     * Department name for quality rebuild.
     * Env: QUALITY_REBUILD_DEPARTMENT (default 重症医学科)
     */
    private String qualityRebuildDepartment = "重症医学科";

    /**
     * Dry run mode for rebuild - only output diff, don't write to DB.
     * Env: QUALITY_REBUILD_DRY_RUN (default true)
     */
    private boolean qualityRebuildDryRun = true;

    // Getters and setters

    public boolean isEnableDeptFilter() {
        return enableDeptFilter;
    }

    public void setEnableDeptFilter(boolean enableDeptFilter) {
        this.enableDeptFilter = enableDeptFilter;
    }

    public String getQualitySourceMode() {
        return qualitySourceMode;
    }

    public void setQualitySourceMode(String qualitySourceMode) {
        this.qualitySourceMode = qualitySourceMode;
    }

    public boolean isQualityDiagnosticEnabled() {
        return qualityDiagnosticEnabled;
    }

    public void setQualityDiagnosticEnabled(boolean qualityDiagnosticEnabled) {
        this.qualityDiagnosticEnabled = qualityDiagnosticEnabled;
    }

    public boolean isQualityRebuildEnabled() {
        return qualityRebuildEnabled;
    }

    public void setQualityRebuildEnabled(boolean qualityRebuildEnabled) {
        this.qualityRebuildEnabled = qualityRebuildEnabled;
    }

    public String getQualityRebuildStartMonth() {
        return qualityRebuildStartMonth;
    }

    public void setQualityRebuildStartMonth(String qualityRebuildStartMonth) {
        this.qualityRebuildStartMonth = qualityRebuildStartMonth;
    }

    public String getQualityRebuildEndMonth() {
        return qualityRebuildEndMonth;
    }

    public void setQualityRebuildEndMonth(String qualityRebuildEndMonth) {
        this.qualityRebuildEndMonth = qualityRebuildEndMonth;
    }

    public String getQualityRebuildDeptCode() {
        return qualityRebuildDeptCode;
    }

    public void setQualityRebuildDeptCode(String qualityRebuildDeptCode) {
        this.qualityRebuildDeptCode = qualityRebuildDeptCode;
    }

    public String getQualityRebuildDepartment() {
        return qualityRebuildDepartment;
    }

    public void setQualityRebuildDepartment(String qualityRebuildDepartment) {
        this.qualityRebuildDepartment = qualityRebuildDepartment;
    }

    public boolean isQualityRebuildDryRun() {
        return qualityRebuildDryRun;
    }

    public void setQualityRebuildDryRun(boolean qualityRebuildDryRun) {
        this.qualityRebuildDryRun = qualityRebuildDryRun;
    }
}
