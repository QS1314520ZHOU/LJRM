package com.smartcare.icustats.dto;

import java.util.List;

/**
 * Blood sugar page API response data.
 */
public class BloodSugarPageData {
    private PatientSummary patient;
    private BloodSugarTimeRange range;
    private List<BloodSugarRow> rows;

    public BloodSugarPageData() {}

    public BloodSugarPageData(PatientSummary patient, BloodSugarTimeRange range, List<BloodSugarRow> rows) {
        this.patient = patient;
        this.range = range;
        this.rows = rows;
    }

    public PatientSummary getPatient() { return patient; }
    public void setPatient(PatientSummary patient) { this.patient = patient; }

    public BloodSugarTimeRange getRange() { return range; }
    public void setRange(BloodSugarTimeRange range) { this.range = range; }

    public List<BloodSugarRow> getRows() { return rows; }
    public void setRows(List<BloodSugarRow> rows) { this.rows = rows; }
}
