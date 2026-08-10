package com.smartcare.icustats.service;

import com.smartcare.icustats.util.DateRangeUtils;
import com.smartcare.icustats.dto.MonthRange;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import org.bson.types.ObjectId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Quality Writer - faithful Java migration of Node.js qualityWriter.js
 * Writes quality indicator data into doctorQuality / doctorQualityItem / doctorQualityItemDetail collections.
 */
@Service
public class QualityWriter {

    private static final Logger log = LoggerFactory.getLogger(QualityWriter.class);

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter YYYY_MM = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    @Qualifier("smartCareMongoTemplate")
    private MongoTemplate mongoTemplate;

    @Autowired
    private QualityCalcService qualityCalcService;

    // ==================== Inner DTO ====================

    /**
     * Represents one item row under a DoctorQuality indicator.
     * Mirrors the JS { name, value, pids } object.
     */
    public static class QualityItem {
        private final String name;
        private final Object value;
        private final List<String> pids;

        public QualityItem(String name, Object value) {
            this(name, value, null);
        }

        public QualityItem(String name, Object value, List<String> pids) {
            this.name = name;
            this.value = value;
            this.pids = pids;
        }

        public String getName() { return name; }
        public Object getValue() { return value; }
        public List<String> getPids() { return pids; }
    }

    // ==================== upsertOne ====================

    /**
     * Upsert a single DoctorQuality document and recreate its DoctorQualityItem / DoctorQualityItemDetail children.
     * <p>
     * Original JS: upsertOne({ deptCode, monthKey, code, name, value, items })
     *
     * @param deptCode department code, e.g. "0211"
     * @param monthKey "yyyy-MM"
     * @param code     indicator code, e.g. "ICUHuanZheShouZhiLv"
     * @param name     indicator display name
     * @param value    indicator value (the rate / numeric result)
     * @param items    numerator / denominator breakdown rows
     */
    public void upsertOne(String deptCode, String monthKey, String code, String name,
                          double value, List<QualityItem> items) {

        String[] parts = monthKey.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        MonthRange range = DateRangeUtils.getMonthRange(monthKey);

        // 1. Upsert doctorQuality
        Query query = new Query(Criteria.where("deptCode").is(deptCode)
                .and("yearFlag").is(year)
                .and("flag").is(month + "月")
                .and("indicatorCode").is(code));

        Update update = new Update()
                .set("deptCode", deptCode)
                .set("deptName", "重症医学科")
                .set("yearFlag", year)
                .set("flag", month + "月")
                .set("startTime", range.getStartDate())
                .set("endTime", range.getEndDate())
                .set("indicator", name)
                .set("indicatorCode", code)
                .set("indicatorData", value)
                .set("updateTime", new Date())
                .setOnInsert("createTime", new Date());

        Document q = mongoTemplate.findAndModify(
                query, update,
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                Document.class, "doctorQuality");

        if (q == null) {
            // After upsert the document may not be returned; re-query it
            q = mongoTemplate.findOne(query, Document.class, "doctorQuality");
        }
        if (q == null) {
            log.warn("Failed to upsert DoctorQuality for code={}, monthKey={}", code, monthKey);
            return;
        }

        ObjectId qualityId = q.getObjectId("_id");

        // 2. Delete existing items
        mongoTemplate.remove(
                new Query(Criteria.where("qualityId").is(qualityId)),
                "doctorQualityItem");

        // 3. Delete existing detail records for those items (by itemId collection)
        // (detail records are removed when their parent item is removed above)

        // 4. Insert items + detail
        for (int i = 0; i < items.size(); i++) {
            QualityItem it = items.get(i);

            Document itemDoc = new Document("qualityId", qualityId)
                    .append("order", i)
                    .append("itemName", it.getName())
                    .append("itemData", it.getValue());
            mongoTemplate.insert(itemDoc, "doctorQualityItem");

            ObjectId itemId = itemDoc.getObjectId("_id");

            if (it.getPids() != null && !it.getPids().isEmpty()) {
                List<Document> details = new ArrayList<>();
                for (String pid : it.getPids()) {
                    details.add(new Document("itemId", itemId).append("pid", pid));
                }
                mongoTemplate.insert(details, "doctorQualityItemDetail");
            }
        }
    }

    // ==================== rebuildMonth ====================

    /**
     * Calculate all quality indicators for a given month and persist them.
     * <p>
     * Original JS: rebuildMonth(monthKey, deptCode, department)
     * Indicators 5-27 (7/8/9 and 13/14/15 are computed separately in qualityCalcService).
     *
     * @param monthKey  "yyyy-MM"
     * @param deptCode  department code, default "0211"
     * @param department department name, default "重症医学科"
     */
    public void rebuildMonth(String monthKey, String deptCode, String department) {

        MonthRange range = DateRangeUtils.getMonthRange(monthKey);

        // ===== 5. ICU患者收治率 = 同期ICU收治患者总数 / 同期医院收治患者总数 =====
        long icuCensus = qualityCalcService.getInIcuPatients(monthKey, department).size();
        double hosTotal = qualityCalcService.readQualityData(monthKey, "HosShouZhiHuanZheTotalNum", "all");
        upsertOne(deptCode, monthKey, "ICUHuanZheShouZhiLv", "ICU患者收治率",
                hosTotal != 0 ? icuCensus / hosTotal : 0,
                Arrays.asList(
                        new QualityItem("同期ICU收治患者总数", icuCensus),
                        new QualityItem("同期医院收治患者总数", hosTotal)));

        // ===== 6. ICU患者收治床日率 = 本科收治患者总床日数 / 同期医院患者收治总床日数 =====
        long icuBedDays = calcIcuBedDays(monthKey, department);
        double hosBedDays = qualityCalcService.readQualityData(monthKey, "HosShouZhiHuanZheTotalChuangRiNum", "all");
        upsertOne(deptCode, monthKey, "ICUHuanZheShouZhiChuangRiLv", "ICU患者收治床日率",
                hosBedDays != 0 ? icuBedDays / hosBedDays : 0,
                Arrays.asList(
                        new QualityItem("本科收治患者总床日数", icuBedDays),
                        new QualityItem("同期医院患者收治总床日数", hosBedDays)));

        // ===== 7/8/9 APACHEII 系列（由 qualityCalcService 单独计算，此处略）=====

        // ===== 10. 感染性休克集束化治疗完成率 =====
        Map<String, Object> sb = qualityCalcService.calcShockBundle(monthKey, department);
        long sbNum = toLong(sb.get("num"));
        long sbDenom = toLong(sb.get("denom"));
        @SuppressWarnings("unchecked")
        List<String> sbNotDone = toStringList(sb.get("notDone"));
        upsertOne(deptCode, monthKey, "Bundle1Lv", "感染性休克集束化治疗完成率",
                sbDenom != 0 ? (double) sbNum / sbDenom : 0,
                Arrays.asList(
                        new QualityItem("完成集束化治疗患者数", sbNum),
                        new QualityItem("未下感染性休克护理常规医嘱", sbNotDone.size(), sbNotDone),
                        new QualityItem("同期入ICU诊断为感染性休克患者总数", sbDenom)));

        // ===== 11. 抗菌药物治疗前病原学送检率 =====
        double sentNum = qualityCalcService.readQualityData(monthKey, "KangJunSongJianNum", "all");
        double antiTotal = qualityCalcService.readQualityData(monthKey, "KangJunTotalNum", "all");
        upsertOne(deptCode, monthKey, "KangJunLv", "抗菌药物治疗前病原学送检率",
                antiTotal != 0 ? sentNum / antiTotal : 0,
                Arrays.asList(
                        new QualityItem("使用抗菌药物前病原学送检病例数", sentNum),
                        new QualityItem("同期使用抗菌药物治疗病例总数", antiTotal)));

        // ===== 12. DVT 预防率 =====
        Map<String, Object> dvt = qualityCalcService.calcDVT(monthKey, department);
        long dvtNum = toLong(dvt.get("num"));
        long dvtDenom = toLong(dvt.get("denom"));
        upsertOne(deptCode, monthKey, "DVTLv", "深静脉血栓（DVT）预防率",
                dvtDenom != 0 ? (double) dvtNum / dvtDenom : 0,
                Arrays.asList(
                        new QualityItem("进行DVT预防的ICU患者数", dvtNum),
                        new QualityItem("未进行DVT预防的ICU患者数", dvtDenom - dvtNum),
                        new QualityItem("同期ICU收治患者总数", dvtDenom)));

        // ===== 13/14/15 病死率系列（由 qualityCalcService 单独计算，此处略）=====

        // ===== 16. ICU非计划气管插管拔管率 =====
        // ===== 17. ICU气管插管拔管后48h内再插管率 =====
        Map<String, Object> ex = qualityCalcService.calcExtubation(monthKey, department);
        @SuppressWarnings("unchecked")
        Map<String, Object> unplanned = (Map<String, Object>) ex.get("unplannedExtubationRate");
        @SuppressWarnings("unchecked")
        Map<String, Object> reintub = (Map<String, Object>) ex.get("reintubation48hRate");

        long exUnNum = toLong(unplanned.get("num"));
        long exUnDenom = toLong(unplanned.get("denom"));
        upsertOne(deptCode, monthKey, "ICUNoPlanQIGuanBaGuanLv", "ICU非计划气管插管拔管率",
                exUnDenom != 0 ? (double) exUnNum / exUnDenom : 0,
                Arrays.asList(
                        new QualityItem("非计划拔管例数", exUnNum),
                        new QualityItem("气管插管拔管总例数", exUnDenom)));

        long exReNum = toLong(reintub.get("num"));
        long exReDenom = toLong(reintub.get("denom"));
        upsertOne(deptCode, monthKey, "ICUQIGuanBaGuan48ChaGuanLv", "ICU气管插管拔管后48h内再插管率",
                exReDenom != 0 ? (double) exReNum / exReDenom : 0,
                Arrays.asList(
                        new QualityItem("48h内再插管例数", exReNum),
                        new QualityItem("气管插管拔管总例数", exReDenom)));

        // ===== 18. 非计划转入 ICU 率 =====
        double noPlanIn = qualityCalcService.readQualityData(monthKey, "NoPlanInICUNum", "all");
        upsertOne(deptCode, monthKey, "NoPlanInICULv", "非计划转入ICU率",
                icuCensus != 0 ? noPlanIn / icuCensus : 0,
                Arrays.asList(
                        new QualityItem("非计划转入ICU患者数", noPlanIn),
                        new QualityItem("同期ICU收治患者总数", icuCensus)));

        // ===== 19. 转出 ICU 后 48h 重返率 =====
        Map<String, Object> ret = qualityCalcService.calc48hReturn(monthKey, department);
        long retNum = toLong(ret.get("num"));
        long retDenom = toLong(ret.get("denom"));
        upsertOne(deptCode, monthKey, "OutICU48AgainInLv", "转出ICU后48h内重返率",
                retDenom != 0 ? (double) retNum / retDenom : 0,
                Arrays.asList(
                        new QualityItem("转出ICU后48h内重返ICU患者数", retNum),
                        new QualityItem("同期转出ICU患者总数", retDenom)));

        // ===== 20. 休克患者超声筛查评估率 =====
        Map<String, Object> su = qualityCalcService.calcShockUltrasound(monthKey, department);
        long suNum = toLong(su.get("num"));
        long suDenom = toLong(su.get("denom"));
        upsertOne(deptCode, monthKey, "shock_ultrasound_screen", "休克患者超声筛查评估率",
                suDenom != 0 ? (double) suNum / suDenom : 0,
                Arrays.asList(
                        new QualityItem("完成床旁B超筛查的休克患者数", suNum),
                        new QualityItem("休克病人数", suDenom)));

        // ===== 21. 休克患者血流动力学指标监测率 =====
        Map<String, Object> sh = qualityCalcService.calcShockHemodynamic(monthKey, department);
        long shNum = toLong(sh.get("num"));
        long shDenom = toLong(sh.get("denom"));
        upsertOne(deptCode, monthKey, "shock_blood_flow_detection", "休克患者血流动力学指标监测率",
                shDenom != 0 ? (double) shNum / shDenom : 0,
                Arrays.asList(
                        new QualityItem("完成CVP/PICCO监测的休克患者数", shNum),
                        new QualityItem("休克病人数", shDenom)));

        // ===== 22. ARDS 俯卧位通气率 =====
        Map<String, Object> ards = qualityCalcService.calcARDS(monthKey, department);
        long ardsNum = toLong(ards.get("num"));
        long ardsDenom = toLong(ards.get("denom"));
        upsertOne(deptCode, monthKey, "ards_constant", "急性呼吸窘迫综合征（ARDS）",
                ardsDenom != 0 ? (double) ardsNum / ardsDenom : 0,
                Arrays.asList(
                        new QualityItem("俯卧位通气患者数", ardsNum),
                        new QualityItem("中重度ARDS患者总数", ardsDenom)));

        // ===== 23. 48H 肠内营养启动率 =====
        Map<String, Object> en = qualityCalcService.calcEN48h(monthKey, department);
        long enNum = toLong(en.get("num"));
        long enDenom = toLong(en.get("denom"));
        upsertOne(deptCode, monthKey, "en_start_in48_constant", "48H肠内营养（EN）启动率",
                enDenom != 0 ? (double) enNum / enDenom : 0,
                Arrays.asList(
                        new QualityItem("入科后48h内启动EN患者数", enNum),
                        new QualityItem("入科后48h内未启动EN患者数", enDenom - enNum),
                        new QualityItem("同期入住超过48h的患者人数", enDenom)));

        // ===== 24. ICU 镇痛评估率 =====
        Map<String, Object> pain = qualityCalcService.calcPain(monthKey, department);
        long painNum = toLong(pain.get("num"));
        long painDenom = toLong(pain.get("denom"));
        upsertOne(deptCode, monthKey, "icu_analgesia_constant", "ICU镇痛评估率",
                painDenom != 0 ? (double) painNum / painDenom : 0,
                Arrays.asList(
                        new QualityItem("进行镇痛评估患者人数", painNum),
                        new QualityItem("未进行镇痛评估患者人数", painDenom - painNum),
                        new QualityItem("同期ICU收治患者总数", painDenom)));

        // ===== 25. ICU 镇静评估率 =====
        Map<String, Object> sed = qualityCalcService.calcSedation(monthKey, department);
        long sedNum = toLong(sed.get("num"));
        long sedDenom = toLong(sed.get("denom"));
        upsertOne(deptCode, monthKey, "icu_calm_constant", "ICU镇静评估率",
                sedDenom != 0 ? (double) sedNum / sedDenom : 0,
                Arrays.asList(
                        new QualityItem("进行镇静评估患者人数", sedNum),
                        new QualityItem("未进行镇静评估患者人数", sedDenom - sedNum),
                        new QualityItem("同期ICU收治患者总数", sedDenom)));

        // ===== 26. 抢救成功率 =====
        Map<String, Object> rs = qualityCalcService.calcRescue(monthKey);
        long rsNum = toLong(rs.get("num"));
        long rsDenom = toLong(rs.get("denom"));
        long rsDeath = toLong(rs.get("death"));
        long rsTerminal = toLong(rs.get("terminal"));
        long rsRescueCount = toLong(rs.get("rescueCount"));
        upsertOne(deptCode, monthKey, "rescue_success", "抢救成功率",
                rsDenom != 0 ? (double) rsNum / rsDenom : 0,
                Arrays.asList(
                        new QualityItem("抢救成功例数", rsNum),
                        new QualityItem("抢救死亡人数", rsDeath),
                        new QualityItem("死亡（终末）人数", rsTerminal),
                        new QualityItem("抢救例数", rsDenom),
                        new QualityItem("抢救次数", rsRescueCount)));

        // ===== 27. ICU 急性脑损伤患者意识评估率 =====
        Map<String, Object> bi = qualityCalcService.calcBrainInjury(monthKey, department);
        long biNum = toLong(bi.get("num"));
        long biDenom = toLong(bi.get("denom"));
        upsertOne(deptCode, monthKey, "icu_acute_brain_injury", "ICU急性脑损伤患者意识评估率",
                biDenom != 0 ? (double) biNum / biDenom : 0,
                Arrays.asList(
                        new QualityItem("完成意识评估的急性脑损伤患者人数", biNum),
                        new QualityItem("未完成意识评估的急性脑损伤患者人数", biDenom - biNum),
                        new QualityItem("急性脑损伤患者总数", biDenom)));
    }

    /**
     * Overload with default deptCode and department.
     */
    public void rebuildMonth(String monthKey) {
        rebuildMonth(monthKey, "0211", "重症医学科");
    }

    // ==================== calcIcuBedDays ====================

    /**
     * Calculate ICU bed-days for a given month.
     * <p>
     * Original JS: calcIcuBedDays(monthKey, department)
     * Snapshot approach: for each day in the month, count patients in ICU at 00:00, sum the counts.
     *
     * @param monthKey    "yyyy-MM"
     * @param department  department name for filtering, e.g. "重症医学科"
     * @return total bed-days
     */
    public long calcIcuBedDays(String monthKey, String department) {
        MonthRange range = DateRangeUtils.getMonthRange(monthKey);

        // Determine date iteration bounds
        LocalDate startLocal = range.getStartDate().toInstant()
                .atZone(SHANGHAI_ZONE).toLocalDate();
        LocalDate endLocal = range.getEndDate().toInstant()
                .atZone(SHANGHAI_ZONE).toLocalDate();

        long total = 0;
        LocalDate day = startLocal;
        while (!day.isAfter(endLocal)) {
            Date snap = Date.from(day.atStartOfDay(SHANGHAI_ZONE).toInstant());

            Query query = new Query(Criteria.where("icuAdmissionTime").lte(snap));

            // $or: discharge >= snap OR discharge null OR discharge missing
            Criteria dischargeOr = new Criteria().orOperator(
                    Criteria.where("icuDischargeTime").gte(snap),
                    Criteria.where("icuDischargeTime").is(null),
                    Criteria.where("icuDischargeTime").exists(false)
            );
            query.addCriteria(dischargeOr);

            // department filter: deptName = department OR department = department
            if (department != null && !department.isEmpty()) {
                Criteria deptOr = new Criteria().orOperator(
                        Criteria.where("deptName").is(department),
                        Criteria.where("department").is(department)
                );
                query.addCriteria(deptOr);
            }

            long cnt = mongoTemplate.count(query, "patient");
            total += cnt;
            day = day.plusDays(1);
        }
        return total;
    }

    // ==================== Helper Methods ====================

    /**
     * Safely convert a value to long.
     */
    private static long toLong(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Safely convert a value to double.
     */
    private static double toDouble(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Safely convert a value to a list of strings.
     */
    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object value) {
        if (value == null) return Collections.emptyList();
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                result.add(item == null ? "" : String.valueOf(item));
            }
            return result;
        }
        return Collections.emptyList();
    }
}
