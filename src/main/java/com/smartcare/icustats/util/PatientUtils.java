package com.smartcare.icustats.util;

import org.bson.Document;

import java.util.*;

/**
 * 患者工具类 - 构建患者相关查询过滤器和行数据
 * 原Node.js文件: statsService.js / qualityService.js / nutritionService.js
 */
public class PatientUtils {

    public static final String[] DEPARTMENT_FIELDS = {
        "department", "deptName", "wardName", "inDeptName", "currentDeptName", "unitName"
    };

    public static final String[] PATIENT_SELECT_FIELDS = {
        "_id", "hisPid", "mrn", "name", "birthday", "age", "gender", "hisBed", "bedNo", "bedCode", "bedName", "bedNumber",
        "hospitalNo", "hospitalNumber", "zyh", "zyhm", "hospitalTime", "icuAdmissionTime", "icuDischargeTime",
        "department", "deptName", "wardName", "inDeptName", "currentDeptName", "unitName", "admissionDoctor",
        "admissionDoctorName", "attendingDoctor", "attendingDoctorName", "chargeDoctorName", "tubeDoctorName",
        "bedDoctor", "admissionSource", "inSource", "source", "dischargedType", "dischargeType", "outType",
        "dischargedDepartment", "transferDept", "outDeptName",
        "admissionDiagnosis", "diagnosis", "clinicalDiagnosis", "primaryDiagnosis",
        "status"
    };

    public static final String[] QUALITY_DEPARTMENT_FIELDS = {
        "department", "deptName", "wardName", "inDeptName", "currentDeptName", "unitName", "dept"
    };

    /**
     * 转义正则表达式特殊字符
     * 原Node.js: escapeRegExp(text)
     */
    public static String escapeRegExp(String text) {
        return text.replaceAll("([.*+?^${}()|[\\]\\\\])", "\\\\$1");
    }

    /**
     * 构建部门过滤OR条件
     * 原Node.js: buildDepartmentOr(department)
     * 当 ENABLE_DEPT_FILTER=false 或 department 为空时返回空列表（不过滤）
     */
    public static List<Document> buildDepartmentOr(String department, boolean enableDeptFilter) {
        if (!enableDeptFilter || department == null || department.isEmpty()) {
            return Collections.emptyList();
        }
        String escaped = escapeRegExp(department);
        List<Document> orConditions = new ArrayList<>();
        for (String field : DEPARTMENT_FIELDS) {
            orConditions.add(new Document(field, new Document("$regex", escaped).append("$options", "i")));
        }
        return orConditions;
    }

    /**
     * 构建患者查询过滤器（含部门过滤）
     * 原Node.js: buildPatientFilter(extra, department)
     */
    public static Document buildPatientFilter(Map<String, Object> extra, String department, boolean enableDeptFilter) {
        Document filter = new Document("status", new Document("$ne", "invalid"));
        if (extra != null) {
            filter.putAll(extra);
        }
        List<Document> deptOr = buildDepartmentOr(department, enableDeptFilter);
        if (!deptOr.isEmpty()) {
            List<Document> andList = new ArrayList<>();
            andList.add(filter);
            andList.add(new Document("$or", deptOr));
            return new Document("$and", andList);
        }
        return filter;
    }

    /**
     * 构建月度重叠过滤器（同期ICU收治患者总数）
     * 原Node.js: buildMonthlyOverlapFilter(startDate, endDate, department)
     */
    public static Document buildMonthlyOverlapFilter(Date startDate, Date endDate, String department, boolean enableDeptFilter) {
        Document baseFilter = new Document("icuAdmissionTime", new Document("$lte", endDate));
        List<Document> orList = new ArrayList<>();
        orList.add(new Document("icuDischargeTime", new Document("$gte", startDate)));
        orList.add(new Document("icuDischargeTime", null));
        orList.add(new Document("icuDischargeTime", new Document("$exists", false)));
        baseFilter.append("$or", orList);

        return buildPatientFilter(baseFilter, department, enableDeptFilter);
    }

    /**
     * 构建医嘱查询过滤器
     * 原Node.js: buildOrderFilter(extra)
     */
    public static Document buildOrderFilter(Map<String, Object> extra) {
        Document filter = new Document("status", new Document("$ne", "作废"));
        if (extra != null) {
            filter.putAll(extra);
        }
        return filter;
    }

    /**
     * 构建bedside查询过滤器
     * 原Node.js: buildBedsideFilter(extra)
     */
    public static Document buildBedsideFilter(Map<String, Object> extra) {
        Document filter = new Document("valid", new Document("$ne", false));
        if (extra != null) {
            filter.putAll(extra);
        }
        return filter;
    }

    /**
     * 从文档中获取患者详情行
     * 原Node.js: toDetailRow(patient, index, extra) / toPatientDetailRow(patient, index, statMonth, extra)
     */
    public static Map<String, Object> toDetailRow(Document patient, int index, String statMonth, Map<String, Object> extra) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("index", index);
        if (statMonth != null) row.put("statMonth", statMonth);
        row.put("department", firstValue(patient, new String[]{
            "department", "deptName", "wardName", "inDeptName", "currentDeptName", "unitName", "dept"
        }, "重症医学科"));
        row.put("bedNo", firstValue(patient, new String[]{"hisBed", "bedNo", "bedName", "bedCode", "bedNumber"}, ""));
        row.put("name", firstValue(patient, new String[]{"name"}, ""));
        row.put("age", NumberUtils.calcAge(patient));
        row.put("hospitalNo", firstValue(patient, new String[]{
            "hospitalNo", "hospitalNumber", "mrn", "zyh", "zyhm"
        }, ""));
        row.put("icuAdmissionTime", NumberUtils.formatDateTime(patient.get("icuAdmissionTime")));
        row.put("icuDischargeTime", NumberUtils.formatDateTime(patient.get("icuDischargeTime")));
        row.put("icuDays", NumberUtils.calcIcuDays(patient));
        row.put("admissionDoctor", firstValue(patient, new String[]{
            "admissionDoctor", "admissionDoctorName", "bedDoctor"
        }, ""));
        row.put("attendingDoctor", firstValue(patient, new String[]{
            "attendingDoctor", "attendingDoctorName", "bedDoctor", "chargeDoctorName", "tubeDoctorName"
        }, ""));
        row.put("admissionSource", firstValue(patient, new String[]{
            "admissionSource", "inSource", "source"
        }, ""));
        row.put("dischargeType", firstValue(patient, new String[]{
            "dischargedType", "dischargeType", "outType"
        }, ""));
        row.put("transferDept", firstValue(patient, new String[]{
            "dischargedDepartment", "transferDept", "outDeptName"
        }, ""));
        row.put("diagnosis", firstValue(patient, new String[]{
            "clinicalDiagnosis", "diagnosis", "admissionDiagnosis", "primaryDiagnosis"
        }, ""));
        if (extra != null) {
            row.putAll(extra);
        }
        return row;
    }

    /**
     * 从文档中获取第一个非空值
     * 原Node.js: firstValue(doc, fields) - 返回Object
     */
    public static Object firstValueRaw(Document doc, String[] fields) {
        for (String field : fields) {
            Object value = doc.get(field);
            if (value != null && !"".equals(String.valueOf(value))) {
                return value;
            }
        }
        return "";
    }

    /**
     * 从文档中获取第一个非空值（带默认值）
     */
    public static String firstValue(Document doc, String[] fields, String defaultValue) {
        for (String field : fields) {
            Object value = doc.get(field);
            if (value != null && !"".equals(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return defaultValue;
    }

    /**
     * 获取指定字段的值（用于排序等）
     */
    public static Object getFieldValue(Document doc, String field) {
        return doc.get(field);
    }
}
