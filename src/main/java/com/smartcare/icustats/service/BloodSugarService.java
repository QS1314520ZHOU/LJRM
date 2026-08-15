package com.smartcare.icustats.service;

import com.smartcare.icustats.config.CollectionConstants;
import com.smartcare.icustats.dto.*;
import com.smartcare.icustats.util.SteroidDoseUtils;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Pattern;

@Service
public class BloodSugarService {

    private static final Logger log = LoggerFactory.getLogger(BloodSugarService.class);
    private static final String SHANGHAI_TZ = "Asia/Shanghai";

    private final MongoTemplate smartCareMongoTemplate;

    public BloodSugarService(@Qualifier("smartCareMongoTemplate") MongoTemplate smartCareMongoTemplate) {
        this.smartCareMongoTemplate = smartCareMongoTemplate;
    }

    /**
     * Get blood sugar page data for a patient.
     */
    public BloodSugarPageData getPageData(String pid) {
        // 1. Find patient
        PatientSummary patient = findPatient(pid);
        if (patient == null) {
            return null;
        }

        // 2. Get patient's ICU stay period
        Date[] stayPeriod = getIcuStayPeriod(pid);
        Date startDate = stayPeriod[0];
        Date endDate = stayPeriod[1];

        // 3. Get blood sugar records
        List<Document> bloodSugarRecords = queryBloodSugar(pid, startDate, endDate);

        // 4. Build rows with IRI calculation
        List<BloodSugarRow> rows = buildRows(pid, bloodSugarRecords);

        BloodSugarPageData data = new BloodSugarPageData();
        data.setPatient(patient);
        data.setRows(rows);
        return data;
    }

    /**
     * Find patient by ID, trying ObjectId first, then String.
     */
    private PatientSummary findPatient(String pid) {
        Query query = new Query();

        // Try to parse as ObjectId
        try {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("_id").is(new ObjectId(pid)),
                    Criteria.where("_id").is(pid),
                    Criteria.where("pid").is(pid)
            ));
        } catch (IllegalArgumentException e) {
            // Not a valid ObjectId, search by pid field only
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
        return summary;
    }

    /**
     * Get ICU stay period for a patient.
     * Returns [startDate, endDate] - from 7 days before admission to now.
     */
    private Date[] getIcuStayPeriod(String pid) {
        // Find the patient's latest admission
        Query patientQuery = new Query();
        try {
            patientQuery.addCriteria(new Criteria().orOperator(
                    Criteria.where("_id").is(new ObjectId(pid)),
                    Criteria.where("_id").is(pid),
                    Criteria.where("pid").is(pid)
            ));
        } catch (IllegalArgumentException e) {
            patientQuery.addCriteria(new Criteria().orOperator(
                    Criteria.where("_id").is(pid),
                    Criteria.where("pid").is(pid)
            ));
        }

        Document patient = smartCareMongoTemplate.findOne(patientQuery, Document.class, CollectionConstants.PATIENT);

        Date now = new Date();
        Date startDate = null;
        Date endDate = now;

        if (patient != null) {
            // Try to get admission time
            Object inIcuTime = patient.get("inIcuTime");
            if (inIcuTime instanceof Date) {
                startDate = (Date) inIcuTime;
            }
        }

        // Default: 7 days ago
        if (startDate == null) {
            startDate = new Date(now.getTime() - 7L * 24 * 60 * 60 * 1000);
        }

        return new Date[]{startDate, endDate};
    }

    /**
     * Query blood sugar records for a patient within a time range.
     */
    private List<Document> queryBloodSugar(String pid, Date startDate, Date endDate) {
        Query query = new Query();
        query.addCriteria(Criteria.where("pid").is(pid));
        query.addCriteria(Criteria.where("valid").in(true, 1, "true"));
        if (startDate != null) {
            query.addCriteria(Criteria.where("time").gte(startDate));
        }
        if (endDate != null) {
            query.addCriteria(Criteria.where("time").lt(endDate));
        }
        query.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "time"));

        return smartCareMongoTemplate.find(query, Document.class, CollectionConstants.BLOOD_SUGAR);
    }

    /**
     * Build blood sugar rows with IRI calculation.
     */
    private List<BloodSugarRow> buildRows(String pid, List<Document> bloodSugarRecords) {
        List<BloodSugarRow> rows = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        sdf.setTimeZone(TimeZone.getTimeZone(SHANGHAI_TZ));

        for (Document record : bloodSugarRecords) {
            BloodSugarRow row = new BloodSugarRow();
            row.setId(getIdString(record));
            Date time = getDateField(record, "time");
            row.setTime(time != null ? sdf.format(time) : "");
            row.setResult(SteroidDoseUtils.safeParseBigDecimal(record.get("result")));
            row.setResultDisplay(getStringField(record, "result"));
            row.setInsulin(SteroidDoseUtils.safeParseInsulin(record.get("insulin")));

            // Get patient's steroid factor
            BigDecimal steroidFactor = getPatientSteroidFactor(pid, time);
            row.setSteroidFactor(steroidFactor);
            row.setCorrectionFactor(SteroidDoseUtils.getCorrectionFactor(steroidFactor));

            // Calculate IRI
            row.setIri(SteroidDoseUtils.calculateIri(row.getResult(), row.getInsulin(), row.getCorrectionFactor()));

            // Get drug details for this window
            row.setDrugDetails(getSteroidDrugDetails(pid, time));

            rows.add(row);
        }
        return rows;
    }

    /**
     * Get patient's steroid factor for a specific time point.
     */
    private BigDecimal getPatientSteroidFactor(String pid, Date time) {
        if (time == null) return BigDecimal.ZERO;

        // Get the 8-8 window for this time point
        Date[] window = get8_8Window(time);
        Date windowStart = window[0];
        Date windowEnd = window[1];

        // Query drugExe for steroids in this window
        BigDecimal totalEquivalent = getTotalSteroidEquivalent(pid, windowStart, windowEnd);
        return totalEquivalent;
    }

    /**
     * Get steroid drug details for a specific time point.
     */
    private List<SteroidDrugDetail> getSteroidDrugDetails(String pid, Date time) {
        if (time == null) return new ArrayList<>();

        Date[] window = get8_8Window(time);
        Date windowStart = window[0];
        Date windowEnd = window[1];

        return querySteroidDrugs(pid, windowStart, windowEnd);
    }

    /**
     * Get total steroid equivalent for a patient in a time window.
     */
    private BigDecimal getTotalSteroidEquivalent(String pid, Date windowStart, Date windowEnd) {
        List<SteroidDrugDetail> drugs = querySteroidDrugs(pid, windowStart, windowEnd);
        BigDecimal total = BigDecimal.ZERO;
        for (SteroidDrugDetail drug : drugs) {
            if (drug.getHydrocortisoneEquivalent() != null) {
                total = total.add(drug.getHydrocortisoneEquivalent());
            }
        }
        return total;
    }

    /**
     * Query steroid drugs for a patient in a time window.
     */
    private List<SteroidDrugDetail> querySteroidDrugs(String pid, Date windowStart, Date windowEnd) {
        // Build drug name regex for target steroids
        Pattern drugPattern = Pattern.compile("甲泼尼龙|氢化可的松|地塞米松");

        Query query = new Query();
        query.addCriteria(Criteria.where("pid").is(pid));
        query.addCriteria(Criteria.where("startTime").gte(windowStart).lt(windowEnd));
        query.addCriteria(Criteria.where("name").regex(drugPattern));
        // Exclude invalid/cancelled/revoke
        query.addCriteria(Criteria.where("status").nin("invalid", "cancel", "cancelled", "revoke", "revoked", 99, -1));
        query.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "startTime"));

        List<Document> drugs = smartCareMongoTemplate.find(query, Document.class, CollectionConstants.DRUG_EXE);

        List<SteroidDrugDetail> details = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        sdf.setTimeZone(TimeZone.getTimeZone(SHANGHAI_TZ));

        for (Document drug : drugs) {
            SteroidDrugDetail detail = new SteroidDrugDetail();
            Date startTime = getDateField(drug, "startTime");
            detail.setTime(startTime != null ? sdf.format(startTime) : "");
            detail.setName(getStringField(drug, "name"));
            detail.setDose(SteroidDoseUtils.safeParseBigDecimal(drug.get("dose")));
            detail.setUnit(getStringField(drug, "unit"));

            // Calculate hydrocortisone equivalent
            BigDecimal doseMg = SteroidDoseUtils.convertToMg(detail.getDose(), detail.getUnit());
            BigDecimal equivalent = SteroidDoseUtils.toHydrocortisoneEquivalent(doseMg, detail.getName());
            detail.setHydrocortisoneEquivalent(equivalent);

            details.add(detail);
        }
        return details;
    }

    /**
     * Get 8-8 window for a natural day D.
     * Window = [D 08:00:00 Shanghai, D+1 08:00:00 Shanghai)
     * If time < D 08:00, window is [D-1 08:00, D 08:00)
     * If time >= D 08:00, window is [D 08:00, D+1 08:00)
     */
    public static Date[] get8_8Window(Date time) {
        if (time == null) {
            return new Date[]{null, null};
        }

        TimeZone shanghaiTz = TimeZone.getTimeZone(SHANGHAI_TZ);
        Calendar cal = Calendar.getInstance(shanghaiTz);
        cal.setTime(time);

        int hour = cal.get(Calendar.HOUR_OF_DAY);

        // Set to 08:00:00 of the current day
        cal.set(Calendar.HOUR_OF_DAY, 8);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date today8am = cal.getTime();

        Date windowStart;
        Date windowEnd;

        if (hour >= 8) {
            // time >= today 08:00 → window is [today 08:00, tomorrow 08:00)
            windowStart = today8am;
            cal.add(Calendar.DAY_OF_MONTH, 1);
            windowEnd = cal.getTime();
        } else {
            // time < today 08:00 → window is [yesterday 08:00, today 08:00)
            cal.add(Calendar.DAY_OF_MONTH, -1);
            windowStart = cal.getTime();
            cal.add(Calendar.DAY_OF_MONTH, 1);
            windowEnd = cal.getTime();
        }

        return new Date[]{windowStart, windowEnd};
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
}
