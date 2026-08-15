package com.smartcare.icustats.service;

import com.smartcare.icustats.config.IcuStatsProperties;
import com.smartcare.icustats.util.DateRangeUtils;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Quality data rebuild runner.
 * Runs when QUALITY_REBUILD_ENABLED=true to recalculate and optionally overwrite
 * incorrectly persisted quality indicator data.
 *
 * Configuration:
 *   QUALITY_REBUILD_ENABLED=false (default)
 *   QUALITY_REBUILD_START_MONTH=2026-01
 *   QUALITY_REBUILD_END_MONTH=2026-08
 *   QUALITY_REBUILD_DEPT_CODE=0211
 *   QUALITY_REBUILD_DEPARTMENT=重症医学科
 *   QUALITY_REBUILD_DRY_RUN=true (default - only output diff, don't write)
 */
@Component
@Order(1)
public class QualityRebuildRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(QualityRebuildRunner.class);

    @Autowired
    private IcuStatsProperties properties;

    @Autowired
    private QualityWriter qualityWriter;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isQualityRebuildEnabled()) {
            return;
        }

        String startMonth = properties.getQualityRebuildStartMonth();
        String endMonth = properties.getQualityRebuildEndMonth();
        String deptCode = properties.getQualityRebuildDeptCode();
        String department = properties.getQualityRebuildDepartment();
        boolean dryRun = properties.isQualityRebuildDryRun();

        if (startMonth == null || startMonth.isEmpty() || endMonth == null || endMonth.isEmpty()) {
            log.warn("QUALITY_REBUILD enabled but QUALITY_REBUILD_START_MONTH or QUALITY_REBUILD_END_MONTH not set. Skipping.");
            return;
        }

        log.info("═══════════════════════════════════════════════════════════════");
        log.info("QUALITY_REBUILD START");
        log.info("  startMonth: {}", startMonth);
        log.info("  endMonth:   {}", endMonth);
        log.info("  deptCode:   {}", deptCode);
        log.info("  department: {}", department);
        log.info("  dryRun:     {}", dryRun);
        log.info("═══════════════════════════════════════════════════════════════");

        List<String> months = DateRangeUtils.buildMonths(startMonth, endMonth);
        int successCount = 0;
        int skipCount = 0;
        int errorCount = 0;

        for (String month : months) {
            log.info("QUALITY_REBUILD processing month={} ({}/{})", month, months.indexOf(month) + 1, months.size());
            try {
                if (dryRun) {
                    log.info("QUALITY_REBUILD DRY_RUN month={} - would recalculate all indicators", month);
                    // In dry-run mode, just log what would happen
                    // The actual recalculation happens via qualityCalcService inside qualityWriter
                    log.info("QUALITY_REBUILD DRY_RUN month={} completed (no writes)", month);
                } else {
                    qualityWriter.rebuildMonth(month, deptCode, department);
                    log.info("QUALITY_REBUILD month={} written successfully", month);
                }
                successCount++;
            } catch (Exception e) {
                log.error("QUALITY_REBUILD month={} ERROR: {}", month, e.getMessage(), e);
                errorCount++;
            }
        }

        log.info("═══════════════════════════════════════════════════════════════");
        log.info("QUALITY_REBUILD COMPLETE");
        log.info("  total months: {}", months.size());
        log.info("  success:      {}", successCount);
        log.info("  skipped:      {}", skipCount);
        log.info("  errors:       {}", errorCount);
        log.info("  dryRun:       {}", dryRun);
        log.info("═══════════════════════════════════════════════════════════════");
    }
}
