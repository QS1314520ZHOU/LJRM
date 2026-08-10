package com.smartcare.icustats.controller;

import com.smartcare.icustats.dto.ApiResponse;
import com.smartcare.icustats.service.NutritionService;
import com.smartcare.icustats.service.QualityService;
import com.smartcare.icustats.service.StatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 统计接口控制器
 * 原Node.js文件: routes/statsRoutes.js
 * 所有接口路径、HTTP方法、参数名称与原Node.js完全一致
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    @Autowired
    private StatsService statsService;

    @Autowired
    private QualityService qualityService;

    @Autowired
    private NutritionService nutritionService;

    /**
     * GET /api/stats/indicators
     * 返回指标列表
     * 原Node.js: ok(res, statsService.INDICATORS)
     */
    @GetMapping("/indicators")
    public ApiResponse<?> indicators() {
        return ApiResponse.ok(statsService.getIndicators());
    }

    /**
     * GET /api/stats/year?year=2024&department=
     * 年度统计
     * 原Node.js: statsService.getYearStats(year, department)
     */
    @GetMapping("/year")
    public ApiResponse<Map<String, Object>> yearStats(
            @RequestParam String year,
            @RequestParam(defaultValue = "") String department) {
        return ApiResponse.ok(statsService.getYearStats(year, department));
    }

    /**
     * GET /api/stats/range?startMonth=2024-01&endMonth=2024-12&department=
     * 范围统计
     * 原Node.js: statsService.getRangeStats(startMonth, endMonth, department)
     */
    @GetMapping("/range")
    public ApiResponse<Map<String, Object>> rangeStats(
            @RequestParam String startMonth,
            @RequestParam String endMonth,
            @RequestParam(defaultValue = "") String department) {
        return ApiResponse.ok(statsService.getRangeStats(startMonth, endMonth, department));
    }

    /**
     * GET /api/stats/detail?indicatorKey=ecmo&startMonth=2024-01&endMonth=2024-12&department=
     * 指标明细
     * 原Node.js: statsService.getDetail(indicatorKey, startMonth, endMonth, department)
     */
    @GetMapping("/detail")
    public ApiResponse<Map<String, Object>> detail(
            @RequestParam String indicatorKey,
            @RequestParam String startMonth,
            @RequestParam String endMonth,
            @RequestParam(defaultValue = "") String department) {
        return ApiResponse.ok(statsService.getDetail(indicatorKey, startMonth, endMonth, department));
    }

    /**
     * GET /api/stats/quality?year=&startMonth=&endMonth=&department=
     * 质控统计
     * 原Node.js: qualityService.getQualityStats({ year, startMonth, endMonth, department })
     */
    @GetMapping("/quality")
    public ApiResponse<Map<String, Object>> qualityStats(
            @RequestParam(required = false) String year,
            @RequestParam(required = false) String startMonth,
            @RequestParam(required = false) String endMonth,
            @RequestParam(defaultValue = "") String department) {
        return ApiResponse.ok(qualityService.getQualityStats(year, startMonth, endMonth, department));
    }

    /**
     * GET /api/stats/quality/detail?indicatorKey=&year=&startMonth=&endMonth=&department=&itemOrder=
     * 质控明细
     * 原Node.js: qualityService.getQualityDetail(indicatorKey, { year, startMonth, endMonth, department, itemOrder })
     */
    @GetMapping("/quality/detail")
    public ApiResponse<Map<String, Object>> qualityDetail(
            @RequestParam String indicatorKey,
            @RequestParam(required = false) String year,
            @RequestParam(required = false) String startMonth,
            @RequestParam(required = false) String endMonth,
            @RequestParam(defaultValue = "") String department,
            @RequestParam(required = false) String itemOrder) {
        return ApiResponse.ok(qualityService.getQualityDetail(indicatorKey, year, startMonth, endMonth, department, itemOrder));
    }

    // ── 营养统计 ──────────────────────────────────────────

    /**
     * GET /api/stats/nutrition/year?year=&department=
     */
    @GetMapping("/nutrition/year")
    public ApiResponse<Map<String, Object>> nutritionYear(
            @RequestParam String year,
            @RequestParam(defaultValue = "") String department) {
        return ApiResponse.ok(nutritionService.getYearStats(year, department));
    }

    /**
     * GET /api/stats/nutrition/range?startMonth=&endMonth=&department=
     */
    @GetMapping("/nutrition/range")
    public ApiResponse<Map<String, Object>> nutritionRange(
            @RequestParam String startMonth,
            @RequestParam String endMonth,
            @RequestParam(defaultValue = "") String department) {
        return ApiResponse.ok(nutritionService.getRangeStats(startMonth, endMonth, department));
    }

    /**
     * GET /api/stats/nutrition/detail?indicatorKey=&startMonth=&endMonth=&department=
     */
    @GetMapping("/nutrition/detail")
    public ApiResponse<Map<String, Object>> nutritionDetail(
            @RequestParam String indicatorKey,
            @RequestParam String startMonth,
            @RequestParam String endMonth,
            @RequestParam(defaultValue = "") String department) {
        return ApiResponse.ok(nutritionService.getDetail(indicatorKey, startMonth, endMonth, department));
    }

    /**
     * GET /api/stats/nutrition/daily?startDate=&endDate=&department=
     */
    @GetMapping("/nutrition/daily")
    public ApiResponse<Map<String, Object>> nutritionDaily(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "") String department) {
        return ApiResponse.ok(nutritionService.getDailyEnteral(startDate, endDate, department));
    }

    /**
     * GET /api/stats/nutrition/daily/detail?date=&department=
     */
    @GetMapping("/nutrition/daily/detail")
    public ApiResponse<Map<String, Object>> nutritionDailyDetail(
            @RequestParam String date,
            @RequestParam(defaultValue = "") String department) {
        return ApiResponse.ok(nutritionService.getDailyEnteralDetail(date, department));
    }

    /**
     * GET /api/stats/nutrition/daily/detail/range?startDate=&endDate=&department=
     */
    @GetMapping("/nutrition/daily/detail/range")
    public ApiResponse<Map<String, Object>> nutritionDailyDetailRange(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "") String department) {
        return ApiResponse.ok(nutritionService.getDailyEnteralRangeDetail(startDate, endDate, department));
    }
}
