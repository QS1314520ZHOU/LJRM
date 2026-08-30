package com.smartcare.icustats.service;

import com.smartcare.icustats.config.NutritionQualityProperties;
import com.smartcare.icustats.dto.NutritionQualityCell;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 营养质量指标纯计算服务
 * 不依赖数据库查询，只负责数据处理、去重和指标计算
 */
@Service
public class NutritionQualityCalculationService {

    private static final Logger log = LoggerFactory.getLogger(NutritionQualityCalculationService.class);
    private static final DateTimeFormatter YYYY_MM = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final NutritionQualityProperties properties;
    private final NutritionQualityRecordAdapter adapter;

    public NutritionQualityCalculationService(NutritionQualityProperties properties,
                                               NutritionQualityRecordAdapter adapter) {
        this.properties = properties;
        this.adapter = adapter;
    }

    // ════════════════════════════════════════════════════════════════════
    // 安全计算
    // ════════════════════════════════════════════════════════════════════

    /**
     * 安全计算率值
     * denominator <= 0 时返回 null
     */
    public static NutritionQualityCell safeRate(int numerator, int denominator, boolean compliant) {
        if (denominator <= 0) {
            return NutritionQualityCell.noDenominator();
        }
        double value = BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP)
                .doubleValue();
        return NutritionQualityCell.ok(numerator, denominator, value, compliant);
    }

    /**
     * 安全计算比值
     * denominator <= 0 时返回 null
     */
    public static NutritionQualityCell safeRatio(int numerator, int denominator, boolean compliant) {
        if (denominator <= 0) {
            return NutritionQualityCell.noDenominator();
        }
        double value = BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP)
                .doubleValue();
        return NutritionQualityCell.ok(numerator, denominator, value, compliant);
    }

    // ════════════════════════════════════════════════════════════════════
    // 日期工具
    // ════════════════════════════════════════════════════════════════════

    /**
     * UTC Date 转上海时区月份字符串
     */
    public String toShanghaiMonth(Date date) {
        if (date == null) return "";
        ZoneId zone = ZoneId.of(properties.getTimezone());
        ZonedDateTime zdt = date.toInstant().atZone(zone);
        return zdt.format(YYYY_MM);
    }

    /**
     * UTC Date 转上海时区日期字符串
     */
    public String toShanghaiDate(Date date) {
        if (date == null) return "";
        ZoneId zone = ZoneId.of(properties.getTimezone());
        ZonedDateTime zdt = date.toInstant().atZone(zone);
        return zdt.format(YYYY_MM_DD);
    }

    /**
     * 获取上海时区当前时间
     */
    public ZonedDateTime nowShanghai() {
        return ZonedDateTime.now(ZoneId.of(properties.getTimezone()));
    }

    // ════════════════════════════════════════════════════════════════════
    // 记录去重
    // ════════════════════════════════════════════════════════════════════

    /**
     * 按患者+日期去重，保留每天最新的一条记录
     * @param records 原始记录列表
     * @return 去重后的记录列表
     */
    public List<Document> selectLatestDailyAssessment(List<Document> records) {
        // key: pid::date → latest record
        Map<String, Document> latestMap = new LinkedHashMap<>();

        for (Document record : records) {
            if (!adapter.isValidRecord(record)) continue;

            String pid = adapter.getPid(record);
            Date time = adapter.getRecordTime(record);
            if (pid.isEmpty() || time == null) continue;

            String date = toShanghaiDate(time);
            String key = pid + "::" + date;

            Document existing = latestMap.get(key);
            if (existing == null) {
                latestMap.put(key, record);
            } else {
                Date existingTime = adapter.getRecordTime(existing);
                if (time.after(existingTime)) {
                    latestMap.put(key, record);
                }
            }
        }

        return new ArrayList<>(latestMap.values());
    }

    /**
     * 按月份分组
     */
    public Map<String, List<Document>> groupByMonth(List<Document> records) {
        Map<String, List<Document>> groups = new LinkedHashMap<>();
        for (Document record : records) {
            Date time = adapter.getRecordTime(record);
            if (time == null) continue;
            String month = toShanghaiMonth(time);
            groups.computeIfAbsent(month, k -> new ArrayList<>()).add(record);
        }
        return groups;
    }

    /**
     * 提取所有出现的月份（排序）
     */
    public List<String> extractMonths(List<Document> records) {
        Set<String> months = new TreeSet<>();
        for (Document record : records) {
            Date time = adapter.getRecordTime(record);
            if (time == null) continue;
            months.add(toShanghaiMonth(time));
        }
        return new ArrayList<>(months);
    }

    // ════════════════════════════════════════════════════════════════════
    // 患者级去重辅助
    // ════════════════════════════════════════════════════════════════════

    /**
     * 按患者去重后的 pid 集合
     */
    public Set<String> uniquePids(List<Document> records) {
        return records.stream()
                .map(adapter::getPid)
                .filter(p -> !p.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 按患者+日期去重的记录数
     */
    public int deduplicatedCount(List<Document> records) {
        return selectLatestDailyAssessment(records).size();
    }

    // ════════════════════════════════════════════════════════════════════
    // 指标计算
    // ════════════════════════════════════════════════════════════════════

    /**
     * 计算肠内营养中断率
     * 分子: csList含J或cs=J 的去重记录（按patient+day去重）
     * 分母: 有效记录数（按patient+day去重）
     */
    public NutritionQualityCell calcInterruptionRate(List<Document> records) {
        if (records.isEmpty()) return NutritionQualityCell.noData();

        // 按 patient+day 去重
        Map<String, Document> latestByPatientDay = new LinkedHashMap<>();
        for (Document record : records) {
            if (!adapter.isValidRecord(record)) continue;
            String pid = adapter.getPid(record);
            Date time = adapter.getRecordTime(record);
            if (pid.isEmpty() || time == null) continue;
            String key = pid + "::" + toShanghaiDate(time);
            latestByPatientDay.put(key, record); // 保留最后一条
        }

        int denominator = latestByPatientDay.size();
        if (denominator == 0) return NutritionQualityCell.noData();

        // 分子: 包含暂停干预的patient+day
        long numerator = latestByPatientDay.values().stream()
                .filter(adapter::hasPauseIntervention)
                .count();

        // 中断率 < 10% 为达标
        double rate = numerator * 100.0 / denominator;
        boolean compliant = rate < 10.0;
        return NutritionQualityCell.ok((int) numerator, denominator,
                BigDecimal.valueOf(rate).setScale(2, RoundingMode.HALF_UP).doubleValue(), compliant);
    }

    /**
     * 计算肠内营养计划完成率
     * 使用 BigDecimal 精确计算，防止精度丢失
     */
    public NutritionQualityCell calcPlanCompletionRate(List<Document> records) {
        if (!adapter.isTargetVolumeMapped() || !adapter.isCompletedVolumeMapped()) {
            return NutritionQualityCell.mappingRequired("targetVolume/completedVolume");
        }

        if (records.isEmpty()) return NutritionQualityCell.noData();

        int denominator = 0;
        int numerator = 0;

        for (Document record : records) {
            if (!adapter.isValidRecord(record)) continue;
            BigDecimal target = adapter.getTargetVolume(record);
            BigDecimal completed = adapter.getCompletedVolume(record);

            // 只有目标量和完成量均有效时才进入分母
            if (target == null || target.compareTo(BigDecimal.ZERO) <= 0) continue;
            if (completed == null) continue;

            denominator++;
            if (completed.compareTo(target) >= 0) {
                numerator++;
            }
        }

        if (denominator == 0) return NutritionQualityCell.noData();

        double rate = numerator * 100.0 / denominator;
        boolean compliant = rate >= 80.0;
        return NutritionQualityCell.ok(numerator, denominator,
                BigDecimal.valueOf(rate).setScale(2, RoundingMode.HALF_UP).doubleValue(), compliant);
    }

    /**
     * 计算喂养管堵管发生率
     * 使用 isChecked 判断机械性并发症
     * 分子: 按患者去重，只要任意一条记录有并发症即计入
     */
    public NutritionQualityCell calcTubeBlockageRate(List<Document> records) {
        if (!adapter.isComplicationFieldMapped("mechanicalComplication")) {
            return NutritionQualityCell.mappingRequired("mechanicalComplication");
        }

        if (records.isEmpty()) return NutritionQualityCell.noData();

        // 按 patient+day 去重
        Map<String, Document> latestByPatientDay = new LinkedHashMap<>();
        for (Document record : records) {
            if (!adapter.isValidRecord(record)) continue;
            String pid = adapter.getPid(record);
            Date time = adapter.getRecordTime(record);
            if (pid.isEmpty() || time == null) continue;
            String key = pid + "::" + toShanghaiDate(time);
            latestByPatientDay.put(key, record);
        }

        int denominator = latestByPatientDay.size();
        if (denominator == 0) return NutritionQualityCell.noData();

        long numerator = latestByPatientDay.values().stream()
                .filter(adapter::hasMechanicalComplication)
                .count();

        double rate = numerator * 100.0 / denominator;
        boolean compliant = rate < 5.0;
        return NutritionQualityCell.ok((int) numerator, denominator,
                BigDecimal.valueOf(rate).setScale(2, RoundingMode.HALF_UP).doubleValue(), compliant);
    }

    /**
     * 计算喂养管非计划拔除发生率
     * 使用 tubeExe 数据（需要外部提供）
     */
    public NutritionQualityCell calcUnplannedRemovalRate(int unplannedCount, int tubeDays) {
        if (tubeDays <= 0) return NutritionQualityCell.noData();

        double rate = unplannedCount * 100.0 / tubeDays;
        boolean compliant = rate < 3.0;
        return NutritionQualityCell.ok(unplannedCount, tubeDays,
                BigDecimal.valueOf(rate).setScale(2, RoundingMode.HALF_UP).doubleValue(), compliant);
    }

    /**
     * 计算喂养管相关皮肤问题发生率
     * 需要对应字段映射
     */
    public NutritionQualityCell calcSkinProblemRate(List<Document> records) {
        // 皮肤问题字段待确认
        return NutritionQualityCell.mappingRequired("skinProblem");
    }

    /**
     * 计算误吸发生率
     * 需要对应字段映射
     */
    public NutritionQualityCell calcAspirationRate(List<Document> records) {
        // 误吸字段待确认
        return NutritionQualityCell.mappingRequired("aspiration");
    }

    /**
     * 计算喂养不耐受发生率
     * 基于耐受性总分:
     * - zf == 0: 耐受
     * - zf > 0: 不耐受
     * - zf == null: 未评估（不计入分母）
     * 不耐受率的分母必须是 assessedCount，不是全部记录数
     */
    public NutritionQualityCell calcFeedingIntoleranceRate(List<Document> records) {
        if (records.isEmpty()) return NutritionQualityCell.noData();

        // 按 patient+day 去重
        Map<String, Document> latestByPatientDay = new LinkedHashMap<>();
        for (Document record : records) {
            if (!adapter.isValidRecord(record)) continue;
            String pid = adapter.getPid(record);
            Date time = adapter.getRecordTime(record);
            if (pid.isEmpty() || time == null) continue;
            String key = pid + "::" + toShanghaiDate(time);
            latestByPatientDay.put(key, record);
        }

        if (latestByPatientDay.isEmpty()) return NutritionQualityCell.noData();

        // 统计耐受性分类
        int assessedCount = 0;
        int intolerantCount = 0;

        for (Document record : latestByPatientDay.values()) {
            String tolerance = adapter.classifyTolerance(record);
            if ("unassessed".equals(tolerance)) continue; // 未评估不进入分母

            assessedCount++;
            // 不耐受: zf > 0 或 包含J干预
            boolean intolerant = "intolerant".equals(tolerance) || adapter.hasPauseIntervention(record);
            if (intolerant) intolerantCount++;
        }

        if (assessedCount == 0) return NutritionQualityCell.noData();

        double rate = intolerantCount * 100.0 / assessedCount;
        boolean compliant = rate < 20.0;
        return NutritionQualityCell.ok(intolerantCount, assessedCount,
                BigDecimal.valueOf(rate).setScale(2, RoundingMode.HALF_UP).doubleValue(), compliant);
    }

    /**
     * 计算肠内营养与肠外营养比
     * 复用现有 NutritionService 口径
     */
    public NutritionQualityCell calcEnteralParenteralRatio(int enteralPatients, int parenteralPatients) {
        if (parenteralPatients <= 0) return NutritionQualityCell.noDenominator();

        double ratio = BigDecimal.valueOf(enteralPatients)
                .divide(BigDecimal.valueOf(parenteralPatients), 2, RoundingMode.HALF_UP)
                .doubleValue();
        boolean compliant = ratio >= 2.0;
        return NutritionQualityCell.ok(enteralPatients, parenteralPatients, ratio, compliant);
    }

    // ════════════════════════════════════════════════════════════════════
    // 并发症患者级统计
    // ════════════════════════════════════════════════════════════════════

    /**
     * 并发症患者级统计
     * 同一患者同一统计周期多条记录，只要任意一条有效记录为 "√"，该患者即计为发生
     *
     * @return Map: assessedCount, affectedCount, assessedPids, affectedPids
     */
    public Map<String, Object> calcComplicationStats(List<Document> records,
                                                      String complicationType) {
        Map<String, Object> result = new LinkedHashMap<>();
        Set<String> assessedPids = new LinkedHashSet<>();
        Set<String> affectedPids = new LinkedHashSet<>();

        for (Document record : records) {
            if (!adapter.isValidRecord(record)) continue;
            String pid = adapter.getPid(record);
            if (pid.isEmpty()) continue;

            assessedPids.add(pid);

            boolean affected;
            switch (complicationType) {
                case "mechanical":
                    affected = adapter.hasMechanicalComplication(record);
                    break;
                case "gastrointestinal":
                    affected = adapter.hasGastrointestinalComplication(record);
                    break;
                case "metabolic":
                    affected = adapter.hasMetabolicComplication(record);
                    break;
                case "infection":
                    affected = adapter.hasInfectionComplication(record);
                    break;
                case "refeeding":
                    affected = adapter.hasRefeedingSyndrome(record);
                    break;
                case "any":
                    affected = adapter.hasAnyComplication(record);
                    break;
                default:
                    affected = false;
            }

            if (affected) {
                affectedPids.add(pid);
            }
        }

        result.put("assessedCount", assessedPids.size());
        result.put("affectedCount", affectedPids.size());
        result.put("assessedPids", assessedPids);
        result.put("affectedPids", affectedPids);
        return result;
    }

    // ════════════════════════════════════════════════════════════════════
    // 耐受性统计
    // ════════════════════════════════════════════════════════════════════

    /**
     * 耐受性患者级统计
     * @return Map: assessedCount, tolerantCount, intolerantCount, unassessedCount
     */
    public Map<String, Object> calcToleranceStats(List<Document> records) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 按 patient+day 去重
        Map<String, Document> latestByPatientDay = new LinkedHashMap<>();
        for (Document record : records) {
            if (!adapter.isValidRecord(record)) continue;
            String pid = adapter.getPid(record);
            Date time = adapter.getRecordTime(record);
            if (pid.isEmpty() || time == null) continue;
            String key = pid + "::" + toShanghaiDate(time);
            latestByPatientDay.put(key, record);
        }

        int assessedCount = 0;
        int tolerantCount = 0;
        int intolerantCount = 0;
        int unassessedCount = 0;

        for (Document record : latestByPatientDay.values()) {
            String tolerance = adapter.classifyTolerance(record);
            switch (tolerance) {
                case "tolerant":
                    assessedCount++;
                    tolerantCount++;
                    break;
                case "intolerant":
                    assessedCount++;
                    intolerantCount++;
                    break;
                default:
                    unassessedCount++;
            }
        }

        result.put("assessedCount", assessedCount);
        result.put("tolerantCount", tolerantCount);
        result.put("intolerantCount", intolerantCount);
        result.put("unassessedCount", unassessedCount);
        return result;
    }

    // ════════════════════════════════════════════════════════════════════
    // 辅助方法
    // ════════════════════════════════════════════════════════════════════

    /**
     * 提取记录中的患者ID列表（去重）
     */
    public List<String> extractUniquePids(List<Document> records) {
        return records.stream()
                .map(adapter::getPid)
                .filter(p -> !p.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 按科室代码过滤记录
     */
    public List<Document> filterByDeptCode(List<Document> records, String deptCode) {
        if (deptCode == null || deptCode.isEmpty()) return records;
        return records.stream()
                .filter(r -> deptCode.equals(adapter.getDeptCode(r)))
                .collect(Collectors.toList());
    }

    /**
     * 构建明细判定原因
     */
    public String buildJudgmentReason(String indicatorKey, Document record,
                                        boolean inNumerator, boolean inDenominator) {
        StringBuilder reason = new StringBuilder();

        switch (indicatorKey) {
            case "enteralInterruptionRate":
                if (adapter.hasPauseIntervention(record)) {
                    reason.append("csList含J，计入分子");
                } else {
                    reason.append("无暂停干预，不计入分子");
                }
                break;

            case "feedingIntoleranceRate":
                String tolerance = adapter.classifyTolerance(record);
                if ("unassessed".equals(tolerance)) {
                    reason.append("zf=null，未评估，不计入分母");
                } else if ("intolerant".equals(tolerance)) {
                    reason.append("zf>0，不耐受，计入分子");
                } else if (adapter.hasPauseIntervention(record)) {
                    reason.append("zf=0但csList含J，计入分子");
                } else {
                    reason.append("耐受性良好，不计入分子");
                }
                break;

            case "enteralPlanCompletionRate":
                BigDecimal target = adapter.getTargetVolume(record);
                BigDecimal completed = adapter.getCompletedVolume(record);
                if (target == null) {
                    reason.append("目标量为空，不计入分母");
                } else if (target.compareTo(BigDecimal.ZERO) <= 0) {
                    reason.append("目标量<=0，不计入分母");
                } else if (completed == null) {
                    reason.append("完成量为空，不计入分母");
                } else if (completed.compareTo(target) >= 0) {
                    reason.append("完成量>=目标量，计入分子");
                } else {
                    reason.append("完成量<目标量，不计入分子");
                }
                break;

            case "feedingTubeBlockageRate":
                if (adapter.hasMechanicalComplication(record)) {
                    reason.append("机械性并发症为" + properties.getCheckedValue() + "，计入分子");
                } else {
                    reason.append("无机械性并发症，不计入分子");
                }
                break;

            default:
                reason.append(inNumerator ? "计入分子" : "不计入分子");
                break;
        }

        return reason.toString();
    }
}
