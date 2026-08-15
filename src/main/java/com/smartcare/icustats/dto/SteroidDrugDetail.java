package com.smartcare.icustats.dto;

import java.math.BigDecimal;

/**
 * Single steroid drug administration detail within an 8-8 window.
 */
public class SteroidDrugDetail {
    private String time;
    private String name;
    private BigDecimal dose;
    private String unit;
    private BigDecimal hydrocortisoneEquivalent;

    public SteroidDrugDetail() {}

    public SteroidDrugDetail(String time, String name, BigDecimal dose, String unit, BigDecimal hydrocortisoneEquivalent) {
        this.time = time;
        this.name = name;
        this.dose = dose;
        this.unit = unit;
        this.hydrocortisoneEquivalent = hydrocortisoneEquivalent;
    }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getDose() { return dose; }
    public void setDose(BigDecimal dose) { this.dose = dose; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getHydrocortisoneEquivalent() { return hydrocortisoneEquivalent; }
    public void setHydrocortisoneEquivalent(BigDecimal hydrocortisoneEquivalent) { this.hydrocortisoneEquivalent = hydrocortisoneEquivalent; }
}
