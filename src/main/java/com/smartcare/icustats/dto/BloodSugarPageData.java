package com.smartcare.icustats.dto;

import java.util.List;

/**
 * Blood sugar page API response data.
 */
public class BloodSugarPageData {
    private PatientSummary patient;
    private List<BloodSugarRow> rows;

    public BloodSugarPageData() {}

    public BloodSugarPageData(PatientSummary patient, List<BloodSugarRow> rows) {
        this.patient = patient;
        this.rows = rows;
    }

    public PatientSummary getPatient() { return patient; }
    public void setPatient(PatientSummary patient) { this.patient = patient; }
    public List<BloodSugarRow> getRows() { return rows; }
    public void setRows(List<BloodSugarRow> rows) { this.rows = rows; }
}
