package com.smartcare.icustats.controller;

import com.smartcare.icustats.dto.ApiResponse;
import com.smartcare.icustats.dto.NutritionQualityDetailResponse;
import com.smartcare.icustats.dto.NutritionQualityResponse;
import com.smartcare.icustats.service.NutritionQualityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/stats/nutrition/quality")
public class NutritionQualityController {

    private final NutritionQualityService nutritionQualityService;

    public NutritionQualityController(NutritionQualityService nutritionQualityService) {
        this.nutritionQualityService = nutritionQualityService;
    }

    /**
     * GET /api/stats/nutrition/quality/year?year=2026&department=重症医学科
     */
    @GetMapping("/year")
    public ApiResponse<NutritionQualityResponse> yearStats(
            @RequestParam String year,
            @RequestParam(defaultValue = "") String department) {
        try {
            NutritionQualityResponse data = nutritionQualityService.getYearStats(year, department);
            return ApiResponse.ok(data);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "查询失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/stats/nutrition/quality/range?startMonth=2026-01&endMonth=2026-12&department=重症医学科
     */
    @GetMapping("/range")
    public ApiResponse<NutritionQualityResponse> rangeStats(
            @RequestParam String startMonth,
            @RequestParam String endMonth,
            @RequestParam(defaultValue = "") String department) {
        try {
            NutritionQualityResponse data = nutritionQualityService.getRangeStats(startMonth, endMonth, department);
            return ApiResponse.ok(data);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "查询失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/stats/nutrition/quality/detail?indicatorKey=feedingIntoleranceRate&startMonth=2026-01&endMonth=2026-12&department=重症医学科
     * 可选: month=2026-08, itemOrder=numerator|denominator
     */
    @GetMapping("/detail")
    public ApiResponse<NutritionQualityDetailResponse> detail(
            @RequestParam String indicatorKey,
            @RequestParam String startMonth,
            @RequestParam String endMonth,
            @RequestParam(defaultValue = "") String department,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String itemOrder) {
        try {
            NutritionQualityDetailResponse data = nutritionQualityService.getDetail(
                    indicatorKey, startMonth, endMonth, department, month, itemOrder);
            return ApiResponse.ok(data);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "查询失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/stats/nutrition/quality/diagnostic
     */
    @GetMapping("/diagnostic")
    public ApiResponse<Map<String, Object>> diagnostic() {
        try {
            Map<String, Object> data = nutritionQualityService.getDiagnostic();
            return ApiResponse.ok(data);
        } catch (Exception e) {
            return ApiResponse.error(500, "诊断失败: " + e.getMessage());
        }
    }
}
