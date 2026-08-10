package com.smartcare.icustats.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

/**
 * 数值工具类
 * 原Node.js文件: statsService.js / qualityService.js / nutritionService.js
 */
public class NumberUtils {

    /**
     * 安全转换为数值，失败返回0
     * 原Node.js: safeNumber(value)
     */
    public static double safeNumber(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 安全转换为数值，失败返回null
     */
    public static Double safeNumberOrNull(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 规范化文本，null转空字符串并trim
     * 原Node.js: normalizeText(value)
     */
    public static String normalizeText(Object value) {
        if (value == null) return "";
        return String.valueOf(value).trim();
    }

    /**
     * 安全解析日期
     * 原Node.js: asDate(value)
     */
    public static Date asDate(Object value) {
        if (value == null) return null;
        if (value instanceof Date) return (Date) value;
        if (value instanceof Number) {
            long time = ((Number) value).longValue();
            Date d = new Date(time);
            return Double.isNaN(d.getTime()) ? null : d;
        }
        try {
            Date d = new Date(String.valueOf(value));
            return Double.isNaN(d.getTime()) ? null : d;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 格式化日期时间为 YYYY-MM-DD HH:mm（东八区）
     * 原Node.js: formatDateTime(value)
     */
    public static String formatDateTime(Object value) {
        Date date = asDate(value);
        if (date == null) return "";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
        return sdf.format(date);
    }

    /**
     * 获取文档中多个字段的第一个非空值
     * 原Node.js: firstValue(doc, fields)
     */
    public static Object firstValue(java.util.Map<String, Object> doc, String[] fields) {
        for (String field : fields) {
            Object value = doc.get(field);
            if (value != null && !"".equals(String.valueOf(value))) {
                return value;
            }
        }
        return "";
    }

    /**
     * 计算年龄
     * 原Node.js: calcAge(patient)
     */
    public static String calcAge(java.util.Map<String, Object> patient) {
        Object explicitAge = firstValue(patient, new String[]{"age"});
        if (!"".equals(explicitAge)) {
            String ageStr = String.valueOf(explicitAge);
            return ageStr.contains("岁") ? ageStr : ageStr + "岁";
        }
        Date birthday = asDate(patient.get("birthday"));
        if (birthday == null) return "";
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(DateRangeUtils.SHANGHAI_ZONE);
        java.time.ZonedDateTime bday = birthday.toInstant().atZone(DateRangeUtils.SHANGHAI_ZONE);
        int age = java.time.temporal.ChronoUnit.YEARS.between(bday, now);
        return age + "岁";
    }

    /**
     * 计算在科天数
     * 原Node.js: calcIcuDays(patient)
     */
    public static String calcIcuDays(java.util.Map<String, Object> patient) {
        Date start = asDate(patient.get("icuAdmissionTime"));
        if (start == null) return "";
        Date end = asDate(patient.get("icuDischargeTime"));
        if (end == null) end = new Date();
        java.time.ZonedDateTime s = start.toInstant().atZone(DateRangeUtils.SHANGHAI_ZONE);
        java.time.ZonedDateTime e = end.toInstant().atZone(DateRangeUtils.SHANGHAI_ZONE);
        long days = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(s, e) + 1);
        return days + "天";
    }

    /**
     * 去除尾部零的BigDecimal格式化
     * 原Node.js: trimTrailingZeros(text)
     */
    public static String trimTrailingZeros(BigDecimal value) {
        if (value == null) return "0";
        String text = value.stripTrailingZeros().toPlainString();
        // 去除末尾多余的0但保留小数点后至少一位（如 "1.0"）
        if (text.contains(".")) {
            text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return text;
    }

    /**
     * 安全除法
     */
    public static String safeDivide(double numerator, double denominator, int scale) {
        if (denominator == 0) return "0";
        BigDecimal num = BigDecimal.valueOf(numerator);
        BigDecimal den = BigDecimal.valueOf(denominator);
        return trimTrailingZeros(num.divide(den, scale, RoundingMode.HALF_UP));
    }

    /**
     * 安全百分比
     */
    public static String safePercent(double numerator, double denominator) {
        if (denominator == 0) return "0.00%";
        double value = (numerator / denominator) * 100;
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP).toString() + "%";
    }

    /**
     * 计算占用床日数
     * 原Node.js: calcOccupiedBedDays(patient, monthKey)
     */
    public static int calcOccupiedBedDays(java.util.Map<String, Object> patient, String monthKey) {
        Date admission = asDate(patient.get("icuAdmissionTime"));
        if (admission == null) return 0;

        MonthRange monthRange = getMonthRange(monthKey);
        Date discharge = asDate(patient.get("icuDischargeTime"));
        if (discharge == null) discharge = monthRange.getEndDate();

        java.time.ZonedDateTime startDate = admission.toInstant().atZone(DateRangeUtils.SHANGHAI_ZONE);
        java.time.ZonedDateTime endDate = discharge.toInstant().atZone(DateRangeUtils.SHANGHAI_ZONE);
        java.time.ZonedDateTime monthStart = monthRange.getStartDate().toInstant().atZone(DateRangeUtils.SHANGHAI_ZONE);
        java.time.ZonedDateTime monthEnd = monthRange.getEndDate().toInstant().atZone(DateRangeUtils.SHANGHAI_ZONE);

        java.time.ZonedDateTime effectiveStart = startDate.isAfter(monthStart) ? startDate : monthStart;
        java.time.ZonedDateTime effectiveEnd = endDate.isBefore(monthEnd) ? endDate : monthEnd;

        if (effectiveEnd.isBefore(effectiveStart)) return 0;
        return (int) (java.time.temporal.ChronoUnit.DAYS.between(
                effectiveStart.toLocalDate().atStartOfDay(SHANGHAI_ZONE).toInstant(),
                effectiveEnd.toLocalDate().atStartOfDay(SHANGHAI_ZONE).toInstant()
        ) / 86400 + 1);
    }

    /**
     * 获取月份范围（静态方法别名）
     */
    public static MonthRange getMonthRange(String monthKey) {
        return DateRangeUtils.getMonthRange(monthKey);
    }

    /**
     * 将Object转为String（处理null）
     */
    public static String objectToString(Object obj) {
        if (obj == null) return "";
        return String.valueOf(obj);
    }
}
