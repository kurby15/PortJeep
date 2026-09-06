package com.example.portjeep.data.model;

import java.io.Serializable;

public class ScheduleItem implements Serializable {
    private String day;
    private String date;
    private String status;
    private String jeepUnit;
    private String driverName;
    private String paoName;
    private String route = "Minuyan - Starmall Loop";

    // Additional fields for contact details
    private String driverEmail = "";
    private String driverContact = "";
    private String paoEmail = "";
    private String paoContact = "";

    // Primary Constructor
    public ScheduleItem(String day, String date, String status, String jeepUnit, String driverName, String paoName) {
        this.day = day;
        this.date = date;
        this.status = status;
        this.jeepUnit = jeepUnit;
        this.driverName = driverName;
        this.paoName = paoName;
    }

    // Overloaded Constructor including Driver & PAO contact details
    public ScheduleItem(String day, String date, String status, String jeepUnit,
                        String driverName, String driverEmail, String driverContact,
                        String paoName, String paoEmail, String paoContact) {
        this(day, date, status, jeepUnit, driverName, paoName);
        this.driverEmail = (driverEmail != null) ? driverEmail : "";
        this.driverContact = (driverContact != null) ? driverContact : "";
        this.paoEmail = (paoEmail != null) ? paoEmail : "";
        this.paoContact = (paoContact != null) ? paoContact : "";
    }

    // Getters
    public String getDay() { return day; }
    public String getDate() { return date; }
    public String getStatus() { return status; }
    public String getJeepUnit() { return jeepUnit; }
    public String getDriverName() { return driverName; }
    public String getPaoName() { return paoName; }
    public String getRoute() { return route; }

    public String getDriverEmail() { return driverEmail; }
    public String getDriverContact() { return driverContact; }
    public String getPaoEmail() { return paoEmail; }
    public String getPaoContact() { return paoContact; }

    // Setters
    public void setDay(String day) { this.day = day; }
    public void setDate(String date) { this.date = date; }
    public void setStatus(String status) { this.status = status; }
    public void setJeepUnit(String jeepUnit) { this.jeepUnit = jeepUnit; }
    public void setDriverName(String driverName) { this.driverName = driverName; }
    public void setPaoName(String paoName) { this.paoName = paoName; }
    public void setRoute(String route) { this.route = route; }

    public void setDriverEmail(String driverEmail) { this.driverEmail = (driverEmail != null) ? driverEmail : ""; }
    public void setDriverContact(String driverContact) { this.driverContact = (driverContact != null) ? driverContact : ""; }
    public void setPaoEmail(String paoEmail) { this.paoEmail = (paoEmail != null) ? paoEmail : ""; }
    public void setPaoContact(String paoContact) { this.paoContact = (paoContact != null) ? paoContact : ""; }
}