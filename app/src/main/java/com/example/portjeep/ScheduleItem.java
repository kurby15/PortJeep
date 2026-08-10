package com.example.portjeep;

public class ScheduleItem {
    private String day;
    private String date;
    private String status;
    private String jeepUnit;
    private String driverName;
    private String paoName;

    public ScheduleItem(String day, String date, String status, String jeepUnit, String driverName, String paoName) {
        this.day = day;
        this.date = date;
        this.status = status;
        this.jeepUnit = jeepUnit;
        this.driverName = driverName;
        this.paoName = paoName;
    }

    public String getDay() { return day; }
    public String getDate() { return date; }
    public String getStatus() { return status; }
    public String getJeepUnit() { return jeepUnit; }
    public String getDriverName() { return driverName; }
    public String getPaoName() { return paoName; }

    public void setJeepUnit(String jeepUnit) { this.jeepUnit = jeepUnit; }
    public void setDriverName(String driverName) { this.driverName = driverName; }
    public void setPaoName(String paoName) { this.paoName = paoName; }
}