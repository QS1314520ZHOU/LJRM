package com.smartcare.icustats.dto;

import java.util.Date;

public class MonthRange {
    private Date startDate;
    private Date endDate;

    public MonthRange() {}

    public MonthRange(Date startDate, Date endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public Date getStartDate() { return startDate; }
    public void setStartDate(Date startDate) { this.startDate = startDate; }
    public Date getEndDate() { return endDate; }
    public void setEndDate(Date endDate) { this.endDate = endDate; }
}
