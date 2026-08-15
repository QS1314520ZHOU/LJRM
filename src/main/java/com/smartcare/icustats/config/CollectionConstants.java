package com.smartcare.icustats.config;

/**
 * 集合名与数据库路由常量
 * 旧版 Mongoose model 是数据库归属和集合名的首要真值。
 *
 * SmartCare 数据库 (smartCareMongoTemplate):
 *   bedside, patient, bedRecord, doctorQuality, doctorQualityItem,
 *   doctorQualityItemDetail, doctorQC, doctorQCIData, doctorQCIDetail,
 *   score, tubeExe, drugExe, configDrugMethod
 *
 * DataCenter 数据库 (dataCenterMongoTemplate):
 *   VI_ICU_ZYYZ, VI_ICU_QUALITY, VI_ICU_EXAM, VI_ICU_EXAM_ITEM
 */
public final class CollectionConstants {

    private CollectionConstants() {}

    // ── SmartCare 集合 ──────────────────────────────────────
    public static final String PATIENT = "patient";
    public static final String BEDSIDE = "bedside";
    public static final String BED_RECORD = "bedRecord";
    public static final String BLOOD_SUGAR = "bloodSugar";
    public static final String SCORE = "score";
    public static final String TUBE_EXE = "tubeExe";
    public static final String DRUG_EXE = "drugExe";
    public static final String CONFIG_DRUG_METHOD = "configDrugMethod";
    public static final String DOCTOR_QUALITY = "doctorQuality";
    public static final String DOCTOR_QUALITY_ITEM = "doctorQualityItem";
    public static final String DOCTOR_QUALITY_ITEM_DETAIL = "doctorQualityItemDetail";
    public static final String DOCTOR_QC = "doctorQC";
    public static final String DOCTOR_QC_DATA = "doctorQCIData";
    public static final String DOCTOR_QC_DETAIL = "doctorQCIDetail";

    // ── DataCenter 集合 ─────────────────────────────────────
    public static final String VI_ICU_ZYYZ = "VI_ICU_ZYYZ";
    public static final String VI_ICU_QUALITY = "VI_ICU_QUALITY";
    public static final String VI_ICU_EXAM = "VI_ICU_EXAM";
    public static final String VI_ICU_EXAM_ITEM = "VI_ICU_EXAM_ITEM";
}
