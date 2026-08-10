package com.smartcare.icustats.dto;

public class ColumnDef {
    private String key;
    private String title;
    private String type;

    public ColumnDef() {}

    public ColumnDef(String key, String title) {
        this.key = key;
        this.title = title;
    }

    public ColumnDef(String key, String title, String type) {
        this.key = key;
        this.title = title;
        this.type = type;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
