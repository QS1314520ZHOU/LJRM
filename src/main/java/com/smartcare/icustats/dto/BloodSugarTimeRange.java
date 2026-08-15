package com.smartcare.icustats.dto;

import java.time.Instant;

/**
 * Blood sugar query time range with Shanghai timezone display values.
 */
public class BloodSugarTimeRange {
    private Instant startTime;
    private Instant endTime;
    private String startTimeShanghai;
    private String endTimeShanghai;
    private String timezone;
    private String defaultReason;

    public BloodSugarTimeRange() {}

    public BloodSugarTimeRange(Instant startTime, Instant endTime,
                                String startTimeShanghai, String endTimeShanghai,
                                String defaultReason) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.startTimeShanghai = startTimeShanghai;
        this.endTimeShanghai = endTimeShanghai;
        this.timezone = "Asia/Shanghai";
        this.defaultReason = defaultReason;
    }

    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }

    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }

    public String getStartTimeShanghai() { return startTimeShanghai; }
    public void setStartTimeShanghai(String startTimeShanghai) { this.startTimeShanghai = startTimeShanghai; }

    public String getEndTimeShanghai() { return endTimeShanghai; }
    public void setEndTimeShanghai(String endTimeShanghai) { this.endTimeShanghai = endTimeShanghai; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public String getDefaultReason() { return defaultReason; }
    public void setDefaultReason(String defaultReason) { this.defaultReason = defaultReason; }
}
