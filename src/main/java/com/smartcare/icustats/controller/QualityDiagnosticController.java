package com.smartcare.icustats.controller;

import com.smartcare.icustats.config.IcuStatsProperties;
import com.smartcare.icustats.config.CollectionConstants;
import com.smartcare.icustats.service.QualityCalcService;
import com.smartcare.icustats.util.DateRangeUtils;
import com.smartcare.icustats.dto.MonthRange;
import com.smartcare.icustats.util.NumberUtils;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Diagnostic endpoint for quality indicator calculation debugging.
 * Only accessible when QUALITY_DIAGNOSTIC_ENABLED=true.
 * Returns counts and field distributions, never patient names or MRNs.
 */
@RestController
@RequestMapping("/api/stats/quality")
public class QualityDiagnosticController {

    private static final Logger log = LoggerFactory.getLogger(QualityDiagnosticController.class);

    @Autowired
    private IcuStatsProperties properties;

    @Autowired
    private QualityCalcService qualityCalcService;

    @Autowired
    @Qualifier("smartCareMongoTemplate")
    private MongoTemplate smartCareMongo;

    @Autowired
    @Qualifier("dataCenterMongoTemplate")
    private MongoTemplate dataCenterMongo;

    /**
     * GET /api/stats/quality/diagnostic?month=2026-07&department=重症医学科
     * Returns diagnostic counts and status for each indicator.
     */
    @GetMapping("/diagnostic")
    public ResponseEntity<Map<String, Object>> diagnostic(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String department) {

        if (!properties.isQualityDiagnosticEnabled()) {
            return ResponseEntity.status(403).body(Map.of("error", "Diagnostic endpoint disabled. Set QUALITY_DIAGNOSTIC_ENABLED=true to enable."));
        }

        if (month == null || month.isEmpty()) {
            // Default to current month
            month = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        if (department == null || department.isEmpty()) {
            department = "重症医学科";
        }

        log.info("QUALITY_DIAGNOSTIC month={} department={} enableDeptFilter={}", month, department, properties.isEnableDeptFilter());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", month);
        result.put("department", department);
        result.put("departmentFilterEnabled", properties.isEnableDeptFilter());
        result.put("qualitySourceMode", properties.getQualitySourceMode());

        // Get patient counts
        List<Document> patients = qualityCalcService.getInIcuPatients(month, department);
        int patientCount = patients.size();
        long patientWithMrn = patients.stream()
                .filter(p -> p.getString("mrn") != null && !p.getString("mrn").trim().isEmpty())
                .count();

        result.put("patientCount", patientCount);
        result.put("patientWithMrnCount", patientWithMrn);

        // Collection counts
        MonthRange range = qualityCalcService.monthRange(month);
        Map<String, Long> collections = new LinkedHashMap<>();
        collections.put("patient", (long) patientCount);

        // Count orders in VI_ICU_ZYYZ
        long orderCount = dataCenterMongo.count(
                new Query(Criteria.where("orderTime").gte(range.getStartDate()).lte(range.getEndDate())),
                CollectionConstants.VI_ICU_ZYYZ);
        collections.put("VI_ICU_ZYYZ", orderCount);

        // Count tubeExe records
        long tubeCount = smartCareMongo.count(
                new Query(Criteria.where("endTime").gte(range.getStartDate()).lte(range.getEndDate())),
                CollectionConstants.TUBE_EXE);
        collections.put("tubeExe", tubeCount);

        // Count score records
        List<String> pids = patients.stream()
                .map(p -> String.valueOf(p.get("_id")))
                .filter(pid -> !pid.isEmpty() && !"null".equals(pid))
                .collect(java.util.stream.Collectors.toList());
        long scoreCount = pids.isEmpty() ? 0 : smartCareMongo.count(
                new Query(Criteria.where("pid").in(pids).and("scoreType").is("apacheII").and("valid").is(true)),
                CollectionConstants.SCORE);
        collections.put("score", scoreCount);

        result.put("collections", collections);

        // Per-indicator diagnostics
        Map<String, Object> indicators = new LinkedHashMap<>();

        // Shock bundle
        Map<String, Object> sb = qualityCalcService.calcShockBundle(month, department);
        indicators.put("shockBundleRate", buildIndicatorDiag(sb, "shockBundleRate"));

        // DVT
        Map<String, Object> dvt = qualityCalcService.calcDVT(month, department);
        indicators.put("dvtRate", buildIndicatorDiag(dvt, "dvtRate"));

        // Extubation
        Map<String, Object> ext = qualityCalcService.calcExtubation(month, department);
        Map<String, Object> unplanned = getMapEntry(ext, "unplannedExtubationRate");
        Map<String, Object> reintub = getMapEntry(ext, "reintubation48hRate");
        indicators.put("unplannedExtubationRate", buildIndicatorDiag(unplanned, "unplannedExtubationRate"));
        indicators.put("reintubation48hRate", buildIndicatorDiag(reintub, "reintubation48hRate"));

        // 48h return
        Map<String, Object> ret = qualityCalcService.calc48hReturn(month, department);
        indicators.put("icuReturn48hRate", buildIndicatorDiag(ret, "icuReturn48hRate"));

        // Shock ultrasound
        Map<String, Object> su = qualityCalcService.calcShockUltrasound(month, department);
        indicators.put("shockUltrasoundRate", buildIndicatorDiag(su, "shockUltrasoundRate"));

        // Shock hemodynamic
        Map<String, Object> sh = qualityCalcService.calcShockHemodynamic(month, department);
        indicators.put("shockHemodynamicRate", buildIndicatorDiag(sh, "shockHemodynamicRate"));

        // ARDS
        Map<String, Object> ards = qualityCalcService.calcARDS(month, department);
        indicators.put("ardsRate", buildIndicatorDiag(ards, "ardsRate"));

        // EN48h
        Map<String, Object> en = qualityCalcService.calcEN48h(month, department);
        indicators.put("en48hRate", buildIndicatorDiag(en, "en48hRate"));

        // Pain
        Map<String, Object> pain = qualityCalcService.calcPain(month, department);
        indicators.put("painRate", buildIndicatorDiag(pain, "painRate"));

        // Sedation
        Map<String, Object> sed = qualityCalcService.calcSedation(month, department);
        indicators.put("sedationRate", buildIndicatorDiag(sed, "sedationRate"));

        // Rescue
        Map<String, Object> rs = qualityCalcService.calcRescue(month);
        indicators.put("rescueSuccessRate", buildIndicatorDiag(rs, "rescueSuccessRate"));

        // Brain injury
        Map<String, Object> bi = qualityCalcService.calcBrainInjury(month, department);
        indicators.put("acuteBrainInjuryRate", buildIndicatorDiag(bi, "acuteBrainInjuryRate"));

        // Apache rates
        Map<String, Object> apache = qualityCalcService.calcApacheRates(month, department);
        indicators.put("apacheGte15Rate", buildIndicatorDiag(getMapEntry(apache, "apacheGte15Rate"), "apacheGte15Rate"));
        indicators.put("apacheLt15Rate", buildIndicatorDiag(getMapEntry(apache, "apacheLt15Rate"), "apacheLt15Rate"));
        indicators.put("apacheScoreRate", buildIndicatorDiag(getMapEntry(apache, "apacheScoreRate"), "apacheScoreRate"));

        // Mortality
        Map<String, Object> mort = qualityCalcService.calcMortality(month, department);
        indicators.put("predictedMortalityRate", buildIndicatorDiag(getMapEntry(mort, "predictedMortalityRate"), "predictedMortalityRate"));
        indicators.put("standardizedMortalityIndex", buildIndicatorDiag(getMapEntry(mort, "standardizedMortalityIndex"), "standardizedMortalityIndex"));

        result.put("indicators", indicators);

        return ResponseEntity.ok(result);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildIndicatorDiag(Map<String, Object> calcResult, String indicatorKey) {
        Map<String, Object> diag = new LinkedHashMap<>();
        Object num = calcResult.get("num");
        Object denom = calcResult.get("denom");
        diag.put("numerator", num != null ? num : 0);
        diag.put("denominator", denom != null ? denom : 0);

        long numVal = num instanceof Number ? ((Number) num).longValue() : 0;
        long denomVal = denom instanceof Number ? ((Number) denom).longValue() : 0;

        if (denomVal == 0 && numVal == 0) {
            diag.put("status", "no_data");
        } else if (denomVal == 0) {
            diag.put("status", "ZERO_DENOMINATOR");
        } else {
            diag.put("status", "ok");
        }

        // Include additional diagnostic fields if present
        if (calcResult.containsKey("death")) diag.put("death", calcResult.get("death"));
        if (calcResult.containsKey("terminal")) diag.put("terminal", calcResult.get("terminal"));
        if (calcResult.containsKey("rescueCount")) diag.put("rescueCount", calcResult.get("rescueCount"));

        return diag;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMapEntry(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Map) return (Map<String, Object>) val;
        return Collections.emptyMap();
    }
}
