package com.smartcare.icustats.config;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 营养质量指标元数据配置
 * 所有指标定义集中管理，便于维护和验证
 */
public class NutritionQualityIndicatorConfig {

    public static final String UNIT_PERCENT = "%";
    public static final String UNIT_RATIO = ":1";

    public static final String COMPARE_LT = "LT";
    public static final String COMPARE_LE = "LE";
    public static final String COMPARE_GT = "GT";
    public static final String COMPARE_GE = "GE";
    public static final String COMPARE_DISPLAY = "DISPLAY_ONLY";

    /**
     * 指标元数据
     */
    public static final List<Map<String, Object>> INDICATORS = Arrays.asList(
            createIndicator(1, "enteralInterruptionRate", "肠内营养中断率", UNIT_PERCENT,
                    "中断事件数/肠内营养患者日×100%",
                    "暂停肠内营养(csList含J)的事件数",
                    "同期接受肠内营养的患者日数",
                    "<10%", COMPARE_LT, "patient_day",
                    "肠内营养记录单"),

            createIndicator(2, "enteralPlanCompletionRate", "肠内营养计划完成率", UNIT_PERCENT,
                    "完成目标量的患者日数/有目标量的患者日数×100%",
                    "完成量≥目标量的记录数",
                    "目标量非空且有效的记录数",
                    "≥80%", COMPARE_GE, "patient_day",
                    "肠内营养记录单"),

            createIndicator(3, "feedingTubeBlockageRate", "喂养管堵管发生率", UNIT_PERCENT,
                    "堵管事件数/喂养管日×100%",
                    "堵管事件数",
                    "同期喂养管留置日数",
                    "<5%", COMPARE_LT, "tube_day",
                    "肠内营养记录单"),

            createIndicator(4, "feedingTubeUnplannedRemovalRate", "喂养管非计划拔除发生率", UNIT_PERCENT,
                    "非计划拔除事件数/喂养管日×100%",
                    "非计划拔除事件数",
                    "同期喂养管留置日数",
                    "<3%", COMPARE_LT, "tube_day",
                    "tubeExe"),

            createIndicator(5, "feedingTubeSkinProblemRate", "喂养管相关皮肤问题发生率", UNIT_PERCENT,
                    "皮肤问题事件数/喂养管日×100%",
                    "喂养管相关皮肤问题事件数",
                    "同期喂养管留置日数",
                    "12%", COMPARE_DISPLAY, "tube_day",
                    "肠内营养记录单"),

            createIndicator(6, "aspirationRate", "误吸发生率", UNIT_PERCENT,
                    "误吸事件数/肠内营养患者日×100%",
                    "误吸事件数",
                    "同期接受肠内营养的患者日数",
                    "<5%", COMPARE_LT, "patient_day",
                    "肠内营养记录单"),

            createIndicator(7, "feedingIntoleranceRate", "喂养不耐受发生率", UNIT_PERCENT,
                    "喂养不耐受事件数/肠内营养患者日×100%",
                    "喂养不耐受事件数(zf>阈值或并发症)",
                    "同期接受肠内营养的患者日数",
                    "<20%", COMPARE_LT, "patient_day",
                    "肠内营养记录单"),

            createIndicator(8, "enteralParenteralRatio", "肠内营养与肠外营养比", UNIT_RATIO,
                    "肠内营养患者数/肠外营养患者数",
                    "同期接受肠内营养的患者数",
                    "同期接受肠外营养的患者数",
                    "≥2:1", COMPARE_GE, "patient_month",
                    "drugExe")
    );

    private static Map<String, Object> createIndicator(int id, String key, String name,
                                                         String unit, String formula,
                                                         String numeratorDef, String denominatorDef,
                                                         String target, String comparison,
                                                         String aggregationUnit, String dataSource) {
        Map<String, Object> indicator = new LinkedHashMap<>();
        indicator.put("id", id);
        indicator.put("key", key);
        indicator.put("name", name);
        indicator.put("unit", unit);
        indicator.put("formula", formula);
        indicator.put("numeratorDefinition", numeratorDef);
        indicator.put("denominatorDefinition", denominatorDef);
        indicator.put("target", target);
        indicator.put("comparison", comparison);
        indicator.put("aggregationUnit", aggregationUnit);
        indicator.put("dataSource", dataSource);
        return indicator;
    }

    /**
     * 根据key查找指标元数据
     */
    public static Map<String, Object> findByKey(String key) {
        return INDICATORS.stream()
                .filter(ind -> key.equals(ind.get("key")))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取所有指标key
     */
    public static List<String> getAllKeys() {
        return Arrays.asList(
                "enteralInterruptionRate",
                "enteralPlanCompletionRate",
                "feedingTubeBlockageRate",
                "feedingTubeUnplannedRemovalRate",
                "feedingTubeSkinProblemRate",
                "aspirationRate",
                "feedingIntoleranceRate",
                "enteralParenteralRatio"
        );
    }
}
