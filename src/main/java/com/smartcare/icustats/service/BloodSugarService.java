package com.smartcare.icustats.service;

import com.smartcare.icustats.config.CollectionConstants;
import com.smartcare.icustats.dto.*;
import com.smartcare.icustats.util.SteroidDoseUtils;
import com.smartcare.icustats.util.ShanghaiTimeRangeUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class BloodSugarService {

    private static final Logger log = LoggerFactory.getLogger(BloodSugarService.class);
    private static final Pattern STEROID_PATTERN = Pattern.compile("甲泼尼龙|氢化可的松|地塞米松");
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final MongoTemplate smartCareMongoTemplate;

    public BloodSugarService(@Qualifier("smartCareMongoTemplate") MongoTemplate smartCareMongoTemplate) {
        this.smartCareMongoTemplate = smartCareMongoTemplate;
    }

    /**
     * Get blood sugar page data for a patient with optional time range.
     */
    public BloodSugarPageData getPageData(String pid, Instant startTime, Instant endTime) {
        // 1. Find patient
        PatientSummary patient = findPatient(pid);
        if (patient == null) {
            return null;
        }

        // 2. Determine time range
        BloodSugarTimeRange range;
        if (startTime != null && endTime != null) {
            range = ShanghaiTimeRangeUtils.requestedRange(startTime, endTime);
        } else {
            range = resolveDefaultRange(patient);
        }

        // 3. Get blood sugar records
        List<Document> bloodSugarRecords = queryBloodSugar(pid, range.getStartTime(), range.getEndTime());

        // 4. Batch query steroids for all 8-8 windows
        Map<String, List<SteroidDrugDetail>> steroidCache = batchQuerySteroids(pid, bloodSugarRecords);

        // 5. Build rows with IRI calculation
        List<BloodSugarRow> rows = buildRows(bloodSugarRecords, steroidCache);

        BloodSugarPageData data = new BloodSugarPageData();
        data.setPatient(patient);
        data.setRange(range);
        data.setRows(rows);
        return data;
    }

    /**
     * Resolve default range based on patient status.
     */
    private BloodSugarTimeRange resolveDefaultRange(PatientSummary patient) {
        if (patient.isDischarged() && patient.getAdmissionTime() != null && patient.getDischargeTime() != null) {
            return ShanghaiTimeRangeUtils.dischargedRange(patient.getAdmissionTime(), patient.getDischargeTime());
        }
        return ShanghaiTimeRangeUtils.currentNursingRange(Instant.now());
    }

    /**
     * Find patient by ID, trying ObjectId first, then String.
     */
    private PatientSummary findPatient(String pid) {
        Query query = new Query();

        try {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("_id").is(new ObjectId(pid)),
                    Criteria.where("_id").is(pid),
                    Criteria.where("pid").is(pid)
            ));
        } catch (IllegalArgumentException e) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("_id").is(pid),
                    Criteria.where("pid").is(pid)
            ));
        }

        Document patient = smartCareMongoTemplate.findOne(query, Document.class, CollectionConstants.PATIENT);
        if (patient == null) {
            return null;
        }

        PatientSummary summary = new PatientSummary();
        summary.setId(getIdString(patient));
        summary.setName(getStringField(patient, "name"));
        summary.setMrn(getStringField(patient, "mrn"));
        summary.setBedNo(getStringField(patient, "bedNo"));
        summary.setGender(getStringField(patient, "sex"));
        summary.setAge(getStringField(patient, "age"));

        // Parse admission/discharge times
        summary.setAdmissionTime(getInstantField(patient, "admissionTime", "inTime", "inIcuTime", "icuInTime", "enterTime"));
        summary.setDischargeTime(getInstantField(patient, "dischargeTime", "outTime", "outIcuTime", "icuOutTime", "leaveTime"));
        summary.setDischarged(isDischarged(patient));

        return summary;
    }

    /**
     * Get Instant from first available field.
     */
    private Instant getInstantField(Document doc, String... fieldNames) {
        for (String field : fieldNames) {
            Object value = doc.get(field);
            if (value instanceof Date) {
                return ((Date) value).toInstant();
            }
        }
        return null;
    }

    /**
     * Check if patient is discharged.
     */
    private boolean isDischarged(Document patient) {
        // Check discharge time exists
        Instant dischargeTime = getInstantField(patient, "dischargeTime", "outTime", "outIcuTime", "icuOutTime", "leaveTime");
        if (dischargeTime != null) return true;

        // Check status field
        String[] statusFields = {"status", "patientStatus"};
        String[] dischargedValues = {"discharged", "已出科", "出科", "转出", "已转出"};

        for (String field : statusFields) {
            Object status = patient.get(field);
            if (status != null) {
                String statusStr = String.valueOf(status).trim().toLowerCase();
                for (String dv : dischargedValues) {
                    if (statusStr.equals(dv.toLowerCase())) return true;
                }
            }
        }
        return false;
    }

    /**
     * Query blood sugar records within time range.
     */
    private List<Document> queryBloodSugar(String pid, Instant startDate, Instant endDate) {
        Query query = new Query();
        query.addCriteria(Criteria.where("pid").is(pid));
        query.addCriteria(Criteria.where("valid").in(true, 1, "true"));
        query.addCriteria(Criteria.where("time").gte(Date.from(startDate)).lt(Date.from(endDate)));
        query.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "time", "_id"));

        return smartCareMongoTemplate.find(query, Document.class, CollectionConstants.BLOOD_SUGAR);
    }

    /**
     * Batch query steroids for all 8-8 windows in blood sugar records.
     * Returns map of windowKey -> drug details.
     */
    private Map<String, List<SteroidDrugDetail>> batchQuerySteroids(String pid, List<Document> bloodSugarRecords) {
        if (bloodSugarRecords.isEmpty()) return Collections.emptyMap();

        // Find min/max time to determine query range
        Date minTime = getDateField(bloodSugarRecords.get(0), "time");
        Date maxTime = getDateField(bloodSugarRecords.get(bloodSugarRecords.size() - 1), "time");

        if (minTime == null || maxTime == null) return Collections.emptyMap();

        // Expand range to cover all possible 8-8 windows
        // minTime - 24h to maxTime + 24h covers all windows
        Date queryStart = new Date(minTime.getTime() - 24L * 60 * 60 * 1000);
        Date queryEnd = new Date(maxTime.getTime() + 24L * 60 * 60 * 1000);

        // Single batch query for all steroids
        // 匹配根级别 name 或 drugList 数组中的 name
        Query query = new Query();
        query.addCriteria(Criteria.where("pid").is(pid));
        query.addCriteria(Criteria.where("startTime").gte(queryStart).lt(queryEnd));
        query.addCriteria(new Criteria().orOperator(
                Criteria.where("name").regex(STEROID_PATTERN),
                Criteria.where("drugList.name").regex(STEROID_PATTERN)
        ));
        query.addCriteria(Criteria.where("status").nin("invalid", "cancel", "cancelled", "revoke", "revoked", 99, -1));
        query.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "startTime"));

        List<Document> drugs = smartCareMongoTemplate.find(query, Document.class, CollectionConstants.DRUG_EXE);

        // Group drugs by 8-8 window
        Map<String, List<SteroidDrugDetail>> windowMap = new HashMap<>();
        for (Document drug : drugs) {
            Date drugTime = getDateField(drug, "startTime");
            if (drugTime == null) continue;

            String windowKey = getWindowKey(drugTime);
            SteroidDrugDetail detail = buildDrugDetail(drug);
            windowMap.computeIfAbsent(windowKey, k -> new ArrayList<>()).add(detail);
        }

        return windowMap;
    }

    /**
     * Get window key for a time point (YYYY-MM-DD of the nursing day).
     */
    private String getWindowKey(Date time) {
        Date[] window = get8_8Window(time);
        ZonedDateTime windowStart = window[0].toInstant().atZone(ShanghaiTimeRangeUtils.SHANGHAI_ZONE);
        return windowStart.toLocalDate().toString();
    }

    /**
     * Build drug detail from document.
     */
    private SteroidDrugDetail buildDrugDetail(Document drug) {
        SteroidDrugDetail detail = new SteroidDrugDetail();
        Date startTime = getDateField(drug, "startTime");
        detail.setTime(startTime != null ? formatShanghai(startTime) : "");
        detail.setName(getStringField(drug, "name"));

        // Parse dose from dose field or drugList
        BigDecimal dose = SteroidDoseUtils.safeParseBigDecimal(drug.get("dose"));
        String unit = getStringField(drug, "unit");

        // Try drugList array format
        if (dose == null && drug.get("drugList") instanceof List) {
            List<?> drugList = (List<?>) drug.get("drugList");
            for (Object item : drugList) {
                if (item instanceof Document) {
                    Document drugItem = (Document) item;
                    String name = getStringField(drugItem, "name");
                    if (SteroidDoseUtils.isTargetSteroid(name)) {
                        dose = SteroidDoseUtils.safeParseBigDecimal(drugItem.get("dose"));
                        unit = getStringField(drugItem, "unit");
                        detail.setName(name);
                        break;
                    }
                }
            }
        }

        // Try drugList object format
        if (dose == null && drug.get("drugList") instanceof Document) {
            Document drugList = (Document) drug.get("drugList");
            dose = SteroidDoseUtils.safeParseBigDecimal(drugList.get("dose"));
            unit = getStringField(drugList, "unit");
        }

        detail.setDose(dose);
        detail.setUnit(unit);

        // Calculate hydrocortisone equivalent
        BigDecimal doseMg = SteroidDoseUtils.convertToMg(dose, unit);
        BigDecimal equivalent = SteroidDoseUtils.toHydrocortisoneEquivalent(doseMg, detail.getName());
        detail.setHydrocortisoneEquivalent(equivalent);

        return detail;
    }

    /**
     * Build blood sugar rows with IRI calculation.
     */
    private List<BloodSugarRow> buildRows(List<Document> bloodSugarRecords, Map<String, List<SteroidDrugDetail>> steroidCache) {
        List<BloodSugarRow> rows = new ArrayList<>();

        for (Document record : bloodSugarRecords) {
            BloodSugarRow row = new BloodSugarRow();
            row.setId(getIdString(record));
            Date time = getDateField(record, "time");
            row.setTime(time != null ? formatShanghai(time) : "");
            row.setResult(SteroidDoseUtils.safeParseBigDecimal(record.get("result")));
            row.setResultDisplay(getStringField(record, "result"));
            row.setInsulin(SteroidDoseUtils.safeParseInsulin(record.get("insulin")));

            // Get steroid factor from cache
            String windowKey = time != null ? getWindowKey(time) : "";
            List<SteroidDrugDetail> drugDetails = steroidCache.getOrDefault(windowKey, Collections.emptyList());

            BigDecimal steroidFactor = BigDecimal.ZERO;
            for (SteroidDrugDetail drug : drugDetails) {
                if (drug.getHydrocortisoneEquivalent() != null) {
                    steroidFactor = steroidFactor.add(drug.getHydrocortisoneEquivalent());
                }
            }

            row.setSteroidFactor(steroidFactor);
            row.setCorrectionFactor(SteroidDoseUtils.getCorrectionFactor(steroidFactor));
            row.setIri(SteroidDoseUtils.calculateIri(row.getResult(), row.getInsulin(), row.getCorrectionFactor()));
            row.setDrugDetails(drugDetails);

            rows.add(row);
        }
        return rows;
    }

    /**
     * Get 8-8 window for a time point.
     */
    public static Date[] get8_8Window(Date time) {
        if (time == null) return new Date[]{null, null};

        ZonedDateTime zdt = time.toInstant().atZone(ShanghaiTimeRangeUtils.SHANGHAI_ZONE);
        int hour = zdt.getHour();

        ZonedDateTime windowStart;
        ZonedDateTime windowEnd;

        if (hour >= 8) {
            windowStart = zdt.toLocalDate().atTime(8, 0).atZone(ShanghaiTimeRangeUtils.SHANGHAI_ZONE);
            windowEnd = zdt.toLocalDate().plusDays(1).atTime(8, 0).atZone(ShanghaiTimeRangeUtils.SHANGHAI_ZONE);
        } else {
            windowStart = zdt.toLocalDate().minusDays(1).atTime(8, 0).atZone(ShanghaiTimeRangeUtils.SHANGHAI_ZONE);
            windowEnd = zdt.toLocalDate().atTime(8, 0).atZone(ShanghaiTimeRangeUtils.SHANGHAI_ZONE);
        }

        return new Date[]{Date.from(windowStart.toInstant()), Date.from(windowEnd.toInstant())};
    }

    // Helper methods

    private String getIdString(Document doc) {
        Object id = doc.get("_id");
        if (id instanceof ObjectId) {
            return ((ObjectId) id).toHexString();
        }
        return id != null ? String.valueOf(id) : "";
    }

    private String getStringField(Document doc, String field) {
        Object value = doc.get(field);
        return value != null ? String.valueOf(value) : "";
    }

    private Date getDateField(Document doc, String field) {
        Object value = doc.get(field);
        if (value instanceof Date) {
            return (Date) value;
        }
        return null;
    }

    private String formatShanghai(Date date) {
        return date.toInstant().atZone(ShanghaiTimeRangeUtils.SHANGHAI_ZONE).format(DISPLAY_FORMATTER);
    }
}
