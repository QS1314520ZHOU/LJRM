package com.smartcare.icustats.dto;

import java.time.Instant;

/**
 * Blood sugar page patient summary - safe subset of patient fields.
 */
public class PatientSummary {
    private String id;
    private String name;
    private String mrn;
    private String bedNo;
    private String gender;
    private String age;
    private Instant admissionTime;
    private Instant dischargeTime;
    private boolean discharged;

    public PatientSummary() {}

    public PatientSummary(String id, String name, String mrn, String bedNo, String gender, String age) {
        this.id = id;
        this.name = name;
        this.mrn = mrn;
        this.bedNo = bedNo;
        this.gender = gender;
        this.age = age;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMrn() { return mrn; }
    public void setMrn(String mrn) { this.mrn = mrn; }

    public String getBedNo() { return bedNo; }
    public void setBedNo(String bedNo) { this.bedNo = bedNo; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getAge() { return age; }
    public void setAge(String age) { this.age = age; }

    public Instant getAdmissionTime() { return admissionTime; }
    public void setAdmissionTime(Instant admissionTime) { this.admissionTime = admissionTime; }

    public Instant getDischargeTime() { return dischargeTime; }
    public void setDischargeTime(Instant dischargeTime) { this.dischargeTime = dischargeTime; }

    public boolean isDischarged() { return discharged; }
    public void setDischarged(boolean discharged) { this.discharged = discharged; }
}
