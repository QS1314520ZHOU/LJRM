package com.smartcare.icustats.service;

import com.smartcare.icustats.config.CollectionConstants;
import com.smartcare.icustats.config.IcuStatsProperties;
import com.smartcare.icustats.dto.MonthRange;
import com.smartcare.icustats.util.DateRangeUtils;
import com.smartcare.icustats.util.NumberUtils;
import com.smartcare.icustats.util.PatientUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * QualityService - faithful Java migration of Node.js qualityService.js
 * Handles ICU quality indicators: 27 specs with computed, calculated fallback, and set-based categories.
 */
@Service
public class QualityService {

    private static final Logger log = LoggerFactory.getLogger(QualityService.class);
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter YYYY_MM = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int APACHE_UNSCORED_ORDER = 2;
    private static final long HOURS_48_MS = 48L * 60 * 60 * 1000;

    // ════════════════════════════════════════════════════════════════════
    // Constants
    // ════════════════════════════════════════════════════════════════════

    private static final String[] QUALITY_DEPARTMENT_FIELDS = {
            "department", "deptName", "wardName", "inDeptName", "currentDeptName", "unitName", "dept"
    };

    private static final List<String> QUALITY_DEPT_CODES = Arrays.asList("0211");

    private static final Map<String, Object> SUMMARY_COLUMNS = columnMap(
            "index", "序号", "item", "指标", "value", "数值", "action", "操作");

    private static final List<Map<String, String>> PATIENT_DETAIL_COLUMNS = Arrays.asList(
            Map.of("key", "index", "title", "序号"),
            Map.of("key", "statMonth", "title", "统计月份"),
            Map.of("key", "department", "title", "科室"),
            Map.of("key", "bedNo", "title", "床号"),
            Map.of("key", "name", "title", "姓名"),
            Map.of("key", "age", "title", "年龄"),
            Map.of("key", "hospitalNo", "title", "住院号"),
            Map.of("key", "icuAdmissionTime", "title", "入科时间"),
            Map.of("key", "icuDischargeTime", "title", "出科时间"),
            Map.of("key", "icuDays", "title", "在科天数"),
            Map.of("key", "admissionDoctor", "title", "收治医生"),
            Map.of("key", "attendingDoctor", "title", "管床医生"),
            Map.of("key", "admissionSource", "title", "入科来源"),
            Map.of("key", "dischargeType", "title", "出科类型"),
            Map.of("key", "transferDept", "title", "转出科室"),
            Map.of("key", "diagnosis", "title", "临床诊断"));

    private static final List<Map<String, String>> OCCUPIED_BED_DAY_COLUMNS;
    private static final List<Map<String, String>> APACHE_DETAIL_COLUMNS;
    private static final List<Map<String, String>> MORTALITY_DETAIL_COLUMNS;
    private static final List<Map<String, String>> RESCUE_DETAIL_COLUMNS;

    static {
        List<Map<String, String>> occ = new ArrayList<>(PATIENT_DETAIL_COLUMNS.subList(0, 9));
        occ.add(Map.of("key", "occupiedBedDays", "title", "占床日数"));
        occ.addAll(PATIENT_DETAIL_COLUMNS.subList(9, PATIENT_DETAIL_COLUMNS.size()));
        OCCUPIED_BED_DAY_COLUMNS = Collections.unmodifiableList(occ);

        List<Map<String, String>> ap = new ArrayList<>(PATIENT_DETAIL_COLUMNS.subList(0, 7));
        ap.add(Map.of("key", "apacheScore", "title", "APACHEⅡ分数"));
        ap.addAll(PATIENT_DETAIL_COLUMNS.subList(7, PATIENT_DETAIL_COLUMNS.size()));
        APACHE_DETAIL_COLUMNS = Collections.unmodifiableList(ap);

        List<Map<String, String>> mort = new ArrayList<>(PATIENT_DETAIL_COLUMNS.subList(0, 7));
        mort.add(Map.of("key", "apacheScore", "title", "APACHEⅡ分数"));
        mort.add(Map.of("key", "predictedMortality", "title", "预计病死率"));
        mort.addAll(PATIENT_DETAIL_COLUMNS.subList(7, PATIENT_DETAIL_COLUMNS.size()));
        MORTALITY_DETAIL_COLUMNS = Collections.unmodifiableList(mort);

        List<Map<String, String>> res = new ArrayList<>(PATIENT_DETAIL_COLUMNS.subList(0, 7));
        res.add(Map.of("key", "rescueCount", "title", "抢救次数"));
        res.addAll(PATIENT_DETAIL_COLUMNS.subList(7, PATIENT_DETAIL_COLUMNS.size()));
        RESCUE_DETAIL_COLUMNS = Collections.unmodifiableList(res);
    }

    // ── Quality Specs (27 indicators) ─────────────────────────────────
    // Each spec: id, key, code, name, type, newCode (optional)

    private static final List<Map<String, Object>> QUALITY_SPECS;
    private static final Map<String, Map<String, Object>> SPEC_BY_KEY = new LinkedHashMap<>();
    private static final Map<String, Map<String, Object>> SPEC_BY_CODE = new LinkedHashMap<>();

    static {
        List<Map<String, Object>> specs = new ArrayList<>();
        specs.add(spec(1, "newAdmissions", "ICUShouZhiHuanZheTotalNum", "ICUShouZhiHuanZheTotalNum", "本科新收患者数", "count"));
        specs.add(spec(2, "icuCensus", "ICUShouZhiNum", null, "同期ICU收治患者总数", "count"));
        specs.add(spec(3, "bedUsage", "BenKeShouChuangRiLv", "BenKeShouChuangRiLv", "本科床位使用率", "percent"));
        specs.add(spec(4, "avgLengthOfStay", "icu_pingjunzhuyuanr", null, "平均住院日数", "decimal"));
        specs.add(spec(5, "icuAdmissionRate", "ICUHuanZheShouZhiLv", null, "ICU患者收治率", "percent"));
        specs.add(spec(6, "icuBedDayRate", "ICUHuanZheShouZhiChuangRiLv", null, "ICU患者收治床日率", "percent"));
        specs.add(spec(7, "apacheGte15Rate", "ApacheUp15Lv", "ApacheUp15Lv", "APACHEII≥15患者收治率", "percent"));
        specs.add(spec(8, "apacheLt15Rate", "ApacheDown15Lv", null, "APACHEII<15患者收治率", "percent"));
        specs.add(spec(9, "apacheScoreRate", "ApacheIIZongLv", null, "APACHEII评分率", "percent"));
        specs.add(spec(10, "shockBundleRate", "Bundle1Lv", "Bundle1Lv", "感染性休克集束化治疗完成率", "percent"));
        specs.add(spec(11, "antibioticCultureRate", "KangJunLv", "KangJunLv", "抗菌药物治疗前病原学送检率", "percent"));
        specs.add(spec(12, "dvtRate", "DVTLv", "DVTLv", "深静脉血栓（DVT）预防率", "percent"));
        specs.add(spec(13, "predictedMortalityRate", "YuJiDeadLv", null, "ICU患者预计病死率", "percent"));
        specs.add(spec(14, "apacheLt15DeathRate", "deathApacheLte15_constant", null, "APACHEII评分<15的死亡率", "percent"));
        specs.add(spec(15, "standardizedMortalityIndex", "ICUBiaoHuaDeadLv", "ICUBiaoHuaDeadLv", "ICU患者标化病死指数", "percent"));
        specs.add(spec(16, "unplannedExtubationRate", "ICUNoPlanQIGuanBaGuanLv", "ICUNoPlanQIGuanBaGuanLv", "ICU非计划气管插管拔管率", "percent"));
        specs.add(spec(17, "reintubation48hRate", "ICUQIGuanBaGuan48ChaGuanLv", "ICUQIGuanBaGuan48ChaGuanLv", "ICU气管插管拔管后48h内再插管率", "percent"));
        specs.add(spec(18, "unplannedIcuTransferRate", "NoPlanInICULv", "NoPlanInICULv", "非计划转入ICU率", "percent"));
        specs.add(spec(19, "icuReturn48hRate", "OutICU48AgainInLv", "OutICU48AgainInLv", "转出ICU后48h内重返率", "percent"));
        specs.add(spec(20, "shockUltrasoundRate", "shock_ultrasound_screen", null, "休克患者超声筛查评估率", "percent"));
        specs.add(spec(21, "shockHemodynamicRate", "shock_blood_flow_detection", null, "休克患者血流动力学指标监测率", "percent"));
        specs.add(spec(22, "ardsRate", "ards_constant", "ARDSLv", "急性呼吸窘迫综合征（ARDS）", "percent"));
        specs.add(spec(23, "en48hRate", "en_start_in48_constant", "StartEnIn48Lv", "48H肠内营养（EN）启动率", "percent"));
        specs.add(spec(24, "painRate", "icu_analgesia_constant", "PAINLv", "ICU镇痛评估率", "percent"));
        specs.add(spec(25, "sedationRate", "icu_calm_constant", "RASSLv", "ICU镇静评估率", "percent"));
        specs.add(spec(26, "rescueSuccessRate", "rescue_success", null, "抢救成功率", "percent"));
        specs.add(spec(27, "acuteBrainInjuryRate", "icu_acute_brain_injury", "ICUBrainHurtLv", "ICU急性脑损伤患者意识评估率", "percent"));
        QUALITY_SPECS = Collections.unmodifiableList(specs);
        for (Map<String, Object> s : specs) {
            SPEC_BY_KEY.put((String) s.get("key"), s);
            SPEC_BY_CODE.put((String) s.get("code"), s);
        }
    }

    private static final Set<String> COMPUTED_RATIO_KEYS = new LinkedHashSet<>(Arrays.asList(
            "bedUsage", "avgLengthOfStay", "icuAdmissionRate", "icuBedDayRate", "antibioticCultureRate"));

    private static final Set<String> CALCULATED_FALLBACK_KEYS = new LinkedHashSet<>(Arrays.asList(
            "shockBundleRate", "apacheGte15Rate", "apacheLt15Rate", "apacheScoreRate",
            "predictedMortalityRate", "apacheLt15DeathRate", "shockUltrasoundRate", "shockHemodynamicRate",
            "ardsRate", "en48hRate", "painRate", "sedationRate", "dvtRate",
            "rescueSuccessRate", "acuteBrainInjuryRate", "standardizedMortalityIndex",
            "unplannedExtubationRate", "reintubation48hRate", "unplannedIcuTransferRate", "icuReturn48hRate"));

    private static final Set<String> APACHE_DETAIL_KEYS = new LinkedHashSet<>(Arrays.asList(
            "apacheGte15Rate", "apacheLt15Rate", "apacheScoreRate"));

    private static final Set<String> ICU_CENSUS_DENOMINATOR_KEYS = new LinkedHashSet<>(Arrays.asList(
            "apacheGte15Rate", "apacheLt15Rate", "apacheScoreRate", "dvtRate",
            "predictedMortalityRate", "standardizedMortalityIndex",
            "unplannedIcuTransferRate", "painRate", "sedationRate", "acuteBrainInjuryRate"));

    private static final Set<String> SET_BASED_KEYS = new LinkedHashSet<>(Arrays.asList(
            "dvtRate", "en48hRate", "painRate", "sedationRate", "acuteBrainInjuryRate"));

    private static final Set<String> CALCULATED_SUMMARY_KEYS = new LinkedHashSet<>(Arrays.asList(
            "predictedMortalityRate", "standardizedMortalityIndex", "unplannedExtubationRate",
            "reintubation48hRate", "unplannedIcuTransferRate", "icuReturn48hRate",
            "shockBundleRate", "shockUltrasoundRate", "shockHemodynamicRate",
            "ardsRate", "rescueSuccessRate", "apacheLt15DeathRate"));

    private final MongoTemplate smartCareMongo;
    private final MongoTemplate dataCenterMongo;
    private final IcuStatsProperties properties;
    private final QualityCalcService qualityCalcService;

    @Autowired
    public QualityService(
            @Qualifier("smartCareMongoTemplate") MongoTemplate smartCareMongo,
            @Qualifier("dataCenterMongoTemplate") MongoTemplate dataCenterMongo,
            IcuStatsProperties properties,
            QualityCalcService qualityCalcService) {
        this.smartCareMongo = smartCareMongo;
        this.dataCenterMongo = dataCenterMongo;
        this.properties = properties;
        this.qualityCalcService = qualityCalcService;
    }

    // ════════════════════════════════════════════════════════════════════
    // Public API
    // ════════════════════════════════════════════════════════════════════

    public Map<String, Object> getQualityStats(String year, String startMonth, String endMonth, String department) {
        List<String> months = resolveMonths(year, startMonth, endMonth);
        String resolvedStartMonth = months.get(0);
        String resolvedEndMonth = months.get(months.size() - 1);

        List<Document> docs = fetchQualityDocs(months, department);
        List<Document> qcDocs = fetchQualityQcDocs(months, department);
        List<Document> items = fetchItemsByQualityIds(extractIds(docs));
        List<Document> qcItems = fetchQcItemsByQualityIds(extractIds(qcDocs));

        Map<String, Map<String, Integer>> basicStats = getBasicMonthlyStats(months, department);
        Map<String, Map<String, Object>> computedStats = getComputedMonthlyStats(months, department, basicStats);
        Map<String, Map<String, Object>> calculatedStats = getCalculatedMonthlyStats(months, department, basicStats);

        Map<String, List<Document>> itemsByQualityId = buildItemsByQualityId(items);
        Map<String, List<Document>> qcItemsByQualityId = buildItemsByQualityId(qcItems);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> spec : QUALITY_SPECS) {
            String key = (String) spec.get("key");
            String code = (String) spec.get("code");
            String type = (String) spec.get("type");

            if ("newAdmissions".equals(key) || "icuCensus".equals(key)) {
                rows.add(buildBasicCountRow(spec, months, basicStats));
            } else if (COMPUTED_RATIO_KEYS.contains(key)) {
                rows.add(buildComputedIndicatorRow(spec, months, computedStats));
            } else if (CALCULATED_FALLBACK_KEYS.contains(key)) {
                Map<String, List<Document>> merged = mergeItems(itemsByQualityId, qcItemsByQualityId);
                rows.add(buildCalculatedFallbackRow(spec, months, docs, qcDocs, merged, basicStats, calculatedStats));
            } else {
                Map<String, List<Document>> merged = mergeItems(itemsByQualityId, qcItemsByQualityId);
                rows.add(buildIndicatorRow(spec, months, docs, qcDocs, merged, basicStats));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("indicators", rows);
        result.put("months", months);
        result.put("startMonth", resolvedStartMonth);
        result.put("endMonth", resolvedEndMonth);
        result.put("department", department != null ? department : "");
        return result;
    }

    public Map<String, Object> getQualityDetail(String indicatorKey, String year, String startMonth, String endMonth, String department, String itemOrder) {
        Map<String, Object> spec = SPEC_BY_KEY.get(indicatorKey);
        if (spec == null) throw new IllegalArgumentException("质控指标不支持");

        List<String> months = resolveMonths(year, startMonth, endMonth);
        String startM = months.get(0);
        String endM = months.get(months.size() - 1);
        Integer itemOrd = parseItemOrder(itemOrder);

        List<Document> docs = fetchQualityDocs(months, department);
        List<Document> qcDocs = fetchQualityQcDocs(months, department);

        List<Document> indicatorDocs = filterByCode(docs, (String) spec.get("code"));
        List<Document> items = fetchItemsByQualityIds(extractIds(indicatorDocs));
        List<Document> detailRows = fetchDetailRowsByItemIds(extractItemIds(items));

        if ((indicatorDocs.isEmpty() || items.isEmpty()) && spec.get("newCode") != null) {
            List<Document> qcIndicatorDocs = filterByCode(qcDocs, (String) spec.get("newCode"));
            List<Document> qcItems = fetchQcItemsByQualityIds(extractIds(qcIndicatorDocs));
            if (!qcIndicatorDocs.isEmpty() && !qcItems.isEmpty()) {
                indicatorDocs = qcIndicatorDocs;
                items = qcItems;
                detailRows = fetchQcDetailRowsByItemIds(extractItemIds(items));
            }
        }

        Map<String, List<Document>> itemsByQualityId = buildItemsByQualityId(items);
        Map<String, List<Document>> detailsByItemId = buildDetailsByItemId(detailRows);
        String key = (String) spec.get("key");
        String specName = (String) spec.get("name");

        // ── COMPUTED_RATIO_KEYS branch ─────────────────────────────────
        if (COMPUTED_RATIO_KEYS.contains(key)) {
            if (itemOrd != null) {
                if (Arrays.asList("bedUsage", "avgLengthOfStay", "icuBedDayRate").contains(key) && itemOrd == 0) {
                    List<Map<String, Object>> rows = getOccupiedBedDayRows(months, department);
                    return detailResult(spec, OCCUPIED_BED_DAY_COLUMNS, rows);
                }
                if (("avgLengthOfStay".equals(key) && itemOrd == 1)
                        || ("icuAdmissionRate".equals(key) && itemOrd == 0)) {
                    List<Map<String, Object>> rows = getBasicPatientRows("icuCensus", months, department);
                    return detailResult(spec, PATIENT_DETAIL_COLUMNS, rows);
                }
                return detailResult(spec, PATIENT_DETAIL_COLUMNS, Collections.emptyList());
            }
            List<Map<String, Object>> rows = buildComputedSummaryRows(spec, months, startM, endM, department);
            return detailResult(spec, summaryColumnsList(), rows);
        }

        // ── APACHE detail keys summary branch ──────────────────────────
        if (APACHE_DETAIL_KEYS.contains(key) && itemOrd == null) {
            List<Map<String, Object>> rows = buildCalculatedSummaryRows(spec, months, department);
            return detailResult(spec, summaryColumnsList(), rows);
        }

        // ── Calculated summary branch ──────────────────────────────────
        if (CALCULATED_SUMMARY_KEYS.contains(key) && itemOrd == null) {
            List<Map<String, Object>> rows = buildCalculatedSummaryRows(spec, months, department);
            return detailResult(spec, summaryColumnsList(), rows);
        }

        // ── Set-based summary branch ───────────────────────────────────
        if (SET_BASED_KEYS.contains(key) && itemOrd == null) {
            List<Map<String, Object>> rows = buildSetBasedSummaryRows(spec, months, startM, endM, department);
            return detailResult(spec, summaryColumnsList(), rows);
        }

        // ── Calculated fallback without items ──────────────────────────
        if (CALCULATED_FALLBACK_KEYS.contains(key) && items.isEmpty() && itemOrd == null) {
            List<Map<String, Object>> rows = buildCalculatedSummaryRows(spec, months, department);
            return detailResult(spec, summaryColumnsList(), rows);
        }

        // ── APACHE detail patient rows ─────────────────────────────────
        if (APACHE_DETAIL_KEYS.contains(key) && itemOrd != null) {
            List<Map<String, Object>> rows = getApachePatientRows(key, itemOrd, months, department);
            List<Map<String, String>> cols = itemOrd == APACHE_UNSCORED_ORDER ? PATIENT_DETAIL_COLUMNS : APACHE_DETAIL_COLUMNS;
            return detailResult(spec, cols, rows);
        }

        // ── apacheLt15DeathRate detail ─────────────────────────────────
        if ("apacheLt15DeathRate".equals(key) && itemOrd != null) {
            List<Map<String, Object>> rows = getApachePatientRows(key, itemOrd, months, department);
            return detailResult(spec, APACHE_DETAIL_COLUMNS, rows);
        }

        // ── Calculated fallback detail with itemOrder ──────────────────
        if (CALCULATED_FALLBACK_KEYS.contains(key) && itemOrd != null) {
            return buildCalculatedDetailRows(spec, key, itemOrd, months, startM, endM, department);
        }

        // ── Occupied bed day fallback ──────────────────────────────────
        if (isOccupiedBedDayIndicator(spec) && (itemOrd == 0 || ("avgLengthOfStay".equals(key) && itemOrd == 1))) {
            List<Map<String, Object>> rows = getOccupiedBedDayRows(months, department);
            return detailResult(spec, OCCUPIED_BED_DAY_COLUMNS, rows);
        }

        // ── Set-based detail with itemOrder ────────────────────────────
        if (itemOrd != null && SET_BASED_KEYS.contains(key)) {
            return buildSetBasedDetailRows(spec, key, itemOrd, months, startM, endM, department);
        }

        // ── Extubation / Return detail with itemOrder ──────────────────
        if (itemOrd != null && Arrays.asList("unplannedExtubationRate", "reintubation48hRate", "icuReturn48hRate").contains(key)) {
            return buildExtubationReturnDetailRows(spec, key, itemOrd, months, department);
        }

        // ── Count type fallback ────────────────────────────────────────
        if ("count".equals(spec.get("type"))) {
            List<Document> firstItems = indicatorDocs.stream()
                    .flatMap(doc -> itemsByQualityId.getOrDefault(doc.get("_id").toString(), Collections.<Document>emptyList()).stream())
                    .filter(item -> NumberUtils.safeNumber(item.get("order")) == 0)
                    .collect(Collectors.toList());
            List<Document> firstDetailRows = firstItems.stream()
                    .flatMap(item -> detailsByItemId.getOrDefault(item.get("_id").toString(), Collections.<Document>emptyList()).stream())
                    .collect(Collectors.toList());
            if (firstDetailRows.isEmpty() && ("newAdmissions".equals(key) || "icuCensus".equals(key))) {
                List<Map<String, Object>> rows = getBasicPatientRows(key, months, department);
                return detailResult(spec, PATIENT_DETAIL_COLUMNS, rows);
            }
            List<Map<String, Object>> rows = getPatientsByDetailPids(firstDetailRows, months, department, false);
            return detailResult(spec, PATIENT_DETAIL_COLUMNS, rows);
        }

        // ── itemOrder detail for doctorQuality-based indicators ────────
        if (itemOrd != null) {
            List<Document> matchedItems = indicatorDocs.stream()
                    .flatMap(doc -> itemsByQualityId.getOrDefault(doc.get("_id").toString(), Collections.<Document>emptyList()).stream())
                    .filter(item -> NumberUtils.safeNumber(item.get("order")) == itemOrd)
                    .collect(Collectors.toList());
            List<Document> matchedDetails = matchedItems.stream()
                    .flatMap(item -> detailsByItemId.getOrDefault(item.get("_id").toString(), Collections.<Document>emptyList()).stream())
                    .collect(Collectors.toList());
            boolean includeApache = APACHE_DETAIL_KEYS.contains(key);
            List<Map<String, Object>> rows = getPatientsByDetailPids(matchedDetails, months, department, includeApache);
            return detailResult(spec, includeApache ? APACHE_DETAIL_COLUMNS : PATIENT_DETAIL_COLUMNS, rows);
        }

        // ── Default: summary rows from items ───────────────────────────
        List<Map<String, Object>> summaryRows = buildDefaultSummaryRows(spec, items, indicatorDocs, itemsByQualityId, detailsByItemId, months, startM, endM, department);
        if (summaryRows.isEmpty()) {
            return detailResult(spec, PATIENT_DETAIL_COLUMNS, Collections.emptyList());
        }
        return detailResult(spec, summaryColumnsList(), summaryRows);
    }

    // ════════════════════════════════════════════════════════════════════
    // Month resolution
    // ════════════════════════════════════════════════════════════════════

    private List<String> resolveMonths(String year, String startMonth, String endMonth) {
        if (year != null && !year.isEmpty()) {
            int y = DateRangeUtils.validateYear(year);
            return DateRangeUtils.getYearMonths(y);
        }
        if (startMonth == null || endMonth == null) throw new IllegalArgumentException("月份参数缺失");
        return DateRangeUtils.buildMonths(startMonth, endMonth);
    }

    private Integer parseItemOrder(String itemOrder) {
        if (itemOrder == null || itemOrder.isEmpty()) return null;
        try { return Integer.parseInt(itemOrder); } catch (NumberFormatException e) { return null; }
    }

    // ════════════════════════════════════════════════════════════════════
    // Database fetch helpers
    // ════════════════════════════════════════════════════════════════════

    private List<Document> fetchQualityDocs(List<String> months, String department) {
        Set<Integer> years = new LinkedHashSet<>();
        Set<Integer> monthNums = new LinkedHashSet<>();
        for (String m : months) {
            years.add(Integer.parseInt(m.substring(0, 4)));
            monthNums.add(Integer.parseInt(m.substring(5, 7)));
        }
        List<String> codes = QUALITY_SPECS.stream().map(s -> (String) s.get("code")).collect(Collectors.toList());

        Document filter = new Document("yearFlag", new Document("$in", years))
                .append("indicatorCode", new Document("$in", codes));
        // Dept code filter if enabled
        String deptCode = resolveDepartmentCode(department);
        if (deptCode != null && !deptCode.isEmpty()) {
            filter.append("deptCode", deptCode);
        }

        List<Document> allDocs = smartCareMongo.find(new BasicQuery(filter), Document.class, CollectionConstants.DOCTOR_QUALITY);
        // Filter by month
        return allDocs.stream().filter(doc -> {
            Integer month = parseMonthFromFlag(doc.get("flag"));
            return month != null && monthNums.contains(month);
        }).collect(Collectors.toList());
    }

    private List<Document> fetchQualityQcDocs(List<String> months, String department) {
        Set<Integer> years = new LinkedHashSet<>();
        Set<Integer> monthNums = new LinkedHashSet<>();
        for (String m : months) {
            years.add(Integer.parseInt(m.substring(0, 4)));
            monthNums.add(Integer.parseInt(m.substring(5, 7)));
        }
        List<String> codes = QUALITY_SPECS.stream()
                .map(s -> (String) s.get("newCode"))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Document filter = new Document("yearFlag", new Document("$in", years))
                .append("indicatorCode", new Document("$in", codes));
        String deptCode = resolveDepartmentCode(department);
        if (deptCode != null && !deptCode.isEmpty()) {
            filter.append("deptCode", deptCode);
        }

        List<Document> allDocs = smartCareMongo.find(new BasicQuery(filter), Document.class, CollectionConstants.DOCTOR_QC);
        return allDocs.stream().filter(doc -> {
            Integer month = parseMonthFromFlag(doc.get("flag"));
            return month != null && monthNums.contains(month);
        }).collect(Collectors.toList());
    }

    private List<Document> fetchItemsByQualityIds(List<String> qualityIds) {
        if (qualityIds.isEmpty()) return Collections.emptyList();
        List<Object> ids = buildDualTypeIds(qualityIds);
        return smartCareMongo.find(
                new Query(Criteria.where("qualityId").in(ids)), Document.class, CollectionConstants.DOCTOR_QUALITY_ITEM);
    }

    private List<Document> fetchQcItemsByQualityIds(List<String> qualityIds) {
        if (qualityIds.isEmpty()) return Collections.emptyList();
        List<Object> ids = buildDualTypeIds(qualityIds);
        return smartCareMongo.find(
                new Query(Criteria.where("qualityId").in(ids)), Document.class, "doctorQCIData");
    }

    private List<Document> fetchDetailRowsByItemIds(List<String> itemIds) {
        if (itemIds.isEmpty()) return Collections.emptyList();
        List<Object> ids = buildDualTypeIds(itemIds);
        return smartCareMongo.find(
                new Query(Criteria.where("itemId").in(ids)), Document.class, CollectionConstants.DOCTOR_QUALITY_ITEM_DETAIL);
    }

    private List<Document> fetchQcDetailRowsByItemIds(List<String> itemIds) {
        if (itemIds.isEmpty()) return Collections.emptyList();
        List<Object> ids = buildDualTypeIds(itemIds);
        return smartCareMongo.find(
                new Query(Criteria.where("itemId").in(ids)), Document.class, "doctorQCIDetail");
    }

    private List<String> extractIds(List<Document> docs) {
        return docs.stream().map(d -> d.get("_id").toString()).collect(Collectors.toList());
    }

    private List<String> extractItemIds(List<Document> items) {
        return items.stream().map(d -> d.get("_id").toString()).collect(Collectors.toList());
    }

    /**
     * Build a list containing both String and ObjectId versions of each ID.
     * This ensures queries match regardless of whether the DB stores the field
     * as an ObjectId or a String representation.
     */
    private List<Object> buildDualTypeIds(List<String> stringIds) {
        List<Object> ids = new ArrayList<>(stringIds.size() * 2);
        for (String id : stringIds) {
            ids.add(id);
            if (ObjectId.isValid(id)) {
                ids.add(new ObjectId(id));
            }
        }
        return ids;
    }

    private List<Document> filterByCode(List<Document> docs, String code) {
        if (code == null) return Collections.emptyList();
        return docs.stream().filter(d -> code.equals(d.get("indicatorCode"))).collect(Collectors.toList());
    }

    private static final Map<String, String> DEFAULT_DEPT_CODE_MAP;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("重症医学科", "0211");
        m.put("ICU", "0211");
        DEFAULT_DEPT_CODE_MAP = Collections.unmodifiableMap(m);
    }

    private volatile Map<String, String> deptCodeMapCache;

    private Map<String, String> getDepartmentCodeMap() {
        if (deptCodeMapCache != null) return deptCodeMapCache;
        Map<String, String> result = new LinkedHashMap<>(DEFAULT_DEPT_CODE_MAP);
        String json = System.getenv("QUALITY_DEPT_CODE_MAP");
        if (json != null && !json.isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(json, Map.class);
                result.putAll(parsed);
            } catch (Exception e) {
                log.warn("QUALITY_DEPT_CODE_MAP JSON 解析失败，使用默认映射: {}", e.getMessage());
            }
        }
        deptCodeMapCache = Collections.unmodifiableMap(result);
        return deptCodeMapCache;
    }

    private String resolveDepartmentCode(String department) {
        Map<String, String> codeMap = getDepartmentCodeMap();
        if (department != null && !department.isEmpty() && codeMap.containsKey(department)) {
            return codeMap.get(department);
        }
        String envDefault = System.getenv("QUALITY_DEFAULT_DEPT_CODE");
        if (envDefault != null && !envDefault.isEmpty()) return envDefault;
        return codeMap.getOrDefault("重症医学科", "");
    }

    private Integer parseMonthFromFlag(Object flag) {
        if (flag == null) return null;
        String s = String.valueOf(flag);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2})").matcher(s);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    // ════════════════════════════════════════════════════════════════════
    // Monthly stats builders
    // ════════════════════════════════════════════════════════════════════

    private Map<String, Map<String, Integer>> getBasicMonthlyStats(List<String> months, String department) {
        Map<String, Map<String, Integer>> result = new LinkedHashMap<>();
        for (String monthKey : months) {
            MonthRange range = DateRangeUtils.getMonthRange(monthKey);
            int newAdmissions = countPatients(buildPatientFilter(
                    new Document("icuAdmissionTime", new Document("$gte", range.getStartDate()).append("$lte", range.getEndDate())),
                    department, properties.isEnableDeptFilter()));
            int icuCensus = countPatients(buildMonthlyOverlapFilter(range.getStartDate(), range.getEndDate(), department));

            Map<String, Integer> stats = new LinkedHashMap<>();
            stats.put("newAdmissions", newAdmissions);
            stats.put("icuCensus", icuCensus);
            result.put(monthKey, stats);
        }
        return result;
    }

    private Map<String, Map<String, Object>> getComputedMonthlyStats(List<String> months, String department, Map<String, Map<String, Integer>> basicStats) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (String monthKey : months) {
            MonthRange range = DateRangeUtils.getMonthRange(monthKey);
            int occupiedBedDays = calcOccupiedBedDayTotal(Collections.singletonList(monthKey), department);
            int configuredBedDays = calcConfiguredBedDayTotal(Collections.singletonList(monthKey), department);
            double hospitalAdmissions = getQualityDataValue(monthKey, "HosShouZhiHuanZheTotalNum", "同期医院收治患者总数");
            double hospitalBedDays = getQualityDataValue(monthKey, "HosShouZhiHuanZheTotalChuangRiNum", "同期医院患者收治总床日数");
            double antibioticCultureCases = getQualityDataValue(monthKey, "KangJunSongJianNum", "使用抗菌药物前病原学送检病例数");
            double antibioticTreatmentCases = getQualityDataValue(monthKey, "KangJunTotalNum", "同期使用抗菌药物治疗病例总数");

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("occupiedBedDays", occupiedBedDays);
            stats.put("configuredBedDays", configuredBedDays);
            stats.put("hospitalAdmissions", hospitalAdmissions);
            stats.put("hospitalBedDays", hospitalBedDays);
            stats.put("antibioticCultureCases", antibioticCultureCases);
            stats.put("antibioticTreatmentCases", antibioticTreatmentCases);
            stats.put("icuCensus", NumberUtils.safeNumber(basicStats.getOrDefault(monthKey, Collections.emptyMap()).get("icuCensus")));
            result.put(monthKey, stats);
        }
        return result;
    }

    private Map<String, Map<String, Object>> getCalculatedMonthlyStats(List<String> months, String department, Map<String, Map<String, Integer>> basicStats) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (String monthKey : months) {
            MonthRange range = DateRangeUtils.getMonthRange(monthKey);
            List<Document> patients = findPatients(buildMonthlyOverlapFilter(range.getStartDate(), range.getEndDate(), department));
            int icuCensus = (int) NumberUtils.safeNumber(basicStats.getOrDefault(monthKey, Collections.emptyMap()).get("icuCensus"));

            Map<String, Document> selectedScoreByPid = loadSelectedScoreMap(patients, range.getStartDate(), range.getEndDate());
            Map<String, Document> anyScoreByPid = loadAnyScoreMap(patients);

            int apacheLt15 = 0, apacheGte15 = 0, apacheLt15Death = 0, apacheScored = anyScoreByPid.size();
            double predictedMortalitySum = 0;
            for (Map.Entry<String, Document> e : selectedScoreByPid.entrySet()) {
                Document score = e.getValue();
                double total = getScoreTotal(score);
                if (total < 15) {
                    apacheLt15++;
                    String pid = e.getKey();
                    Optional<Document> matchedPatient = patients.stream()
                            .filter(p -> pid.equals(String.valueOf(p.get("_id"))))
                            .findFirst();
                    if (matchedPatient.isPresent()) {
                        String dt = getFirstValue(matchedPatient.get(), "dischargedType", "dischargeType");
                        if (dt.contains("死亡")) apacheLt15Death++;
                    }
                } else {
                    apacheGte15++;
                }
                Object apacheII = score.get("apacheII");
                if (apacheII instanceof Map) {
                    Object calDead = ((Map<?, ?>) apacheII).get("calDead");
                    if (calDead instanceof Map) {
                        predictedMortalitySum += NumberUtils.safeNumber(((Map<?, ?>) calDead).get("score"));
                    } else if (calDead != null) {
                        predictedMortalitySum += NumberUtils.safeNumber(calDead);
                    }
                }
            }

            long deathCount = patients.stream()
                    .filter(p -> getFirstValue(p, "dischargedType", "dischargeType").contains("死亡"))
                    .count();

            // Calculate non-apache indicators via QualityCalcService
            Map<String, Object> shockBundle = qualityCalcService.calcShockBundle(monthKey, department);
            Map<String, Object> dvt = qualityCalcService.calcDVT(monthKey, department);
            Map<String, Object> extubation = qualityCalcService.calcExtubation(monthKey, department);
            Map<String, Object> ret48h = qualityCalcService.calc48hReturn(monthKey, department);
            Map<String, Object> shockUltrasound = qualityCalcService.calcShockUltrasound(monthKey, department);
            Map<String, Object> shockHemodynamic = qualityCalcService.calcShockHemodynamic(monthKey, department);
            Map<String, Object> ards = qualityCalcService.calcARDS(monthKey, department);
            Map<String, Object> en48h = qualityCalcService.calcEN48h(monthKey, department);
            Map<String, Object> pain = qualityCalcService.calcPain(monthKey, department);
            Map<String, Object> sedation = qualityCalcService.calcSedation(monthKey, department);
            Map<String, Object> rescue = qualityCalcService.calcRescue(monthKey);
            Map<String, Object> brainInjury = qualityCalcService.calcBrainInjury(monthKey, department);

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("icuCensus", icuCensus);
            stats.put("apacheGte15", apacheGte15);
            stats.put("apacheLt15", apacheLt15);
            stats.put("apacheScored", apacheScored);
            stats.put("apacheUnscored", Math.max(0, icuCensus - apacheScored));
            stats.put("predictedMortalitySum", predictedMortalitySum);
            stats.put("predictedMortalityRate", icuCensus > 0 ? predictedMortalitySum / icuCensus : 0);
            stats.put("actualMortalityRate", icuCensus > 0 ? (double) deathCount / icuCensus : 0);
            stats.put("apacheLt15Death", apacheLt15Death);

            // Non-apache indicators
            stats.put("shockBundleNum", toLong(shockBundle.get("num")));
            stats.put("shockBundleDenom", toLong(shockBundle.get("denom")));
            stats.put("dvtNum", toLong(dvt.get("num")));
            stats.put("dvtDenom", toLong(dvt.get("denom")));
            @SuppressWarnings("unchecked")
            Map<String, Object> unplannedMap = (Map<String, Object>) extubation.get("unplannedExtubationRate");
            @SuppressWarnings("unchecked")
            Map<String, Object> reintubMap = (Map<String, Object>) extubation.get("reintubation48hRate");
            stats.put("unplannedExtubationNum", toLong(unplannedMap.get("num")));
            stats.put("unplannedExtubationDenom", toLong(unplannedMap.get("denom")));
            stats.put("reintubation48hNum", toLong(reintubMap.get("num")));
            stats.put("reintubation48hDenom", toLong(reintubMap.get("denom")));
            stats.put("icuReturn48hNum", toLong(ret48h.get("num")));
            stats.put("icuReturn48hDenom", toLong(ret48h.get("denom")));
            stats.put("shockUltrasoundNum", toLong(shockUltrasound.get("num")));
            stats.put("shockUltrasoundDenom", toLong(shockUltrasound.get("denom")));
            stats.put("shockHemodynamicNum", toLong(shockHemodynamic.get("num")));
            stats.put("shockHemodynamicDenom", toLong(shockHemodynamic.get("denom")));
            stats.put("ardsNum", toLong(ards.get("num")));
            stats.put("ardsDenom", toLong(ards.get("denom")));
            stats.put("en48hNum", toLong(en48h.get("num")));
            stats.put("en48hDenom", toLong(en48h.get("denom")));
            stats.put("painNum", toLong(pain.get("num")));
            stats.put("painDenom", toLong(pain.get("denom")));
            stats.put("sedationNum", toLong(sedation.get("num")));
            stats.put("sedationDenom", toLong(sedation.get("denom")));
            stats.put("rescueNum", toLong(rescue.get("num")));
            stats.put("rescueDenom", toLong(rescue.get("denom")));
            stats.put("brainInjuryNum", toLong(brainInjury.get("num")));
            stats.put("brainInjuryDenom", toLong(brainInjury.get("denom")));

            result.put(monthKey, stats);
        }
        return result;
    }

    // ════════════════════════════════════════════════════════════════════
    // Indicator row builders
    // ════════════════════════════════════════════════════════════════════

    private Map<String, Object> buildBasicCountRow(Map<String, Object> spec, List<String> months, Map<String, Map<String, Integer>> basicStats) {
        String key = (String) spec.get("key");
        int total = (int) months.stream().mapToDouble(m -> NumberUtils.safeNumber(basicStats.getOrDefault(m, Collections.emptyMap()).get(key))).sum();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", spec.get("id"));
        row.put("key", key);
        row.put("code", spec.get("code"));
        row.put("name", spec.get("name"));
        row.put("ratio", "/");
        row.put("numerator", "/");
        row.put("denominator", String.valueOf(total));
        Map<String, Object> monthsMap = new LinkedHashMap<>();
        for (String m : months) {
            monthsMap.put(m, Map.of("display", String.valueOf(NumberUtils.safeNumber(basicStats.getOrDefault(m, Collections.emptyMap()).get(key)))));
        }
        row.put("months", monthsMap);
        return row;
    }

    private Map<String, Object> buildComputedIndicatorRow(Map<String, Object> spec, List<String> months, Map<String, Map<String, Object>> computedStats) {
        String key = (String) spec.get("key");
        String type = (String) spec.get("type");
        double totalNum = 0, totalDen = 0;
        for (String m : months) {
            Map<String, Object> stats = computedStats.getOrDefault(m, Collections.emptyMap());
            Map<String, Double> vals = getComputedMetricValues(key, stats);
            totalNum += vals.get("numerator");
            totalDen += vals.get("denominator");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", spec.get("id"));
        row.put("key", key);
        row.put("code", spec.get("code"));
        row.put("name", spec.get("name"));
        row.put("ratio", formatComputedRatio(type, totalNum, totalDen));
        row.put("numerator", trimTrailingZeros(totalNum, 2));
        row.put("denominator", trimTrailingZeros(totalDen, 2));
        Map<String, Object> monthsMap = new LinkedHashMap<>();
        for (String m : months) {
            Map<String, Object> stats = computedStats.getOrDefault(m, Collections.emptyMap());
            Map<String, Double> vals = getComputedMetricValues(key, stats);
            monthsMap.put(m, Map.of("display", formatComputedRatio(type, vals.get("numerator"), vals.get("denominator"))));
        }
        row.put("months", monthsMap);
        return row;
    }

    private Map<String, Object> buildIndicatorRow(Map<String, Object> spec, List<String> months, List<Document> docs, List<Document> qcDocs, Map<String, List<Document>> itemsByQualityId, Map<String, Map<String, Integer>> basicStats) {
        String key = (String) spec.get("key");
        String code = (String) spec.get("code");
        String type = (String) spec.get("type");
        Map<String, Document> monthDocMap = buildMonthDocMap(filterByCode(docs, code));
        Map<String, Document> qcMonthDocMap = buildMonthDocMap(filterByCode(qcDocs, (String) spec.get("newCode")));
        Map<String, Document> mergedMonthDocMap = new LinkedHashMap<>();
        for (String m : months) {
            Document d = monthDocMap.get(m);
            if (d == null) d = qcMonthDocMap.get(m);
            mergedMonthDocMap.put(m, d);
        }

        double numTotal = 0, denTotal = 0;
        for (String m : months) {
            Document doc = mergedMonthDocMap.get(m);
            if (ICU_CENSUS_DENOMINATOR_KEYS.contains(key) && doc != null) {
                List<Document> docItems = getItemsForDoc(doc, itemsByQualityId);
                numTotal += docItems.stream().filter(i -> NumberUtils.safeNumber(i.get("order")) == 0)
                        .mapToDouble(i -> NumberUtils.safeNumber(i.get("itemData"))).sum();
                denTotal += NumberUtils.safeNumber(basicStats.getOrDefault(m, Collections.emptyMap()).get("icuCensus"));
            } else if (doc != null) {
                numTotal += NumberUtils.safeNumber(doc.get("indicatorData"));
                List<Document> docItems = getItemsForDoc(doc, itemsByQualityId);
                denTotal += docItems.stream().filter(i -> NumberUtils.safeNumber(i.get("order")) == 1)
                        .mapToDouble(i -> NumberUtils.safeNumber(i.get("itemData"))).sum();
            }
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", spec.get("id"));
        row.put("key", key);
        row.put("code", code);
        row.put("name", spec.get("name"));
        row.put("ratio", "count".equals(type) ? "/" : formatAggregateRatio(type, numTotal, denTotal));
        row.put("numerator", "count".equals(type) ? "/" : trimTrailingZeros(numTotal, 2));
        row.put("denominator", "count".equals(type) ? String.valueOf(Math.round(numTotal)) : trimTrailingZeros(denTotal, 2));

        Map<String, Object> monthsMap = new LinkedHashMap<>();
        for (String m : months) {
            Document doc = mergedMonthDocMap.get(m);
            String display;
            if (ICU_CENSUS_DENOMINATOR_KEYS.contains(key) && doc != null) {
                List<Document> docItems = getItemsForDoc(doc, itemsByQualityId);
                double numerator = docItems.stream().filter(i -> NumberUtils.safeNumber(i.get("order")) == 0)
                        .mapToDouble(i -> NumberUtils.safeNumber(i.get("itemData"))).sum();
                double denominator = NumberUtils.safeNumber(basicStats.getOrDefault(m, Collections.emptyMap()).get("icuCensus"));
                display = formatComputedRatio(type, numerator, denominator);
            } else {
                display = doc != null ? formatMetricValue(type, doc.get("indicatorData")) : ("count".equals(type) ? "0" : "/");
            }
            monthsMap.put(m, Map.of("display", display));
        }
        row.put("months", monthsMap);
        return row;
    }

    private Map<String, Object> buildCalculatedFallbackRow(Map<String, Object> spec, List<String> months, List<Document> docs, List<Document> qcDocs, Map<String, List<Document>> itemsByQualityId, Map<String, Map<String, Integer>> basicStats, Map<String, Map<String, Object>> calculatedStats) {
        String key = (String) spec.get("key");
        String code = (String) spec.get("code");
        String type = (String) spec.get("type");
        Map<String, Document> monthDocMap = buildMonthDocMap(filterByCode(docs, code));
        Map<String, Document> qcMonthDocMap = buildMonthDocMap(filterByCode(qcDocs, (String) spec.get("newCode")));
        Map<String, Document> mergedMonthDocMap = new LinkedHashMap<>();
        for (String m : months) {
            Document d = monthDocMap.get(m);
            if (d == null) d = qcMonthDocMap.get(m);
            mergedMonthDocMap.put(m, d);
        }

        double ratioNum = 0, ratioDen = 0;
        Map<String, Object> monthsMap = new LinkedHashMap<>();
        for (String m : months) {
            Map<String, Object> monthStats = calculatedStats.getOrDefault(m, Collections.emptyMap());
            Map<String, Double> vals = getCalculatedMetricValues(key, monthStats);
            ratioNum += vals.get("numerator");
            ratioDen += vals.get("denominator");
            monthsMap.put(m, Map.of("display", formatComputedRatio(type, vals.get("numerator"), vals.get("denominator"))));
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", spec.get("id"));
        row.put("key", key);
        row.put("code", code);
        row.put("name", spec.get("name"));
        row.put("ratio", formatComputedRatio(type, ratioNum, ratioDen));
        // Display numerator/denominator for predictedMortalityRate uses *1000
        if ("predictedMortalityRate".equals(key)) {
            row.put("numerator", trimTrailingZeros(ratioNum * 1000, 2));
        } else {
            row.put("numerator", trimTrailingZeros(ratioNum, 2));
        }
        row.put("denominator", trimTrailingZeros(ratioDen, 2));
        row.put("months", monthsMap);
        return row;
    }

    // ════════════════════════════════════════════════════════════════════
    // Metric value calculators
    // ════════════════════════════════════════════════════════════════════

    private Map<String, Double> getComputedMetricValues(String key, Map<String, Object> stats) {
        Map<String, Double> result = new LinkedHashMap<>();
        switch (key) {
            case "bedUsage":
                result.put("numerator", NumberUtils.safeNumber(stats.get("occupiedBedDays")));
                result.put("denominator", NumberUtils.safeNumber(stats.get("configuredBedDays")));
                break;
            case "avgLengthOfStay":
                result.put("numerator", NumberUtils.safeNumber(stats.get("occupiedBedDays")));
                result.put("denominator", NumberUtils.safeNumber(stats.get("icuCensus")));
                break;
            case "icuAdmissionRate":
                result.put("numerator", NumberUtils.safeNumber(stats.get("icuCensus")));
                result.put("denominator", NumberUtils.safeNumber(stats.get("hospitalAdmissions")));
                break;
            case "icuBedDayRate":
                result.put("numerator", NumberUtils.safeNumber(stats.get("occupiedBedDays")));
                result.put("denominator", NumberUtils.safeNumber(stats.get("hospitalBedDays")));
                break;
            case "antibioticCultureRate":
                result.put("numerator", NumberUtils.safeNumber(stats.get("antibioticCultureCases")));
                result.put("denominator", NumberUtils.safeNumber(stats.get("antibioticTreatmentCases")));
                break;
            default:
                result.put("numerator", 0.0);
                result.put("denominator", 0.0);
        }
        return result;
    }

    private Map<String, Double> getCalculatedMetricValues(String key, Map<String, Object> stats) {
        Map<String, Double> result = new LinkedHashMap<>();
        switch (key) {
            case "apacheGte15Rate":
                result.put("numerator", NumberUtils.safeNumber(stats.get("apacheGte15")));
                result.put("denominator", NumberUtils.safeNumber(stats.get("icuCensus")));
                break;
            case "apacheLt15Rate":
                result.put("numerator", NumberUtils.safeNumber(stats.get("apacheLt15")));
                result.put("denominator", NumberUtils.safeNumber(stats.get("icuCensus")));
                break;
            case "apacheScoreRate":
                result.put("numerator", NumberUtils.safeNumber(stats.get("apacheScored")));
                result.put("denominator", NumberUtils.safeNumber(stats.get("icuCensus")));
                break;
            case "predictedMortalityRate":
                result.put("numerator", NumberUtils.safeNumber(stats.get("predictedMortalitySum")));
                result.put("denominator", NumberUtils.safeNumber(stats.get("icuCensus")));
                break;
            case "apacheLt15DeathRate":
                result.put("numerator", NumberUtils.safeNumber(stats.get("apacheLt15Death")));
                result.put("denominator", NumberUtils.safeNumber(stats.get("apacheLt15")));
                break;
            case "standardizedMortalityIndex":
                result.put("numerator", NumberUtils.safeNumber(stats.get("actualMortalityRate")));
                result.put("denominator", NumberUtils.safeNumber(stats.get("predictedMortalityRate")));
                break;
            case "shockBundleRate":
                result.put("numerator", NumberUtils.safeNumber(stats.get("shockBundleNum")));
                result.put("denominator", NumberUtils.safeNumber(stats.get("shockBundleDenom")));
                break;
            case "dvtRate":
                result.put("numerator", NumberUtils.safeNumber(stats.get("dvtNum")));
                result.put("denominator", NumberUtils.safeNumber(stats.get("dvtDenom")));
                break;
            case "unplannedExtubationRate":
                result.put("numerator", NumberUtils.safeNumber(stats.get("unplannedExtubationNum")));
                result.put("denominator", NumberUtils.safeNumber(stats.get("unplannedExtubationDenom")));
                break;
            case "reintubation48hRate":
                result.put("numerator", NumberUtils.safeNumber(stats.get("reintubation48hNum")));
                result.put("denominator", NumberUtils.safeNumber(stats.get("reintubation48hDenom")));
                break;
            case "icuReturn48hRate":
                result.put("numerator", NumberUtils.safeNumber(stats.get("icuReturn48hNum")));
                result.put("denominator", NumberUtils.safeNumber(stats.get("icuReturn48hDenom")));
                break;
            case "shockUltrasoundRate":
                result.put("numerator", NumberUtils.safeNumber(stats.get("shockUltrasoundNum")));
                result.put("denominator", NumberUtils.safeNumber(stats.get("shockUltrasoundDenom")));
                break;
            case "shockHemodynamicRate":
                result.put("numerator", NumberUtils.safeNumber(stats.get("shockHemodynamicNum")));
                result.put("denominator", NumberUtils.safeNumber(stats.get("shockHemodynamicDenom")));
                break;
            case "ardsRate":
                result.put("numerator", NumberUtils.safeNumber(stats.get("ardsNum")));
                result.put("denominator", NumberUtils.safeNumber(stats.get("ardsDenom")));
                break;
            case "en48hRate":
                result.put("numerator", NumberUtils.safeNumber(stats.get("en48hNum")));
                result.put("denominator", NumberUtils.safeNumber(stats.get("en48hDenom")));
                break;
            case "painRate":
                result.put("numerator", NumberUtils.safeNumber(stats.get("painNum")));
                result.put("denominator", NumberUtils.safeNumber(stats.get("painDenom")));
                break;
            case "sedationRate":
                result.put("numerator", NumberUtils.safeNumber(stats.get("sedationNum")));
                result.put("denominator", NumberUtils.safeNumber(stats.get("sedationDenom")));
                break;
            case "rescueSuccessRate":
                result.put("numerator", NumberUtils.safeNumber(stats.get("rescueNum")));
                result.put("denominator", NumberUtils.safeNumber(stats.get("rescueDenom")));
                break;
            case "acuteBrainInjuryRate":
                result.put("numerator", NumberUtils.safeNumber(stats.get("brainInjuryNum")));
                result.put("denominator", NumberUtils.safeNumber(stats.get("brainInjuryDenom")));
                break;
            default:
                result.put("numerator", 0.0);
                result.put("denominator", 0.0);
        }
        return result;
    }

    // ════════════════════════════════════════════════════════════════════
    // Patient rows
    // ════════════════════════════════════════════════════════════════════

    private List<Map<String, Object>> getBasicPatientRows(String indicatorKey, List<String> months, String department) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String monthKey : months) {
            MonthRange range = DateRangeUtils.getMonthRange(monthKey);
            Document filter = "newAdmissions".equals(indicatorKey)
                    ? buildPatientFilter(new Document("icuAdmissionTime", new Document("$gte", range.getStartDate()).append("$lte", range.getEndDate())), department, properties.isEnableDeptFilter())
                    : buildMonthlyOverlapFilter(range.getStartDate(), range.getEndDate(), department);
            List<Document> patients = findPatients(filter);
            for (Document patient : patients) {
                rows.add(toPatientDetailRow(patient, rows.size() + 1, monthKey, Collections.emptyMap()));
            }
        }
        return rows;
    }

    private List<Map<String, Object>> getOccupiedBedDayRows(List<String> months, String department) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String monthKey : months) {
            MonthRange range = DateRangeUtils.getMonthRange(monthKey);
            List<Document> patients = findPatientsSorted(buildMonthlyOverlapFilter(range.getStartDate(), range.getEndDate(), department));
            for (Document patient : patients) {
                int occupiedDays = calcOccupiedBedDays(patient, monthKey);
                if (occupiedDays > 0) {
                    Map<String, Object> extra = new LinkedHashMap<>();
                    extra.put("occupiedBedDays", occupiedDays + "天");
                    rows.add(toPatientDetailRow(patient, rows.size() + 1, monthKey, extra));
                }
            }
        }
        return rows;
    }

    private List<Map<String, Object>> getApachePatientRows(String indicatorKey, int itemOrder, List<String> months, String department) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String monthKey : months) {
            MonthRange range = DateRangeUtils.getMonthRange(monthKey);
            List<Document> patients = findPatientsSorted(buildMonthlyOverlapFilter(range.getStartDate(), range.getEndDate(), department));
            Map<String, Document> selectedScoreByPid = loadSelectedScoreMap(patients, range.getStartDate(), range.getEndDate());
            Map<String, Document> anyScoreByPid = loadAnyScoreMap(patients);

            for (Document patient : patients) {
                String pid = String.valueOf(patient.get("_id"));
                Document selectedScore = selectedScoreByPid.get(pid);
                Document anyScore = anyScoreByPid.get(pid);
                Document scoreForDisplay = selectedScore != null ? selectedScore : anyScore;
                double total = selectedScore != null ? getScoreTotal(selectedScore) : 0;
                boolean matched = false;

                switch (indicatorKey) {
                    case "apacheGte15Rate":
                        matched = itemOrder == 0 ? (selectedScore != null && total >= 15) : true;
                        break;
                    case "apacheLt15Rate":
                        matched = itemOrder == 0 ? (selectedScore != null && total < 15) : true;
                        break;
                    case "apacheLt15DeathRate":
                        String dt = getFirstValue(patient, "dischargedType", "dischargeType");
                        matched = itemOrder == 0
                                ? (selectedScore != null && total < 15 && dt.contains("死亡"))
                                : (selectedScore != null && total < 15);
                        break;
                    case "apacheScoreRate":
                        if (itemOrder == 0) matched = anyScore != null;
                        else if (itemOrder == APACHE_UNSCORED_ORDER) matched = anyScore == null;
                        else matched = true;
                        break;
                }
                if (!matched) continue;

                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("apacheScore", scoreForDisplay != null ? getScoreTotal(scoreForDisplay) : "");
                rows.add(toPatientDetailRow(patient, rows.size() + 1, monthKey, extra));
            }
        }
        return rows;
    }

    /**
     * 获取 APACHEⅡ 总分。
     *
     * 对应旧版 JavaScript 语义：score.total ?? score.apacheII?.totalScore
     * total=0 是合法分数，必须保留；只有 total 为 null/不存在时才回退到 apacheII.totalScore。
     */
    private double getScoreTotal(Document score) {
        if (score == null) {
            return 0;
        }

        Object totalValue = score.get("total");
        if (totalValue != null) {
            return NumberUtils.safeNumber(totalValue);
        }

        Object apacheIIValue = score.get("apacheII");
        if (apacheIIValue instanceof Map<?, ?>) {
            Object totalScoreValue = ((Map<?, ?>) apacheIIValue).get("totalScore");
            if (totalScoreValue != null) {
                return NumberUtils.safeNumber(totalScoreValue);
            }
        }

        return 0;
    }

    private List<Map<String, Object>> getPatientsByDetailPids(List<Document> detailRows, List<String> months, String department, boolean includeApacheScore) {
        List<String> pids = detailRows.stream()
                .map(d -> String.valueOf(d.get("pid")))
                .filter(p -> !"null".equals(p) && !p.isEmpty())
                .distinct().collect(Collectors.toList());
        if (pids.isEmpty()) return Collections.emptyList();

        List<Document> patients = findPatients(buildPatientFilter(new Document("_id", new Document("$in", pids)), department, properties.isEnableDeptFilter()));
        Map<String, Document> patientById = new LinkedHashMap<>();
        for (Document p : patients) patientById.put(String.valueOf(p.get("_id")), p);

        String statMonth = months.size() == 1 ? months.get(0) : months.get(0) + "至" + months.get(months.size() - 1);
        Map<String, Document> apacheScoreByPid = new LinkedHashMap<>();

        if (includeApacheScore && !patients.isEmpty()) {
            MonthRange firstRange = DateRangeUtils.getMonthRange(months.get(0));
            MonthRange lastRange = DateRangeUtils.getMonthRange(months.get(months.size() - 1));
            Map<String, Document> selectedMap = loadSelectedScoreMap(patients, firstRange.getStartDate(), lastRange.getEndDate());
            Map<String, Document> anyMap = loadAnyScoreMap(patients);
            for (Document patient : patients) {
                String pid = String.valueOf(patient.get("_id"));
                apacheScoreByPid.put(pid, selectedMap.getOrDefault(pid, anyMap.get(pid)));
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < detailRows.size(); i++) {
            Document detail = detailRows.get(i);
            String pid = String.valueOf(detail.get("pid"));
            Document patient = patientById.get(pid);
            if (patient == null) continue;
            Map<String, Object> extra = new LinkedHashMap<>();
            if (includeApacheScore) {
                Document apacheScore = apacheScoreByPid.get(pid);
                extra.put("apacheScore", apacheScore != null ? getScoreTotal(apacheScore) : "");
            }
            rows.add(toPatientDetailRow(patient, rows.size() + 1, statMonth, extra));
        }
        return rows;
    }

    // ════════════════════════════════════════════════════════════════════
    // Summary row builders
    // ════════════════════════════════════════════════════════════════════

    private List<Map<String, Object>> buildComputedSummaryRows(Map<String, Object> spec, List<String> months, String startMonth, String endMonth, String department) {
        Map<String, Map<String, Integer>> basicStats = getBasicMonthlyStats(months, department);
        Map<String, Map<String, Object>> computedStats = getComputedMonthlyStats(months, department, basicStats);
        String key = (String) spec.get("key");

        double totalOccupiedBedDays = months.stream().mapToDouble(m -> NumberUtils.safeNumber(computedStats.getOrDefault(m, Collections.emptyMap()).get("occupiedBedDays"))).sum();
        double totalConfiguredBedDays = months.stream().mapToDouble(m -> NumberUtils.safeNumber(computedStats.getOrDefault(m, Collections.emptyMap()).get("configuredBedDays"))).sum();
        double icuCensusTotal = months.stream().mapToDouble(m -> NumberUtils.safeNumber(basicStats.getOrDefault(m, Collections.emptyMap()).get("icuCensus"))).sum();
        double hospitalAdmissions = months.stream().mapToDouble(m -> NumberUtils.safeNumber(computedStats.getOrDefault(m, Collections.emptyMap()).get("hospitalAdmissions"))).sum();
        double hospitalBedDays = months.stream().mapToDouble(m -> NumberUtils.safeNumber(computedStats.getOrDefault(m, Collections.emptyMap()).get("hospitalBedDays"))).sum();

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> ratioRow = new LinkedHashMap<>();
        switch (key) {
            case "bedUsage":
                ratioRow = makeSummaryRow(1, spec.get("name"), formatComputedRatio((String) spec.get("type"), totalOccupiedBedDays, totalConfiguredBedDays), null);
                rows.add(ratioRow);
                rows.add(makeSummaryRow(2, "本科收治患者总床日数", trimTrailingZeros(totalOccupiedBedDays, 2), makeAction(spec, startMonth, endMonth, 0)));
                rows.add(makeSummaryRow(3, "本科总床日数", trimTrailingZeros(totalConfiguredBedDays, 2), null));
                break;
            case "avgLengthOfStay":
                ratioRow = makeSummaryRow(1, spec.get("name"), formatComputedRatio((String) spec.get("type"), totalOccupiedBedDays, icuCensusTotal), null);
                rows.add(ratioRow);
                rows.add(makeSummaryRow(2, "本科收治患者总床日数", trimTrailingZeros(totalOccupiedBedDays, 2), makeAction(spec, startMonth, endMonth, 0)));
                rows.add(makeSummaryRow(3, "同期ICU收治患者总数", trimTrailingZeros(icuCensusTotal, 2), makeAction(spec, startMonth, endMonth, 1)));
                break;
            case "icuAdmissionRate":
                ratioRow = makeSummaryRow(1, spec.get("name"), formatComputedRatio((String) spec.get("type"), icuCensusTotal, hospitalAdmissions), null);
                rows.add(ratioRow);
                rows.add(makeSummaryRow(2, "同期ICU收治患者总数", trimTrailingZeros(icuCensusTotal, 2), makeAction(spec, startMonth, endMonth, 0)));
                rows.add(makeSummaryRow(3, "同期医院收治患者总数", trimTrailingZeros(hospitalAdmissions, 2), null));
                break;
            case "icuBedDayRate":
                ratioRow = makeSummaryRow(1, spec.get("name"), formatComputedRatio((String) spec.get("type"), totalOccupiedBedDays, hospitalBedDays), null);
                rows.add(ratioRow);
                rows.add(makeSummaryRow(2, "本科收治患者总床日数", trimTrailingZeros(totalOccupiedBedDays, 2), makeAction(spec, startMonth, endMonth, 0)));
                rows.add(makeSummaryRow(3, "同期医院患者收治总床日数", trimTrailingZeros(hospitalBedDays, 2), null));
                break;
            case "antibioticCultureRate":
                double cultureCases = months.stream().mapToDouble(m -> NumberUtils.safeNumber(computedStats.getOrDefault(m, Collections.emptyMap()).get("antibioticCultureCases"))).sum();
                double treatmentCases = months.stream().mapToDouble(m -> NumberUtils.safeNumber(computedStats.getOrDefault(m, Collections.emptyMap()).get("antibioticTreatmentCases"))).sum();
                ratioRow = makeSummaryRow(1, spec.get("name"), formatComputedRatio((String) spec.get("type"), cultureCases, treatmentCases), null);
                rows.add(ratioRow);
                rows.add(makeSummaryRow(2, "使用抗菌药物前病原学检验标本送检病例数", trimTrailingZeros(cultureCases, 2), null));
                rows.add(makeSummaryRow(3, "同期使用抗菌药物治疗病例总数", trimTrailingZeros(treatmentCases, 2), null));
                break;
        }
        return rows;
    }

    private List<Map<String, Object>> buildCalculatedSummaryRows(Map<String, Object> spec, List<String> months, String department) {
        Map<String, Map<String, Integer>> basicStats = getBasicMonthlyStats(months, department);
        Map<String, Map<String, Object>> calculatedStats = getCalculatedMonthlyStats(months, department, basicStats);
        String key = (String) spec.get("key");
        String type = (String) spec.get("type");

        List<Map<String, Object>> rows = new ArrayList<>();
        switch (key) {
            case "apacheGte15Rate":
                rows.add(makeSummaryRow(1, spec.get("name"), formatComputedRatio(type, sumCalc(calculatedStats, months, "apacheGte15"), sumBasic(basicStats, months, "icuCensus")), null));
                rows.add(makeSummaryRow(2, "APACHEⅡ≥15患者数", trimTrailingZeros(sumCalc(calculatedStats, months, "apacheGte15"), 2), makeAction(spec, months.get(0), months.get(months.size()-1), 0)));
                rows.add(makeSummaryRow(3, "同期ICU收治患者总数", trimTrailingZeros(sumBasic(basicStats, months, "icuCensus"), 2), makeAction(spec, months.get(0), months.get(months.size()-1), 1)));
                break;
            case "apacheLt15Rate":
                rows.add(makeSummaryRow(1, spec.get("name"), formatComputedRatio(type, sumCalc(calculatedStats, months, "apacheLt15"), sumBasic(basicStats, months, "icuCensus")), null));
                rows.add(makeSummaryRow(2, "APACHEⅡ<15患者数", trimTrailingZeros(sumCalc(calculatedStats, months, "apacheLt15"), 2), makeAction(spec, months.get(0), months.get(months.size()-1), 0)));
                rows.add(makeSummaryRow(3, "同期ICU收治患者总数", trimTrailingZeros(sumBasic(basicStats, months, "icuCensus"), 2), makeAction(spec, months.get(0), months.get(months.size()-1), 1)));
                break;
            case "apacheScoreRate":
                double scored = sumCalc(calculatedStats, months, "apacheScored");
                double denom = sumBasic(basicStats, months, "icuCensus");
                rows.add(makeSummaryRow(1, spec.get("name"), formatComputedRatio(type, scored, denom), null));
                rows.add(makeSummaryRow(2, "APACHEⅡ评分患者数", trimTrailingZeros(scored, 2), makeAction(spec, months.get(0), months.get(months.size()-1), 0)));
                rows.add(makeSummaryRow(3, "同期ICU收治患者总数", trimTrailingZeros(denom, 2), makeAction(spec, months.get(0), months.get(months.size()-1), 1)));
                rows.add(makeSummaryRow(4, "未评APACHEⅡ人数", trimTrailingZeros(Math.max(0, denom - scored), 2),
                        makeAction(spec, months.get(0), months.get(months.size()-1), APACHE_UNSCORED_ORDER)));
                break;
            case "predictedMortalityRate":
                double predSum = sumCalc(calculatedStats, months, "predictedMortalitySum");
                double icuCensus = sumBasic(basicStats, months, "icuCensus");
                rows.add(makeSummaryRow(1, spec.get("name"), formatComputedRatio(type, predSum, icuCensus), null));
                rows.add(makeSummaryRow(2, "ICU收治患者预计病死率总和", trimTrailingZeros(predSum * 1000, 2), makeAction(spec, months.get(0), months.get(months.size()-1), 0)));
                rows.add(makeSummaryRow(3, "同期ICU收治患者总数", trimTrailingZeros(icuCensus, 2), makeAction(spec, months.get(0), months.get(months.size()-1), 1)));
                break;
            case "apacheLt15DeathRate":
                double lt15Death = sumCalc(calculatedStats, months, "apacheLt15Death");
                double lt15Total = sumCalc(calculatedStats, months, "apacheLt15");
                rows.add(makeSummaryRow(1, spec.get("name"), formatComputedRatio(type, lt15Death, lt15Total), null));
                rows.add(makeSummaryRow(2, "APACHEⅡ<15死亡患者数", trimTrailingZeros(lt15Death, 2), makeAction(spec, months.get(0), months.get(months.size()-1), 0)));
                rows.add(makeSummaryRow(3, "APACHEⅡ<15患者数", trimTrailingZeros(lt15Total, 2), makeAction(spec, months.get(0), months.get(months.size()-1), 1)));
                break;
            case "standardizedMortalityIndex":
                double actualRate = months.stream().mapToDouble(m -> NumberUtils.safeNumber(calculatedStats.getOrDefault(m, Collections.emptyMap()).get("actualMortalityRate"))).sum();
                double predictedRate = months.stream().mapToDouble(m -> NumberUtils.safeNumber(calculatedStats.getOrDefault(m, Collections.emptyMap()).get("predictedMortalityRate"))).sum();
                rows.add(makeSummaryRow(1, spec.get("name"), formatComputedRatio(type, actualRate, predictedRate), null));
                rows.add(makeSummaryRow(2, "ICU患者实际病死率", trimTrailingZeros(actualRate, 6), makeAction(spec, months.get(0), months.get(months.size()-1), 0)));
                rows.add(makeSummaryRow(3, "同期ICU患者预计病死率", trimTrailingZeros(predictedRate, 6), makeAction(spec, months.get(0), months.get(months.size()-1), 1)));
                break;
            default: {
                // Generic calculated indicator: show ratio + numerator + denominator
                Map<String, Map<String, Integer>> basicStats2 = getBasicMonthlyStats(months, department);
                Map<String, Map<String, Object>> calculatedStats2 = getCalculatedMonthlyStats(months, department, basicStats2);
                double totalNum = 0, totalDenom = 0;
                for (String m : months) {
                    Map<String, Double> vals = getCalculatedMetricValues(key, calculatedStats2.getOrDefault(m, Collections.emptyMap()));
                    totalNum += vals.get("numerator");
                    totalDenom += vals.get("denominator");
                }
                String ratio = totalDenom > 0 ? String.format("%.2f%%", totalNum * 100.0 / totalDenom) : "N/A";
                rows.add(makeSummaryRow(1, spec.get("name"), ratio, null));
                rows.add(makeSummaryRow(2, "分子", trimTrailingZeros(totalNum, 2), makeAction(spec, months.get(0), months.get(months.size()-1), 0)));
                rows.add(makeSummaryRow(3, "分母", trimTrailingZeros(totalDenom, 2), makeAction(spec, months.get(0), months.get(months.size()-1), 1)));
                break;
            }
        }
        return rows;
    }

    private List<Map<String, Object>> buildSetBasedSummaryRows(Map<String, Object> spec, List<String> months, String startMonth, String endMonth, String department) {
        String key = (String) spec.get("key");
        String type = (String) spec.get("type");

        Map<String, Map<String, Integer>> basicStats = getBasicMonthlyStats(months, department);
        Map<String, Map<String, Object>> calculatedStats = getCalculatedMonthlyStats(months, department, basicStats);

        double totalNum = 0, totalDenom = 0;
        for (String m : months) {
            Map<String, Double> vals = getCalculatedMetricValues(key, calculatedStats.getOrDefault(m, Collections.emptyMap()));
            totalNum += vals.get("numerator");
            totalDenom += vals.get("denominator");
        }

        String ratio = totalDenom > 0 ? String.format("%.2f%%", totalNum * 100.0 / totalDenom) : "N/A";

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(makeSummaryRow(1, spec.get("name"), ratio, null));
        rows.add(makeSummaryRow(2, "分子", trimTrailingZeros(totalNum, 2), makeAction(spec, startMonth, endMonth, 0)));
        rows.add(makeSummaryRow(3, "分母", trimTrailingZeros(totalDenom, 2), makeAction(spec, startMonth, endMonth, 1)));
        return rows;
    }

    private List<Map<String, Object>> buildDefaultSummaryRows(Map<String, Object> spec, List<Document> items, List<Document> indicatorDocs, Map<String, List<Document>> itemsByQualityId, Map<String, List<Document>> detailsByItemId, List<String> months, String startMonth, String endMonth, String department) {
        String key = (String) spec.get("key");
        Set<Integer> orderSet = items.stream()
                .map(i -> (int) NumberUtils.safeNumber(i.get("order")))
                .collect(Collectors.toCollection(TreeSet::new));
        if (orderSet.isEmpty() && isOccupiedBedDayIndicator(spec)) {
            orderSet.addAll(Arrays.asList(0, 1));
        }

        List<Map<String, Object>> summaryRows = new ArrayList<>();
        List<Integer> orders = new ArrayList<>(orderSet);
        for (int idx = 0; idx < orders.size(); idx++) {
            int order = orders.get(idx);
            double itemValue = indicatorDocs.stream()
                    .flatMap(doc -> itemsByQualityId.getOrDefault(doc.get("_id").toString(), Collections.<Document>emptyList()).stream())
                    .filter(item -> (int) NumberUtils.safeNumber(item.get("order")) == order)
                    .mapToDouble(item -> NumberUtils.safeNumber(item.get("itemData")))
                    .sum();

            String itemName = indicatorDocs.stream()
                    .flatMap(doc -> itemsByQualityId.getOrDefault(doc.get("_id").toString(), Collections.<Document>emptyList()).stream())
                    .filter(item -> (int) NumberUtils.safeNumber(item.get("order")) == order)
                    .map(item -> String.valueOf(item.get("itemName")))
                    .findFirst().orElse("明细项" + (order + 1));

            boolean hasDetail = indicatorDocs.stream()
                    .flatMap(doc -> itemsByQualityId.getOrDefault(doc.get("_id").toString(), Collections.<Document>emptyList()).stream())
                    .filter(item -> (int) NumberUtils.safeNumber(item.get("order")) == order)
                    .flatMap(item -> detailsByItemId.getOrDefault(item.get("_id").toString(), Collections.<Document>emptyList()).stream())
                    .findAny().isPresent();

            Map<String, Object> action = hasDetail ? makeAction(spec, startMonth, endMonth, order) : null;
            summaryRows.add(makeSummaryRow(idx + 1, itemName, trimTrailingZeros(itemValue, 2), action));
        }
        return prependIndicatorRatioRow(summaryRows, spec);
    }

    // ════════════════════════════════════════════════════════════════════
    // Detail dispatchers
    // ════════════════════════════════════════════════════════════════════

    private Map<String, Object> buildCalculatedDetailRows(Map<String, Object> spec, String key, int itemOrder, List<String> months, String startMonth, String endMonth, String department) {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, String>> columns = PATIENT_DETAIL_COLUMNS;

        if ("predictedMortalityRate".equals(key)) {
            if (itemOrder == 0) {
                rows = getPredictedMortalityRows(months, department);
                columns = MORTALITY_DETAIL_COLUMNS;
            } else {
                rows = getBasicPatientRows("icuCensus", months, department);
            }
        } else if ("unplannedIcuTransferRate".equals(key)) {
            rows = getBasicPatientRows("icuCensus", months, department);
        } else if ("apacheLt15DeathRate".equals(key)) {
            rows = getApachePatientRows(key, itemOrder, months, department);
            columns = APACHE_DETAIL_COLUMNS;
        } else if ("standardizedMortalityIndex".equals(key)) {
            rows = getBasicPatientRows("icuCensus", months, department);
        } else {
            // Generic calculated indicator: use order-based patient matching
            rows = getCalculatedIndicatorPatientRows(key, itemOrder, months, department);
        }
        return detailResult(spec, columns, rows);
    }

    private Map<String, Object> buildSetBasedDetailRows(Map<String, Object> spec, String key, int itemOrder, List<String> months, String startMonth, String endMonth, String department) {
        List<Map<String, Object>> rows = getCalculatedIndicatorPatientRows(key, itemOrder, months, department);
        return detailResult(spec, PATIENT_DETAIL_COLUMNS, rows);
    }

    private Map<String, Object> buildExtubationReturnDetailRows(Map<String, Object> spec, String key, int itemOrder, List<String> months, String department) {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, String>> columns = PATIENT_DETAIL_COLUMNS;

        for (String monthKey : months) {
            MonthRange range = DateRangeUtils.getMonthRange(monthKey);
            Date start = range.getStartDate();
            Date end = range.getEndDate();

            if ("unplannedExtubationRate".equals(key) || "reintubation48hRate".equals(key)) {
                // Get tube records for this month
                Query tubeQuery = new Query(Criteria.where("type").is("气插管")
                        .and("valid").ne(false)
                        .and("replace").ne(true)
                        .and("endTime").gte(start).lte(end));
                List<Document> tubes = smartCareMongo.find(tubeQuery, Document.class, CollectionConstants.TUBE_EXE);

                Set<String> pids = tubes.stream()
                        .map(t -> String.valueOf(t.get("pid")))
                        .filter(pid -> !pid.isEmpty() && !"null".equals(pid))
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                if (!pids.isEmpty()) {
                    // Get patients
                    Query patientQuery = new Query(Criteria.where("_id").in(
                            pids.stream().map(ObjectId::new).collect(Collectors.toList())));
                    List<Document> patients = smartCareMongo.find(patientQuery, Document.class, CollectionConstants.PATIENT);
                    Map<String, Document> patientByPid = new LinkedHashMap<>();
                    for (Document p : patients) {
                        patientByPid.put(p.get("_id").toString(), p);
                    }

                    if ("unplannedExtubationRate".equals(key)) {
                        // Numerator: unplanned extubation
                        if (itemOrder == 0) {
                            for (Document t : tubes) {
                                if (Boolean.TRUE.equals(t.get("unPlannedEndTube"))) {
                                    String pid = String.valueOf(t.get("pid"));
                                    Document patient = patientByPid.get(pid);
                                    if (patient != null) {
                                        Map<String, Object> extra = new LinkedHashMap<>();
                                        extra.put("tubeTime", t.get("endTime"));
                                        extra.put("unPlanned", "是");
                                        rows.add(toPatientDetailRow(patient, rows.size() + 1, monthKey, extra));
                                    }
                                }
                            }
                        } else {
                            // Denominator: all tube records
                            for (Document t : tubes) {
                                String pid = String.valueOf(t.get("pid"));
                                Document patient = patientByPid.get(pid);
                                if (patient != null) {
                                    Map<String, Object> extra = new LinkedHashMap<>();
                                    extra.put("tubeTime", t.get("endTime"));
                                    rows.add(toPatientDetailRow(patient, rows.size() + 1, monthKey, extra));
                                }
                            }
                        }
                    } else {
                        // reintubation48hRate
                        // Get all history for these patients
                        Query historyQuery = new Query(Criteria.where("pid").in(pids)
                                .and("type").is("气插管")
                                .and("valid").ne(false)
                                .and("replace").ne(true));
                        historyQuery.with(Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "startTime"));
                        List<Document> allHistory = smartCareMongo.find(historyQuery, Document.class, CollectionConstants.TUBE_EXE);

                        Map<String, List<Document>> byPid = new LinkedHashMap<>();
                        for (Document t : allHistory) {
                            String pid = String.valueOf(t.get("pid"));
                            byPid.computeIfAbsent(pid, k -> new ArrayList<>()).add(t);
                        }

                        if (itemOrder == 0) {
                            // Numerator: reintubated within 48h
                            for (Document t : tubes) {
                                Date endTime = NumberUtils.asDate(t.get("endTime"));
                                if (endTime == null) continue;
                                String pid = String.valueOf(t.get("pid"));
                                List<Document> arr = byPid.getOrDefault(pid, Collections.emptyList());
                                for (Document x : arr) {
                                    Date xStart = NumberUtils.asDate(x.get("startTime"));
                                    if (xStart != null && xStart.after(endTime) && (xStart.getTime() - endTime.getTime()) <= HOURS_48_MS) {
                                        Document patient = patientByPid.get(pid);
                                        if (patient != null) {
                                            Map<String, Object> extra = new LinkedHashMap<>();
                                            extra.put("tubeTime", endTime);
                                            extra.put("reintubationTime", xStart);
                                            rows.add(toPatientDetailRow(patient, rows.size() + 1, monthKey, extra));
                                        }
                                        break;
                                    }
                                }
                            }
                        } else {
                            // Denominator: all tube records
                            for (Document t : tubes) {
                                String pid = String.valueOf(t.get("pid"));
                                Document patient = patientByPid.get(pid);
                                if (patient != null) {
                                    Map<String, Object> extra = new LinkedHashMap<>();
                                    extra.put("tubeTime", t.get("endTime"));
                                    rows.add(toPatientDetailRow(patient, rows.size() + 1, monthKey, extra));
                                }
                            }
                        }
                    }
                }
            } else if ("icuReturn48hRate".equals(key)) {
                // Get patients discharged during the month with type containing "转出"
                Query outQuery = new Query(Criteria.where("icuDischargeTime").gte(start).lte(end)
                        .and("dischargedType").regex("转出"));
                List<Document> out = smartCareMongo.find(outQuery, Document.class, CollectionConstants.PATIENT);

                if (!out.isEmpty()) {
                    List<String> mrns = out.stream()
                            .map(p -> (String) p.get("mrn"))
                            .filter(Objects::nonNull)
                            .distinct()
                            .collect(Collectors.toList());

                    Query allQuery = new Query(Criteria.where("mrn").in(mrns));
                    allQuery.with(Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "icuAdmissionTime"));
                    List<Document> all = smartCareMongo.find(allQuery, Document.class, CollectionConstants.PATIENT);

                    Map<String, List<Document>> byMrn = new LinkedHashMap<>();
                    for (Document p : all) {
                        String mrn = (String) p.get("mrn");
                        if (mrn != null) byMrn.computeIfAbsent(mrn, k -> new ArrayList<>()).add(p);
                    }

                    if (itemOrder == 0) {
                        // Numerator: returned within 48h
                        for (Document p : out) {
                            Date dischargeTime = NumberUtils.asDate(p.get("icuDischargeTime"));
                            String mrn = (String) p.get("mrn");
                            if (dischargeTime == null || mrn == null) continue;

                            List<Document> arr = byMrn.getOrDefault(mrn, Collections.emptyList());
                            for (Document r : arr) {
                                Date rAdmission = NumberUtils.asDate(r.get("icuAdmissionTime"));
                                if (rAdmission != null && rAdmission.after(dischargeTime)
                                        && (rAdmission.getTime() - dischargeTime.getTime()) <= HOURS_48_MS) {
                                    Map<String, Object> extra = new LinkedHashMap<>();
                                    extra.put("dischargeTime", dischargeTime);
                                    extra.put("returnTime", rAdmission);
                                    rows.add(toPatientDetailRow(p, rows.size() + 1, monthKey, extra));
                                    break;
                                }
                            }
                        }
                    } else {
                        // Denominator: all discharged patients
                        for (Document p : out) {
                            Map<String, Object> extra = new LinkedHashMap<>();
                            extra.put("dischargeTime", p.get("icuDischargeTime"));
                            rows.add(toPatientDetailRow(p, rows.size() + 1, monthKey, extra));
                        }
                    }
                }
            }
        }
        return detailResult(spec, columns, rows);
    }

    /**
     * Get patient rows for calculated/set-based indicators using order matching.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getCalculatedIndicatorPatientRows(String key, int itemOrder, List<String> months, String department) {
        List<Map<String, Object>> rows = new ArrayList<>();

        for (String monthKey : months) {
            List<Document> patients = qualityCalcService.getInIcuPatients(monthKey, department);
            if (patients.isEmpty()) continue;

            Map<String, Object> matched = null;

            switch (key) {
                case "shockBundleRate":
                    matched = qualityCalcService.getMatchedPatientsByOrderFilter(monthKey, patients,
                            qualityCalcService.orderQueryToMonthEnd(monthKey, Collections.singletonList("感染性休克集束化治疗")));
                    break;
                case "shockUltrasoundRate":
                    matched = qualityCalcService.calcOrderBasedCarryover(monthKey, department, "休克护理常规", "重症超声筛查评估");
                    break;
                case "shockHemodynamicRate":
                    matched = qualityCalcService.calcOrderBasedCarryover(monthKey, department, "休克护理常规", "CVP");
                    break;
                case "ardsRate":
                    matched = qualityCalcService.calcOrderBasedCarryover(monthKey, department, "中重度ARDS护理常规", "俯卧位通气");
                    break;
                case "acuteBrainInjuryRate":
                    matched = qualityCalcService.calcOrderBasedCarryover(monthKey, department, "急性脑损伤护理常规", "格拉斯哥昏迷评分");
                    break;
                case "dvtRate":
                    matched = qualityCalcService.calcDVT(monthKey, department);
                    break;
                case "en48hRate":
                    matched = qualityCalcService.calcEN48h(monthKey, department);
                    break;
                case "painRate":
                    matched = qualityCalcService.calcOrderHitOnIcuCarryover(monthKey, department, "镇痛评估");
                    break;
                case "sedationRate":
                    matched = qualityCalcService.calcOrderHitOnIcuCarryover(monthKey, department, "镇静评估");
                    break;
                case "rescueSuccessRate":
                    matched = qualityCalcService.calcRescue(monthKey);
                    break;
                default:
                    // For unknown indicators, show all ICU patients as denominator
                    if (itemOrder == 1) {
                        for (Document patient : patients) {
                            rows.add(toPatientDetailRow(patient, rows.size() + 1, monthKey, Collections.emptyMap()));
                        }
                    }
                    continue;
            }

            if (matched == null) continue;

            // For most indicators, the matched map has "done" (numerator) and "denominator" keys
            List<Document> doneList = (List<Document>) matched.get("done");
            List<Document> denomList = (List<Document>) matched.get("denominator");

            // For rescue success rate, use different keys
            if ("rescueSuccessRate".equals(key)) {
                // Rescue uses calcRescue which returns different structure
                // itemOrder 0 = success (num), 1 = total rescues (denom)
                // We'll show all rescue patients as denominator
                if (itemOrder == 1) {
                    for (Document patient : patients) {
                        rows.add(toPatientDetailRow(patient, rows.size() + 1, monthKey, Collections.emptyMap()));
                    }
                }
                continue;
            }

            if (itemOrder == 0 && doneList != null) {
                // Numerator: patients who completed the order
                for (Document patient : doneList) {
                    rows.add(toPatientDetailRow(patient, rows.size() + 1, monthKey, Collections.emptyMap()));
                }
            } else if (itemOrder == 1 && denomList != null) {
                // Denominator: all matched patients
                for (Document patient : denomList) {
                    rows.add(toPatientDetailRow(patient, rows.size() + 1, monthKey, Collections.emptyMap()));
                }
            }
        }
        return rows;
    }

    private List<Map<String, Object>> getPredictedMortalityRows(List<String> months, String department) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String monthKey : months) {
            MonthRange range = DateRangeUtils.getMonthRange(monthKey);
            List<Document> patients = findPatientsSorted(buildMonthlyOverlapFilter(range.getStartDate(), range.getEndDate(), department));
            Map<String, Document> selectedScoreByPid = loadSelectedScoreMap(patients, range.getStartDate(), range.getEndDate());
            for (Document patient : patients) {
                String pid = String.valueOf(patient.get("_id"));
                Document score = selectedScoreByPid.get(pid);
                if (score == null) continue;
                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("apacheScore", getScoreTotal(score));
                Object apacheII = score.get("apacheII");
                double calDead = 0;
                if (apacheII instanceof Map) {
                    Object calDeadObj = ((Map<?, ?>) apacheII).get("calDead");
                    if (calDeadObj instanceof Map) {
                        calDead = NumberUtils.safeNumber(((Map<?, ?>) calDeadObj).get("score"));
                    } else if (calDeadObj != null) {
                        calDead = NumberUtils.safeNumber(calDeadObj);
                    }
                }
                extra.put("predictedMortality", trimTrailingZeros(calDead * 1000, 2));
                rows.add(toPatientDetailRow(patient, rows.size() + 1, monthKey, extra));
            }
        }
        return rows;
    }

    // ════════════════════════════════════════════════════════════════════
    // Helper: score loading
    // ════════════════════════════════════════════════════════════════════

    private Map<String, Document> loadSelectedScoreMap(List<Document> patients, Date startDate, Date endDate) {
        List<String> pids = patients.stream().map(p -> String.valueOf(p.get("_id"))).collect(Collectors.toList());
        if (pids.isEmpty()) return Collections.emptyMap();

        Query scoreQuery = new Query(Criteria.where("pid").in(pids)
                .and("scoreType").is("apacheII")
                .and("valid").is(true));
        scoreQuery.with(Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "time"));
        List<Document> scores = smartCareMongo.find(scoreQuery, Document.class, CollectionConstants.SCORE);

        Map<String, List<Document>> grouped = new LinkedHashMap<>();
        for (Document score : scores) {
            String pid = String.valueOf(score.get("pid"));
            grouped.computeIfAbsent(pid, k -> new ArrayList<>()).add(score);
        }

        Map<String, Document> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Document>> e : grouped.entrySet()) {
            List<Document> patientScores = e.getValue();
            Document matched = pickApacheScoreForStats(patientScores, startDate, endDate);
            if (matched != null) result.put(e.getKey(), matched);
        }
        return result;
    }

    private Map<String, Document> loadAnyScoreMap(List<Document> patients) {
        List<String> pids = patients.stream().map(p -> String.valueOf(p.get("_id"))).collect(Collectors.toList());
        if (pids.isEmpty()) return Collections.emptyMap();

        Query scoreQuery = new Query(Criteria.where("pid").in(pids)
                .and("scoreType").is("apacheII")
                .and("valid").is(true));
        scoreQuery.with(Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "time"));
        List<Document> scores = smartCareMongo.find(scoreQuery, Document.class, CollectionConstants.SCORE);

        Map<String, Document> result = new LinkedHashMap<>();
        for (Document score : scores) {
            String pid = String.valueOf(score.get("pid"));
            result.putIfAbsent(pid, score);
        }
        return result;
    }

    private Document pickApacheScoreForStats(List<Document> patientScores, Date startDate, Date endDate) {
        if (patientScores.isEmpty()) return null;
        if (patientScores.size() == 1) return patientScores.get(0);

        for (Document s : patientScores) {
            Date scoreTime = NumberUtils.asDate(s.get("time"));
            if (scoreTime != null && !scoreTime.before(startDate) && !scoreTime.after(endDate)) return s;
        }
        for (Document s : patientScores) {
            Date scoreTime = NumberUtils.asDate(s.get("time"));
            if (scoreTime != null && scoreTime.before(startDate)) return s;
        }
        return null;
    }

    // ════════════════════════════════════════════════════════════════════
    // Bed day calculation
    // ════════════════════════════════════════════════════════════════════

    private int calcOccupiedBedDayTotal(List<String> months, String department) {
        int total = 0;
        for (String monthKey : months) {
            MonthRange range = DateRangeUtils.getMonthRange(monthKey);
            List<Document> patients = findPatientsSorted(buildMonthlyOverlapFilter(range.getStartDate(), range.getEndDate(), department));
            for (Document patient : patients) {
                total += calcOccupiedBedDays(patient, monthKey);
            }
        }
        return total;
    }

    private int calcConfiguredBedDayTotal(List<String> months, String department) {
        int total = 0;
        for (String monthKey : months) {
            int bedNum = getBedNumForMonth(monthKey);
            LocalDate monthStart = LocalDate.parse(monthKey + "-01", DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            int daysInMonth = monthStart.lengthOfMonth();
            total += bedNum * daysInMonth;
        }
        return total;
    }

    private int getBedNumForMonth(String monthKey) {
        MonthRange range = DateRangeUtils.getMonthRange(monthKey);
        Document filter = new Document("time", new Document("$lte", range.getEndDate()));
        List<Document> records = smartCareMongo.find(new BasicQuery(filter).with(Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "time")).limit(1),
                Document.class, "bedRecord");
        if (!records.isEmpty()) return (int) NumberUtils.safeNumber(records.get(0).get("bedNum"));
        return 0;
    }

    private int calcOccupiedBedDays(Document patient, String monthKey) {
        Date admission = NumberUtils.asDate(patient.get("icuAdmissionTime"));
        if (admission == null) return 0;
        MonthRange range = DateRangeUtils.getMonthRange(monthKey);
        Date discharge = NumberUtils.asDate(patient.get("icuDischargeTime"));
        if (discharge == null) discharge = range.getEndDate();

        Date start = admission.after(range.getStartDate()) ? admission : range.getStartDate();
        Date end = discharge.before(range.getEndDate()) ? discharge : range.getEndDate();
        if (end.before(start)) return 0;

        LocalDate startDay = start.toInstant().atZone(DateRangeUtils.SHANGHAI_ZONE).toLocalDate();
        LocalDate endDay = end.toInstant().atZone(DateRangeUtils.SHANGHAI_ZONE).toLocalDate();
        return Math.max(1, (int) java.time.temporal.ChronoUnit.DAYS.between(startDay, endDay) + 1);
    }

    // ════════════════════════════════════════════════════════════════════
    // Query helpers
    // ════════════════════════════════════════════════════════════════════

    private Document buildPatientFilter(Document extra, String department, boolean enableDeptFilter) {
        // Use PatientUtils for consistent department filtering behavior
        Map<String, Object> extraMap = new LinkedHashMap<>();
        if (extra != null) {
            for (Map.Entry<String, Object> e : extra.entrySet()) {
                extraMap.put(e.getKey(), e.getValue());
            }
        }
        return PatientUtils.buildPatientFilter(extraMap, department, enableDeptFilter);
    }

    private Document buildMonthlyOverlapFilter(Date startDate, Date endDate, String department) {
        return buildPatientFilter(
                new Document("icuAdmissionTime", new Document("$lte", endDate))
                        .append("$or", Arrays.asList(
                                new Document("icuDischargeTime", new Document("$gte", startDate)),
                                new Document("icuDischargeTime", null),
                                new Document("icuDischargeTime", new Document("$exists", false)))),
                department, properties.isEnableDeptFilter());
    }

    private List<Document> findPatients(Document filter) {
        Query query = new BasicQuery(filter);
        query.fields().include("_id", "mrn", "hospitalNo", "hospitalNumber", "zyh", "zyhm", "hisPid",
                "name", "birthday", "age", "gender", "hisBed", "bedNo", "bedCode", "bedName", "bedNumber",
                "hospitalTime", "icuAdmissionTime", "icuDischargeTime",
                "department", "deptName", "wardName", "inDeptName", "currentDeptName", "unitName",
                "admissionDoctor", "admissionDoctorName", "attendingDoctor", "attendingDoctorName",
                "chargeDoctorName", "tubeDoctorName", "bedDoctor",
                "admissionSource", "inSource", "source",
                "dischargedType", "dischargeType", "outType",
                "dischargedDepartment", "transferDept", "outDeptName",
                "admissionDiagnosis", "diagnosis", "clinicalDiagnosis", "primaryDiagnosis", "status");
        return smartCareMongo.find(query, Document.class, CollectionConstants.PATIENT);
    }

    private List<Document> findPatientsSorted(Document filter) {
        Query query = new BasicQuery(filter);
        query.with(Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "icuAdmissionTime", "_id"));
        query.fields().include("_id", "mrn", "hospitalNo", "hospitalNumber", "zyh", "zyhm", "hisPid",
                "name", "birthday", "age", "gender", "hisBed", "bedNo", "bedCode", "bedName", "bedNumber",
                "hospitalTime", "icuAdmissionTime", "icuDischargeTime",
                "department", "deptName", "wardName", "inDeptName", "currentDeptName", "unitName",
                "admissionDoctor", "admissionDoctorName", "attendingDoctor", "attendingDoctorName",
                "chargeDoctorName", "tubeDoctorName", "bedDoctor",
                "admissionSource", "inSource", "source",
                "dischargedType", "dischargeType", "outType",
                "dischargedDepartment", "transferDept", "outDeptName",
                "admissionDiagnosis", "diagnosis", "clinicalDiagnosis", "primaryDiagnosis", "status");
        return smartCareMongo.find(query, Document.class, CollectionConstants.PATIENT);
    }

    private int countPatients(Document filter) {
        return (int) smartCareMongo.count(new BasicQuery(filter), Document.class, CollectionConstants.PATIENT);
    }

    // ════════════════════════════════════════════════════════════════════
    // Formatting helpers
    // ════════════════════════════════════════════════════════════════════

    private String formatComputedRatio(String type, double numerator, double denominator) {
        if (denominator == 0) return "decimal".equals(type) ? "/" : "0.00%";
        double value = numerator / denominator;
        if ("decimal".equals(type)) return trimTrailingZeros(value, 4);
        return String.format("%.2f%%", value * 100);
    }

    private String formatAggregateRatio(String type, double numerator, double denominator) {
        if ("decimal".equals(type)) {
            if (denominator == 0) return "/";
            return trimTrailingZeros(numerator / denominator, 4);
        }
        if (denominator != 0) return String.format("%.2f%%", (numerator / denominator) * 100);
        return "/";
    }

    private String formatMetricValue(String type, Object rawValue) {
        if (rawValue == null) return "count".equals(type) ? "0" : "/";
        double num = NumberUtils.safeNumber(rawValue);
        if ("count".equals(type)) return String.valueOf(Math.round(num));
        if ("decimal".equals(type)) return trimTrailingZeros(num, 4);
        double pct = num <= 1.000001 ? num * 100 : num;
        return String.format("%.2f%%", pct);
    }

    private String trimTrailingZeros(double value, int maxDecimals) {
        String formatted = String.format("%." + maxDecimals + "f", value);
        return formatted.replaceAll("(\\.\\d*?[1-9])0+$", "$1").replaceAll("\\.0+$", "");
    }

    private Map<String, Object> toPatientDetailRow(Document patient, int index, String statMonth, Map<String, Object> extra) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("index", index);
        row.put("statMonth", statMonth);
        row.put("department", getFirstValue(patient, "department", "deptName", "wardName", "inDeptName", "currentDeptName", "unitName", "dept"));
        row.put("bedNo", getFirstValue(patient, "hisBed", "bedNo", "bedName", "bedCode", "bedNumber"));
        row.put("name", getFirstValue(patient, "name"));
        row.put("age", NumberUtils.calcAge(patient));
        row.put("hospitalNo", getFirstValue(patient, "hospitalNo", "hospitalNumber", "mrn", "zyh", "zyhm"));
        row.put("icuAdmissionTime", NumberUtils.formatDateTime(patient.get("icuAdmissionTime")));
        row.put("icuDischargeTime", NumberUtils.formatDateTime(patient.get("icuDischargeTime")));
        row.put("icuDays", NumberUtils.calcIcuDays(patient));
        row.put("admissionDoctor", getFirstValue(patient, "admissionDoctor", "admissionDoctorName", "bedDoctor"));
        row.put("attendingDoctor", getFirstValue(patient, "attendingDoctor", "attendingDoctorName", "bedDoctor", "chargeDoctorName", "tubeDoctorName"));
        row.put("admissionSource", getFirstValue(patient, "admissionSource", "inSource", "source"));
        row.put("dischargeType", getFirstValue(patient, "dischargedType", "dischargeType", "outType"));
        row.put("transferDept", getFirstValue(patient, "dischargedDepartment", "transferDept", "outDeptName"));
        row.put("diagnosis", getFirstValue(patient, "clinicalDiagnosis", "diagnosis", "admissionDiagnosis", "primaryDiagnosis"));
        row.putAll(extra);
        return row;
    }

    private String getFirstValue(Document doc, String... fields) {
        for (String f : fields) {
            Object v = doc.get(f);
            if (v != null && !String.valueOf(v).isEmpty()) return String.valueOf(v);
        }
        return "";
    }

    private double getQualityDataValue(String monthKey, String indicatorCode, String indicatorName) {
        String[] parts = monthKey.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        Document filter = new Document("deptCode", "all")
                .append("year", year)
                .append("month", month)
                .append("$or", Arrays.asList(
                        new Document("indicatorCode", indicatorCode),
                        new Document("indicator", indicatorName)));
        List<Document> docs = dataCenterMongo.find(new BasicQuery(filter), Document.class, CollectionConstants.VI_ICU_QUALITY);
        return docs.isEmpty() ? 0 : NumberUtils.safeNumber(docs.get(0).get("indicatorData"));
    }

    // ════════════════════════════════════════════════════════════════════
    // Map/structure helpers
    // ════════════════════════════════════════════════════════════════════

    private Map<String, List<Document>> buildItemsByQualityId(List<Document> items) {
        Map<String, List<Document>> map = new LinkedHashMap<>();
        for (Document item : items) {
            String key = normalizeIdKey(item.get("qualityId"));
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }
        for (List<Document> list : map.values()) {
            list.sort(Comparator.comparingDouble(i -> NumberUtils.safeNumber(i.get("order"))));
        }
        return map;
    }

    private Map<String, List<Document>> buildDetailsByItemId(List<Document> details) {
        Map<String, List<Document>> map = new LinkedHashMap<>();
        for (Document d : details) {
            String key = normalizeIdKey(d.get("itemId"));
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
        }
        return map;
    }

    /**
     * Normalize an ID value to a consistent string key.
     * Handles both ObjectId and String types.
     */
    private static String normalizeIdKey(Object value) {
        if (value == null) return "null";
        if (value instanceof ObjectId) return value.toString();
        String s = String.valueOf(value);
        // If it looks like a hex ObjectId, normalize it
        if (s.length() == 24 && s.matches("[0-9a-fA-F]{24}")) {
            return s.toLowerCase();
        }
        return s;
    }

    private Map<String, Document> buildMonthDocMap(List<Document> docs) {
        Map<String, Document> map = new LinkedHashMap<>();
        for (Document doc : docs) {
            Integer month = parseMonthFromFlag(doc.get("flag"));
            Object yearFlag = doc.get("yearFlag");
            if (yearFlag == null || month == null) continue;
            map.put(yearFlag + "-" + String.format("%02d", month), doc);
        }
        return map;
    }

    private Map<String, List<Document>> mergeItems(Map<String, List<Document>> items1, Map<String, List<Document>> items2) {
        Map<String, List<Document>> merged = new LinkedHashMap<>(items1);
        for (Map.Entry<String, List<Document>> e : items2.entrySet()) {
            merged.merge(e.getKey(), e.getValue(), (a, b) -> {
                List<Document> combined = new ArrayList<>(a);
                combined.addAll(b);
                return combined;
            });
        }
        return merged;
    }

    private List<Document> getItemsForDoc(Document doc, Map<String, List<Document>> itemsByQualityId) {
        return itemsByQualityId.getOrDefault(normalizeIdKey(doc.get("_id")), Collections.emptyList());
    }

    private boolean isOccupiedBedDayIndicator(Map<String, Object> spec) {
        String key = (String) spec.get("key");
        return "bedUsage".equals(key) || "avgLengthOfStay".equals(key);
    }

    private double sumCalc(Map<String, Map<String, Object>> stats, List<String> months, String field) {
        return months.stream().mapToDouble(m -> NumberUtils.safeNumber(stats.getOrDefault(m, Collections.emptyMap()).get(field))).sum();
    }

    private double sumCalcD(Map<String, Map<String, Object>> stats, List<String> months, String field) {
        return months.stream().mapToDouble(m -> NumberUtils.safeNumber(stats.getOrDefault(m, Collections.emptyMap()).get(field))).sum();
    }

    private double sumBasic(Map<String, Map<String, Integer>> stats, List<String> months, String field) {
        return months.stream().mapToDouble(m -> NumberUtils.safeNumber(stats.getOrDefault(m, Collections.emptyMap()).get(field))).sum();
    }

    // ════════════════════════════════════════════════════════════════════
    // Result construction
    // ════════════════════════════════════════════════════════════════════

    private Map<String, Object> detailResult(Map<String, Object> spec, List<Map<String, String>> columns, List<Map<String, Object>> rows) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("indicator", Map.of("key", spec.get("key"), "name", spec.get("name")));
        result.put("columns", columns);
        result.put("rows", rows);
        return result;
    }

    private List<Map<String, String>> summaryColumnsList() {
        return Arrays.asList(
                Map.of("key", "index", "title", "序号"),
                Map.of("key", "item", "title", "指标"),
                Map.of("key", "value", "title", "数值"),
                Map.of("key", "action", "title", "操作", "type", "action"));
    }

    private Map<String, Object> makeSummaryRow(int index, Object item, Object value, Object action) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("index", index);
        row.put("item", item);
        row.put("value", value);
        row.put("action", action);
        return row;
    }

    private Map<String, Object> makeAction(Map<String, Object> spec, String startMonth, String endMonth, int order) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("label", "查看详情");
        action.put("target", spec.get("key"));
        action.put("startMonth", startMonth);
        action.put("endMonth", endMonth);
        action.put("itemOrder", String.valueOf(order));
        return action;
    }

    private List<Map<String, Object>> prependIndicatorRatioRow(List<Map<String, Object>> rows, Map<String, Object> spec) {
        if (rows.isEmpty()) return rows;
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> ratioRow = new LinkedHashMap<>();
        ratioRow.put("index", 1);
        ratioRow.put("item", spec.get("name"));
        ratioRow.put("value", "");
        ratioRow.put("action", null);
        result.add(ratioRow);
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = new LinkedHashMap<>(rows.get(i));
            row.put("index", i + 2);
            result.add(row);
        }
        return result;
    }

    // ════════════════════════════════════════════════════════════════════
    // Static helpers
    // ════════════════════════════════════════════════════════════════════

    private static Map<String, Object> spec(int id, String key, String code, String newCode, String name, String type) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", id);
        s.put("key", key);
        s.put("code", code);
        s.put("newCode", newCode);
        s.put("name", name);
        s.put("type", type);
        return s;
    }

    @SafeVarargs
    private static Map<String, Object> columnMap(String... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return map;
    }

    private static long toLong(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static class Sort {
        static org.springframework.data.domain.Sort by(org.springframework.data.domain.Sort.Direction dir, String... properties) {
            return org.springframework.data.domain.Sort.by(dir, properties);
        }
    }
}
