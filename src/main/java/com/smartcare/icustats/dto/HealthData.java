package com.smartcare.icustats.dto;

import java.util.Map;

public class HealthData {
    private double uptime;
    private Map<String, String> db;

    public HealthData() {}

    public HealthData(double uptime, Map<String, String> db) {
        this.uptime = uptime;
        this.db = db;
    }

    public double getUptime() { return uptime; }
    public void setUptime(double uptime) { this.uptime = uptime; }
    public Map<String, String> getDb() { return db; }
    public void setDb(Map<String, String> db) { this.db = db; }
}
