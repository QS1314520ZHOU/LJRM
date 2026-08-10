package com.smartcare.icustats.util;

import com.smartcare.icustats.dto.MonthRange;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 日期范围工具类
 * 原Node.js文件: statsService.js / qualityService.js / nutritionService.js
 * 时区固定: Asia/Shanghai (UTC+8)
 */
public class DateRangeUtils {

    public static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter YYYY_MM = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 验证年份参数
     * 原Node.js: validateYear(year)
     */
    public static int validateYear(String year) {
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
     * 验证月份格式 YYYY-MM
     * 原Node.js: validateMonth(month, fieldName)
     */
    public static void validateMonth(String month, String fieldName) {
        try {
            YearMonth.parse(month, YYYY_MM);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(fieldName + "格式不正确，应为 YYYY-MM");
        }
    }

    /**
     * 验证日期格式 YYYY-MM-DD
     * 原Node.js: validateDate(date, fieldName)
     */
    public static void validateDate(String date, String fieldName) {
        try {
            LocalDate.parse(date, YYYY_MM_DD);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(fieldName + "格式不正确，应为 YYYY-MM-DD");
        }
    }

    /**
     * 构建月份列表
     * 原Node.js: buildMonths(startMonth, endMonth)
     */
    public static List<String> buildMonths(String startMonth, String endMonth) {
        validateMonth(startMonth, "开始月份");
        validateMonth(endMonth, "结束月份");
        YearMonth cur = YearMonth.parse(startMonth, YYYY_MM);
        YearMonth end = YearMonth.parse(endMonth, YYYY_MM);
        if (cur.isAfter(end)) {
            throw new IllegalArgumentException("开始月份不能晚于结束月份");
        }
        if (cur.until(end, java.time.temporal.ChronoUnit.MONTHS) > 36) {
            throw new IllegalArgumentException("查询范围不能超过 36 个月");
        }
        List<String> months = new ArrayList<>();
        while (!cur.isAfter(end)) {
            months.add(cur.format(YYYY_MM));
            cur = cur.plusMonths(1);
        }
        return months;
    }

    /**
     * 获取月份的起止日期（东八区）
     * 原Node.js: getMonthRange(monthKey)
     * startDate: 该月1日 00:00:00 +08:00
     * endDate: 该月最后一日 23:59:59.999 +08:00
     */
    public static MonthRange getMonthRange(String monthKey) {
        YearMonth ym = YearMonth.parse(monthKey, YYYY_MM);
        ZonedDateTime startDate = ym.atDay(1).atStartOfDay(SHANGHAI_ZONE);
        ZonedDateTime endDate = ym.atEndOfMonth().atTime(LocalTime.MAX).atZone(SHANGHAI_ZONE);
        return new MonthRange(
                Date.from(startDate.toInstant()),
                Date.from(endDate.toInstant())
        );
    }

    /**
     * 获取日期的起止时间（东八区）
     * 原Node.js: getDayRange(dateStr)
     */
    public static MonthRange getDayRange(String dateStr) {
        validateDate(dateStr, "日期");
        LocalDate ld = LocalDate.parse(dateStr, YYYY_MM_DD);
        ZonedDateTime startDate = ld.atStartOfDay(SHANGHAI_ZONE);
        ZonedDateTime endDate = ld.atTime(LocalTime.MAX).atZone(SHANGHAI_ZONE);
        return new MonthRange(
                Date.from(startDate.toInstant()),
                Date.from(endDate.toInstant())
        );
    }

    /**
     * 获取完整范围的起止日期
     * 原Node.js: getFullRange(startMonth, endMonth)
     */
    public static MonthRange getFullRange(String startMonth, String endMonth) {
        List<String> months = buildMonths(startMonth, endMonth);
        MonthRange start = getMonthRange(months.get(0));
        MonthRange end = getMonthRange(months.get(months.size() - 1));
        return new MonthRange(start.getStartDate(), end.getEndDate());
    }

    /**
     * 生成年份的12个月份列表
     * 原Node.js: Array.from({ length: 12 }, (_, i) => `${y}-${String(i + 1).padStart(2, '0')}`)
     */
    public static List<String> getYearMonths(int year) {
        List<String> months = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            months.add(String.format("%d-%02d", year, i));
        }
        return months;
    }

    /**
     * 日期列表（日期范围）
     * 原Node.js: buildDateList(startDate, endDate)
     */
    public static List<String> buildDateList(String startDate, String endDate) {
        validateDate(startDate, "开始日期");
        validateDate(endDate, "结束日期");
        LocalDate cur = LocalDate.parse(startDate, YYYY_MM_DD);
        LocalDate end = LocalDate.parse(endDate, YYYY_MM_DD);
        if (cur.isAfter(end)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        if (cur.until(end, java.time.temporal.ChronoUnit.DAYS) > 366) {
            throw new IllegalArgumentException("查询范围不能超过 366 天");
        }
        List<String> dates = new ArrayList<>();
        while (!cur.isAfter(end)) {
            dates.add(cur.format(YYYY_MM_DD));
            cur = cur.plusDays(1);
        }
        return dates;
    }
}
