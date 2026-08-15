package com.smartcare.icustats.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Single blood sugar row with IRI calculation results.
 */
public class BloodSugarRow {
    private String id;
    private String time;
    private BigDecimal result;
    private String resultDisplay;
    private BigDecimal insulin;
    private BigDecimal steroidFactor;
    private BigDecimal correctionFactor;
    private BigDecimal iri;
    private String steroidWindowStart;
    private String steroidWindowEnd;
    private List<SteroidDrugDetail> drugDetails;

    public BloodSugarRow() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    public BigDecimal getResult() { return result; }
    public void setResult(BigDecimal result) { this.result = result; }
    public String getResultDisplay() { return resultDisplay; }
    public void setResultDisplay(String resultDisplay) { this.resultDisplay = resultDisplay; }
    public BigDecimal getInsulin() { return insulin; }
    public void setInsulin(BigDecimal insulin) { this.insulin = insulin; }
    public BigDecimal getSteroidFactor() { return steroidFactor; }
    public void setSteroidFactor(BigDecimal steroidFactor) { this.steroidFactor = steroidFactor; }
    public BigDecimal getCorrectionFactor() { return correctionFactor; }
    public void setCorrectionFactor(BigDecimal correctionFactor) { this.correctionFactor = correctionFactor; }
    public BigDecimal getIri() { return iri; }
    public void setIri(BigDecimal iri) { this.iri = iri; }
    public String getSteroidWindowStart() { return steroidWindowStart; }
    public void setSteroidWindowStart(String steroidWindowStart) { this.steroidWindowStart = steroidWindowStart; }
    public String getSteroidWindowEnd() { return steroidWindowEnd; }
    public void setSteroidWindowEnd(String steroidWindowEnd) { this.steroidWindowEnd = steroidWindowEnd; }
    public List<SteroidDrugDetail> getDrugDetails() { return drugDetails; }
    public void setDrugDetails(List<SteroidDrugDetail> drugDetails) { this.drugDetails = drugDetails; }
}
