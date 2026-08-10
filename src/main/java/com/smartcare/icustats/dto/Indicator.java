package com.smartcare.icustats.dto;

public class Indicator {
    private int id;
    private String name;
    private String key;
    private String unit;

    public Indicator() {}

    public Indicator(int id, String name, String key, String unit) {
        this.id = id;
        this.name = name;
        this.key = key;
        this.unit = unit;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
}
