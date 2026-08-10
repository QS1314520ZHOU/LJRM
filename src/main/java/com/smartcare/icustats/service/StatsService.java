package com.smartcare.icustats.service;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * StatsService - Faithful migration of Node.js statsService.js
 * Original file: statsService.js
 * Migrated to Java Spring Boot with MongoTemplate
 */
@Service
public class StatsService {

    private static final int EAST_8_OFFSET_MINUTES = 8 * 60;
    private static final ZoneOffset ZONE_OFFSET = ZoneOffset.ofHours(8);
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // Original Node.js: INDICATORS constant
    private static final List<Map<String, Object>> INDICATORS = Arrays.asList(
        Map.of("id", 1, "name", "体外人工膜肺（ECMO）", "key", "ecmo", "unit", "例"),
        Map.of("id", 2, "name", "有创呼吸机支持≥96小时", "key", "ventilatorGte96", "unit", "例"),
        Map.of("id", 3, "name", "有创呼吸机支持＜96小时", "key", "ventilatorLt96", "unit", "例"),
        Map.of("id", 4, "name", "有创呼吸机支持≥96小时伴CRRT", "key", "ventilatorCrrt", "unit", "例")
    );

    // Original Node.js: DETAIL_COLUMNS constant
    private static final List<Map<String, String>> DETAIL_COLUMNS = Arrays.asList(
        Map.of("key", "index", "title", "序号"),
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
        Map.of("key", "diagnosis", "title", "临床诊断")
    );

    // Original Node.js: DURATION_COLUMN constant
    private static final Map<String, String> DURATION_COLUMN = Map.of("key", "durationCount", "title", "呼吸通气时长");

    // Original Node.js: SUPPORTED_KEYS constant
    private static final Set<String> SUPPORTED_KEYS = new HashSet<>(Arrays.asList(
        "ecmo", "ventilatorGte96", "ventilatorLt96", "ventilatorCrrt"
    ));

    // Original Node.js: DEPARTMENT_FIELDS constant
    private static final List<String> DEPARTMENT_FIELDS = Arrays.asList(
        "department", "deptName", "wardName", "inDeptName", "currentDeptName", "unitName"
    );

    // Original Node.js: PATIENT_SELECT constant
    private static final List<String> PATIENT_SELECT = Arrays.asList(
        "_id", "hisPid", "mrn", "name", "birthday", "age", "gender", "hisBed", "bedNo", "bedCode", "bedName", "bedNumber",
        "hospitalNo", "hospitalNumber", "zyh", "zyhm", "hospitalTime", "icuAdmissionTime", "icuDischargeTime",
        "department", "deptName", "wardName", "inDeptName", "currentDeptName", "unitName", "admissionDoctor",
        "admissionDoctorName", "attendingDoctor", "attendingDoctorName", "chargeDoctorName", "tubeDoctorName",
        "bedDoctor", "admissionSource", "inSource", "source", "dischargedType", "dischargeType", "outType",
        "dischargedDepartment", "transferDept", "outDeptName",
        "admissionDiagnosis", "diagnosis", "clinicalDiagnosis", "primaryDiagnosis",
        "status"
    );

    private final MongoTemplate smartCareMongoTemplate;
    private final MongoTemplate dataCenterMongoTemplate;

    public StatsService(
            @Qualifier("smartCareMongoTemplate") MongoTemplate smartCareMongoTemplate,
            @Qualifier("dataCenterMongoTemplate") MongoTemplate dataCenterMongoTemplate) {
        this.smartCareMongoTemplate = smartCareMongoTemplate;
        this.dataCenterMongoTemplate = dataCenterMongoTemplate;
    }

    /**
     * Original Node.js: escapeRegExp(text)
     */
    private String escapeRegExp(String text) {
        return text.replaceAll("([.*+?^${}()|\\[\\]\\\\])", "\\\\$&");
    }

    /**
     * Original Node.js: buildDepartmentOr(department)
     */
    private List<Document> buildDepartmentOr(String department) {
        if (!"true".equals(System.getenv().getOrDefault("ENABLE_DEPT_FILTER", "")) || department == null || department.isEmpty()) {
            return Collections.emptyList();
        }
        String escaped = escapeRegExp(department);
        Pattern pattern = Pattern.compile(escaped, Pattern.CASE_INSENSITIVE);
        List<Document> orConditions = new ArrayList<>();
        for (String field : DEPARTMENT_FIELDS) {
            orConditions.add(new Document(field, pattern));
        }
        return orConditions;
    }

    /**
     * Original Node.js: buildAdmissionRangeFilter(startDate, endDate, department)
     */
    private Document buildAdmissionRangeFilter(Date startDate, Date endDate, String department) {
        List<Document> and = new ArrayList<>();
        and.add(new Document("icuAdmissionTime", new Document("$gte", startDate).append("$lte", endDate)));
        and.add(new Document("status", new Document("$ne", "invalid")));

        List<Document> deptOr = buildDepartmentOr(department);
        if (!deptOr.isEmpty()) {
            and.add(new Document("$or", deptOr));
        }
        return new Document("$and", and);
    }

    /**
     * Original Node.js: buildPatientFilter(extra, department)
     */
    private Document buildPatientFilter(Document extra, String department) {
        List<Document> and = new ArrayList<>();
        and.add(new Document("status", new Document("$ne", "invalid")));
        and.add(extra != null ? extra : new Document());

        List<Document> deptOr = buildDepartmentOr(department);
        if (!deptOr.isEmpty()) {
            and.add(new Document("$or", deptOr));
        }
        return new Document("$and", and);
    }

    /**
     * Original Node.js: buildOrderFilter(extra)
     */
    private Document buildOrderFilter(Document extra) {
        Document filter = extra != null ? new Document(extra) : new Document();
        filter.append("status", new Document("$ne", "作废"));
        return filter;
    }

    /**
     * Original Node.js: buildBedsideFilter(extra)
     */
    private Document buildBedsideFilter(Document extra) {
        Document filter = extra != null ? new Document(extra) : new Document();
        filter.append("valid", new Document("$ne", false));
        return filter;
    }

    /**
     * Original Node.js: validateYear(year)
     */
    private int validateYear(String year) {
        int n;
        try {
            n = Integer.parseInt(year);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("年份格式不正确");
        }
        if (n < 2000 || n > 2099) {
            throw new IllegalArgumentException("年份格式不正确");
        }
        return n;
    }

    /**
     * Original Node.js: validateMonth(month, fieldName)
     */
    private String validateMonth(String month, String fieldName) {
        try {
            LocalDate.parse(month + "-01", DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            throw new IllegalArgumentException(fieldName + "格式不正确，应为 YYYY-MM");
        }
        return month;
    }

    /**
     * Original Node.js: buildMonths(startMonth, endMonth)
     */
    private List<String> buildMonths(String startMonth, String endMonth) {
        validateMonth(startMonth, "开始月份");
        validateMonth(endMonth, "结束月份");

        LocalDate cur = LocalDate.parse(startMonth + "-01");
        LocalDate end = LocalDate.parse(endMonth + "-01");

        if (cur.isAfter(end)) {
            throw new IllegalArgumentException("开始月份不能晚于结束月份");
        }

        long monthsBetween = ChronoUnit.MONTHS.between(cur, end);
        if (monthsBetween > 36) {
            throw new IllegalArgumentException("查询范围不能超过 36 个月");
        }

        List<String> months = new ArrayList<>();
        while (!cur.isAfter(end)) {
            months.add(cur.format(MONTH_FORMATTER));
            cur = cur.plusMonths(1);
        }
        return months;
    }

    /**
     * Original Node.js: getMonthRange(monthKey)
     */
    private Map<String, Date> getMonthRange(String monthKey) {
        LocalDate firstDay = LocalDate.parse(monthKey + "-01");
        LocalDateTime startDateTime = firstDay.atStartOfDay();
        LocalDateTime endDateTime = firstDay.withDayOfMonth(firstDay.lengthOfMonth()).atTime(23, 59, 59);

        Date startDate = Date.from(startDateTime.toInstant(ZONE_OFFSET));
        Date endDate = Date.from(endDateTime.toInstant(ZONE_OFFSET));

        return Map.of("startDate", startDate, "endDate", endDate);
    }

    /**
     * Original Node.js: normalizeText(value)
     */
    private String normalizeText(Object value) {
        return String.valueOf(value != null ? value : "").trim();
    }

    /**
     * Original Node.js: asDate(value)
     */
    private Date asDate(Object value) {
        if (value == null) return null;
        if (value instanceof Date) return (Date) value;
        if (value instanceof String) {
            try {
                return Date.from(Instant.parse((String) value));
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Original Node.js: formatDateTime(value)
     */
    private String formatDateTime(Object value) {
        Date date = asDate(value);
        if (date == null) return "";
        Instant instant = date.toInstant();
        LocalDateTime localDateTime = instant.atZone(ZONE_OFFSET).toLocalDateTime();
        return localDateTime.format(DATE_FORMATTER);
    }

    /**
     * Original Node.js: firstValue(doc, fields)
     */
    private Object firstValue(Document doc, List<String> fields) {
        for (String field : fields) {
            Object value = doc.get(field);
            if (value != null && !value.toString().isEmpty()) {
                return value;
            }
        }
        return "";
    }

    /**
     * Original Node.js: calcAge(patient)
     */
    private String calcAge(Document patient) {
        Object explicitAge = firstValue(patient, Arrays.asList("age"));
        if (!"".equals(explicitAge)) {
            String ageStr = String.valueOf(explicitAge);
            return ageStr.contains("岁") ? ageStr : ageStr + "岁";
        }

        Date birthday = asDate(patient.get("birthday"));
        if (birthday == null) return "";

        Instant now = Instant.now();
        Instant birthdayInstant = birthday.toInstant();
        long years = ChronoUnit.YEARS.between(
            birthdayInstant.atZone(ZONE_OFFSET).toLocalDate(),
            now.atZone(ZONE_OFFSET).toLocalDate()
        );
        return years + "岁";
    }

    /**
     * Original Node.js: calcIcuDays(patient)
     */
    private String calcIcuDays(Document patient) {
        Date start = asDate(patient.get("icuAdmissionTime"));
        if (start == null) return "";

        Date end = asDate(patient.get("icuDischargeTime"));
        if (end == null) end = new Date();

        Instant startInstant = start.toInstant();
        Instant endInstant = end.toInstant();

        long days = ChronoUnit.DAYS.between(
            startInstant.atZone(ZONE_OFFSET).toLocalDate(),
            endInstant.atZone(ZONE_OFFSET).toLocalDate()
        ) + 1;

        return Math.max(1, days) + "天";
    }

    /**
     * Original Node.js: toDetailRow(patient, index, extra)
     */
    private Map<String, Object> toDetailRow(Document patient, int index, Map<String, Object> extra) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("index", index);
        row.put("department", firstValue(patient, Arrays.asList("department", "deptName", "wardName", "inDeptName", "currentDeptName", "unitName")));
        if (row.get("department").toString().isEmpty()) {
            row.put("department", "重症医学科");
        }
        row.put("bedNo", firstValue(patient, Arrays.asList("hisBed")));
        row.put("name", firstValue(patient, Arrays.asList("name")));
        row.put("age", calcAge(patient));
        row.put("hospitalNo", firstValue(patient, Arrays.asList("mrn")));
        row.put("icuAdmissionTime", formatDateTime(patient.get("icuAdmissionTime")));
        row.put("icuDischargeTime", formatDateTime(patient.get("icuDischargeTime")));
        row.put("ecmoOrderTime", formatDateTime(patient.get("ecmoOrderTime")));
        row.put("icuDays", calcIcuDays(patient));
        row.put("admissionDoctor", firstValue(patient, Arrays.asList("bedDoctor")));
        row.put("attendingDoctor", firstValue(patient, Arrays.asList("bedDoctor")));
        row.put("admissionSource", firstValue(patient, Arrays.asList("admissionSource", "inSource", "source")));
        row.put("dischargeType", firstValue(patient, Arrays.asList("dischargedType")));
        row.put("transferDept", firstValue(patient, Arrays.asList("dischargedDepartment")));
        row.put("diagnosis", firstValue(patient, Arrays.asList("clinicalDiagnosis", "diagnosis", "admissionDiagnosis", "primaryDiagnosis")));

        if (extra != null) {
            row.putAll(extra);
        }
        return row;
    }

    /**
     * Original Node.js: getAdmissionPatients(startDate, endDate, department)
     */
    private List<Document> getAdmissionPatients(Date startDate, Date endDate, String department) {
        Document filter = buildAdmissionRangeFilter(startDate, endDate, department);
        Query query = new BasicQuery(filter);
        query.fields().include(PATIENT_SELECT.toArray(new String[0]));
        return smartCareMongoTemplate.find(query, Document.class, "patient");
    }

    /**
     * Original Node.js: getEcmoPatients(startDate, endDate, department)
     */
    private List<Document> getEcmoPatients(Date startDate, Date endDate, String department) {
        Document orderFilter = buildOrderFilter(new Document("orderTime", new Document("$gte", startDate).append("$lte", endDate)));
        orderFilter.append("orderName", "体外人工膜肺（ECMO）安装术");

        Query orderQuery = new BasicQuery(orderFilter);
        orderQuery.fields().include("mrn").include("orderTime");
        List<Document> orders = dataCenterMongoTemplate.find(orderQuery, Document.class, "order");

        Map<String, List<Date>> mrnToOrderTimes = new HashMap<>();
        for (Document order : orders) {
            String mrn = normalizeText(order.get("mrn"));
            Date time = asDate(order.get("orderTime"));
            if (mrn.isEmpty() || time == null) continue;
            mrnToOrderTimes.computeIfAbsent(mrn, k -> new ArrayList<>()).add(time);
        }

        for (List<Date> times : mrnToOrderTimes.values()) {
            Collections.sort(times);
        }

        List<String> mrns = new ArrayList<>(mrnToOrderTimes.keySet());
        if (mrns.isEmpty()) return Collections.emptyList();

        Document patientFilter = buildPatientFilter(new Document("mrn", new Document("$in", mrns)), department);
        Query patientQuery = new BasicQuery(patientFilter);
        patientQuery.fields().include(PATIENT_SELECT.toArray(new String[0]));
        List<Document> patients = smartCareMongoTemplate.find(patientQuery, Document.class, "patient");

        Map<String, List<Document>> patientsByMrn = new HashMap<>();
        for (Document patient : patients) {
            String mrn = normalizeText(patient.get("mrn"));
            if (mrn.isEmpty()) continue;
            patientsByMrn.computeIfAbsent(mrn, k -> new ArrayList<>()).add(patient);
        }

        List<Document> matchedAdmissions = new ArrayList<>();
        Set<String> added = new HashSet<>();

        for (Map.Entry<String, List<Document>> entry : patientsByMrn.entrySet()) {
            String mrn = entry.getKey();
            List<Document> mrnPatients = entry.getValue();
            List<Date> orderTimes = mrnToOrderTimes.getOrDefault(mrn, Collections.emptyList());

            if (orderTimes.isEmpty()) continue;

            if (mrnPatients.size() == 1) {
                Document patient = mrnPatients.get(0);
                String key = String.valueOf(patient.get("_id"));
                if (!added.contains(key)) {
                    added.add(key);
                    Document result = new Document(patient);
                    result.put("ecmoOrderTime", orderTimes.get(0));
                    matchedAdmissions.add(result);
                }
                continue;
            }

            for (Document patient : mrnPatients) {
                Date start = asDate(patient.get("icuAdmissionTime"));
                Date end = asDate(patient.get("icuDischargeTime"));
                if (end == null) end = new Date();
                if (start == null) continue;

                Instant startWithTolerance = start.toInstant().minus(12, ChronoUnit.HOURS);
                Date matchedTime = null;
                for (Date time : orderTimes) {
                    Instant timeInstant = time.toInstant();
                    if (!timeInstant.isBefore(startWithTolerance) && !timeInstant.isAfter(end.toInstant())) {
                        matchedTime = time;
                        break;
                    }
                }

                if (matchedTime == null) continue;

                String key = String.valueOf(patient.get("_id"));
                if (added.contains(key)) continue;

                added.add(key);
                Document result = new Document(patient);
                result.put("ecmoOrderTime", matchedTime);
                matchedAdmissions.add(result);
            }
        }

        return matchedAdmissions;
    }

    /**
     * Original Node.js: isValidVentilatorValue(value)
     */
    private boolean isValidVentilatorValue(Object value) {
        String text = normalizeText(value).toUpperCase();
        return !text.isEmpty() && !"S/T".equals(text) && !"BIPAP".equals(text);
    }

    /**
     * Original Node.js: isNotBlank(value)
     */
    private boolean isNotBlank(Object value) {
        return !normalizeText(value).isEmpty();
    }

    /**
     * Original Node.js: groupByPid(events)
     */
    private Map<String, List<Document>> groupByPid(List<Document> events) {
        Map<String, List<Document>> grouped = new HashMap<>();
        for (Document event : events) {
            String pid = normalizeText(event.get("pid"));
            if (pid.isEmpty()) continue;
            grouped.computeIfAbsent(pid, k -> new ArrayList<>()).add(event);
        }
        return grouped;
    }

    /**
     * Original Node.js: eventTime(event)
     */
    private Date eventTime(Document event) {
        Date time = asDate(event.get("time"));
        if (time != null) return time;
        return asDate(event.get("editTime"));
    }

    /**
     * Original Node.js: isWithinIcu(event, patient)
     */
    private boolean isWithinIcu(Document event, Document patient) {
        Date time = eventTime(event);
        Date start = asDate(patient.get("icuAdmissionTime"));
        Date end = asDate(patient.get("icuDischargeTime"));
        if (end == null) end = new Date();

        if (time == null || start == null) return false;

        return !time.before(start) && !time.after(end);
    }

    /**
     * Original Node.js: calcCappedRecordCount(events, patient)
     */
    private int calcCappedRecordCount(List<Document> events, Document patient) {
        Map<String, Integer> dailyCount = new HashMap<>();
        for (Document event : events) {
            if (!isValidVentilatorValue(event.get("strVal")) || !isWithinIcu(event, patient)) continue;

            Date eventDate = eventTime(event);
            String day = eventDate.toInstant().atZone(ZONE_OFFSET).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
            dailyCount.merge(day, 1, Integer::sum);
        }

        int total = 0;
        for (int count : dailyCount.values()) {
            total += Math.min(count, 24);
        }
        return total;
    }

    /**
     * Original Node.js: getVentilatorStatsPatients(startDate, endDate, department)
     */
    private List<Map<String, Object>> getVentilatorStatsPatients(Date startDate, Date endDate, String department) {
        List<Document> patients = getAdmissionPatients(startDate, endDate, department);
        List<String> pids = patients.stream()
            .map(p -> String.valueOf(p.get("_id")))
            .collect(Collectors.toList());

        if (pids.isEmpty()) return Collections.emptyList();

        Document bedsideFilter = buildBedsideFilter(new Document("pid", new Document("$in", pids))
            .append("code", "param_HuXiMoShi")
            .append("strVal", new Document("$exists", true).append("$nin", Arrays.asList("", null, "S/T", "BIPAP", "s/t", "bipap"))));

        Query bedsideQuery = new BasicQuery(bedsideFilter);
        bedsideQuery.fields().include("pid").include("code").include("strVal").include("time").include("editTime");
        List<Document> events = dataCenterMongoTemplate.find(bedsideQuery, Document.class, "bedside");

        Map<String, List<Document>> grouped = groupByPid(events);

        return patients.stream()
            .map(patient -> {
                String pid = String.valueOf(patient.get("_id"));
                int count = calcCappedRecordCount(grouped.getOrDefault(pid, Collections.emptyList()), patient);
                Map<String, Object> item = new HashMap<>();
                item.put("patient", patient);
                item.put("count", count);
                return item;
            })
            .filter(item -> (int) item.get("count") > 0)
            .collect(Collectors.toList());
    }

    /**
     * Original Node.js: getVentilatorPatientsByThreshold(startDate, endDate, department, predicate)
     */
    private List<Document> getVentilatorPatientsByThreshold(Date startDate, Date endDate, String department, ThresholdPredicate predicate) {
        List<Map<String, Object>> stats = getVentilatorStatsPatients(startDate, endDate, department);
        return stats.stream()
            .filter(item -> predicate.test((int) item.get("count")))
            .map(item -> (Document) item.get("patient"))
            .collect(Collectors.toList());
    }

    /**
     * Original Node.js: getVentilatorStatsByThreshold(startDate, endDate, department, predicate)
     */
    private List<Map<String, Object>> getVentilatorStatsByThreshold(Date startDate, Date endDate, String department, ThresholdPredicate predicate) {
        List<Map<String, Object>> stats = getVentilatorStatsPatients(startDate, endDate, department);
        return stats.stream()
            .filter(item -> predicate.test((int) item.get("count")))
            .collect(Collectors.toList());
    }

    /**
     * Original Node.js: getVentilatorCrrtPatients(startDate, endDate, department)
     */
    private List<Document> getVentilatorCrrtPatients(Date startDate, Date endDate, String department) {
        List<Map<String, Object>> stats = getVentilatorStatsByThreshold(startDate, endDate, department, count -> count >= 96);
        if (stats.isEmpty()) return Collections.emptyList();

        Map<String, Document> patientsByPid = new HashMap<>();
        for (Map<String, Object> item : stats) {
            Document patient = (Document) item.get("patient");
            patientsByPid.put(String.valueOf(patient.get("_id")), patient);
        }

        List<String> pids = new ArrayList<>(patientsByPid.keySet());

        Document crrtFilter = buildBedsideFilter(new Document("pid", new Document("$in", pids))
            .append("code", "param_CBP_set_Blood_Flow")
            .append("strVal", new Document("$exists", true).append("$nin", Arrays.asList("", null))));

        Query crrtQuery = new BasicQuery(crrtFilter);
        crrtQuery.fields().include("pid").include("code").include("strVal").include("time").include("editTime");
        List<Document> crrtEvents = dataCenterMongoTemplate.find(crrtQuery, Document.class, "bedside");

        Set<String> matched = new HashSet<>();
        for (Document event : crrtEvents) {
            if (!isNotBlank(event.get("strVal"))) continue;
            Document patient = patientsByPid.get(normalizeText(event.get("pid")));
            if (patient != null && isWithinIcu(event, patient)) {
                matched.add(String.valueOf(patient.get("_id")));
            }
        }

        return stats.stream()
            .filter(item -> matched.contains(String.valueOf(((Document) item.get("patient")).get("_id"))))
            .map(item -> (Document) item.get("patient"))
            .collect(Collectors.toList());
    }

    /**
     * Original Node.js: getVentilatorCrrtStatsPatients(startDate, endDate, department)
     */
    private List<Map<String, Object>> getVentilatorCrrtStatsPatients(Date startDate, Date endDate, String department) {
        List<Map<String, Object>> stats = getVentilatorStatsByThreshold(startDate, endDate, department, count -> count >= 96);
        if (stats.isEmpty()) return Collections.emptyList();

        Map<String, Document> patientsByPid = new HashMap<>();
        for (Map<String, Object> item : stats) {
            Document patient = (Document) item.get("patient");
            patientsByPid.put(String.valueOf(patient.get("_id")), patient);
        }

        List<String> pids = new ArrayList<>(patientsByPid.keySet());

        Document crrtFilter = buildBedsideFilter(new Document("pid", new Document("$in", pids))
            .append("code", "param_CBP_set_Blood_Flow")
            .append("strVal", new Document("$exists", true).append("$nin", Arrays.asList("", null))));

        Query crrtQuery = new BasicQuery(crrtFilter);
        crrtQuery.fields().include("pid").include("code").include("strVal").include("time").include("editTime");
        List<Document> crrtEvents = dataCenterMongoTemplate.find(crrtQuery, Document.class, "bedside");

        Set<String> matched = new HashSet<>();
        for (Document event : crrtEvents) {
            if (!isNotBlank(event.get("strVal"))) continue;
            Document patient = patientsByPid.get(normalizeText(event.get("pid")));
            if (patient != null && isWithinIcu(event, patient)) {
                matched.add(String.valueOf(patient.get("_id")));
            }
        }

        return stats.stream()
            .filter(item -> matched.contains(String.valueOf(((Document) item.get("patient")).get("_id"))))
            .collect(Collectors.toList());
    }

    /**
     * Original Node.js: getIndicatorPatients(indicatorKey, startDate, endDate, department)
     */
    private List<Document> getIndicatorPatients(String indicatorKey, Date startDate, Date endDate, String department) {
        switch (indicatorKey) {
            case "ecmo":
                return getEcmoPatients(startDate, endDate, department);
            case "ventilatorGte96":
                return getVentilatorPatientsByThreshold(startDate, endDate, department, count -> count >= 96);
            case "ventilatorLt96":
                return getVentilatorPatientsByThreshold(startDate, endDate, department, count -> count < 96);
            case "ventilatorCrrt":
                return getVentilatorCrrtPatients(startDate, endDate, department);
            default:
                throw new IllegalArgumentException("指标不支持");
        }
    }

    /**
     * Original Node.js: calculateIndicator(indicatorKey, startDate, endDate, department)
     */
    private int calculateIndicator(String indicatorKey, Date startDate, Date endDate, String department) {
        List<Document> patients = getIndicatorPatients(indicatorKey, startDate, endDate, department);
        return patients.size();
    }

    /**
     * Original Node.js: buildRows(months, department)
     */
    private List<Map<String, Object>> buildRows(List<String> months, String department) {
        List<Map<String, Object>> rows = new ArrayList<>();

        for (Map<String, Object> indicator : INDICATORS) {
            String key = (String) indicator.get("key");
            Map<String, Integer> monthMap = new LinkedHashMap<>();
            int total = 0;

            for (String monthKey : months) {
                Map<String, Date> range = getMonthRange(monthKey);
                int value = calculateIndicator(key, range.get("startDate"), range.get("endDate"), department);
                monthMap.put(monthKey, value);
                total += value;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", indicator.get("id"));
            row.put("name", indicator.get("name"));
            row.put("key", indicator.get("key"));
            row.put("unit", indicator.get("unit"));
            row.put("total", total);
            row.put("months", monthMap);
            rows.add(row);
        }

        return rows;
    }

    /**
     * 返回指标列表
     * Original Node.js: ok(res, statsService.INDICATORS)
     */
    public List<Map<String, Object>> getIndicators() {
        return INDICATORS;
    }

    /**
     * Original Node.js: getYearStats(year, department)
     */
    public Map<String, Object> getYearStats(String year, String department) {
        int y = validateYear(year);

        List<String> months = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            months.add(String.format("%d-%02d", y, i));
        }

        List<Map<String, Object>> data = buildRows(months, department);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("months", months);
        result.put("data", data);
        result.put("startMonth", y + "-01");
        result.put("endMonth", y + "-12");
        return result;
    }

    /**
     * Original Node.js: getRangeStats(startMonth, endMonth, department)
     */
    public Map<String, Object> getRangeStats(String startMonth, String endMonth, String department) {
        List<String> months = buildMonths(startMonth, endMonth);
        List<Map<String, Object>> data = buildRows(months, department);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("months", months);
        result.put("data", data);
        result.put("startMonth", startMonth);
        result.put("endMonth", endMonth);
        return result;
    }

    /**
     * Original Node.js: getDetailColumns(indicatorKey)
     */
    private List<Map<String, String>> getDetailColumns(String indicatorKey) {
        if ("ventilatorGte96".equals(indicatorKey) || "ventilatorLt96".equals(indicatorKey) || "ventilatorCrrt".equals(indicatorKey)) {
            int insertIndex = -1;
            for (int i = 0; i < DETAIL_COLUMNS.size(); i++) {
                if ("icuDays".equals(DETAIL_COLUMNS.get(i).get("key"))) {
                    insertIndex = i + 1;
                    break;
                }
            }

            List<Map<String, String>> columns = new ArrayList<>();
            columns.addAll(DETAIL_COLUMNS.subList(0, insertIndex));
            columns.add(DURATION_COLUMN);
            columns.addAll(DETAIL_COLUMNS.subList(insertIndex, DETAIL_COLUMNS.size()));
            return columns;
        }
        return DETAIL_COLUMNS;
    }

    /**
     * Original Node.js: getDetail(indicatorKey, startMonth, endMonth, department)
     */
    public Map<String, Object> getDetail(String indicatorKey, String startMonth, String endMonth, String department) {
        if (!SUPPORTED_KEYS.contains(indicatorKey)) {
            throw new IllegalArgumentException("指标不支持");
        }

        List<String> months = buildMonths(startMonth, endMonth);

        Optional<Map<String, Object>> indicatorOpt = INDICATORS.stream()
            .filter(i -> indicatorKey.equals(i.get("key")))
            .findFirst();

        if (!indicatorOpt.isPresent()) {
            throw new IllegalArgumentException("指标不支持");
        }

        Map<String, Object> indicator = indicatorOpt.get();

        LocalDate firstMonth = LocalDate.parse(months.get(0) + "-01");
        LocalDate lastMonth = LocalDate.parse(months.get(months.size() - 1) + "-01");

        Date startDate = Date.from(firstMonth.atStartOfDay().toInstant(ZONE_OFFSET));
        Date endDate = Date.from(lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()).atTime(23, 59, 59).toInstant(ZONE_OFFSET));

        List<Map<String, String>> columns = getDetailColumns(indicatorKey);

        if ("ventilatorGte96".equals(indicatorKey) || "ventilatorLt96".equals(indicatorKey)) {
            ThresholdPredicate predicate = "ventilatorGte96".equals(indicatorKey) ? count -> count >= 96 : count -> count < 96;
            List<Map<String, Object>> stats = getVentilatorStatsByThreshold(startDate, endDate, department, predicate);

            List<Map<String, Object>> sorted = stats.stream()
                .map(item -> {
                    Map<String, Object> mapped = new LinkedHashMap<>();
                    mapped.put("patient", item.get("patient"));
                    mapped.put("count", item.get("count"));
                    mapped.put("sortTime", asDate(((Document) item.get("patient")).get("icuAdmissionTime")));
                    return mapped;
                })
                .sorted((a, b) -> {
                    Date timeA = (Date) a.get("sortTime");
                    Date timeB = (Date) b.get("sortTime");
                    if (timeA == null && timeB == null) return 0;
                    if (timeA == null) return 1;
                    if (timeB == null) return -1;
                    return timeA.compareTo(timeB);
                })
                .collect(Collectors.toList());

            List<Map<String, Object>> rows = new ArrayList<>();
            for (int idx = 0; idx < sorted.size(); idx++) {
                Map<String, Object> item = sorted.get(idx);
                Document patient = (Document) item.get("patient");
                int count = (int) item.get("count");
                rows.add(toDetailRow(patient, idx + 1, Map.of("durationCount", count)));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("indicator", indicator);
            result.put("columns", columns);
            result.put("rows", rows);
            return result;
        }

        if ("ventilatorCrrt".equals(indicatorKey)) {
            List<Map<String, Object>> stats = getVentilatorCrrtStatsPatients(startDate, endDate, department);

            List<Map<String, Object>> sorted = stats.stream()
                .map(item -> {
                    Map<String, Object> mapped = new LinkedHashMap<>();
                    mapped.put("patient", item.get("patient"));
                    mapped.put("count", item.get("count"));
                    mapped.put("sortTime", asDate(((Document) item.get("patient")).get("icuAdmissionTime")));
                    return mapped;
                })
                .sorted((a, b) -> {
                    Date timeA = (Date) a.get("sortTime");
                    Date timeB = (Date) b.get("sortTime");
                    if (timeA == null && timeB == null) return 0;
                    if (timeA == null) return 1;
                    if (timeB == null) return -1;
                    return timeA.compareTo(timeB);
                })
                .collect(Collectors.toList());

            List<Map<String, Object>> rows = new ArrayList<>();
            for (int idx = 0; idx < sorted.size(); idx++) {
                Map<String, Object> item = sorted.get(idx);
                Document patient = (Document) item.get("patient");
                int count = (int) item.get("count");
                rows.add(toDetailRow(patient, idx + 1, Map.of("durationCount", count)));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("indicator", indicator);
            result.put("columns", columns);
            result.put("rows", rows);
            return result;
        }

        List<Document> patients = getIndicatorPatients(indicatorKey, startDate, endDate, department);

        List<Map<String, Object>> sortedPatients = patients.stream()
            .map(p -> {
                Map<String, Object> mapped = new LinkedHashMap<>();
                mapped.put("patient", p);
                mapped.put("sortTime", asDate(p.get("icuAdmissionTime")));
                return mapped;
            })
            .sorted((a, b) -> {
                Date timeA = (Date) a.get("sortTime");
                Date timeB = (Date) b.get("sortTime");
                if (timeA == null && timeB == null) return 0;
                if (timeA == null) return 1;
                if (timeB == null) return -1;
                return timeA.compareTo(timeB);
            })
            .collect(Collectors.toList());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int idx = 0; idx < sortedPatients.size(); idx++) {
            Map<String, Object> item = sortedPatients.get(idx);
            Document patient = (Document) item.get("patient");
            rows.add(toDetailRow(patient, idx + 1, null));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("indicator", indicator);
        result.put("columns", columns);
        result.put("rows", rows);
        return result;
    }

    /**
     * Functional interface for threshold predicates
     */
    @FunctionalInterface
    private interface ThresholdPredicate {
        boolean test(int count);
    }
}