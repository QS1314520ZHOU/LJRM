package com.smartcare.icustats.service;

import com.smartcare.icustats.config.NutritionQualityProperties;
import com.smartcare.icustats.util.NumberUtils;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * 营养质量记录字段适配器
 * 负责从 MongoDB Document 中读取字段值，处理类型兼容
 */
@Component
public class NutritionQualityRecordAdapter {

    private static final Logger log = LoggerFactory.getLogger(NutritionQualityRecordAdapter.class);

    private final NutritionQualityProperties properties;

    public NutritionQualityRecordAdapter(NutritionQualityProperties properties) {
        this.properties = properties;
    }

    // ════════════════════════════════════════════════════════════════════
    // 公共判断与转换方法
    // ════════════════════════════════════════════════════════════════════

    /**
     * 统一判断字段值是否为"已发生/已执行"
     * 适用于: 并发症(jxx/wcd/dxx/grx/zhz)、管道通畅(tcx)、冲管(cg)
     *
     * 规则:
     * - trim 后严格等于配置的 checked-value（默认 "√"）→ true
     * - null、空字符串、"×"、"否"、"false"、"0" 等 → false
     * - 不允许使用 Boolean.parseBoolean()，因为 "√" 会被判为 false
     */
    public boolean isChecked(Object value) {
        if (value == null) return false;
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return false;
        String checkedVal = properties.getCheckedValue();
        return checkedVal != null && checkedVal.equals(text);
    }

    /**
     * 安全将任意值转换为 BigDecimal
     * 支持: Integer, Long, Double, Float, BigDecimal, Decimal128, 数字字符串
     *
     * @return 解析成功返回 BigDecimal，失败返回 null（不抛异常）
     */
    public static BigDecimal toDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) {
            // 处理 Integer, Long, Double, Float 以及 BSON 的 Int32/Int64/Double
            try {
                return new BigDecimal(value.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "/".equals(text) || "-".equals(text)) return null;
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            log.debug("无法解析数值: {}", text);
            return null;
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 字段映射
    // ════════════════════════════════════════════════════════════════════

    /**
     * 获取字段名（从配置中）
     */
    public String fieldName(String key) {
        return properties.getField(key);
    }

    /**
     * 检查字段是否已配置
     */
    public boolean isFieldMapped(String key) {
        String field = properties.getField(key);
        return field != null && !field.isEmpty();
    }

    // ════════════════════════════════════════════════════════════════════
    // 基础字段读取
    // ════════════════════════════════════════════════════════════════════

    /**
     * 获取 pid (String)
     */
    public String getPid(Document doc) {
        String field = fieldName("pid");
        if (field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    /**
     * 获取评估时间 (Date, UTC)
     */
    public Date getRecordTime(Document doc) {
        String field = fieldName("recordTime");
        if (field.isEmpty()) return null;
        return NumberUtils.asDate(doc.get(field));
    }

    /**
     * 获取营养途径原始值
     */
    public String getRoute(Document doc) {
        String field = fieldName("route");
        if (field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    /**
     * 对喂养管类型进行分类
     * 根据 routeValues 配置将 tj 字段值映射为管路类型
     * A=鼻胃管, B=鼻肠管, C=胃造口, D=其它
     */
    public String classifyRoute(Document doc) {
        String rawRoute = getRoute(doc);
        if (rawRoute.isEmpty()) return "unknown";

        Map<String, String> routeValues = properties.getRouteValues();
        if (routeValues == null || routeValues.isEmpty()) {
            return classifyStandardTubeType(rawRoute);
        }

        String nasogastric = routeValues.get("nasogastric");
        String nasojejunal = routeValues.get("nasojejunal");
        String gastrostomy = routeValues.get("gastrostomy");
        String other = routeValues.get("other");

        if (nasogastric != null && nasogastric.equals(rawRoute)) return "nasogastric";
        if (nasojejunal != null && nasojejunal.equals(rawRoute)) return "nasojejunal";
        if (gastrostomy != null && gastrostomy.equals(rawRoute)) return "gastrostomy";
        if (other != null && other.equals(rawRoute)) return "other";

        return classifyStandardTubeType(rawRoute);
    }

    /**
     * 兼容标准编码
     */
    private String classifyStandardTubeType(String rawRoute) {
        if ("A".equals(rawRoute)) return "nasogastric";
        if ("B".equals(rawRoute)) return "nasojejunal";
        if ("C".equals(rawRoute)) return "gastrostomy";
        if ("D".equals(rawRoute)) return "other";
        log.debug("未识别的喂养管类型编码: {}", rawRoute);
        return "unknown";
    }

    /**
     * 获取营养速度 (mL/h)，安全转数值
     */
    public Double getSpeed(Document doc) {
        String field = fieldName("speed");
        if (field.isEmpty()) return null;
        return NumberUtils.safeNumberOrNull(doc.get(field));
    }

    /**
     * 获取喂养管深度 (cm)
     */
    public String getDepth(Document doc) {
        String field = fieldName("depth");
        if (field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    /**
     * 获取管道通畅性
     */
    public String getPatency(Document doc) {
        String field = fieldName("patency");
        if (field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    /**
     * 判断管道是否通畅 (tcx="√" 表示通畅)
     */
    public boolean isPatent(Document doc) {
        return isChecked(doc.get(fieldName("patency")));
    }

    /**
     * 获取胃液颜色
     * 真实数据可能是选项编码(如"5")，编码字典待确认
     */
    public String getGastricColor(Document doc) {
        String field = fieldName("gastricColor");
        if (field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    /**
     * 获取冲管操作
     */
    public String getFlushing(Document doc) {
        String field = fieldName("flushing");
        if (field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    /**
     * 判断是否已冲管 (cg="√" 表示已执行冲管)
     */
    public boolean isFlushed(Document doc) {
        return isChecked(doc.get(fieldName("flushing")));
    }

    /**
     * 获取胃残余量 (mL)
     */
    public String getResidualVolume(Document doc) {
        String field = fieldName("residualVolume");
        if (field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    // ════════════════════════════════════════════════════════════════════
    // 耐受性评分
    // ════════════════════════════════════════════════════════════════════

    /**
     * 获取耐受性总分 (zf)
     * 返回 null 表示未评估
     */
    public Integer getToleranceScore(Document doc) {
        String field = fieldName("toleranceScore");
        if (field.isEmpty()) return null;
        Object value = doc.get(field);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 判断耐受性:
     * - zf == 0 → 耐受
     * - zf > 0  → 不耐受
     * - zf == null → 未评估（不能当作耐受）
     */
    public String classifyTolerance(Document doc) {
        Integer score = getToleranceScore(doc);
        if (score == null) return "unassessed";
        if (score == 0) return "tolerant";
        return "intolerant";
    }

    /**
     * 获取耐受性分项评分（统一解析为数值，>0 表示阳性）
     */
    public BigDecimal getScoreE(Document doc) {
        String field = fieldName("scoreE");
        if (field.isEmpty()) return null;
        return toDecimal(doc.get(field));
    }

    public BigDecimal getScoreF(Document doc) {
        String field = fieldName("scoreF");
        if (field.isEmpty()) return null;
        return toDecimal(doc.get(field));
    }

    public BigDecimal getScoreG(Document doc) {
        String field = fieldName("scoreG");
        if (field.isEmpty()) return null;
        return toDecimal(doc.get(field));
    }

    /**
     * 判断各分项是否阳性 (>0)
     */
    public boolean isScoreEPositive(Document doc) {
        BigDecimal score = getScoreE(doc);
        return score != null && score.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isScoreFPositive(Document doc) {
        BigDecimal score = getScoreF(doc);
        return score != null && score.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isScoreGPositive(Document doc) {
        BigDecimal score = getScoreG(doc);
        return score != null && score.compareTo(BigDecimal.ZERO) > 0;
    }

    // ════════════════════════════════════════════════════════════════════
    // 干预措施
    // ════════════════════════════════════════════════════════════════════

    /**
     * 获取干预措施（单个）
     */
    public String getIntervention(Document doc) {
        String field = fieldName("intervention");
        if (field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    /**
     * 获取干预措施列表
     * 兼容: Array, 单字符串, null
     */
    @SuppressWarnings("unchecked")
    public List<String> getInterventionList(Document doc) {
        String field = fieldName("interventionList");
        if (field.isEmpty()) return Collections.emptyList();

        Object value = doc.get(field);
        if (value == null) return Collections.emptyList();

        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                String s = NumberUtils.normalizeText(item);
                if (!s.isEmpty()) result.add(s);
            }
            return result;
        }

        // Single string
        String s = NumberUtils.normalizeText(value);
        if (s.isEmpty()) return Collections.emptyList();
        return Collections.singletonList(s);
    }

    // ════════════════════════════════════════════════════════════════════
    // 暂停原因
    // ════════════════════════════════════════════════════════════════════

    /**
     * 获取暂停原因列表（优先使用 ztyyList，缺失时回退到 ztyy）
     */
    @SuppressWarnings("unchecked")
    public List<String> getPauseReasonList(Document doc) {
        // 优先使用 pauseReasonList (ztyyList)
        String listField = fieldName("pauseReasonList");
        if (listField != null && !listField.isEmpty()) {
            Object value = doc.get(listField);
            if (value instanceof List) {
                List<String> result = new ArrayList<>();
                for (Object item : (List<?>) value) {
                    String s = NumberUtils.normalizeText(item);
                    if (!s.isEmpty()) result.add(s);
                }
                if (!result.isEmpty()) return result;
            }
        }

        // 回退到 pauseReason (ztyy)
        String reasonField = fieldName("pauseReason");
        if (reasonField != null && !reasonField.isEmpty()) {
            String reason = NumberUtils.normalizeText(doc.get(reasonField));
            if (!reason.isEmpty()) {
                return Collections.singletonList(reason);
            }
        }

        return Collections.emptyList();
    }

    /**
     * 获取暂停原因文本
     */
    public String getPauseReason(Document doc) {
        String field = fieldName("pauseReason");
        if (field == null || field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    // ════════════════════════════════════════════════════════════════════
    // 目标量和完成量
    // ════════════════════════════════════════════════════════════════════

    /**
     * 获取目标量 (BigDecimal，安全转换)
     * 返回 null 表示字段未映射或值无效
     */
    public BigDecimal getTargetVolume(Document doc) {
        String field = fieldName("targetVolume");
        if (field == null || field.isEmpty()) return null; // 未映射
        return toDecimal(doc.get(field));
    }

    /**
     * 获取完成量 (BigDecimal，安全转换)
     * 返回 null 表示字段未映射或值无效
     */
    public BigDecimal getCompletedVolume(Document doc) {
        String field = fieldName("completedVolume");
        if (field == null || field.isEmpty()) return null; // 未映射
        return toDecimal(doc.get(field));
    }

    /**
     * 计算完成率和达标状态
     * @return Map 包含: targetVolume, completedVolume, completionRate, targetReached, mappingStatus
     */
    public Map<String, Object> calcCompletionInfo(Document doc) {
        Map<String, Object> info = new LinkedHashMap<>();

        String targetField = fieldName("targetVolume");
        String completedField = fieldName("completedVolume");

        // 检查字段映射
        if (targetField == null || targetField.isEmpty() || completedField == null || completedField.isEmpty()) {
            info.put("targetVolume", null);
            info.put("completedVolume", null);
            info.put("completionRate", null);
            info.put("targetReached", null);
            info.put("mappingStatus", "需映射");
            return info;
        }

        BigDecimal target = getTargetVolume(doc);
        BigDecimal completed = getCompletedVolume(doc);

        info.put("targetVolume", target);
        info.put("completedVolume", completed);

        if (target == null || target.compareTo(BigDecimal.ZERO) <= 0) {
            info.put("completionRate", null);
            info.put("targetReached", null);
            info.put("mappingStatus", target == null ? "目标量为空" : "目标量无效");
            return info;
        }

        if (completed == null) {
            info.put("completionRate", null);
            info.put("targetReached", null);
            info.put("mappingStatus", "完成量为空");
            return info;
        }

        // 计算完成率: completed / target * 100，保留两位小数
        BigDecimal rate = completed
                .multiply(BigDecimal.valueOf(100))
                .divide(target, 2, java.math.RoundingMode.HALF_UP);

        info.put("completionRate", rate);
        info.put("targetReached", completed.compareTo(target) >= 0);
        info.put("mappingStatus", "ok");
        return info;
    }

    // ════════════════════════════════════════════════════════════════════
    // 并发症判断（全部复用 isChecked）
    // ════════════════════════════════════════════════════════════════════

    /**
     * 判断机械性并发症是否发生
     */
    public boolean hasMechanicalComplication(Document doc) {
        String field = fieldName("mechanicalComplication");
        if (field == null || field.isEmpty()) return false;
        return isChecked(doc.get(field));
    }

    /**
     * 判断胃肠道并发症是否发生
     */
    public boolean hasGastrointestinalComplication(Document doc) {
        String field = fieldName("gastrointestinalComplication");
        if (field == null || field.isEmpty()) return false;
        return isChecked(doc.get(field));
    }

    /**
     * 判断代谢性并发症是否发生
     */
    public boolean hasMetabolicComplication(Document doc) {
        String field = fieldName("metabolicComplication");
        if (field == null || field.isEmpty()) return false;
        return isChecked(doc.get(field));
    }

    /**
     * 判断感染性并发症是否发生
     */
    public boolean hasInfectionComplication(Document doc) {
        String field = fieldName("infectionComplication");
        if (field == null || field.isEmpty()) return false;
        return isChecked(doc.get(field));
    }

    /**
     * 判断再喂养综合征是否发生
     */
    public boolean hasRefeedingSyndrome(Document doc) {
        String field = fieldName("refeedingSyndrome");
        if (field == null || field.isEmpty()) return false;
        return isChecked(doc.get(field));
    }

    /**
     * 判断是否有任意并发症（五类之一）
     */
    public boolean hasAnyComplication(Document doc) {
        return hasMechanicalComplication(doc)
                || hasGastrointestinalComplication(doc)
                || hasMetabolicComplication(doc)
                || hasInfectionComplication(doc)
                || hasRefeedingSyndrome(doc);
    }

    /**
     * 获取机械性并发症原始值（用于展示）
     */
    public String getMechanicalComplication(Document doc) {
        String field = fieldName("mechanicalComplication");
        if (field == null || field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    /**
     * 获取胃肠道并发症原始值
     */
    public String getGastrointestinalComplication(Document doc) {
        String field = fieldName("gastrointestinalComplication");
        if (field == null || field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    /**
     * 获取代谢性并发症原始值
     */
    public String getMetabolicComplication(Document doc) {
        String field = fieldName("metabolicComplication");
        if (field == null || field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    /**
     * 获取感染性并发症原始值
     */
    public String getInfectionComplication(Document doc) {
        String field = fieldName("infectionComplication");
        if (field == null || field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    /**
     * 获取再喂养综合征原始值
     */
    public String getRefeedingSyndrome(Document doc) {
        String field = fieldName("refeedingSyndrome");
        if (field == null || field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    // ════════════════════════════════════════════════════════════════════
    // 备注和其他
    // ════════════════════════════════════════════════════════════════════

    /**
     * 获取备注
     */
    public String getRemark(Document doc) {
        String field = fieldName("remark");
        if (field == null || field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    /**
     * 获取科室代码
     */
    public String getDeptCode(Document doc) {
        String field = fieldName("deptCode");
        if (field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    /**
     * 获取病历号
     */
    public String getMrn(Document doc) {
        String field = fieldName("mrn");
        if (field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    /**
     * 获取姓名快照
     */
    public String getName(Document doc) {
        String field = fieldName("name");
        if (field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    /**
     * 获取床号快照
     */
    public String getBed(Document doc) {
        String field = fieldName("bed");
        if (field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    // ════════════════════════════════════════════════════════════════════
    // 有效性判断
    // ════════════════════════════════════════════════════════════════════

    /**
     * 判断记录是否有效
     * 规则: document.valid trim 后 == 配置的 validValue
     * null、"invalid"、空字符串 → 排除
     */
    public boolean isValidRecord(Document doc) {
        Object value = doc.get("valid");
        if (value == null) return false;

        String validValue = properties.getValidValue();
        String actualValue = String.valueOf(value).trim();

        // 严格比较配置的有效值
        if (validValue != null && validValue.equals(actualValue)) return true;

        // Legacy 兼容: boolean true, 1 (不推荐，保留向后兼容)
        if (Boolean.TRUE.equals(value)) return true;
        if ("1".equals(actualValue)) return true;

        return false;
    }

    /**
     * 判断是否包含暂停干预 (J)
     */
    public boolean hasPauseIntervention(Document doc) {
        // Check csList first
        List<String> csList = getInterventionList(doc);
        if (csList.contains("J")) return true;

        // Fallback to cs field
        String cs = getIntervention(doc);
        return "J".equals(cs);
    }

    /**
     * 判断是否有目标量字段映射
     */
    public boolean isTargetVolumeMapped() {
        String field = fieldName("targetVolume");
        return field != null && !field.isEmpty();
    }

    /**
     * 判断是否有完成量字段映射
     */
    public boolean isCompletedVolumeMapped() {
        String field = fieldName("completedVolume");
        return field != null && !field.isEmpty();
    }

    /**
     * 判断是否有并发症字段映射
     */
    public boolean isComplicationFieldMapped(String key) {
        String field = fieldName(key);
        return field != null && !field.isEmpty();
    }

    // ════════════════════════════════════════════════════════════════════
    // 脱敏和构建
    // ════════════════════════════════════════════════════════════════════

    /**
     * 患者ID脱敏
     * 6a7b****d292
     */
    public static String maskPid(String pid) {
        if (pid == null || pid.length() < 8) return "****";
        return pid.substring(0, 4) + "****" + pid.substring(pid.length() - 4);
    }

    /**
     * 构建明细行数据
     */
    public Map<String, Object> buildDetailRow(Document doc, int index, String statMonth,
                                                String patientName, String patientMrn,
                                                String patientBedNo, String department,
                                                boolean inNumerator, boolean inDenominator,
                                                String judgmentReason, String dataSource) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("index", index);
        row.put("recordTime", NumberUtils.formatDateTime(getRecordTime(doc)));
        row.put("statMonth", statMonth != null ? statMonth : "");
        row.put("department", department != null ? department : "");
        row.put("deptCode", getDeptCode(doc));
        row.put("bedNo", patientBedNo != null ? patientBedNo : getBed(doc));
        row.put("name", patientName != null ? patientName : getName(doc));
        row.put("hospitalNo", patientMrn != null ? patientMrn : getMrn(doc));
        row.put("patientIdMasked", maskPid(getPid(doc)));
        row.put("route", getRoute(doc));
        row.put("routeClassified", classifyRoute(doc));

        // 目标量和完成量
        BigDecimal targetVol = getTargetVolume(doc);
        row.put("targetVolume", targetVol != null ? targetVol : "");
        BigDecimal completedVol = getCompletedVolume(doc);
        row.put("completedVolume", completedVol != null ? completedVol : "");

        row.put("speed", getSpeed(doc) != null ? getSpeed(doc) : "");
        row.put("depth", getDepth(doc));
        row.put("patency", getPatency(doc));
        row.put("isPatent", isPatent(doc));
        row.put("gastricColor", getGastricColor(doc));
        row.put("flushing", getFlushing(doc));
        row.put("isFlushed", isFlushed(doc));
        row.put("residualVolume", getResidualVolume(doc));
        row.put("toleranceScore", getToleranceScore(doc) != null ? getToleranceScore(doc) : "");
        row.put("toleranceClassified", classifyTolerance(doc));
        row.put("scoreE", getScoreE(doc) != null ? getScoreE(doc) : "");
        row.put("scoreF", getScoreF(doc) != null ? getScoreF(doc) : "");
        row.put("scoreG", getScoreG(doc) != null ? getScoreG(doc) : "");
        row.put("mechanicalComplication", getMechanicalComplication(doc));
        row.put("gastrointestinalComplication", getGastrointestinalComplication(doc));
        row.put("metabolicComplication", getMetabolicComplication(doc));
        row.put("infectionComplication", getInfectionComplication(doc));
        row.put("refeedingSyndrome", getRefeedingSyndrome(doc));
        row.put("hasAnyComplication", hasAnyComplication(doc));
        row.put("intervention", getIntervention(doc));
        row.put("interventionList", String.join(", ", getInterventionList(doc)));
        row.put("pauseReason", getPauseReason(doc));
        row.put("pauseReasonList", String.join(", ", getPauseReasonList(doc)));
        row.put("remark", getRemark(doc));
        row.put("inNumerator", inNumerator ? "是" : "否");
        row.put("inDenominator", inDenominator ? "是" : "否");
        row.put("judgmentReason", judgmentReason != null ? judgmentReason : "");
        row.put("dataSource", dataSource != null ? dataSource : "");
        return row;
    }
}
