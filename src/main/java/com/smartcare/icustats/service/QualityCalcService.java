package com.smartcare.icustats.service;

import com.smartcare.icustats.config.CollectionConstants;
import com.smartcare.icustats.config.IcuStatsProperties;
import com.smartcare.icustats.dto.MonthRange;
import com.smartcare.icustats.util.DateRangeUtils;
import com.smartcare.icustats.util.NumberUtils;
import com.smartcare.icustats.util.PatientUtils;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Quality Calculation Service - faithful Java migration of Node.js qualityCalcService.js.
 * Calculates ICU quality indicators from Patient, Order, Score, TubeExe, QualityData collections.
 *
 * MongoDB collections:
 *   Patient   (SmartCare):  "patient"
 *   Order     (DataCenter): "VI_ICU_ZYYZ"
 *   Score     (SmartCare):  "score"
 *   TubeExe   (SmartCare):  "tubeExe"
 *   QualityData (DataCenter): "VI_ICU_QUALITY"
 */
@Service
public class QualityCalcService {

    private static final Logger log = LoggerFactory.getLogger(QualityCalcService.class);

    private static final long HOURS_48_MS = 48L * 3600 * 1000;

    private static final String[] DVT_DEVICE = {"肢体气压治疗", "梯度压力弹力袜", "腔静脉滤器"};
    private static final String[] DVT_HEPARIN = {"低分子肝素钠", "低分子肝素钙", "那屈肝素", "依诺肝素", "达肝素钠注射液"};
    private static final String[] DVT_RIVA = {"利伐沙班"};

    @Autowired
    @Qualifier("smartCareMongoTemplate")
    private MongoTemplate smartCareMongo;

    @Autowired
    @Qualifier("dataCenterMongoTemplate")
    private MongoTemplate dataCenterMongo;

    @Autowired
    private IcuStatsProperties properties;

    // ==================== Public API ====================

    /**
     * Calculate month start/end Date range (Asia/Shanghai timezone).
     * Original JS: monthRange(monthKey)
     */
    public MonthRange monthRange(String monthKey) {
        return DateRangeUtils.getMonthRange(monthKey);
    }

    /**
     * Get patients who were in ICU at any point during the given month.
     * Original JS: getInIcuPatients(monthKey, department)
     * Respects ENABLE_DEPT_FILTER configuration.
     */
    @SuppressWarnings("unchecked")
    public List<Document> getInIcuPatients(String monthKey, String department) {
        MonthRange range = monthRange(monthKey);
        boolean enableDeptFilter = properties.isEnableDeptFilter();
        Document filter = PatientUtils.buildMonthlyOverlapFilter(
                range.getStartDate(), range.getEndDate(), department, enableDeptFilter);

        log.info("QUALITY_CALC getInIcuPatients month={} department={} enableDeptFilter={}",
                monthKey, department, enableDeptFilter);

        List<Document> patients = smartCareMongo.find(new BasicQuery(filter), Document.class, CollectionConstants.PATIENT);

        long mrnCount = patients.stream()
                .filter(p -> p.getString("mrn") != null && !p.getString("mrn").trim().isEmpty())
                .count();
        log.info("QUALITY_CALC getInIcuPatients result: patientCount={} patientWithMrnCount={}",
                patients.size(), mrnCount);

        return patients;
    }

    /**
     * Build an order query filter for orders within a month matching keyword patterns, excluding cancelled orders.
     * Original JS: orderQuery(monthKey, nameKeywords, extra)
     *
     * @return the Document query filter
     */
    public Document orderQuery(String monthKey, List<String> nameKeywords) {
        MonthRange range = monthRange(monthKey);
        List<Document> ors = new ArrayList<>();
        for (String k : nameKeywords) {
            ors.add(new Document("orderName", new Document("$regex", escapeRegex(k))));
        }
        return new Document("orderTime", new Document("$gte", range.getStartDate()).append("$lte", range.getEndDate()))
                .append("$or", ors)
                .append("orderName", new Document("$not", Pattern.compile("撤销")));
    }

    /**
     * Count patients who have at least one matching order during their ICU stay.
     * Original JS: countMatchedOrderPatients(patients, orders, fallbackEndDate)
     */
    @SuppressWarnings("unchecked")
    public long countMatchedOrderPatients(List<Document> patients, List<Document> orders, Date fallbackEndDate) {
        Map<String, List<Document>> patientsByMrn = new LinkedHashMap<>();
        Map<String, List<Date>> orderTimesByMrn = new LinkedHashMap<>();

        for (Document patient : patients) {
            String mrn = normalizeMrn(patient.getString("mrn"));
            if (mrn.isEmpty()) continue;
            patientsByMrn.computeIfAbsent(mrn, k -> new ArrayList<>()).add(patient);
        }

        for (Document order : orders) {
            String mrn = normalizeMrn(order.getString("mrn"));
            Date orderTime = asDate(order.get("orderTime"));
            if (mrn.isEmpty() || orderTime == null) continue;
            orderTimesByMrn.computeIfAbsent(mrn, k -> new ArrayList<>()).add(orderTime);
        }

        for (List<Date> times : orderTimesByMrn.values()) {
            times.sort(Comparator.naturalOrder());
        }

        long hitCount = 0;
        for (Map.Entry<String, List<Document>> entry : patientsByMrn.entrySet()) {
            String mrn = entry.getKey();
            List<Document> mrnPatients = sortPatientsByAdmission(entry.getValue());
            List<Date> orderTimes = orderTimesByMrn.getOrDefault(mrn, Collections.emptyList());

            if (mrnPatients.size() == 1) {
                if (!orderTimes.isEmpty()) hitCount += 1;
                continue;
            }

            Set<Integer> usedOrders = new HashSet<>();
            for (Document patient : mrnPatients) {
                Date start = floorDateToMinute(patient.get("icuAdmissionTime"));
                Date end = orElse(asDate(patient.get("icuDischargeTime")), fallbackEndDate);
                int matchedOrderIndex = -1;
                for (int i = 0; i < orderTimes.size(); i++) {
                    if (!usedOrders.contains(i) && start != null
                            && !orderTimes.get(i).before(start)
                            && !orderTimes.get(i).after(end)) {
                        matchedOrderIndex = i;
                        break;
                    }
                }
                if (matchedOrderIndex >= 0) {
                    usedOrders.add(matchedOrderIndex);
                    hitCount += 1;
                }
            }
        }

        return hitCount;
    }

    /**
     * Pick the best Apache score for statistics: prefer an in-month score, otherwise the latest before month start.
     * Original JS: pickApacheScoreForStats(patientScores, start, end)
     */
    @SuppressWarnings("unchecked")
    public Document pickApacheScoreForStats(List<Document> patientScores, Date start, Date end) {
        if (patientScores.isEmpty()) return null;
        if (patientScores.size() == 1) return patientScores.get(0);

        for (Document item : patientScores) {
            Date scoreTime = asDate(item.get("time"));
            if (scoreTime != null && !scoreTime.before(start) && !scoreTime.after(end)) {
                return item;
            }
        }

        for (Document item : patientScores) {
            Date scoreTime = asDate(item.get("time"));
            if (scoreTime != null && scoreTime.before(start)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Load Apache score maps for a list of patients: anyScoreByPid and selectedScoreByPid.
     * Original JS: loadApacheScoreMaps(patients, monthKey)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Map<String, Document>> loadApacheScoreMaps(List<Document> patients, String monthKey) {
        MonthRange range = monthRange(monthKey);
        List<String> pids = patients.stream()
                .map(p -> String.valueOf(p.get("_id")))
                .collect(Collectors.toList());

        Map<String, Map<String, Document>> result = new HashMap<>();
        Map<String, Document> anyScoreByPid = new HashMap<>();
        Map<String, Document> selectedScoreByPid = new HashMap<>();
        result.put("anyScoreByPid", anyScoreByPid);
        result.put("selectedScoreByPid", selectedScoreByPid);

        if (pids.isEmpty()) return result;

        Query scoreQuery = new Query(Criteria.where("pid").in(pids)
                .and("valid").is(true)
                .and("scoreType").is("apacheII"));
        scoreQuery.with(Sort.by(Sort.Direction.DESC, "time"));
        List<Document> scores = smartCareMongo.find(scoreQuery, Document.class, CollectionConstants.SCORE);

        Map<String, List<Document>> grouped = new LinkedHashMap<>();
        for (Document s : scores) {
            String pid = String.valueOf(s.get("pid"));
            if (pid.isEmpty() || "null".equals(pid)) continue;
            grouped.computeIfAbsent(pid, k -> new ArrayList<>()).add(s);
        }

        for (Map.Entry<String, List<Document>> entry : grouped.entrySet()) {
            String pid = entry.getKey();
            List<Document> patientScores = entry.getValue();
            anyScoreByPid.put(pid, patientScores.get(0));
            Document matched = pickApacheScoreForStats(patientScores, range.getStartDate(), range.getEndDate());
            if (matched != null) {
                selectedScoreByPid.put(pid, matched);
            }
        }

        return result;
    }

    /**
     * Calculate APACHEII rates (indicators 7/8/9).
     * Original JS: calcApacheRates(monthKey, department)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> calcApacheRates(String monthKey, String department) {
        List<Document> patients = getInIcuPatients(monthKey, department);
        Map<String, Map<String, Document>> maps = loadApacheScoreMaps(patients, monthKey);
        Map<String, Document> anyScoreByPid = maps.get("anyScoreByPid");
        Map<String, Document> selectedScoreByPid = maps.get("selectedScoreByPid");

        int denom = patients.size();
        int gte15 = 0, lt15 = 0, scored = 0, notScored = 0;
        List<String> detailGte15 = new ArrayList<>();
        List<String> detailLt15 = new ArrayList<>();
        List<String> detailScored = new ArrayList<>();
        List<String> detailNotScored = new ArrayList<>();

        for (Document p : patients) {
            String pid = String.valueOf(p.get("_id"));
            boolean hasAnyScore = anyScoreByPid.containsKey(pid);
            Document s = selectedScoreByPid.get(pid);

            if (!hasAnyScore) {
                notScored++;
                detailNotScored.add(pid);
                continue;
            }
            scored++;
            detailScored.add(pid);

            if (s == null) continue;

            double total = NumberUtils.safeNumber(s.get("total"));
            if (total >= 15) {
                gte15++;
                detailGte15.add(pid);
            } else {
                lt15++;
                detailLt15.add(pid);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("apacheGte15Rate", mapOf("num", gte15, "denom", denom, "detail", detailGte15));
        result.put("apacheLt15Rate", mapOf("num", lt15, "denom", denom, "detail", detailLt15));
        result.put("apacheScoreRate", mapOf("num", scored, "denom", denom, "notScored", notScored, "detail",
                mapOf("gte15", detailGte15, "lt15", detailLt15, "scored", detailScored, "notScored", detailNotScored)));
        return result;
    }

    /**
     * Calculate mortality rates (indicators 13/14/15).
     * Original JS: calcMortality(monthKey, department)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> calcMortality(String monthKey, String department) {
        List<Document> patients = getInIcuPatients(monthKey, department);
        Map<String, Map<String, Document>> maps = loadApacheScoreMaps(patients, monthKey);
        Map<String, Document> scoreMap = maps.get("selectedScoreByPid");

        double predictedSum = 0;
        int predictedHit = 0;
        int actualDeath = 0;
        int lt15Total = 0, lt15Death = 0;

        for (Document p : patients) {
            String dischargedType = String.valueOf(p.getOrDefault("dischargedType", ""));
            if (dischargedType.contains("死亡")) actualDeath++;

            Document s = scoreMap.get(String.valueOf(p.get("_id")));
            if (s != null) {
                Object apacheII = s.get("apacheII");
                if (apacheII instanceof Map) {
                    Object calDead = ((Map<?, ?>) apacheII).get("calDead");
                    if (calDead instanceof Map) {
                        Object scoreVal = ((Map<?, ?>) calDead).get("score");
                        if (scoreVal != null) {
                            predictedSum += NumberUtils.safeNumber(scoreVal);
                            predictedHit++;
                        }
                    }
                }

                double total = NumberUtils.safeNumber(s.get("total"));
                if (total < 15) {
                    lt15Total++;
                    if (dischargedType.contains("死亡")) lt15Death++;
                }
            }
        }

        int denom = patients.size();
        double predictedRate = denom != 0 ? predictedSum / denom : 0;
        double actualRate = denom != 0 ? (double) actualDeath / denom : 0;
        double smr = predictedRate != 0 ? actualRate / predictedRate : 0;
        double lt15DeathRate = lt15Total != 0 ? (double) lt15Death / lt15Total : 0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("predictedMortalityRate", mapOf("value", round(predictedRate, 3), "sum", predictedSum, "denom", denom));
        result.put("apacheLt15DeathRate", mapOf("num", lt15Death, "denom", lt15Total));
        result.put("standardizedMortalityIndex", mapOf("value", round(smr, 3), "actualRate", actualRate, "predictedRate", predictedRate));
        return result;
    }

    /**
     * Shock bundle carryover version.
     * Original JS: calcShockBundleCarryover(monthKey, department) => exported as calcShockBundle
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> calcShockBundle(String monthKey, String department) {
        List<Document> patients = getInIcuPatients(monthKey, department);
        Document denomFilter = orderQueryToMonthEnd(monthKey, Collections.singletonList("感染性休克护理常规"));
        Map<String, Object> denominatorMatched = getMatchedPatientsByOrderFilter(monthKey, patients, denomFilter);

        List<Document> denominatorDone = (List<Document>) denominatorMatched.get("done");
        List<Document> denominatorAll = (List<Document>) denominatorMatched.get("denominator");

        if (denominatorDone.isEmpty()) {
            log.info("QUALITY_CALC indicator=shockBundleRate month={} patientCount={} denomCandidateCount={} matchedPatientCount=0 status=NO_ORDER_CANDIDATE",
                    monthKey, patients.size(), denominatorAll.size());
            return mapOf("num", 0, "denom", 0, "notDone", Collections.emptyList());
        }

        Document numFilter = orderQueryToMonthEnd(monthKey, Collections.singletonList("感染性休克患者集束化治疗"));
        Map<String, Object> numeratorMatched = getMatchedPatientsByOrderFilter(monthKey, denominatorDone, numFilter);

        List<Document> numeratorNotDone = (List<Document>) numeratorMatched.get("notDone");
        List<String> notDoneIds = numeratorNotDone.stream()
                .map(d -> String.valueOf(d.get("_id")))
                .collect(Collectors.toList());

        List<Document> numeratorDone = (List<Document>) numeratorMatched.get("done");

        log.info("QUALITY_CALC indicator=shockBundleRate month={} enableDeptFilter={} patientCount={} denomCandidateCount={} matchedPatientCount={} numerator={} denominator={} source=live status=ok",
                monthKey, properties.isEnableDeptFilter(), patients.size(), denominatorAll.size(), denominatorDone.size(), numeratorDone.size(), denominatorDone.size());

        return mapOf("num", numeratorDone.size(), "denom", denominatorDone.size(), "notDone", notDoneIds);
    }

    /**
     * DVT prevention rate (indicator 12) - carryover version.
     * Original JS: calcDVTCarryover(monthKey, department) => exported as calcDVT
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> calcDVT(String monthKey, String department) {
        List<Document> patients = getInIcuPatients(monthKey, department);
        MonthRange range = monthRange(monthKey);

        List<Document> orConditions = new ArrayList<>();
        for (String k : DVT_DEVICE) {
            orConditions.add(new Document("orderName", new Document("$regex", escapeRegex(k))));
        }
        for (String k : DVT_HEPARIN) {
            orConditions.add(new Document("orderName", new Document("$regex", escapeRegex(k)))
                    .append("exeMethod", "皮下注射"));
        }
        for (String k : DVT_RIVA) {
            orConditions.add(new Document("orderName", new Document("$regex", escapeRegex(k)))
                    .append("exeMethod", new Document("$in", Arrays.asList("口服", "胃管置管术注药"))));
        }

        Document filter = new Document("orderTime", new Document("$lte", range.getEndDate()))
                .append("orderName", new Document("$not", Pattern.compile("撤销")))
                .append("$or", orConditions);

        Map<String, Object> matched = getMatchedPatientsByOrderFilter(monthKey, patients, filter);
        List<Document> done = (List<Document>) matched.get("done");
        List<Document> denominator = (List<Document>) matched.get("denominator");

        log.info("QUALITY_CALC indicator=dvtRate month={} enableDeptFilter={} patientCount={} matchedPatientCount={} numerator={} denominator={} source=live status={}",
                monthKey, properties.isEnableDeptFilter(), patients.size(), denominator.size(), done.size(), denominator.size(),
                denominator.isEmpty() ? "NO_ORDER_CANDIDATE" : "ok");

        return mapOf("num", done.size(), "denom", denominator.size());
    }

    /**
     * Calculate extubation rates (indicators 16/17).
     * Original JS: calcExtubation(monthKey, department)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> calcExtubation(String monthKey, String department) {
        MonthRange range = monthRange(monthKey);
        Date start = range.getStartDate();
        Date end = range.getEndDate();

        Query tubeQuery = new Query(Criteria.where("type").is("气插管")
                .and("valid").ne(false)
                .and("replace").ne(true)
                .and("endTime").gte(start).lte(end));
        List<Document> tubes = smartCareMongo.find(tubeQuery, Document.class, CollectionConstants.TUBE_EXE);

        if (tubes.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("unplannedExtubationRate", mapOf("num", 0, "denom", 0));
            result.put("reintubation48hRate", mapOf("num", 0, "denom", 0));
            return result;
        }

        // Unplanned extubation count
        long unplanned = tubes.stream()
                .filter(t -> Boolean.TRUE.equals(t.get("unPlannedEndTube")))
                .count();

        // 48h reintubation: per patient, find if next tube startTime - previous endTime <= 48h
        Set<String> pids = tubes.stream()
                .map(t -> String.valueOf(t.get("pid")))
                .filter(pid -> !pid.isEmpty() && !"null".equals(pid))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Query historyQuery = new Query(Criteria.where("pid").in(pids)
                .and("type").is("气插管")
                .and("valid").ne(false)
                .and("replace").ne(true));
        historyQuery.with(Sort.by(Sort.Direction.ASC, "startTime"));
        List<Document> allHistory = smartCareMongo.find(historyQuery, Document.class, CollectionConstants.TUBE_EXE);

        Map<String, List<Document>> byPid = new LinkedHashMap<>();
        for (Document t : allHistory) {
            String pid = String.valueOf(t.get("pid"));
            byPid.computeIfAbsent(pid, k -> new ArrayList<>()).add(t);
        }

        long reintubated = 0;
        for (Document t : tubes) {
            Date endTime = asDate(t.get("endTime"));
            if (endTime == null) continue;
            String pid = String.valueOf(t.get("pid"));
            List<Document> arr = byPid.getOrDefault(pid, Collections.emptyList());
            for (Document x : arr) {
                Date xStart = asDate(x.get("startTime"));
                if (xStart != null && xStart.after(endTime) && (xStart.getTime() - endTime.getTime()) <= HOURS_48_MS) {
                    reintubated++;
                    break;
                }
            }
        }

        log.info("QUALITY_CALC indicator=unplannedExtubationRate month={} tubeRecordCount={} unplanned={} reintubated={} source=live status={}",
                monthKey, tubes.size(), unplanned, reintubated, tubes.isEmpty() ? "NO_TUBE_RECORD" : "ok");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unplannedExtubationRate", mapOf("num", unplanned, "denom", tubes.size()));
        result.put("reintubation48hRate", mapOf("num", reintubated, "denom", tubes.size()));
        return result;
    }

    /**
     * Calculate 48h return rate after ICU transfer out (indicator 19).
     * Original JS: calc48hReturn(monthKey, department)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> calc48hReturn(String monthKey, String department) {
        MonthRange range = monthRange(monthKey);
        Date start = range.getStartDate();
        Date end = range.getEndDate();

        // Patients discharged during the month with type containing "转出"
        Query outQuery = new Query(Criteria.where("icuDischargeTime").gte(start).lte(end)
                .and("dischargedType").regex("转出"));
        List<Document> out = smartCareMongo.find(outQuery, Document.class, CollectionConstants.PATIENT);

        if (out.isEmpty()) return mapOf("num", 0, "denom", 0);

        // Get all admission records for these patients
        List<String> mrns = out.stream()
                .map(p -> (String) p.get("mrn"))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Query allQuery = new Query(Criteria.where("mrn").in(mrns));
        allQuery.with(Sort.by(Sort.Direction.ASC, "icuAdmissionTime"));
        allQuery.fields().include("mrn").include("icuAdmissionTime").include("icuDischargeTime");
        List<Document> all = smartCareMongo.find(allQuery, Document.class, CollectionConstants.PATIENT);

        Map<String, List<Document>> byMrn = new LinkedHashMap<>();
        for (Document p : all) {
            String mrn = (String) p.get("mrn");
            if (mrn != null) {
                byMrn.computeIfAbsent(mrn, k -> new ArrayList<>()).add(p);
            }
        }

        long hit = 0;
        for (Document p : out) {
            Date dischargeTime = asDate(p.get("icuDischargeTime"));
            String mrn = (String) p.get("mrn");
            if (dischargeTime == null || mrn == null) continue;

            List<Document> arr = byMrn.getOrDefault(mrn, Collections.emptyList());
            for (Document r : arr) {
                Date rAdmission = asDate(r.get("icuAdmissionTime"));
                if (rAdmission != null && rAdmission.after(dischargeTime)
                        && (rAdmission.getTime() - dischargeTime.getTime()) <= HOURS_48_MS) {
                    hit++;
                    break;
                }
            }
        }

        log.info("QUALITY_CALC indicator=icuReturn48hRate month={} dischargedPatientCount={} returnHit={} source=live status={}",
                monthKey, out.size(), hit, out.isEmpty() ? "NO_PATIENT" : "ok");

        return mapOf("num", hit, "denom", out.size());
    }

    /**
     * Shock ultrasound screening rate (indicator 20).
     * Original JS: calcShockUltrasound => calcOrderBasedCarryover(m, d, '休克护理常规', '重症超声筛查评估')
     */
    public Map<String, Object> calcShockUltrasound(String monthKey, String department) {
        return calcOrderBasedCarryover(monthKey, department, "休克护理常规", "重症超声筛查评估");
    }

    /**
     * Shock hemodynamic monitoring rate (indicator 21).
     * Original JS: calcShockHemodynamic => calcOrderBasedCarryover(m, d, '休克护理常规', 'CVP')
     */
    public Map<String, Object> calcShockHemodynamic(String monthKey, String department) {
        return calcOrderBasedCarryover(monthKey, department, "休克护理常规", "CVP");
    }

    /**
     * ARDS prone ventilation rate (indicator 22).
     * Original JS: calcARDS => calcOrderBasedCarryover(m, d, '中重度ARDS护理常规', '俯卧位通气')
     */
    public Map<String, Object> calcARDS(String monthKey, String department) {
        return calcOrderBasedCarryover(monthKey, department, "中重度ARDS护理常规", "俯卧位通气");
    }

    /**
     * 48h enteral nutrition start rate (indicator 23) - carryover version.
     * Original JS: calcEN48hCarryover(monthKey, department) => exported as calcEN48h
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> calcEN48h(String monthKey, String department) {
        MonthRange range = monthRange(monthKey);
        Date end = range.getEndDate();

        List<Document> patients = getInIcuPatients(monthKey, department).stream()
                .filter(p -> {
                    Date admission = asDate(p.get("icuAdmissionTime"));
                    Date discharge = orElse(asDate(p.get("icuDischargeTime")), end);
                    return admission != null && discharge.getTime() - admission.getTime() >= HOURS_48_MS;
                })
                .collect(Collectors.toList());

        if (patients.isEmpty()) {
            log.info("QUALITY_CALC indicator=en48hRate month={} allPatientCount={} eligiblePatientCount=0 source=live status=NO_ICU_PATIENT",
                    monthKey, getInIcuPatients(monthKey, department).size());
            return mapOf("num", 0, "denom", 0);
        }

        List<String> mrns = patients.stream()
                .map(p -> (String) p.get("mrn"))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // Combine both regex conditions into one to avoid duplicate key conflict
        Query orderQuery = new Query(Criteria.where("mrn").in(mrns)
                .and("orderTime").lte(end)
                .and("orderName").regex("^(?!.*撤销).*流质饮食.*$"));
        List<Document> orders = dataCenterMongo.find(orderQuery, Document.class, CollectionConstants.VI_ICU_ZYYZ);

        // Group order times by mrn
        Map<String, List<Date>> orderTimesByMrn = new LinkedHashMap<>();
        for (Document order : orders) {
            String mrn = normalizeMrn(order.getString("mrn"));
            Date orderTime = asDate(order.get("orderTime"));
            if (mrn.isEmpty() || orderTime == null) continue;
            orderTimesByMrn.computeIfAbsent(mrn, k -> new ArrayList<>()).add(orderTime);
        }
        for (List<Date> times : orderTimesByMrn.values()) {
            times.sort(Comparator.naturalOrder());
        }

        // Group patients by mrn
        Map<String, List<Document>> patientsByMrn = new LinkedHashMap<>();
        for (Document patient : patients) {
            String mrn = normalizeMrn(patient.getString("mrn"));
            if (mrn.isEmpty()) continue;
            patientsByMrn.computeIfAbsent(mrn, k -> new ArrayList<>()).add(patient);
        }

        long num = 0;
        for (Map.Entry<String, List<Document>> entry : patientsByMrn.entrySet()) {
            String mrn = entry.getKey();
            List<Document> mrnPatients = sortPatientsByAdmission(entry.getValue());
            List<Date> orderTimes = orderTimesByMrn.getOrDefault(mrn, Collections.emptyList());
            Set<Integer> usedOrders = new HashSet<>();

            for (Document patient : mrnPatients) {
                Date admission = floorDateToMinute(patient.get("icuAdmissionTime"));
                Date discharge = orElse(asDate(patient.get("icuDischargeTime")), end);
                Date latestStart = admission != null ? new Date(admission.getTime() + HOURS_48_MS) : null;

                int matchedOrderIndex = -1;
                for (int i = 0; i < orderTimes.size(); i++) {
                    if (!usedOrders.contains(i) && admission != null && latestStart != null
                            && !orderTimes.get(i).before(admission)
                            && !orderTimes.get(i).after(discharge)
                            && !orderTimes.get(i).after(latestStart)) {
                        matchedOrderIndex = i;
                        break;
                    }
                }
                if (matchedOrderIndex >= 0) {
                    usedOrders.add(matchedOrderIndex);
                    num += 1;
                }
            }
        }

        log.info("QUALITY_CALC indicator=en48hRate month={} enableDeptFilter={} eligiblePatientCount={} orderCandidateCount={} numerator={} denominator={} source=live status=ok",
                monthKey, properties.isEnableDeptFilter(), patients.size(), orders.size(), num, patients.size());

        return mapOf("num", num, "denom", patients.size());
    }

    /**
     * Pain assessment rate (indicator 24).
     * Original JS: calcPain => calcOrderHitOnIcuCarryover(m, d, '镇痛评估')
     */
    public Map<String, Object> calcPain(String monthKey, String department) {
        return calcOrderHitOnIcuCarryover(monthKey, department, "镇痛评估");
    }

    /**
     * Sedation assessment rate (indicator 25).
     * Original JS: calcSedation => calcOrderHitOnIcuCarryover(m, d, '镇静评估')
     */
    public Map<String, Object> calcSedation(String monthKey, String department) {
        return calcOrderHitOnIcuCarryover(monthKey, department, "镇静评估");
    }

    /**
     * Rescue success rate (indicator 26).
     * Original JS: calcRescue(monthKey)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> calcRescue(String monthKey) {
        Document orderFilter = orderQuery(monthKey, Collections.singletonList("抢救"));
        List<Document> rescueOrders = dataCenterMongo.find(
                new BasicQuery(orderFilter),
                Document.class, CollectionConstants.VI_ICU_ZYYZ
        );

        List<String> mrns = rescueOrders.stream()
                .map(o -> (String) o.get("mrn"))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (mrns.isEmpty()) {
            return mapOf("num", 0, "denom", 0, "death", 0, "terminal", 0, "rescueCount", 0);
        }

        Query patientQuery = new Query(Criteria.where("mrn").in(mrns));
        patientQuery.fields().include("mrn").include("dischargedType");
        List<Document> patients = smartCareMongo.find(patientQuery, Document.class, CollectionConstants.PATIENT);

        Map<String, Document> byMrn = new LinkedHashMap<>();
        for (Document p : patients) {
            String mrn = (String) p.get("mrn");
            if (mrn != null) byMrn.put(mrn, p);
        }

        long denom = 0, success = 0, death = 0, terminal = 0;
        for (String mrn : mrns) {
            Document p = byMrn.get(mrn);
            if (p == null) continue;
            String dischargedType = String.valueOf(p.getOrDefault("dischargedType", ""));
            if (dischargedType.contains("死亡（终末）")) {
                terminal++;
                continue;
            }
            denom++;
            if (dischargedType.contains("死亡")) death++;
            else success++;
        }

        log.info("QUALITY_CALC indicator=rescueSuccessRate month={} rescueOrderCount={} mrnCount={} patientMatchCount={} success={} death={} terminal={} denom={} source=live status={}",
                monthKey, rescueOrders.size(), mrns.size(), patients.size(), success, death, terminal, denom,
                rescueOrders.isEmpty() ? "NO_ORDER_CANDIDATE" : "ok");

        return mapOf("num", success, "denom", denom, "death", death, "terminal", terminal,
                "rescueCount", rescueOrders.size());
    }

    /**
     * Brain injury consciousness assessment rate (indicator 27).
     * Original JS: calcBrainInjury => calcOrderBasedCarryover(m, d, '急性脑损伤护理常规', '格拉斯哥昏迷评分')
     */
    public Map<String, Object> calcBrainInjury(String monthKey, String department) {
        return calcOrderBasedCarryover(monthKey, department, "急性脑损伤护理常规", "格拉斯哥昏迷评分");
    }

    /**
     * Read quality indicator data from VI_ICU_QUALITY.
     * Original JS: readQualityData(monthKey, indicatorCode, deptCode)
     */
    public double readQualityData(String monthKey, String indicatorCode, String deptCode) {
        if (deptCode == null) deptCode = "all";
        String[] parts = monthKey.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);

        Document doc = dataCenterMongo.findOne(
                new Query(Criteria.where("deptCode").is(deptCode)
                        .and("year").is(year)
                        .and("month").is(month)
                        .and("indicatorCode").is(indicatorCode)),
                Document.class, CollectionConstants.VI_ICU_QUALITY
        );

        if (doc == null) return 0;
        Object indicatorData = doc.get("indicatorData");
        return NumberUtils.safeNumber(indicatorData);
    }

    /**
     * Match patients by orders, classifying each patient visit as done/notDone.
     * Original JS: matchPatientsByOrders(patients, orders, fallbackEndDate)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> matchPatientsByOrders(List<Document> patients, List<Document> orders, Date fallbackEndDate) {
        Map<String, List<Document>> patientsByMrn = new LinkedHashMap<>();
        Map<String, List<Date>> orderTimesByMrn = new LinkedHashMap<>();

        for (Document patient : patients) {
            String mrn = normalizeMrn(patient.getString("mrn"));
            if (mrn.isEmpty()) continue;
            patientsByMrn.computeIfAbsent(mrn, k -> new ArrayList<>()).add(patient);
        }

        for (Document order : orders) {
            String mrn = normalizeMrn(order.getString("mrn"));
            Date orderTime = asDate(order.get("orderTime"));
            if (mrn.isEmpty() || orderTime == null) continue;
            orderTimesByMrn.computeIfAbsent(mrn, k -> new ArrayList<>()).add(orderTime);
        }

        for (List<Date> times : orderTimesByMrn.values()) {
            times.sort(Comparator.naturalOrder());
        }

        List<Document> denominator = new ArrayList<>();
        List<Document> done = new ArrayList<>();
        List<Document> notDone = new ArrayList<>();

        for (Map.Entry<String, List<Document>> entry : patientsByMrn.entrySet()) {
            String mrn = entry.getKey();
            List<Document> mrnPatients = sortPatientsByAdmission(entry.getValue());
            List<Date> orderTimes = orderTimesByMrn.getOrDefault(mrn, Collections.emptyList());
            Set<Integer> usedOrders = new HashSet<>();

            for (Document patient : mrnPatients) {
                denominator.add(patient);
                Date start = floorDateToMinute(patient.get("icuAdmissionTime"));
                Date end = orElse(asDate(patient.get("icuDischargeTime")), fallbackEndDate);

                int matchedOrderIndex = -1;
                for (int i = 0; i < orderTimes.size(); i++) {
                    if (!usedOrders.contains(i) && start != null
                            && !orderTimes.get(i).before(start)
                            && !orderTimes.get(i).after(end)) {
                        matchedOrderIndex = i;
                        break;
                    }
                }
                if (matchedOrderIndex >= 0) {
                    usedOrders.add(matchedOrderIndex);
                    done.add(patient);
                } else {
                    notDone.add(patient);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("denominator", denominator);
        result.put("done", done);
        result.put("notDone", notDone);
        return result;
    }

    /**
     * Get matched patients by order filter for a given month.
     * Original JS: getMatchedPatientsByOrderFilter(monthKey, patients, filter)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMatchedPatientsByOrderFilter(String monthKey, List<Document> patients, Document filter) {
        List<String> mrns = patients.stream()
                .map(p -> (String) p.get("mrn"))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (mrns.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("denominator", Collections.emptyList());
            result.put("done", Collections.emptyList());
            result.put("notDone", Collections.emptyList());
            return result;
        }

        // Combine filter with mrn constraint
        Query query = new BasicQuery(filter);
        query.addCriteria(Criteria.where("mrn").in(mrns));
        query.fields().include("mrn").include("orderTime");
        List<Document> orders = dataCenterMongo.find(query, Document.class, CollectionConstants.VI_ICU_ZYYZ);

        return matchPatientsByOrders(patients, orders, monthRange(monthKey).getEndDate());
    }

    /**
     * Generic order-based carryover calculation.
     * Original JS: calcOrderBasedCarryover(monthKey, department, denomKeyword, numKeyword)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> calcOrderBasedCarryover(String monthKey, String department, String denomKeyword, String numKeyword) {
        List<Document> patients = getInIcuPatients(monthKey, department);
        Document denomFilter = orderQueryToMonthEnd(monthKey, Collections.singletonList(denomKeyword));
        Map<String, Object> denominatorMatched = getMatchedPatientsByOrderFilter(monthKey, patients, denomFilter);

        List<Document> denominatorDone = (List<Document>) denominatorMatched.get("done");
        if (denominatorDone.isEmpty()) {
            String indicatorKey = denomKeyword.contains("休克") ? "shockRate" :
                    denomKeyword.contains("ARDS") ? "ardsRate" :
                            denomKeyword.contains("脑损伤") ? "acuteBrainInjuryRate" : "unknown";
            log.info("QUALITY_CALC indicator={} denomKeyword={} numKeyword={} month={} patientCount={} denomMatchedCount=0 source=live status=NO_ORDER_CANDIDATE",
                    indicatorKey, denomKeyword, numKeyword, monthKey, patients.size());
            return mapOf("num", 0, "denom", 0);
        }

        Document numFilter = orderQueryToMonthEnd(monthKey, Collections.singletonList(numKeyword));
        Map<String, Object> numeratorMatched = getMatchedPatientsByOrderFilter(monthKey, denominatorDone, numFilter);

        List<Document> numeratorDone = (List<Document>) numeratorMatched.get("done");

        String indicatorKey = denomKeyword.contains("休克") && numKeyword.contains("超声") ? "shockUltrasoundRate" :
                denomKeyword.contains("休克") && numKeyword.contains("CVP") ? "shockHemodynamicRate" :
                        denomKeyword.contains("ARDS") ? "ardsRate" :
                                denomKeyword.contains("脑损伤") ? "acuteBrainInjuryRate" : "unknown";
        log.info("QUALITY_CALC indicator={} month={} enableDeptFilter={} patientCount={} denomMatchedCount={} numerator={} denominator={} source=live status=ok",
                indicatorKey, monthKey, properties.isEnableDeptFilter(), patients.size(), denominatorDone.size(), numeratorDone.size(), denominatorDone.size());

        return mapOf("num", numeratorDone.size(), "denom", denominatorDone.size());
    }

    /**
     * Order hit on ICU carryover calculation.
     * Original JS: calcOrderHitOnIcuCarryover(monthKey, department, keyword)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> calcOrderHitOnIcuCarryover(String monthKey, String department, String keyword) {
        List<Document> patients = getInIcuPatients(monthKey, department);
        Document filter = orderQueryToMonthEnd(monthKey, Collections.singletonList(keyword));
        Map<String, Object> matched = getMatchedPatientsByOrderFilter(monthKey, patients, filter);

        List<Document> done = (List<Document>) matched.get("done");
        List<Document> denominator = (List<Document>) matched.get("denominator");

        String indicatorKey = keyword.contains("镇痛") ? "painRate" :
                keyword.contains("镇静") ? "sedationRate" : "unknown";
        log.info("QUALITY_CALC indicator={} month={} enableDeptFilter={} patientCount={} matchedPatientCount={} numerator={} denominator={} source=live status={}",
                indicatorKey, monthKey, properties.isEnableDeptFilter(), patients.size(), denominator.size(), done.size(), denominator.size(),
                denominator.isEmpty() ? "NO_ORDER_CANDIDATE" : "ok");

        return mapOf("num", done.size(), "denom", denominator.size());
    }

    // ==================== Private Helpers ====================

    /**
     * Build order query filter up to month end (inclusive of all orders before month end).
     * Original JS: orderQueryToMonthEnd(monthKey, nameKeywords, extra)
     */
    private Document orderQueryToMonthEnd(String monthKey, List<String> nameKeywords) {
        MonthRange range = monthRange(monthKey);
        List<Document> ors = new ArrayList<>();
        for (String k : nameKeywords) {
            ors.add(new Document("orderName", new Document("$regex", escapeRegex(k))));
        }
        return new Document("orderTime", new Document("$lte", range.getEndDate()))
                .append("$or", ors)
                .append("orderName", new Document("$not", Pattern.compile("撤销")));
    }

    /**
     * Escape regex special characters.
     * Original JS: escapeReg(s)
     */
    private static String escapeRegex(String s) {
        return s.replaceAll("([.\\\\*+?^${}()|\\[\\]])", "\\\\$1");
    }

    /**
     * Safely parse a value as Date, returning null on failure.
     * Original JS: asDate(value)
     */
    private static Date asDate(Object value) {
        return NumberUtils.asDate(value);
    }

    /**
     * Floor a date to the minute (zero out seconds and milliseconds).
     * Original JS: floorDateToMinute(value)
     */
    private static Date floorDateToMinute(Object value) {
        Date date = asDate(value);
        if (date == null) return null;
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    /**
     * Sort patients by ICU admission time (ascending).
     * Original JS: sortPatientsByAdmission(patients)
     */
    private static List<Document> sortPatientsByAdmission(List<Document> patients) {
        List<Document> sorted = new ArrayList<>(patients);
        sorted.sort((a, b) -> {
            long aAdmit = Optional.ofNullable(asDate(a.get("icuAdmissionTime"))).map(Date::getTime).orElse(0L);
            long bAdmit = Optional.ofNullable(asDate(b.get("icuAdmissionTime"))).map(Date::getTime).orElse(0L);
            if (aAdmit != bAdmit) return Long.compare(aAdmit, bAdmit);
            String aId = String.valueOf(a.get("_id"));
            String bId = String.valueOf(b.get("_id"));
            return aId.compareTo(bId);
        });
        return sorted;
    }

    /**
     * Normalize MRN: trim whitespace, return empty string for null.
     */
    private static String normalizeMrn(Object mrn) {
        if (mrn == null) return "";
        String s = String.valueOf(mrn).trim();
        return "null".equals(s) ? "" : s;
    }

    /**
     * Return value if not null, otherwise fallback.
     */
    private static <T> T orElse(T value, T fallback) {
        return value != null ? value : fallback;
    }

    /**
     * Round a double to n decimal places.
     * Original JS: round(v, n)
     */
    private static double round(double v, int n) {
        double factor = Math.pow(10, n);
        return Math.round(v * factor) / factor;
    }

    /**
     * Build a Map from varargs key-value pairs.
     */
    @SafeVarargs
    private static Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}
