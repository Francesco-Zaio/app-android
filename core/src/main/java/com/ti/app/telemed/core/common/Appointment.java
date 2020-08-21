package com.ti.app.telemed.core.common;

public class Appointment {

    private int id;
    private String appointmentId;
    private int type;
    private String idUser;
    private long timestamp;
    private String url;
    private String data;

    public void setId(int id) {
        this.id = id;
    }

    public void setAppointmentId(String appointmentId) {
        this.appointmentId = appointmentId;
    }

    public void setType(int type) {
        this.type = type;
    }

    public void setIdUser(String idUser) {
        this.idUser = idUser;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setData(String data) {
        this.data = data;
    }

    public int getId() {
        return id;
    }

    public String getAppointmentId() {
        return appointmentId;
    }

    public int getType() {
        return type;
    }

    public String getIdUser() {
        return idUser;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getUrl() {
        return url;
    }

    public String getData() {
        return data;
    }
}
