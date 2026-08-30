package com.smartcare.icustats.service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.smartcare.icustats.config.NutritionQualityIndicatorConfig;
import com.smartcare.icustats.config.NutritionQualityProperties;
import com.smartcare.icustats.dto.*;
import com.smartcare.icustats.util.DateRangeUtils;
import com.smartcare.icustats.util.NumberUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 营养质量控制指标监测服务
 * 负责 MongoDB 查询编排和指标计算调度
 */
@Service
public class NutritionQualityService {

    private static final Logger log = LoggerFactory.getLogger(NutritionQualityService.class);
    private static final DateTimeFormatter YYYY_MM = DateTimeFormatter.ofPattern("yyyy-MM");

    private final MongoTemplate smartCareMongoTemplate;
    private final NutritionQualityProperties properties;
    private final NutritionQualityRecordAdapter adapter;
    private final NutritionQualityCalculationService calcService;
    private final NutritionService nutritionService;

    public NutritionQualityService(
            @Qualifier("smartCareMongoTemplate") MongoTemplate smartCareMongoTemplate,
            NutritionQualityProperties properties,
            NutritionQualityRecordAdapter adapter,
            NutritionQualityCalculationService calcService,
            NutritionService nutritionService) {
        this.smartCareMongoTemplate = smartCareMongoTemplate;
        this.properties = properties;
        this.adapter = adapter;
        this.calcService = calcService;
        this.nutritionService = nutritionService;
    }

    // ════════════════════════════════════════════════════════════════════
    // Public API
    // ════════════════════════════════════════════════════════════════════

    /**
     * 年度汇总查询
     */
    public NutritionQualityResponse getYearStats(String year, String department) {
        int y = DateRangeUtils.validateYear(year);
        String startMonth = y + "-01";
        String endMonth = y + "-12";
        return getRangeStats(startMonth, endMonth, department);
    }

    /**
     * 月份范围汇总查询
     */
    public NutritionQualityResponse getRangeStats(String startMonth, String endMonth, String department) {
        NutritionQualityResponse response = new NutritionQualityResponse();
        response.setStartMonth(startMonth);
        response.setEndMonth(endMonth);

        // 检查功能是否启用
        if (!properties.isEnabled()) {
            response.setDataStatus("disabled");
            response.setMessage("营养质量控制指标监测功能已禁用");
            response.setIndicators(buildDisabledIndicators());
            response.setMonths(DateRangeUtils.buildMonths(startMonth, endMonth));
            return response;
        }

        // 检查集合配置
        String collection = resolveCollection();
        if (collection == null || collection.isEmpty()) {
            response.setDataStatus("collection_not_configured");
            response.setMessage("集合未配置，请设置 icu-stats.nutrition-quality.collection");
            response.setIndicators(buildNotConfiguredIndicators());
            response.setMonths(DateRangeUtils.buildMonths(startMonth, endMonth));
            return response;
        }

        // 检查集合是否存在
        if (!collectionExists(collection)) {
            response.setDataStatus("collection_not_found");
            response.setMessage("集合 " + collection + " 不存在");
            response.setIndicators(buildNotFoundIndicators());
            response.setMonths(DateRangeUtils.buildMonths(startMonth, endMonth));
            return response;
        }

        try {
            // 查询数据
            List<String> months = DateRangeUtils.buildMonths(startMonth, endMonth);
            response.setMonths(months);

            List<Document> allRecords = queryRecords(startMonth, endMonth, department, collection);
            List<Document> validRecords = filterValidRecords(allRecords);

            if (validRecords.isEmpty()) {
                response.setDataStatus("no_data");
                response.setMessage("查询范围内暂无有效数据");
                response.setIndicators(buildNoDataIndicators());
                return response;
            }

            // 计算指标
            List<NutritionQualityIndicator> indicators = calculateIndicators(validRecords, months, department);
            response.setIndicators(indicators);
            response.setDataStatus("ok");
            response.setMessage(null);

        } catch (Exception e) {
            log.error("营养质量指标查询异常", e);
            response.setDataStatus("query_error");
            response.setMessage("查询失败: " + e.getMessage());
            response.setIndicators(buildErrorIndicators(e.getMessage()));
        }

        return response;
    }

    /**
     * 指标明细查询
     * 支持两层钻取：
     *   - 无 itemOrder：返回汇总行（分子/分母/比率），带"查看详情"按钮
     *   - 有 itemOrder：返回对应的患者记录明细
     */
    public NutritionQualityDetailResponse getDetail(String indicatorKey, String startMonth,
                                                      String endMonth, String department,
                                                      String month, String itemOrder) {
        NutritionQualityDetailResponse response = new NutritionQualityDetailResponse();
        response.setIndicatorKey(indicatorKey);

        // 查找指标元数据
        Map<String, Object> indicatorMeta = NutritionQualityIndicatorConfig.findByKey(indicatorKey);
        if (indicatorMeta == null) {
            response.setDataStatus("query_error");
            response.setMessage("未知指标: " + indicatorKey);
            return response;
        }
        response.setIndicatorName((String) indicatorMeta.get("name"));

        // 确定查询范围
        String effectiveStart = month != null && !month.isEmpty() ? month : startMonth;
        String effectiveEnd = month != null && !month.isEmpty() ? month : endMonth;
        response.setStartMonth(effectiveStart);
        response.setEndMonth(effectiveEnd);

        // 检查集合
        String collection = resolveCollection();
        if (collection == null || collection.isEmpty()) {
            response.setDataStatus("collection_not_configured");
            response.setMessage("集合未配置");
            response.setColumns(Collections.emptyList());
            response.setRows(Collections.emptyList());
            return response;
        }

        try {
            List<Document> records = queryRecords(effectiveStart, effectiveEnd, department, collection);
            List<Document> validRecords = filterValidRecords(records);

            if (itemOrder != null && !itemOrder.isEmpty()) {
                // 有 itemOrder：返回患者记录明细
                List<Map<String, String>> columns = buildDetailColumns(indicatorKey);
                List<Map<String, Object>> rows = buildDetailRows(indicatorKey, validRecords,
                        effectiveStart, department, itemOrder);
                response.setColumns(columns);
                response.setRows(rows);
            } else {
                // 无 itemOrder：返回汇总行（分子/分母/比率），带"查看详情"按钮
                List<Map<String, String>> columns = summaryColumnsList();
                List<Map<String, Object>> rows = buildSummaryRows(indicatorKey, validRecords,
                        effectiveStart, effectiveEnd, department);
                response.setColumns(columns);
                response.setRows(rows);
            }

            response.setDataStatus("ok");

        } catch (Exception e) {
            log.error("营养质量指标明细查询异常: indicator={}", indicatorKey, e);
            response.setDataStatus("query_error");
            response.setMessage("查询失败: " + e.getMessage());
            response.setColumns(Collections.emptyList());
            response.setRows(Collections.emptyList());
        }

        return response;
    }

    /**
     * 数据源诊断
     */
    public Map<String, Object> getDiagnostic() {
        Map<String, Object> diag = new LinkedHashMap<>();
        diag.put("enabled", properties.isEnabled());
        diag.put("timezone", properties.getTimezone());
        diag.put("validValue", properties.getValidValue());
        diag.put("defaultDeptCode", properties.getDefaultDeptCode());
        diag.put("classPattern", properties.getClassPattern());
        diag.put("autoDiscoverCollection", properties.isAutoDiscoverCollection());

        String collection = resolveCollection();
        diag.put("collection", collection);
        diag.put("collectionConfigured", collection != null && !collection.isEmpty());

        if (collection != null && !collection.isEmpty()) {
            boolean exists = collectionExists(collection);
            diag.put("collectionExists", exists);

            if (exists) {
                try {
                    // Count valid records
                    Document filter = new Document("valid", properties.getValidValue());
                    long count = smartCareMongoTemplate.count(new BasicQuery(filter), collection);
                    diag.put("validRecordCount", count);

                    // Field discovery
                    List<String> discoveredFields = discoverFields(collection);
                    diag.put("discoveredFields", discoveredFields);

                    // Mapped fields
                    Map<String, String> mappedFields = new LinkedHashMap<>();
                    Map<String, String> unmappedFields = new LinkedHashMap<>();
                    for (Map.Entry<String, String> entry : properties.getFields().entrySet()) {
                        if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                            mappedFields.put(entry.getKey(), entry.getValue());
                        } else {
                            unmappedFields.put(entry.getKey(), "");
                        }
                    }
                    diag.put("mappedFields", mappedFields);
                    diag.put("unmappedFields", unmappedFields);

                    diag.put("dataStatus", "ok");
                    diag.put("message", "诊断完成");

                } catch (Exception e) {
                    log.error("诊断查询异常", e);
                    diag.put("dataStatus", "query_error");
                    diag.put("message", "诊断查询失败: " + e.getMessage());
                }
            } else {
                diag.put("dataStatus", "collection_not_found");
                diag.put("message", "集合不存在");
            }
        } else {
            diag.put("dataStatus", "collection_not_configured");
            diag.put("message", "集合未配置");
        }

        // Remove sensitive info
        diag.remove("patient");
        diag.remove("name");
        diag.remove("mrn");
        diag.remove("hisPid");
        diag.remove("clinicalDiagnosis");

        return diag;
    }

    // ════════════════════════════════════════════════════════════════════
    // 数据查询
    // ════════════════════════════════════════════════════════════════════

    /**
     * 解析集合名
     */
    private String resolveCollection() {
        String configured = properties.getCollection();
        if (configured != null && !configured.isEmpty()) {
            return configured;
        }

        // Auto-discover
        if (properties.isAutoDiscoverCollection()) {
            return discoverCollection();
        }

        return null;
    }

    /**
     * 自动发现集合
     */
    private String discoverCollection() {
        try {
            MongoDatabase db = smartCareMongoTemplate.getDb();
            String classPattern = properties.getClassPattern();

            for (String name : db.listCollectionNames()) {
                @SuppressWarnings("unchecked")
                MongoCollection<Document> coll = (MongoCollection<Document>) db.getCollection(name, Document.class);
                Document sample = coll.find(new Document("_class", new Document("$regex", classPattern))).first();
                if (sample != null) {
                    log.info("自动发现营养记录集合: {}", name);
                    return name;
                }
            }
        } catch (Exception e) {
            log.warn("自动发现集合失败", e);
        }
        return null;
    }

    /**
     * 检查集合是否存在
     */
    private boolean collectionExists(String collection) {
        try {
            MongoDatabase db = smartCareMongoTemplate.getDb();
            for (String name : db.listCollectionNames()) {
                if (name.equals(collection)) return true;
            }
        } catch (Exception e) {
            log.warn("检查集合存在性失败: {}", collection, e);
        }
        return false;
    }

    /**
     * 发现集合中的字段
     */
    private List<String> discoverFields(String collection) {
        try {
            Document sample = smartCareMongoTemplate.findOne(new Query().limit(1), Document.class, collection);
            if (sample == null) return Collections.emptyList();
            return new ArrayList<>(sample.keySet());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 查询营养记录
     */
    private List<Document> queryRecords(String startMonth, String endMonth,
                                          String department, String collection) {
        // Build date range (Shanghai timezone → UTC)
        MonthRange range = DateRangeUtils.getFullRange(startMonth, endMonth);
        Date startDate = range.getStartDate();
        Date endDate = range.getEndDate();

        // Build query
        Document filter = new Document();
        String timeField = adapter.fieldName("recordTime");
        if (timeField != null && !timeField.isEmpty()) {
            filter.append(timeField, new Document("$gte", startDate).append("$lte", endDate));
        }

        // Valid filter
        filter.append("valid", new Document("$in", Arrays.asList("valid", true, 1)));

        // Department filter
        boolean enableDeptFilter = "true".equals(System.getenv().getOrDefault("ENABLE_DEPT_FILTER", ""));
        if (enableDeptFilter && department != null && !department.isEmpty()) {
            String deptCodeField = adapter.fieldName("deptCode");
            if (deptCodeField != null && !deptCodeField.isEmpty()) {
                filter.append(deptCodeField, department);
            }
        }

        Query query = new BasicQuery(filter);
        return smartCareMongoTemplate.find(query, Document.class, collection);
    }

    /**
     * 过滤有效记录
     */
    private List<Document> filterValidRecords(List<Document> records) {
        return records.stream()
                .filter(adapter::isValidRecord)
                .collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════════════
    // 指标计算调度
    // ════════════════════════════════════════════════════════════════════

    /**
     * 计算所有指标
     */
    private List<NutritionQualityIndicator> calculateIndicators(List<Document> records,
                                                                   List<String> months,
                                                                   String department) {
        List<NutritionQualityIndicator> indicators = new ArrayList<>();

        // 1. 肠内营养中断率
        indicators.add(calculateInterruptionRate(records, months));

        // 2. 肠内营养计划完成率
        indicators.add(calculatePlanCompletionRate(records, months));

        // 3. 喂养管堵管发生率
        indicators.add(calculateTubeBlockageRate(records, months));

        // 4. 喂养管非计划拔除发生率
        indicators.add(calculateUnplannedRemovalRate(records, months));

        // 5. 喂养管相关皮肤问题发生率
        indicators.add(calculateSkinProblemRate(records, months));

        // 6. 误吸发生率
        indicators.add(calculateAspirationRate(records, months));

        // 7. 喂养不耐受发生率
        indicators.add(calculateFeedingIntoleranceRate(records, months));

        // 8. 肠内营养与肠外营养比
        indicators.add(calculateEnteralParenteralRatio(records, months, department));

        return indicators;
    }

    private NutritionQualityIndicator calculateInterruptionRate(List<Document> records, List<String> months) {
        Map<String, Object> meta = NutritionQualityIndicatorConfig.findByKey("enteralInterruptionRate");
        NutritionQualityIndicator indicator = createIndicatorFromMeta(meta);

        // Total
        indicator.setTotal(calcService.calcInterruptionRate(records));

        // Monthly
        Map<String, NutritionQualityCell> monthly = new LinkedHashMap<>();
        Map<String, List<Document>> byMonth = calcService.groupByMonth(records);
        for (String month : months) {
            List<Document> monthRecords = byMonth.getOrDefault(month, Collections.emptyList());
            monthly.put(month, calcService.calcInterruptionRate(monthRecords));
        }
        indicator.setMonthly(monthly);

        return indicator;
    }

    private NutritionQualityIndicator calculatePlanCompletionRate(List<Document> records, List<String> months) {
        Map<String, Object> meta = NutritionQualityIndicatorConfig.findByKey("enteralPlanCompletionRate");
        NutritionQualityIndicator indicator = createIndicatorFromMeta(meta);

        indicator.setTotal(calcService.calcPlanCompletionRate(records));

        Map<String, NutritionQualityCell> monthly = new LinkedHashMap<>();
        Map<String, List<Document>> byMonth = calcService.groupByMonth(records);
        for (String month : months) {
            monthly.put(month, calcService.calcPlanCompletionRate(byMonth.getOrDefault(month, Collections.emptyList())));
        }
        indicator.setMonthly(monthly);

        return indicator;
    }

    private NutritionQualityIndicator calculateTubeBlockageRate(List<Document> records, List<String> months) {
        Map<String, Object> meta = NutritionQualityIndicatorConfig.findByKey("feedingTubeBlockageRate");
        NutritionQualityIndicator indicator = createIndicatorFromMeta(meta);

        indicator.setTotal(calcService.calcTubeBlockageRate(records));

        Map<String, NutritionQualityCell> monthly = new LinkedHashMap<>();
        Map<String, List<Document>> byMonth = calcService.groupByMonth(records);
        for (String month : months) {
            monthly.put(month, calcService.calcTubeBlockageRate(byMonth.getOrDefault(month, Collections.emptyList())));
        }
        indicator.setMonthly(monthly);

        return indicator;
    }

    private NutritionQualityIndicator calculateUnplannedRemovalRate(List<Document> records, List<String> months) {
        Map<String, Object> meta = NutritionQualityIndicatorConfig.findByKey("feedingTubeUnplannedRemovalRate");
        NutritionQualityIndicator indicator = createIndicatorFromMeta(meta);

        indicator.setTotal(calcService.calcUnplannedRemovalRate(records));

        Map<String, NutritionQualityCell> monthly = new LinkedHashMap<>();
        Map<String, List<Document>> byMonth = calcService.groupByMonth(records);
        for (String month : months) {
            monthly.put(month, calcService.calcUnplannedRemovalRate(byMonth.getOrDefault(month, Collections.emptyList())));
        }
        indicator.setMonthly(monthly);

        return indicator;
    }

    private NutritionQualityIndicator calculateSkinProblemRate(List<Document> records, List<String> months) {
        Map<String, Object> meta = NutritionQualityIndicatorConfig.findByKey("feedingTubeSkinProblemRate");
        NutritionQualityIndicator indicator = createIndicatorFromMeta(meta);

        indicator.setTotal(calcService.calcSkinProblemRate(records));

        Map<String, NutritionQualityCell> monthly = new LinkedHashMap<>();
        Map<String, List<Document>> byMonth = calcService.groupByMonth(records);
        for (String month : months) {
            monthly.put(month, calcService.calcSkinProblemRate(byMonth.getOrDefault(month, Collections.emptyList())));
        }
        indicator.setMonthly(monthly);

        return indicator;
    }

    private NutritionQualityIndicator calculateAspirationRate(List<Document> records, List<String> months) {
        Map<String, Object> meta = NutritionQualityIndicatorConfig.findByKey("aspirationRate");
        NutritionQualityIndicator indicator = createIndicatorFromMeta(meta);

        indicator.setTotal(calcService.calcAspirationRate(records));

        Map<String, NutritionQualityCell> monthly = new LinkedHashMap<>();
        Map<String, List<Document>> byMonth = calcService.groupByMonth(records);
        for (String month : months) {
            monthly.put(month, calcService.calcAspirationRate(byMonth.getOrDefault(month, Collections.emptyList())));
        }
        indicator.setMonthly(monthly);

        return indicator;
    }

    private NutritionQualityIndicator calculateFeedingIntoleranceRate(List<Document> records, List<String> months) {
        Map<String, Object> meta = NutritionQualityIndicatorConfig.findByKey("feedingIntoleranceRate");
        NutritionQualityIndicator indicator = createIndicatorFromMeta(meta);

        indicator.setTotal(calcService.calcFeedingIntoleranceRate(records));

        Map<String, NutritionQualityCell> monthly = new LinkedHashMap<>();
        Map<String, List<Document>> byMonth = calcService.groupByMonth(records);
        for (String month : months) {
            monthly.put(month, calcService.calcFeedingIntoleranceRate(byMonth.getOrDefault(month, Collections.emptyList())));
        }
        indicator.setMonthly(monthly);

        return indicator;
    }

    private NutritionQualityIndicator calculateEnteralParenteralRatio(List<Document> records,
                                                                        List<String> months,
                                                                        String department) {
        Map<String, Object> meta = NutritionQualityIndicatorConfig.findByKey("enteralParenteralRatio");
        NutritionQualityIndicator indicator = createIndicatorFromMeta(meta);

        // 复用 NutritionService 获取 EN/PN 患者数
        String startMonth = months.get(0);
        String endMonth = months.get(months.size() - 1);

        try {
            Map<String, Integer> enteralCounts = getEnteralPatientCounts(startMonth, endMonth, department);
            Map<String, Integer> parenteralCounts = getParenteralPatientCounts(startMonth, endMonth, department);

            int totalEnteral = enteralCounts.values().stream().mapToInt(Integer::intValue).sum();
            int totalParenteral = parenteralCounts.values().stream().mapToInt(Integer::intValue).sum();
            indicator.setTotal(calcService.calcEnteralParenteralRatio(totalEnteral, totalParenteral));

            Map<String, NutritionQualityCell> monthly = new LinkedHashMap<>();
            for (String month : months) {
                int en = enteralCounts.getOrDefault(month, 0);
                int pn = parenteralCounts.getOrDefault(month, 0);
                monthly.put(month, calcService.calcEnteralParenteralRatio(en, pn));
            }
            indicator.setMonthly(monthly);

        } catch (Exception e) {
            log.warn("获取EN/PN数据失败", e);
            indicator.setTotal(NutritionQualityCell.queryError(e.getMessage()));
            Map<String, NutritionQualityCell> monthly = new LinkedHashMap<>();
            for (String month : months) {
                monthly.put(month, NutritionQualityCell.queryError(e.getMessage()));
            }
            indicator.setMonthly(monthly);
        }

        return indicator;
    }

    /**
     * 获取肠内营养患者月度计数（复用 NutritionService 逻辑）
     */
    private Map<String, Integer> getEnteralPatientCounts(String startMonth, String endMonth, String department) {
        Map<String, Object> result = nutritionService.getRangeStats(startMonth, endMonth, department);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (data != null) {
            for (Map<String, Object> item : data) {
                if ("enteral".equals(item.get("key"))) {
                    @SuppressWarnings("unchecked")
                    Map<String, Integer> months = (Map<String, Integer>) item.get("months");
                    if (months != null) counts.putAll(months);
                }
            }
        }
        return counts;
    }

    /**
     * 获取肠外营养患者月度计数
     */
    private Map<String, Integer> getParenteralPatientCounts(String startMonth, String endMonth, String department) {
        Map<String, Object> result = nutritionService.getRangeStats(startMonth, endMonth, department);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (data != null) {
            for (Map<String, Object> item : data) {
                if ("parenteral".equals(item.get("key"))) {
                    @SuppressWarnings("unchecked")
                    Map<String, Integer> months = (Map<String, Integer>) item.get("months");
                    if (months != null) counts.putAll(months);
                }
            }
        }
        return counts;
    }

    // ════════════════════════════════════════════════════════════════════
    // 汇总视图（第一层：分子/分母/比率，带"查看详情"按钮）
    // ════════════════════════════════════════════════════════════════════

    private List<Map<String, String>> summaryColumnsList() {
        return Arrays.asList(
                Map.of("key", "index", "title", "序号"),
                Map.of("key", "item", "title", "项目"),
                Map.of("key", "value", "title", "数值"),
                Map.of("key", "action", "title", "操作", "type", "action"));
    }

    private List<Map<String, Object>> buildSummaryRows(String indicatorKey, List<Document> records,
                                                         String startMonth, String endMonth,
                                                         String department) {
        // 统计分子/分母
        int numerator = 0;
        int denominator = 0;

        for (Document record : records) {
            boolean inNumerator = false;
            boolean inDenominator = true;

            switch (indicatorKey) {
                case "enteralInterruptionRate":
                    inNumerator = adapter.hasPauseIntervention(record);
                    break;
                case "feedingIntoleranceRate":
                    Integer score = adapter.getToleranceScore(record);
                    inNumerator = (score != null && score > 0) || adapter.hasPauseIntervention(record);
                    break;
                case "enteralPlanCompletionRate":
                    java.math.BigDecimal tgt = adapter.getTargetVolume(record);
                    java.math.BigDecimal cpl = adapter.getCompletedVolume(record);
                    inDenominator = tgt != null && tgt.compareTo(java.math.BigDecimal.ZERO) > 0 && cpl != null;
                    inNumerator = inDenominator && cpl.compareTo(tgt) >= 0;
                    break;
                default:
                    break;
            }

            if (inDenominator) denominator++;
            if (inNumerator) numerator++;
        }

        String ratio = denominator > 0
                ? String.format("%.2f%%", numerator * 100.0 / denominator)
                : "N/A";

        // 构建汇总行
        List<Map<String, Object>> rows = new ArrayList<>();
        int index = 1;

        // 分子行
        Map<String, Object> numeratorRow = new LinkedHashMap<>();
        numeratorRow.put("index", index++);
        numeratorRow.put("item", "分子（" + getNumeratorLabel(indicatorKey) + "）");
        numeratorRow.put("value", String.valueOf(numerator));
        numeratorRow.put("action", makeAction(indicatorKey, startMonth, endMonth, "numerator"));
        rows.add(numeratorRow);

        // 分母行
        Map<String, Object> denominatorRow = new LinkedHashMap<>();
        denominatorRow.put("index", index++);
        denominatorRow.put("item", "分母（" + getDenominatorLabel(indicatorKey) + "）");
        denominatorRow.put("value", String.valueOf(denominator));
        denominatorRow.put("action", makeAction(indicatorKey, startMonth, endMonth, "denominator"));
        rows.add(denominatorRow);

        // 比率行
        Map<String, Object> ratioRow = new LinkedHashMap<>();
        ratioRow.put("index", index++);
        ratioRow.put("item", "比率");
        ratioRow.put("value", ratio);
        rows.add(ratioRow);

        return rows;
    }

    private Map<String, Object> makeAction(String indicatorKey, String startMonth, String endMonth,
                                             String itemOrder) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("label", "查看详情");
        action.put("target", indicatorKey);
        action.put("startMonth", startMonth);
        action.put("endMonth", endMonth);
        action.put("itemOrder", itemOrder);
        return action;
    }

    private String getNumeratorLabel(String indicatorKey) {
        switch (indicatorKey) {
            case "enteralInterruptionRate": return "中断例数";
            case "feedingIntoleranceRate": return "不耐受例数";
            case "enteralPlanCompletionRate": return "完成例数";
            default: return "分子";
        }
    }

    private String getDenominatorLabel(String indicatorKey) {
        switch (indicatorKey) {
            case "enteralInterruptionRate": return "总肠内营养例数";
            case "feedingIntoleranceRate": return "总肠内营养例数";
            case "enteralPlanCompletionRate": return "有目标量例数";
            default: return "分母";
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 明细构建（第二层：患者记录）
    // ════════════════════════════════════════════════════════════════════

    private List<Map<String, String>> buildDetailColumns(String indicatorKey) {
        // 精简列：只显示重要字段，参考质控详情格式
        List<Map<String, String>> columns = new ArrayList<>();
        columns.add(Map.of("key", "index", "title", "序号"));
        columns.add(Map.of("key", "recordTime", "title", "评估时间"));
        columns.add(Map.of("key", "patientIdMasked", "title", "患者ID"));
        columns.add(Map.of("key", "bedNo", "title", "床号"));
        columns.add(Map.of("key", "name", "title", "姓名"));
        columns.add(Map.of("key", "route", "title", "营养途径"));
        columns.add(Map.of("key", "intervention", "title", "干预措施"));
        columns.add(Map.of("key", "inNumerator", "title", "进入分子"));
        columns.add(Map.of("key", "inDenominator", "title", "进入分母"));
        columns.add(Map.of("key", "judgmentReason", "title", "判定原因"));
        columns.add(Map.of("key", "dataSource", "title", "数据来源"));
        return columns;
    }

    private List<Map<String, Object>> buildDetailRows(String indicatorKey, List<Document> records,
                                                        String month, String department,
                                                        String itemOrder) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int index = 1;

        for (Document record : records) {
            Date time = adapter.getRecordTime(record);
            String recordMonth = time != null ? calcService.toShanghaiMonth(time) : "";

            // Filter by month if specified
            if (month != null && !month.isEmpty() && !month.equals(recordMonth)) continue;

            boolean inNumerator = false;
            boolean inDenominator = true;
            String judgmentReason = "";

            // Determine numerator/denominator based on indicator
            switch (indicatorKey) {
                case "enteralInterruptionRate":
                    inNumerator = adapter.hasPauseIntervention(record);
                    judgmentReason = calcService.buildJudgmentReason(indicatorKey, record, inNumerator, inDenominator);
                    break;

                case "feedingIntoleranceRate":
                    Integer score = adapter.getToleranceScore(record);
                    inNumerator = (score != null && score > 0) || adapter.hasPauseIntervention(record);
                    judgmentReason = calcService.buildJudgmentReason(indicatorKey, record, inNumerator, inDenominator);
                    break;

                case "enteralPlanCompletionRate":
                    java.math.BigDecimal tgt2 = adapter.getTargetVolume(record);
                    java.math.BigDecimal cpl2 = adapter.getCompletedVolume(record);
                    inDenominator = tgt2 != null && tgt2.compareTo(java.math.BigDecimal.ZERO) > 0 && cpl2 != null;
                    inNumerator = inDenominator && cpl2.compareTo(tgt2) >= 0;
                    judgmentReason = calcService.buildJudgmentReason(indicatorKey, record, inNumerator, inDenominator);
                    break;

                default:
                    judgmentReason = "待确认";
                    break;
            }

            // 按 itemOrder 过滤：numerator 只保留分子记录，denominator 只保留分母记录
            if ("numerator".equals(itemOrder) && !inNumerator) continue;
            if ("denominator".equals(itemOrder) && !inDenominator) continue;

            // Build patient info
            String patientName = adapter.getName(record);
            String patientMrn = adapter.getMrn(record);
            String patientBedNo = adapter.getBed(record);

            Map<String, Object> row = adapter.buildDetailRow(record, index++, recordMonth,
                    patientName, patientMrn, patientBedNo, department,
                    inNumerator, inDenominator, judgmentReason, "肠内营养记录单");
            rows.add(row);
        }

        return rows;
    }

    // ════════════════════════════════════════════════════════════════════
    // 辅助方法
    // ════════════════════════════════════════════════════════════════════

    private NutritionQualityIndicator createIndicatorFromMeta(Map<String, Object> meta) {
        NutritionQualityIndicator indicator = new NutritionQualityIndicator();
        if (meta != null) {
            indicator.setId((int) meta.get("id"));
            indicator.setKey((String) meta.get("key"));
            indicator.setName((String) meta.get("name"));
            indicator.setUnit((String) meta.get("unit"));
            indicator.setFormula((String) meta.get("formula"));
            indicator.setNumeratorDefinition((String) meta.get("numeratorDefinition"));
            indicator.setDenominatorDefinition((String) meta.get("denominatorDefinition"));
            indicator.setTarget((String) meta.get("target"));
            indicator.setComparison((String) meta.get("comparison"));
            indicator.setAggregationUnit((String) meta.get("aggregationUnit"));
        }
        return indicator;
    }

    private List<NutritionQualityIndicator> buildDisabledIndicators() {
        return NutritionQualityIndicatorConfig.INDICATORS.stream()
                .map(meta -> {
                    NutritionQualityIndicator ind = createIndicatorFromMeta(meta);
                    ind.setTotal(NutritionQualityCell.disabled());
                    ind.setMonthly(new LinkedHashMap<>());
                    return ind;
                })
                .collect(Collectors.toList());
    }

    private List<NutritionQualityIndicator> buildNotConfiguredIndicators() {
        return NutritionQualityIndicatorConfig.INDICATORS.stream()
                .map(meta -> {
                    NutritionQualityIndicator ind = createIndicatorFromMeta(meta);
                    ind.setTotal(NutritionQualityCell.collectionNotConfigured());
                    ind.setMonthly(new LinkedHashMap<>());
                    return ind;
                })
                .collect(Collectors.toList());
    }

    private List<NutritionQualityIndicator> buildNotFoundIndicators() {
        return NutritionQualityIndicatorConfig.INDICATORS.stream()
                .map(meta -> {
                    NutritionQualityIndicator ind = createIndicatorFromMeta(meta);
                    ind.setTotal(NutritionQualityCell.collectionNotFound());
                    ind.setMonthly(new LinkedHashMap<>());
                    return ind;
                })
                .collect(Collectors.toList());
    }

    private List<NutritionQualityIndicator> buildNoDataIndicators() {
        return NutritionQualityIndicatorConfig.INDICATORS.stream()
                .map(meta -> {
                    NutritionQualityIndicator ind = createIndicatorFromMeta(meta);
                    ind.setTotal(NutritionQualityCell.noData());
                    ind.setMonthly(new LinkedHashMap<>());
                    return ind;
                })
                .collect(Collectors.toList());
    }

    private List<NutritionQualityIndicator> buildErrorIndicators(String message) {
        return NutritionQualityIndicatorConfig.INDICATORS.stream()
                .map(meta -> {
                    NutritionQualityIndicator ind = createIndicatorFromMeta(meta);
                    ind.setTotal(NutritionQualityCell.queryError(message));
                    ind.setMonthly(new LinkedHashMap<>());
                    return ind;
                })
                .collect(Collectors.toList());
    }
}
