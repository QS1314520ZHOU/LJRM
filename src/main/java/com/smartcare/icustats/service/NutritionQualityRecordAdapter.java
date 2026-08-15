package com.smartcare.icustats.service;

import com.smartcare.icustats.config.NutritionQualityProperties;
import com.smartcare.icustats.util.NumberUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
     * 获取营养途径
     */
    public String getRoute(Document doc) {
        String field = fieldName("route");
        if (field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
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
     * 获取通畅性
     */
    public String getPatency(Document doc) {
        String field = fieldName("patency");
        if (field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    /**
     * 获取胃液颜色
     */
    public String getGastricColor(Document doc) {
        String field = fieldName("gastricColor");
        if (field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    /**
     * 获取冲管
     */
    public String getFlushing(Document doc) {
        String field = fieldName("flushing");
        if (field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    /**
     * 获取胃残余量 (mL)
     */
    public String getResidualVolume(Document doc) {
        String field = fieldName("residualVolume");
        if (field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    /**
     * 获取耐受性总分
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
     * 获取耐受性分项E
     */
    public String getScoreE(Document doc) {
        String field = fieldName("scoreE");
        if (field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    /**
     * 获取耐受性分项F
     */
    public String getScoreF(Document doc) {
        String field = fieldName("scoreF");
        if (field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    /**
     * 获取耐受性分项G
     */
    public String getScoreG(Document doc) {
        String field = fieldName("scoreG");
        if (field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

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

    /**
     * 获取目标量
     * 返回 null 表示字段未映射或值无效
     */
    public Double getTargetVolume(Document doc) {
        String field = fieldName("targetVolume");
        if (field == null || field.isEmpty()) return null; // 未映射
        Object value = doc.get(field);
        if (value == null) return null;
        String str = String.valueOf(value).trim();
        if (str.isEmpty() || "/".equals(str) || "-".equals(str)) return null;
        Double num = NumberUtils.safeNumberOrNull(value);
        if (num == null || num.isNaN() || num.isInfinite()) return null;
        return num;
    }

    /**
     * 获取完成量
     * 返回 null 表示字段未映射或值无效
     */
    public Double getCompletedVolume(Document doc) {
        String field = fieldName("completedVolume");
        if (field == null || field.isEmpty()) return null; // 未映射
        Object value = doc.get(field);
        if (value == null) return null;
        String str = String.valueOf(value).trim();
        if (str.isEmpty() || "/".equals(str) || "-".equals(str)) return null;
        Double num = NumberUtils.safeNumberOrNull(value);
        if (num == null || num.isNaN() || num.isInfinite()) return null;
        return num;
    }

    /**
     * 获取机械性并发症
     */
    public String getMechanicalComplication(Document doc) {
        String field = fieldName("mechanicalComplication");
        if (field == null || field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    /**
     * 获取胃肠道并发症
     */
    public String getGastrointestinalComplication(Document doc) {
        String field = fieldName("gastrointestinalComplication");
        if (field == null || field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    /**
     * 获取代谢性并发症
     */
    public String getMetabolicComplication(Document doc) {
        String field = fieldName("metabolicComplication");
        if (field == null || field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    /**
     * 获取感染性并发症
     */
    public String getInfectionComplication(Document doc) {
        String field = fieldName("infectionComplication");
        if (field == null || field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    /**
     * 获取再喂养综合征
     */
    public String getRefeedingSyndrome(Document doc) {
        String field = fieldName("refeedingSyndrome");
        if (field == null || field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

    /**
     * 获取暂停原因
     */
    public String getPauseReason(Document doc) {
        String field = fieldName("pauseReason");
        if (field == null || field.isEmpty()) return "";
        return NumberUtils.normalizeText(doc.get(field));
    }

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

    /**
     * 判断记录是否有效
     * 默认有效值: "valid"
     * 兼容: boolean true, 1
     */
    public boolean isValidRecord(Document doc) {
        Object value = doc.get("valid");
        if (value == null) return false;

        String validValue = properties.getValidValue();
        if (validValue != null) {
            // Check against configured valid value
            if (validValue.equals(String.valueOf(value))) return true;
        }

        // Legacy compatibility
        if (Boolean.TRUE.equals(value)) return true;
        if ("1".equals(String.valueOf(value))) return true;

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
        Double targetVol = getTargetVolume(doc);
        row.put("targetVolume", targetVol != null ? targetVol : "");
        Double completedVol = getCompletedVolume(doc);
        row.put("completedVolume", completedVol != null ? completedVol : "");
        row.put("speed", getSpeed(doc) != null ? getSpeed(doc) : "");
        row.put("depth", getDepth(doc));
        row.put("patency", getPatency(doc));
        row.put("gastricColor", getGastricColor(doc));
        row.put("flushing", getFlushing(doc));
        row.put("residualVolume", getResidualVolume(doc));
        row.put("toleranceScore", getToleranceScore(doc) != null ? getToleranceScore(doc) : "");
        row.put("scoreE", getScoreE(doc));
        row.put("scoreF", getScoreF(doc));
        row.put("scoreG", getScoreG(doc));
        row.put("mechanicalComplication", getMechanicalComplication(doc));
        row.put("gastrointestinalComplication", getGastrointestinalComplication(doc));
        row.put("metabolicComplication", getMetabolicComplication(doc));
        row.put("infectionComplication", getInfectionComplication(doc));
        row.put("refeedingSyndrome", getRefeedingSyndrome(doc));
        row.put("intervention", getIntervention(doc));
        row.put("interventionList", String.join(", ", getInterventionList(doc)));
        row.put("pauseReason", getPauseReason(doc));
        row.put("remark", getRemark(doc));
        row.put("inNumerator", inNumerator ? "是" : "否");
        row.put("inDenominator", inDenominator ? "是" : "否");
        row.put("judgmentReason", judgmentReason != null ? judgmentReason : "");
        row.put("dataSource", dataSource != null ? dataSource : "");
        return row;
    }
}
