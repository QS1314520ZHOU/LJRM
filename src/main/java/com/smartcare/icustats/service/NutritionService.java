package com.smartcare.icustats.service;

import com.smartcare.icustats.dto.MonthRange;
import com.smartcare.icustats.util.DateRangeUtils;
import com.smartcare.icustats.util.NumberUtils;
import com.smartcare.icustats.util.PatientUtils;
import org.bson.Document;
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
 * NutritionService - faithful Java migration of Node.js nutritionService.js
 * Handles nutrition statistics: enteral, parenteral, gastricTube, enteralExec, enteralPowder.
 */
@Service
public class NutritionService {

    private static final Logger log = LoggerFactory.getLogger(NutritionService.class);
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter YYYY_MM = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ── Constants ──────────────────────────────────────────────────────
    private static final List<Map<String, Object>> NUTRITION_INDICATORS = Arrays.asList(
            Map.of("id", 1, "name", "肠内营养统计", "key", "enteral", "unit", "人"),
            Map.of("id", 2, "name", "肠外营养统计", "key", "parenteral", "unit", "人"),
            Map.of("id", 3, "name", "胃肠管留置例数", "key", "gastricTube", "unit", "人"),
            Map.of("id", 4, "name", "肠内实施例数", "key", "enteralExec", "unit", "人"),
            Map.of("id", 5, "name", "使用肠内营养粉剂的例数", "key", "enteralPowder", "unit", "人")
    );

    private static final List<String> ENTERAL_KEYWORDS = Arrays.asList("肠内营养粉剂", "肠内营养混悬液");
    private static final List<String> ENTERAL_POWDER_KEYWORDS = Collections.singletonList("肠内营养粉剂");
    private static final List<String> PARENTERAL_KEYWORDS = Collections.singletonList("脂肪乳氨基酸");
    private static final List<String> GASTRIC_TUBE_TYPES = Arrays.asList("鼻肠管", "胃肠管", "胃管");

    private static final Map<String, String> LAB_ITEM_CODES = new LinkedHashMap<>();
    private static final Map<String, String> LAB_LABELS = new LinkedHashMap<>();
    private static final List<String> LAB_ORDER = Arrays.asList("albumin", "prealbumin", "crp", "il6");
    static {
        LAB_ITEM_CODES.put("albumin", "5353");
        LAB_ITEM_CODES.put("prealbumin", "5356");
        LAB_ITEM_CODES.put("crp", "5458");
        LAB_ITEM_CODES.put("il6", "5977");
        LAB_LABELS.put("albumin", "白蛋白");
        LAB_LABELS.put("prealbumin", "前白蛋白");
        LAB_LABELS.put("crp", "C反应蛋白");
        LAB_LABELS.put("il6", "白介素6");
    }

    private static final Set<String> SUPPORTED_NUTRITION_KEYS = new HashSet<>(Arrays.asList(
            "enteral", "parenteral", "gastricTube", "enteralExec", "enteralPowder"));

    // ── Column definitions ─────────────────────────────────────────────
    private static final List<Map<String, String>> NUTRITION_DETAIL_COLUMNS = columnList(
            "index", "序号", "department", "科室", "bedNo", "床号", "name", "姓名",
            "age", "年龄", "hospitalNo", "住院号", "icuAdmissionTime", "入科时间",
            "icuDischargeTime", "出科时间", "icuDays", "在科天数",
            "admissionDoctor", "收治医生", "attendingDoctor", "管床医生",
            "admissionSource", "入科来源", "dischargeType", "出科类型",
            "transferDept", "转出科室", "diagnosis", "临床诊断");

    private static final List<Map<String, String>> GASTRIC_TUBE_DETAIL_COLUMNS = columnList(
            "index", "序号", "department", "科室", "bedNo", "床号", "name", "姓名",
            "age", "年龄", "hospitalNo", "住院号", "icuAdmissionTime", "入科时间",
            "icuDischargeTime", "出科时间", "icuDays", "在科天数",
            "tubeType", "置管类型", "tubeEndTime", "置管结束时间",
            "admissionDoctor", "收治医生", "attendingDoctor", "管床医生",
            "admissionSource", "入科来源", "dischargeType", "出科类型",
            "transferDept", "转出科室", "diagnosis", "临床诊断");

    private static final List<Map<String, String>> DAILY_DETAIL_COLUMNS = columnList(
            "index", "序号", "department", "科室", "bedNo", "床号", "name", "姓名",
            "age", "年龄", "hospitalNo", "住院号", "icuAdmissionTime", "入科时间",
            "icuDischargeTime", "出科时间", "icuDays", "在科天数",
            "drugNames", "药名", "medicationTime", "用药时间",
            "liquidAmount", "剂量(mL)", "liquidAmountUnit", "单位",
            "admissionDoctor", "收治医生", "attendingDoctor", "管床医生",
            "admissionSource", "入科来源", "dischargeType", "出科类型",
            "transferDept", "转出科室", "diagnosis", "临床诊断");

    private static final List<Map<String, String>> ENTERAL_POWDER_DETAIL_COLUMNS = columnList(
            "index", "序号", "department", "科室", "bedNo", "床号", "name", "姓名",
            "age", "年龄", "hospitalNo", "住院号", "icuAdmissionTime", "入科时间",
            "icuDischargeTime", "出科时间", "icuDays", "在科天数",
            "drugNames", "药名", "medicationTime", "用药时间",
            "liquidAmount", "剂量(mL)", "liquidAmountUnit", "单位",
            "drugMethod", "执行用药方式",
            "albumin", "白蛋白", "prealbumin", "前白蛋白", "crp", "C反应蛋白", "il6", "白介素6",
            "admissionDoctor", "收治医生", "attendingDoctor", "管床医生",
            "admissionSource", "入科来源", "dischargeType", "出科类型",
            "transferDept", "转出科室", "diagnosis", "临床诊断");

    private static final List<Map<String, String>> ENTERAL_BASE_DETAIL_COLUMNS = columnList(
            "index", "序号", "department", "科室", "bedNo", "床号", "name", "姓名",
            "age", "年龄", "hospitalNo", "住院号", "icuAdmissionTime", "入科时间",
            "icuDischargeTime", "出科时间", "icuDays", "在科天数");

    private static final String[] DEPARTMENT_FIELDS = {
            "department", "deptName", "wardName", "inDeptName", "currentDeptName", "unitName"
    };

    private static final String[] PATIENT_SELECT = {
            "_id", "hisPid", "mrn", "name", "birthday", "age", "gender", "hisBed", "bedNo", "bedCode", "bedName", "bedNumber",
            "hospitalNo", "hospitalNumber", "zyh", "zyhm", "hospitalTime", "icuAdmissionTime", "icuDischargeTime",
            "department", "deptName", "wardName", "inDeptName", "currentDeptName", "unitName", "admissionDoctor",
            "admissionDoctorName", "attendingDoctor", "attendingDoctorName", "chargeDoctorName", "tubeDoctorName",
            "bedDoctor", "admissionSource", "inSource", "source", "dischargedType", "dischargeType", "outType",
            "dischargedDepartment", "transferDept", "outDeptName",
            "admissionDiagnosis", "diagnosis", "clinicalDiagnosis", "primaryDiagnosis", "status"
    };

    private final MongoTemplate smartCareMongo;
    private final MongoTemplate dataCenterMongo;

    @Autowired
    public NutritionService(
            @Qualifier("smartCareMongoTemplate") MongoTemplate smartCareMongo,
            @Qualifier("dataCenterMongoTemplate") MongoTemplate dataCenterMongo) {
        this.smartCareMongo = smartCareMongo;
        this.dataCenterMongo = dataCenterMongo;
    }

    // ════════════════════════════════════════════════════════════════════
    // Public API
    // ════════════════════════════════════════════════════════════════════

    public Map<String, Object> getYearStats(String year, String department) {
        int y = DateRangeUtils.validateYear(year);
        List<String> months = DateRangeUtils.getYearMonths(y);
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> indicator : NUTRITION_INDICATORS) {
            String key = (String) indicator.get("key");
            Map<String, Integer> monthMap = getIndicatorMonthlyCounts(key, y + "-01", y + "-12", department);
            int total = monthMap.values().stream().mapToInt(Integer::intValue).sum();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", indicator.get("id"));
            row.put("name", indicator.get("name"));
            row.put("key", indicator.get("key"));
            row.put("unit", indicator.get("unit"));
            row.put("total", total);
            row.put("months", monthMap);
            data.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("months", months);
        result.put("data", data);
        result.put("startMonth", y + "-01");
        result.put("endMonth", y + "-12");
        return result;
    }

    public Map<String, Object> getRangeStats(String startMonth, String endMonth, String department) {
        List<String> months = DateRangeUtils.buildMonths(startMonth, endMonth);
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> indicator : NUTRITION_INDICATORS) {
            String key = (String) indicator.get("key");
            Map<String, Integer> monthMap = getIndicatorMonthlyCounts(key, startMonth, endMonth, department);
            int total = monthMap.values().stream().mapToInt(Integer::intValue).sum();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", indicator.get("id"));
            row.put("name", indicator.get("name"));
            row.put("key", indicator.get("key"));
            row.put("unit", indicator.get("unit"));
            row.put("total", total);
            row.put("months", monthMap);
            data.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("months", months);
        result.put("data", data);
        result.put("startMonth", startMonth);
        result.put("endMonth", endMonth);
        return result;
    }

    public Map<String, Object> getDetail(String indicatorKey, String startMonth, String endMonth, String department) {
        if (!SUPPORTED_NUTRITION_KEYS.contains(indicatorKey)) {
            throw new IllegalArgumentException("营养指标不支持");
        }
        switch (indicatorKey) {
            case "gastricTube": return getGastricTubeDetail(startMonth, endMonth, department);
            case "enteral": return getEnteralDetail(startMonth, endMonth, department);
            case "enteralExec": return getEnteralExecDetail(startMonth, endMonth, department);
            case "enteralPowder": return getEnteralPowderDetail(startMonth, endMonth, department);
            case "parenteral": return getParenteralDetail(startMonth, endMonth, department);
            default: throw new IllegalArgumentException("营养指标不支持");
        }
    }

    public Map<String, Object> getDailyEnteral(String startDateStr, String endDateStr, String department) {
        DateRangeUtils.validateDate(startDateStr, "开始日期");
        DateRangeUtils.validateDate(endDateStr, "结束日期");
        List<String> days = DateRangeUtils.buildDateList(startDateStr, endDateStr);

        Date overallStart = Date.from(LocalDate.parse(days.get(0), YYYY_MM_DD)
                .atStartOfDay(SHANGHAI_ZONE).toInstant());
        Date overallEnd = Date.from(LocalDate.parse(days.get(days.size() - 1), YYYY_MM_DD)
                .atTime(23, 59, 59).atZone(SHANGHAI_ZONE).toInstant());

        List<Document> matchPipeline = buildDrugExeMatch(overallStart, overallEnd, ENTERAL_KEYWORDS, department);
        if (matchPipeline == null) {
            return emptyDailyResult(days, startDateStr, endDateStr);
        }

        // Aggregate by date+pid, then by date
        List<Document> allDocs = dataCenterMongo.find(
                new BasicQuery(new Document("$and", matchPipeline)), Document.class, "drugExe");

        Map<String, Set<String>> datePidMap = new LinkedHashMap<>();
        days.forEach(d -> datePidMap.put(d, new LinkedHashSet<>()));
        for (Document doc : allDocs) {
            Date startTime = NumberUtils.asDate(doc.get("startTime"));
            if (startTime == null) continue;
            String date = toShanghaiDateString(startTime);
            if (datePidMap.containsKey(date)) {
                String pid = NumberUtils.normalizeText(doc.get("pid"));
                if (!pid.isEmpty()) datePidMap.get(date).add(pid);
            }
        }

        List<Map<String, Object>> data = new ArrayList<>();
        for (String date : days) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date);
            item.put("count", datePidMap.getOrDefault(date, Collections.emptySet()).size());
            data.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("data", data);
        result.put("startDate", startDateStr);
        result.put("endDate", endDateStr);
        return result;
    }

    public Map<String, Object> getDailyEnteralDetail(String dateStr, String department) {
        DateRangeUtils.validateDate(dateStr, "日期");
        MonthRange range = DateRangeUtils.getDayRange(dateStr);

        List<Document> matchPipeline = buildDrugExeMatch(range.getStartDate(), range.getEndDate(), ENTERAL_KEYWORDS, department);
        Map<String, Object> indicator = Map.of("key", "dailyEnteral", "name", "每日肠内营养使用人数(" + dateStr + ")");

        if (matchPipeline == null) {
            return Map.of("indicator", indicator, "columns", DAILY_DETAIL_COLUMNS, "rows", Collections.emptyList());
        }

        List<Document> drugRecords = dataCenterMongo.find(
                new BasicQuery(new Document("$and", matchPipeline)), Document.class, "drugExe");

        if (drugRecords.isEmpty()) {
            return Map.of("indicator", indicator, "columns", DAILY_DETAIL_COLUMNS, "rows", Collections.emptyList());
        }

        List<String> pids = drugRecords.stream()
                .map(d -> NumberUtils.normalizeText(d.get("pid")))
                .filter(p -> !p.isEmpty()).distinct().collect(Collectors.toList());
        Map<String, Document> patientMap = fetchPatientMap(pids, department);

        List<Map<String, Object>> rows = drugRecords.stream()
                .map(record -> buildDailyDetailRow(record, patientMap, null))
                .sorted(Comparator.comparing(m -> (Date) m.getOrDefault("_sortTime", null),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).put("index", i + 1);
            rows.get(i).remove("_sortTime");
        }

        return Map.of("indicator", indicator, "columns", DAILY_DETAIL_COLUMNS, "rows", rows);
    }

    public Map<String, Object> getDailyEnteralRangeDetail(String startDateStr, String endDateStr, String department) {
        DateRangeUtils.validateDate(startDateStr, "开始日期");
        DateRangeUtils.validateDate(endDateStr, "结束日期");
        List<String> days = DateRangeUtils.buildDateList(startDateStr, endDateStr);

        Date overallStart = Date.from(LocalDate.parse(days.get(0), YYYY_MM_DD)
                .atStartOfDay(SHANGHAI_ZONE).toInstant());
        Date overallEnd = Date.from(LocalDate.parse(days.get(days.size() - 1), YYYY_MM_DD)
                .atTime(23, 59, 59).atZone(SHANGHAI_ZONE).toInstant());

        List<Document> matchPipeline = buildDrugExeMatch(overallStart, overallEnd, ENTERAL_KEYWORDS, department);

        List<Map<String, String>> columns = new ArrayList<>();
        columns.add(Map.of("key", "index", "title", "序号"));
        columns.add(Map.of("key", "statDate", "title", "统计日期"));
        for (int i = 1; i < DAILY_DETAIL_COLUMNS.size(); i++) {
            columns.add(DAILY_DETAIL_COLUMNS.get(i));
        }

        if (matchPipeline == null) {
            return Map.of("columns", (Object) columns, "rows", Collections.emptyList());
        }

        List<Document> drugRecords = dataCenterMongo.find(
                new BasicQuery(new Document("$and", matchPipeline)), Document.class, "drugExe");

        if (drugRecords.isEmpty()) {
            return Map.of("columns", (Object) columns, "rows", Collections.emptyList());
        }

        List<String> pids = drugRecords.stream()
                .map(d -> NumberUtils.normalizeText(d.get("pid")))
                .filter(p -> !p.isEmpty()).distinct().collect(Collectors.toList());
        Map<String, Document> patientMap = fetchPatientMap(pids, department);

        List<Map<String, Object>> rows = drugRecords.stream()
                .map(record -> {
                    Map<String, Object> row = buildDailyDetailRow(record, patientMap, null);
                    row.put("statDate", toShanghaiDateString(NumberUtils.asDate(record.get("startTime"))));
                    return row;
                })
                .sorted(Comparator.comparing(m -> (Date) m.getOrDefault("_sortTime", null),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).put("index", i + 1);
            rows.get(i).remove("_sortTime");
        }

        return Map.of("columns", (Object) columns, "rows", (Object) rows);
    }

    // ════════════════════════════════════════════════════════════════════
    // Monthly count dispatchers
    // ════════════════════════════════════════════════════════════════════

    private Map<String, Integer> getIndicatorMonthlyCounts(String indicatorKey, String startMonth, String endMonth, String department) {
        switch (indicatorKey) {
            case "enteral":
            case "parenteral":
                return getMonthlyCounts(startMonth, endMonth, indicatorKey, department);
            case "gastricTube":
                return getGastricTubeMonthlyCounts(startMonth, endMonth, department);
            case "enteralExec":
                return getEnteralExecMonthlyCounts(startMonth, endMonth, department);
            case "enteralPowder":
                return getEnteralPowderMonthlyCounts(startMonth, endMonth, department);
            default:
                throw new IllegalArgumentException("不支持的营养指标: " + indicatorKey);
        }
    }

    /** enteral/parenteral: DrugExe aggregate, pid dedup per month */
    private Map<String, Integer> getMonthlyCounts(String startMonth, String endMonth, String indicatorKey, String department) {
        List<String> months = DateRangeUtils.buildMonths(startMonth, endMonth);
        MonthRange fullRange = DateRangeUtils.getFullRange(startMonth, endMonth);
        List<String> keywords = "enteral".equals(indicatorKey) ? ENTERAL_KEYWORDS : PARENTERAL_KEYWORDS;

        List<Document> matchPipeline = buildDrugExeMatch(fullRange.getStartDate(), fullRange.getEndDate(), keywords, department);
        if (matchPipeline == null) return emptyMonthMap(months);

        List<Document> docs = dataCenterMongo.find(
                new BasicQuery(new Document("$and", matchPipeline)), Document.class, "drugExe");

        // pid dedup per month
        Map<String, Set<String>> monthPidMap = new LinkedHashMap<>();
        months.forEach(m -> monthPidMap.put(m, new LinkedHashSet<>()));
        for (Document doc : docs) {
            Date startTime = NumberUtils.asDate(doc.get("startTime"));
            if (startTime == null) continue;
            String month = toShanghaiMonthString(startTime);
            if (monthPidMap.containsKey(month)) {
                String pid = NumberUtils.normalizeText(doc.get("pid"));
                if (!pid.isEmpty()) monthPidMap.get(month).add(pid);
            }
        }

        Map<String, Integer> result = new LinkedHashMap<>();
        months.forEach(m -> result.put(m, monthPidMap.getOrDefault(m, Collections.emptySet()).size()));
        return result;
    }

    /** enteralExec: DrugExe, same keywords, no pid dedup (逐条计数) */
    private Map<String, Integer> getEnteralExecMonthlyCounts(String startMonth, String endMonth, String department) {
        List<String> months = DateRangeUtils.buildMonths(startMonth, endMonth);
        MonthRange fullRange = DateRangeUtils.getFullRange(startMonth, endMonth);

        List<Document> matchPipeline = buildDrugExeMatch(fullRange.getStartDate(), fullRange.getEndDate(), ENTERAL_KEYWORDS, department);
        if (matchPipeline == null) return emptyMonthMap(months);

        List<Document> docs = dataCenterMongo.find(
                new BasicQuery(new Document("$and", matchPipeline)), Document.class, "drugExe");

        Map<String, Integer> countMap = new LinkedHashMap<>();
        months.forEach(m -> countMap.put(m, 0));
        for (Document doc : docs) {
            Date startTime = NumberUtils.asDate(doc.get("startTime"));
            if (startTime == null) continue;
            String month = toShanghaiMonthString(startTime);
            countMap.merge(month, 1, Integer::sum);
        }
        return countMap;
    }

    /** enteralPowder: DrugExe, powder keywords only, no pid dedup */
    private Map<String, Integer> getEnteralPowderMonthlyCounts(String startMonth, String endMonth, String department) {
        List<String> months = DateRangeUtils.buildMonths(startMonth, endMonth);
        MonthRange fullRange = DateRangeUtils.getFullRange(startMonth, endMonth);

        List<Document> matchPipeline = buildDrugExeMatch(fullRange.getStartDate(), fullRange.getEndDate(), ENTERAL_POWDER_KEYWORDS, department);
        if (matchPipeline == null) return emptyMonthMap(months);

        List<Document> docs = dataCenterMongo.find(
                new BasicQuery(new Document("$and", matchPipeline)), Document.class, "drugExe");

        Map<String, Integer> countMap = new LinkedHashMap<>();
        months.forEach(m -> countMap.put(m, 0));
        for (Document doc : docs) {
            Date startTime = NumberUtils.asDate(doc.get("startTime"));
            if (startTime == null) continue;
            String month = toShanghaiMonthString(startTime);
            countMap.merge(month, 1, Integer::sum);
        }
        return countMap;
    }

    /** gastricTube: TubeExe with episode merging, effectiveEndTime-based monthly counts */
    private Map<String, Integer> getGastricTubeMonthlyCounts(String startMonth, String endMonth, String department) {
        List<String> months = DateRangeUtils.buildMonths(startMonth, endMonth);
        MonthRange fullRange = DateRangeUtils.getFullRange(startMonth, endMonth);

        // Fetch tube records
        Document tubeMatch = new Document("type", new Document("$in", GASTRIC_TUBE_TYPES))
                .append("valid", new Document("$ne", false));
        List<String> deptPids = getDepartmentPatientIds(department);
        if (deptPids != null) {
            tubeMatch.append("pid", new Document("$in", deptPids));
        }
        List<Document> tubeRecords = smartCareMongo.find(
                new BasicQuery(tubeMatch), Document.class, "tubeExe");

        if (tubeRecords.isEmpty()) return emptyMonthMap(months);

        // Compute effectiveEndTime for each record
        List<String> recordPids = tubeRecords.stream()
                .map(r -> NumberUtils.normalizeText(r.get("pid")))
                .filter(p -> !p.isEmpty()).distinct().collect(Collectors.toList());
        Map<String, Document> patientMap = fetchPatientMap(recordPids, department);

        List<Document> validRecords = new ArrayList<>();
        for (Document record : tubeRecords) {
            Date rawEnd = NumberUtils.asDate(record.get("endTime"));
            Date effectiveEnd;
            if (rawEnd != null) {
                effectiveEnd = rawEnd;
            } else {
                Document patient = patientMap.get(NumberUtils.normalizeText(record.get("pid")));
                effectiveEnd = patient != null ? NumberUtils.asDate(patient.get("icuDischargeTime")) : null;
            }
            if (effectiveEnd == null) continue;
            if (effectiveEnd.before(fullRange.getStartDate()) || effectiveEnd.after(fullRange.getEndDate())) continue;
            record.append("effectiveEndTime", effectiveEnd);
            validRecords.add(record);
        }

        if (validRecords.isEmpty()) return emptyMonthMap(months);

        // Build episodes via merging
        List<Document> episodes = buildGastricTubeEpisodes(validRecords);

        // Count by month of finalEndTime
        Map<String, Integer> countMap = new LinkedHashMap<>();
        months.forEach(m -> countMap.put(m, 0));
        for (Document ep : episodes) {
            Date endTime = (Date) ep.get("effectiveEndTime");
            if (endTime == null) continue;
            String month = toShanghaiMonthString(endTime);
            countMap.merge(month, 1, Integer::sum);
        }
        return countMap;
    }

    // ════════════════════════════════════════════════════════════════════
    // Detail methods
    // ════════════════════════════════════════════════════════════════════

    private Map<String, Object> getGastricTubeDetail(String startMonth, String endMonth, String department) {
        List<String> months = DateRangeUtils.buildMonths(startMonth, endMonth);
        MonthRange fullRange = DateRangeUtils.getFullRange(startMonth, endMonth);
        Map<String, Object> indicator = findIndicator("gastricTube");

        Document tubeMatch = new Document("type", new Document("$in", GASTRIC_TUBE_TYPES))
                .append("valid", new Document("$ne", false));
        List<String> deptPids = getDepartmentPatientIds(department);
        if (deptPids != null) tubeMatch.append("pid", new Document("$in", deptPids));

        List<Document> tubeRecords = smartCareMongo.find(new BasicQuery(tubeMatch), Document.class, "tubeExe");
        if (tubeRecords.isEmpty()) {
            return Map.of("indicator", indicator, "columns", GASTRIC_TUBE_DETAIL_COLUMNS, "rows", Collections.emptyList());
        }

        List<String> recordPids = tubeRecords.stream()
                .map(r -> NumberUtils.normalizeText(r.get("pid")))
                .filter(p -> !p.isEmpty()).distinct().collect(Collectors.toList());
        Map<String, Document> patientMap = fetchPatientMap(recordPids, department);

        List<Document> validRecords = new ArrayList<>();
        for (Document record : tubeRecords) {
            Date rawEnd = NumberUtils.asDate(record.get("endTime"));
            Date effectiveEnd;
            if (rawEnd != null) {
                effectiveEnd = rawEnd;
            } else {
                Document patient = patientMap.get(NumberUtils.normalizeText(record.get("pid")));
                effectiveEnd = patient != null ? NumberUtils.asDate(patient.get("icuDischargeTime")) : null;
            }
            if (effectiveEnd == null) continue;
            if (effectiveEnd.before(fullRange.getStartDate()) || effectiveEnd.after(fullRange.getEndDate())) continue;
            record.append("effectiveEndTime", effectiveEnd);
            validRecords.add(record);
        }

        if (validRecords.isEmpty()) {
            return Map.of("indicator", indicator, "columns", GASTRIC_TUBE_DETAIL_COLUMNS, "rows", Collections.emptyList());
        }

        List<Document> episodes = buildGastricTubeEpisodes(validRecords);
        if (episodes.isEmpty()) {
            return Map.of("indicator", indicator, "columns", GASTRIC_TUBE_DETAIL_COLUMNS, "rows", Collections.emptyList());
        }

        List<Map<String, Object>> rows = episodes.stream().map(episode -> {
            String pid = NumberUtils.normalizeText(episode.get("pid"));
            Document patient = patientMap.get(pid);
            Map<String, Object> base = patient != null ? toDetailRow(patient, 0) : emptyDetailRow();
            base.put("tubeType", NumberUtils.normalizeText(episode.get("type")));
            base.put("tubeEndTime", NumberUtils.formatDateTime(episode.get("effectiveEndTime")));
            Date sortTime = patient != null ? NumberUtils.asDate(patient.get("icuAdmissionTime")) : null;
            base.put("_sortTime", sortTime);
            return base;
        }).collect(Collectors.toList());

        rows.sort(Comparator.comparing(m -> (Date) m.getOrDefault("_sortTime", null),
                Comparator.nullsLast(Comparator.naturalOrder())));
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).put("index", i + 1);
            rows.get(i).remove("_sortTime");
        }

        return Map.of("indicator", indicator, "columns", GASTRIC_TUBE_DETAIL_COLUMNS, "rows", (Object) rows);
    }

    private Map<String, Object> getEnteralDetail(String startMonth, String endMonth, String department) {
        List<String> months = DateRangeUtils.buildMonths(startMonth, endMonth);
        MonthRange fullRange = DateRangeUtils.getFullRange(startMonth, endMonth);
        Map<String, Object> indicator = findIndicator("enteral");

        List<Document> matchPipeline = buildDrugExeMatch(fullRange.getStartDate(), fullRange.getEndDate(), ENTERAL_KEYWORDS, department);
        if (matchPipeline == null) {
            return Map.of("indicator", indicator, "columns", ENTERAL_BASE_DETAIL_COLUMNS, "rows", Collections.emptyList());
        }

        List<Document> rawRecords = dataCenterMongo.find(
                new BasicQuery(new Document("$and", matchPipeline)), Document.class, "drugExe");

        // pid + month dedup
        Set<String> seen = new HashSet<>();
        List<String> dedupedPids = new ArrayList<>();
        for (Document record : rawRecords) {
            String pid = NumberUtils.normalizeText(record.get("pid"));
            Date startTime = NumberUtils.asDate(record.get("startTime"));
            if (startTime == null) continue;
            String month = toShanghaiMonthString(startTime);
            if (month.compareTo(months.get(0)) < 0 || month.compareTo(months.get(months.size() - 1)) > 0) continue;
            String key = pid + "::" + month;
            if (seen.add(key)) dedupedPids.add(pid);
        }

        List<String> uniquePids = dedupedPids.stream().distinct().collect(Collectors.toList());
        if (uniquePids.isEmpty()) {
            return Map.of("indicator", indicator, "columns", ENTERAL_BASE_DETAIL_COLUMNS, "rows", Collections.emptyList());
        }

        Map<String, Document> patientMap = fetchPatientMap(uniquePids, department);
        List<Document> sortedPatients = uniquePids.stream()
                .map(patientMap::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(p -> NumberUtils.asDate(p.get("icuAdmissionTime")),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        // Build lab series map
        Map<String, Map<String, Object>> windowByMrn = new LinkedHashMap<>();
        Date queryStart = fullRange.getStartDate();
        Date queryEnd = fullRange.getEndDate();
        for (Document patient : sortedPatients) {
            String mrn = NumberUtils.normalizeText(firstValue(patient, new String[]{"mrn"}));
            if (mrn.isEmpty()) continue;
            Date admit = NumberUtils.asDate(patient.get("icuAdmissionTime"));
            Date discharge = NumberUtils.asDate(patient.get("icuDischargeTime"));
            if (discharge == null) discharge = new Date();
            Date winStart = new Date(Math.max(admit != null ? admit.getTime() : queryStart.getTime(), queryStart.getTime()));
            Date winEnd = new Date(Math.min(discharge.getTime(), queryEnd.getTime()));
            if (winEnd.before(winStart)) continue;
            Map<String, Object> win = new LinkedHashMap<>();
            win.put("start", winStart);
            win.put("end", winEnd);
            windowByMrn.put(mrn, Collections.singletonMap("window", win));
        }

        Map<String, Map<String, List<Map<String, Object>>>> labMap = buildLabSeriesMap(windowByMrn);

        // Calculate max counts per lab type
        Map<String, Integer> maxCounts = new LinkedHashMap<>();
        LAB_ORDER.forEach(k -> maxCounts.put(k, 0));
        for (Map<String, List<Map<String, Object>>> series : labMap.values()) {
            for (String k : LAB_ORDER) {
                List<Map<String, Object>> list = series.getOrDefault(k, Collections.emptyList());
                maxCounts.merge(k, list.size(), Math::max);
            }
        }

        List<Map<String, String>> columns = new ArrayList<>(ENTERAL_BASE_DETAIL_COLUMNS);
        columns.addAll(buildLabColumns(maxCounts));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int idx = 0; idx < sortedPatients.size(); idx++) {
            Document patient = sortedPatients.get(idx);
            Map<String, Object> base = toDetailRow(patient, idx + 1);
            String mrn = NumberUtils.normalizeText(firstValue(patient, new String[]{"mrn"}));
            Map<String, Object> cells = fillLabCells(labMap.get(mrn));
            base.putAll(cells);
            rows.add(base);
        }

        return Map.of("indicator", indicator, "columns", (Object) columns, "rows", (Object) rows);
    }

    private Map<String, Object> getEnteralExecDetail(String startMonth, String endMonth, String department) {
        List<String> months = DateRangeUtils.buildMonths(startMonth, endMonth);
        MonthRange fullRange = DateRangeUtils.getFullRange(startMonth, endMonth);
        Map<String, Object> indicator = findIndicator("enteralExec");

        List<Document> matchPipeline = buildDrugExeMatch(fullRange.getStartDate(), fullRange.getEndDate(), ENTERAL_KEYWORDS, department);
        if (matchPipeline == null) {
            return Map.of("indicator", indicator, "columns", DAILY_DETAIL_COLUMNS, "rows", Collections.emptyList());
        }

        List<Document> drugRecords = dataCenterMongo.find(
                new BasicQuery(new Document("$and", matchPipeline)), Document.class, "drugExe");

        // Filter to months range
        String firstMonth = months.get(0);
        String lastMonth = months.get(months.size() - 1);
        List<Document> filtered = drugRecords.stream().filter(r -> {
            Date startTime = NumberUtils.asDate(r.get("startTime"));
            if (startTime == null) return false;
            String m = toShanghaiMonthString(startTime);
            return m.compareTo(firstMonth) >= 0 && m.compareTo(lastMonth) <= 0;
        }).collect(Collectors.toList());

        if (filtered.isEmpty()) {
            return Map.of("indicator", indicator, "columns", DAILY_DETAIL_COLUMNS, "rows", Collections.emptyList());
        }

        List<String> pids = filtered.stream()
                .map(d -> NumberUtils.normalizeText(d.get("pid")))
                .filter(p -> !p.isEmpty()).distinct().collect(Collectors.toList());
        Map<String, Document> patientMap = fetchPatientMap(pids, department);

        List<Map<String, Object>> rows = filtered.stream()
                .map(record -> buildDailyDetailRow(record, patientMap, null))
                .sorted(Comparator.comparing(m -> (Date) m.getOrDefault("_sortTime", null),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).put("index", i + 1);
            rows.get(i).remove("_sortTime");
        }

        return Map.of("indicator", indicator, "columns", DAILY_DETAIL_COLUMNS, "rows", (Object) rows);
    }

    private Map<String, Object> getEnteralPowderDetail(String startMonth, String endMonth, String department) {
        List<String> months = DateRangeUtils.buildMonths(startMonth, endMonth);
        MonthRange fullRange = DateRangeUtils.getFullRange(startMonth, endMonth);
        Map<String, Object> indicator = findIndicator("enteralPowder");

        List<Document> matchPipeline = buildDrugExeMatch(fullRange.getStartDate(), fullRange.getEndDate(), ENTERAL_POWDER_KEYWORDS, department);
        if (matchPipeline == null) {
            return Map.of("indicator", indicator, "columns", ENTERAL_POWDER_DETAIL_COLUMNS, "rows", Collections.emptyList());
        }

        // Load drug method map
        Map<String, String> methodMap = getDrugMethodMap();

        List<Document> drugRecords = dataCenterMongo.find(
                new BasicQuery(new Document("$and", matchPipeline)), Document.class, "drugExe");

        String firstMonth = months.get(0);
        String lastMonth = months.get(months.size() - 1);
        List<Document> filtered = drugRecords.stream().filter(r -> {
            Date startTime = NumberUtils.asDate(r.get("startTime"));
            if (startTime == null) return false;
            String m = toShanghaiMonthString(startTime);
            return m.compareTo(firstMonth) >= 0 && m.compareTo(lastMonth) <= 0;
        }).collect(Collectors.toList());

        if (filtered.isEmpty()) {
            return Map.of("indicator", indicator, "columns", ENTERAL_POWDER_DETAIL_COLUMNS, "rows", Collections.emptyList());
        }

        List<String> pids = filtered.stream()
                .map(d -> NumberUtils.normalizeText(d.get("pid")))
                .filter(p -> !p.isEmpty()).distinct().collect(Collectors.toList());
        Map<String, Document> patientMap = fetchPatientMap(pids, department);

        // Batch load lab values
        List<String> mrns = pids.stream()
                .map(pid -> {
                    Document patient = patientMap.get(pid);
                    return patient != null ? NumberUtils.normalizeText(firstValue(patient, new String[]{"mrn"})) : "";
                })
                .filter(m -> !m.isEmpty()).distinct().collect(Collectors.toList());
        Map<String, List<Map<String, Object>>> labMap = buildLabValueMap(mrns);

        List<Map<String, Object>> rows = filtered.stream()
                .map(record -> {
                    String pid = NumberUtils.normalizeText(record.get("pid"));
                    Document patient = patientMap.get(pid);
                    Map<String, Object> base = patient != null ? toDetailRow(patient, 0) : emptyDetailRow();
                    base.put("drugNames", extractDrugNames(record));
                    base.put("medicationTime", NumberUtils.formatDateTime(record.get("startTime")));
                    base.put("liquidAmount", record.get("liquidAmount") != null ? record.get("liquidAmount") : "");
                    base.put("liquidAmountUnit", record.get("liquidAmountUnit") != null ? record.get("liquidAmountUnit") : "");
                    base.put("drugMethod", methodMap.getOrDefault(NumberUtils.normalizeText(record.get("methodCode")), ""));
                    String mrn = patient != null ? NumberUtils.normalizeText(firstValue(patient, new String[]{"mrn"})) : "";
                    Map<String, Object> labVals = pickLabValuesSameDay(labMap.get(mrn), NumberUtils.asDate(record.get("startTime")));
                    base.putAll(labVals);
                    Date sortTime = patient != null ? NumberUtils.asDate(patient.get("icuAdmissionTime")) : null;
                    base.put("_sortTime", sortTime);
                    return base;
                })
                .sorted(Comparator.comparing(m -> (Date) m.getOrDefault("_sortTime", null),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).put("index", i + 1);
            rows.get(i).remove("_sortTime");
        }

        return Map.of("indicator", indicator, "columns", ENTERAL_POWDER_DETAIL_COLUMNS, "rows", (Object) rows);
    }

    private Map<String, Object> getParenteralDetail(String startMonth, String endMonth, String department) {
        List<String> months = DateRangeUtils.buildMonths(startMonth, endMonth);
        MonthRange fullRange = DateRangeUtils.getFullRange(startMonth, endMonth);
        Map<String, Object> indicator = findIndicator("parenteral");

        List<Document> matchPipeline = buildDrugExeMatch(fullRange.getStartDate(), fullRange.getEndDate(), PARENTERAL_KEYWORDS, department);
        if (matchPipeline == null) {
            return Map.of("indicator", indicator, "columns", NUTRITION_DETAIL_COLUMNS, "rows", Collections.emptyList());
        }

        List<Document> rawRecords = dataCenterMongo.find(
                new BasicQuery(new Document("$and", matchPipeline)), Document.class, "drugExe");

        Set<String> seen = new HashSet<>();
        List<String> dedupedPids = new ArrayList<>();
        for (Document record : rawRecords) {
            String pid = NumberUtils.normalizeText(record.get("pid"));
            Date startTime = NumberUtils.asDate(record.get("startTime"));
            if (startTime == null) continue;
            String month = toShanghaiMonthString(startTime);
            if (month.compareTo(months.get(0)) < 0 || month.compareTo(months.get(months.size() - 1)) > 0) continue;
            String key = pid + "::" + month;
            if (seen.add(key)) dedupedPids.add(pid);
        }

        List<String> uniquePids = dedupedPids.stream().distinct().collect(Collectors.toList());
        if (uniquePids.isEmpty()) {
            return Map.of("indicator", indicator, "columns", NUTRITION_DETAIL_COLUMNS, "rows", Collections.emptyList());
        }

        Map<String, Document> patientMap = fetchPatientMap(uniquePids, department);
        List<Map<String, Object>> rows = uniquePids.stream()
                .map(patientMap::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(p -> NumberUtils.asDate(p.get("icuAdmissionTime")),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList()).stream()
                .map(p -> toDetailRow(p, 0))
                .collect(Collectors.toList());
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).put("index", i + 1);
        }

        return Map.of("indicator", indicator, "columns", NUTRITION_DETAIL_COLUMNS, "rows", (Object) rows);
    }

    // ════════════════════════════════════════════════════════════════════
    // Gastric tube episode merging
    // ════════════════════════════════════════════════════════════════════

    private List<Document> buildGastricTubeEpisodes(List<Document> tubeRecords) {
        // Group by pid::type
        Map<String, List<Document>> groups = new LinkedHashMap<>();
        for (Document record : tubeRecords) {
            String pid = NumberUtils.normalizeText(record.get("pid"));
            String type = NumberUtils.normalizeText(record.get("type"));
            if (pid.isEmpty() || type.isEmpty()) continue;
            String groupKey = pid + "::" + type;
            groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(record);
        }

        List<Document> episodes = new ArrayList<>();
        for (List<Document> records : groups.values()) {
            // Sort by startTime ascending
            records.sort((a, b) -> {
                Date aTime = NumberUtils.asDate(a.get("startTime"));
                Date bTime = NumberUtils.asDate(b.get("startTime"));
                if (aTime == null && bTime == null) return 0;
                if (aTime == null) return 1;
                if (bTime == null) return -1;
                return aTime.compareTo(bTime);
            });

            Document currentEp = null;
            for (Document record : records) {
                Date recEnd = (Date) record.get("effectiveEndTime");
                if (recEnd == null) continue;
                Date recStart = NumberUtils.asDate(record.get("startTime"));

                if (currentEp != null) {
                    Date epEnd = (Date) currentEp.get("effectiveEndTime");
                    if (recStart != null && epEnd != null && recStart.getTime() == epEnd.getTime()) {
                        // Merge: update endpoint
                        currentEp.put("effectiveEndTime", recEnd);
                        continue;
                    }
                    // Not adjacent → finish current episode
                    episodes.add(currentEp);
                }
                // Start new episode
                currentEp = new Document("pid", NumberUtils.normalizeText(record.get("pid")))
                        .append("type", NumberUtils.normalizeText(record.get("type")))
                        .append("effectiveEndTime", recEnd);
            }
            if (currentEp != null) episodes.add(currentEp);
        }
        return episodes;
    }

    // ════════════════════════════════════════════════════════════════════
    // Lab value helpers
    // ════════════════════════════════════════════════════════════════════

    /** Batch load lab values: mrn → list of {itemCode, result, authTime} */
    private Map<String, List<Map<String, Object>>> buildLabValueMap(List<String> mrns) {
        Map<String, List<Map<String, Object>>> map = new LinkedHashMap<>();
        if (mrns.isEmpty()) return map;

        Query examQuery = new Query(Criteria.where("mrn").in(mrns)
                .and("valid").ne(false));
        examQuery.fields().include("reportID").include("mrn").include("authTime");
        List<Document> exams = dataCenterMongo.find(examQuery, Document.class, "viIcuExam");

        if (exams.isEmpty()) return map;

        Map<String, Document> examMap = new LinkedHashMap<>();
        for (Document e : exams) {
            examMap.put(String.valueOf(e.get("reportID")), e);
        }

        List<String> examIds = exams.stream().map(e -> String.valueOf(e.get("reportID"))).collect(Collectors.toList());
        List<String> itemCodes = new ArrayList<>(LAB_ITEM_CODES.values());

        Query itemQuery = new Query(Criteria.where("examID").in(examIds)
                .and("itemCode").in(itemCodes));
        itemQuery.fields().include("examID").include("itemCode").include("result");
        List<Document> items = dataCenterMongo.find(itemQuery, Document.class, "viIcuExamItem");

        for (Document it : items) {
            Document exam = examMap.get(String.valueOf(it.get("examID")));
            if (exam == null) continue;
            String mrn = String.valueOf(exam.get("mrn"));
            map.computeIfAbsent(mrn, k -> new ArrayList<>()).add(
                    Map.of("itemCode", String.valueOf(it.get("itemCode")),
                            "result", it.get("result") != null ? it.get("result") : "",
                            "authTime", NumberUtils.asDate(exam.get("authTime"))));
        }
        return map;
    }

    /** Build lab series map for enteral detail (filtered by ICU window) */
    private Map<String, Map<String, List<Map<String, Object>>>> buildLabSeriesMap(
            Map<String, Map<String, Object>> windowByMrn) {
        List<String> mrns = new ArrayList<>(windowByMrn.keySet());
        if (mrns.isEmpty()) return Collections.emptyMap();

        Map<String, List<Map<String, Object>>> labMap = buildLabValueMap(mrns);

        // Convert flat lab list to series by mrn, filtering by window
        Map<String, Map<String, List<Map<String, Object>>>> result = new LinkedHashMap<>();
        Map<String, String> codeToKey = new LinkedHashMap<>();
        LAB_ITEM_CODES.forEach((k, c) -> codeToKey.put(c, k));

        for (Map.Entry<String, List<Map<String, Object>>> entry : labMap.entrySet()) {
            String mrn = entry.getKey();
            Map<String, Object> windowInfo = windowByMrn.get(mrn);
            if (windowInfo == null) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> win = (Map<String, Object>) windowInfo.get("window");
            if (win == null) continue;
            Date winStart = (Date) win.get("start");
            Date winEnd = (Date) win.get("end");

            Map<String, List<Map<String, Object>>> series = new LinkedHashMap<>();
            LAB_ORDER.forEach(k -> series.put(k, new ArrayList<>()));

            for (Map<String, Object> lab : entry.getValue()) {
                String itemCode = (String) lab.get("itemCode");
                String key = codeToKey.get(itemCode);
                if (key == null) continue;
                Date authTime = (Date) lab.get("authTime");
                if (authTime == null) continue;
                if (winStart != null && authTime.before(winStart)) continue;
                if (winEnd != null && authTime.after(winEnd)) continue;
                series.get(key).add(lab);
            }

            // Sort each series by authTime
            for (String k : LAB_ORDER) {
                series.get(k).sort(Comparator.comparing(m -> (Date) m.getOrDefault("authTime", null),
                        Comparator.nullsLast(Comparator.naturalOrder())));
            }
            result.put(mrn, series);
        }
        return result;
    }

    /** Pick same-day lab values for a medication time */
    private Map<String, Object> pickLabValuesSameDay(List<Map<String, Object>> labList, Date medicationTime) {
        Map<String, Object> out = new LinkedHashMap<>();
        LAB_ORDER.forEach(k -> out.put(k, ""));
        if (labList == null || labList.isEmpty() || medicationTime == null) return out;

        String day = toShanghaiDateString(medicationTime);
        for (Map.Entry<String, String> entry : LAB_ITEM_CODES.entrySet()) {
            String key = entry.getKey();
            String code = entry.getValue();
            labList.stream()
                    .filter(x -> code.equals(x.get("itemCode"))
                            && x.get("authTime") != null
                            && day.equals(toShanghaiDateString((Date) x.get("authTime"))))
                    .max(Comparator.comparing(x -> (Date) x.get("authTime")))
                    .ifPresent(x -> out.put(key, x.get("result") != null ? x.get("result") : ""));
        }
        return out;
    }

    /** Build lab columns dynamically based on max counts */
    private List<Map<String, String>> buildLabColumns(Map<String, Integer> maxCounts) {
        List<Map<String, String>> cols = new ArrayList<>();
        for (String key : LAB_ORDER) {
            int n = maxCounts.getOrDefault(key, 0);
            for (int i = 1; i <= n; i++) {
                String suffix = n > 1 ? "(" + i + ")" : "";
                cols.add(Map.of("key", key + "_time_" + i, "title", LAB_LABELS.get(key) + "结果时间" + suffix));
                cols.add(Map.of("key", key + "_" + i, "title", LAB_LABELS.get(key) + suffix));
            }
        }
        return cols;
    }

    /** Fill lab cells for a patient row */
    private Map<String, Object> fillLabCells(Map<String, List<Map<String, Object>>> series) {
        Map<String, Object> cell = new LinkedHashMap<>();
        if (series == null) {
            LAB_ORDER.forEach(k -> cell.put(k, ""));
            return cell;
        }
        for (String key : LAB_ORDER) {
            List<Map<String, Object>> list = series.getOrDefault(key, Collections.emptyList());
            for (int idx = 0; idx < list.size(); idx++) {
                int i = idx + 1;
                Map<String, Object> rec = list.get(idx);
                Date authTime = (Date) rec.get("authTime");
                cell.put(key + "_time_" + i, authTime != null ? NumberUtils.formatDateTime(authTime) : "");
                cell.put(key + "_" + i, rec.get("result") != null ? rec.get("result") : "");
            }
        }
        return cell;
    }

    // ════════════════════════════════════════════════════════════════════
    // Query helpers
    // ════════════════════════════════════════════════════════════════════

    private List<Document> buildDrugExeMatch(Date startDate, Date endDate, List<String> keywords, String department) {
        List<Document> and = new ArrayList<>();
        and.add(new Document("status", new Document("$ne", "invalid")));
        and.add(new Document("startTime", new Document("$gte", startDate).append("$lte", endDate)));

        List<Document> keywordOr = new ArrayList<>();
        for (String kw : keywords) {
            keywordOr.add(new Document("drugList.name", new Document("$regex", Pattern.quote(kw))));
        }
        and.add(new Document("$or", keywordOr));

        boolean enableDeptFilter = "true".equals(System.getenv().getOrDefault("ENABLE_DEPT_FILTER", ""));
        if (enableDeptFilter && department != null && !department.isEmpty()) {
            List<String> pids = getDepartmentPatientIds(department);
            if (pids != null && pids.isEmpty()) return null; // no patients in dept
            if (pids != null) and.add(new Document("pid", new Document("$in", pids)));
        }
        return and;
    }

    private List<String> getDepartmentPatientIds(String department) {
        boolean enableDeptFilter = "true".equals(System.getenv().getOrDefault("ENABLE_DEPT_FILTER", ""));
        if (!enableDeptFilter || department == null || department.isEmpty()) return null;
        List<Document> deptOr = new ArrayList<>();
        String escaped = Pattern.quote(department);
        for (String field : DEPARTMENT_FIELDS) {
            deptOr.add(new Document(field, new Document("$regex", escaped).append("$options", "i")));
        }
        Document filter = new Document("$or", deptOr);
        filter.append("status", new Document("$ne", "invalid"));
        List<Document> patients = smartCareMongo.find(new BasicQuery(filter), Document.class, "patient");
        return patients.stream().map(p -> String.valueOf(p.get("_id"))).collect(Collectors.toList());
    }

    private Map<String, Document> fetchPatientMap(List<String> pids, String department) {
        if (pids.isEmpty()) return Collections.emptyMap();
        boolean enableDeptFilter = "true".equals(System.getenv().getOrDefault("ENABLE_DEPT_FILTER", ""));
        Document extra = new Document("_id", new Document("$in", pids));
        Document filter = PatientUtils.buildPatientFilter(extra, department, enableDeptFilter);
        Query query = new BasicQuery(filter);
        query.fields().include(PATIENT_SELECT);
        List<Document> patients = smartCareMongo.find(query, Document.class, "patient");
        Map<String, Document> map = new LinkedHashMap<>();
        for (Document p : patients) {
            map.put(String.valueOf(p.get("_id")), p);
        }
        return map;
    }

    private Map<String, String> getDrugMethodMap() {
        List<Document> list = smartCareMongo.find(new Query(), Document.class, "configDrugMethod");
        Map<String, String> map = new LinkedHashMap<>();
        for (Document doc : list) {
            map.put(NumberUtils.normalizeText(doc.get("code")), NumberUtils.normalizeText(doc.get("name")));
        }
        return map;
    }

    // ════════════════════════════════════════════════════════════════════
    // Row building helpers
    // ════════════════════════════════════════════════════════════════════

    private Map<String, Object> toDetailRow(Document patient, int index) {
        return toDetailRow(patient, index, null);
    }

    private Map<String, Object> toDetailRow(Document patient, int index, Map<String, Object> extra) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("index", index);
        row.put("department", firstValue(patient, new String[]{
                "department", "deptName", "wardName", "inDeptName", "currentDeptName", "unitName"}, "重症医学科"));
        row.put("bedNo", firstValue(patient, new String[]{"hisBed"}, ""));
        row.put("name", firstValue(patient, new String[]{"name"}, ""));
        row.put("age", NumberUtils.calcAge(patient));
        row.put("hospitalNo", firstValue(patient, new String[]{"mrn"}, ""));
        row.put("icuAdmissionTime", NumberUtils.formatDateTime(patient.get("icuAdmissionTime")));
        row.put("icuDischargeTime", NumberUtils.formatDateTime(patient.get("icuDischargeTime")));
        row.put("icuDays", NumberUtils.calcIcuDays(patient));
        row.put("admissionDoctor", firstValue(patient, new String[]{"bedDoctor"}, ""));
        row.put("attendingDoctor", firstValue(patient, new String[]{"bedDoctor"}, ""));
        row.put("admissionSource", firstValue(patient, new String[]{"admissionSource", "inSource", "source"}, ""));
        row.put("dischargeType", firstValue(patient, new String[]{"dischargedType"}, ""));
        row.put("transferDept", firstValue(patient, new String[]{"dischargedDepartment"}, ""));
        row.put("diagnosis", firstValue(patient, new String[]{
                "clinicalDiagnosis", "diagnosis", "admissionDiagnosis", "primaryDiagnosis"}, ""));
        if (extra != null) row.putAll(extra);
        return row;
    }

    private Map<String, Object> emptyDetailRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("index", 0);
        row.put("department", "");
        row.put("bedNo", "");
        row.put("name", "");
        row.put("age", "");
        row.put("hospitalNo", "");
        row.put("icuAdmissionTime", "");
        row.put("icuDischargeTime", "");
        row.put("icuDays", "");
        row.put("admissionDoctor", "");
        row.put("attendingDoctor", "");
        row.put("admissionSource", "");
        row.put("dischargeType", "");
        row.put("transferDept", "");
        row.put("diagnosis", "");
        return row;
    }

    private Map<String, Object> buildDailyDetailRow(Document record, Map<String, Document> patientMap, Map<String, Object> extra) {
        String pid = NumberUtils.normalizeText(record.get("pid"));
        Document patient = patientMap.get(pid);
        Map<String, Object> base = patient != null ? toDetailRow(patient, 0) : emptyDetailRow();
        base.put("drugNames", extractDrugNames(record));
        base.put("medicationTime", NumberUtils.formatDateTime(record.get("startTime")));
        base.put("liquidAmount", record.get("liquidAmount") != null ? record.get("liquidAmount") : "");
        base.put("liquidAmountUnit", record.get("liquidAmountUnit") != null ? record.get("liquidAmountUnit") : "");
        Date sortTime = patient != null ? NumberUtils.asDate(patient.get("icuAdmissionTime")) : null;
        base.put("_sortTime", sortTime);
        if (extra != null) base.putAll(extra);
        return base;
    }

    @SuppressWarnings("unchecked")
    private String extractDrugNames(Document record) {
        Object drugListObj = record.get("drugList");
        if (drugListObj == null) return "";
        if (drugListObj instanceof List) {
            return ((List<?>) drugListObj).stream()
                    .filter(d -> d instanceof Map)
                    .map(d -> NumberUtils.normalizeText(((Map<?, ?>) d).get("name")))
                    .filter(n -> !n.isEmpty())
                    .collect(Collectors.joining("、"));
        }
        return "";
    }

    private String firstValue(Document doc, String[] fields) {
        for (String field : fields) {
            Object value = doc.get(field);
            if (value != null && !"".equals(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    private String firstValue(Document doc, String[] fields, String defaultValue) {
        String v = firstValue(doc, fields);
        return v.isEmpty() ? defaultValue : v;
    }

    // ════════════════════════════════════════════════════════════════════
    // Formatting / utility
    // ════════════════════════════════════════════════════════════════════

    private Map<String, Object> findIndicator(String key) {
        for (Map<String, Object> ind : NUTRITION_INDICATORS) {
            if (key.equals(ind.get("key"))) return ind;
        }
        return Map.of("key", key, "name", key);
    }

    private Map<String, Integer> emptyMonthMap(List<String> months) {
        Map<String, Integer> map = new LinkedHashMap<>();
        months.forEach(m -> map.put(m, 0));
        return map;
    }

    private Map<String, Object> emptyDailyResult(List<String> days, String startDate, String endDate) {
        List<Map<String, Object>> data = new ArrayList<>();
        days.forEach(d -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", d);
            item.put("count", 0);
            data.add(item);
        });
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("data", data);
        result.put("startDate", startDate);
        result.put("endDate", endDate);
        return result;
    }

    private String toShanghaiMonthString(Date date) {
        if (date == null) return "";
        java.time.ZonedDateTime zdt = date.toInstant().atZone(SHANGHAI_ZONE);
        return zdt.format(YYYY_MM);
    }

    private String toShanghaiDateString(Date date) {
        if (date == null) return "";
        java.time.ZonedDateTime zdt = date.toInstant().atZone(SHANGHAI_ZONE);
        return zdt.format(YYYY_MM_DD);
    }

    private static List<Map<String, String>> columnList(String... pairs) {
        List<Map<String, String>> cols = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            cols.add(Map.of("key", pairs[i], "title", pairs[i + 1]));
        }
        return cols;
    }
}
