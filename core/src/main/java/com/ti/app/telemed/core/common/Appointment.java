package com.ti.app.telemed.core.common;

public class Appointment {

    private String id;
    private int type;
    private String idUser;
    private long timestamp;
    private String data;

    public void setId(String id) {
        this.id = id;
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

    public void setData(String data) {
        this.data = data;
    }

    public String getId() {
        return id;
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

    public String getData() {
        return data;
    }
}
