package com.ti.app.telemed.core.btdevices;

interface IHealtDevice {

    String KPO3IHealth = "PO3";
    String KBP5IHealth = "BP5";
    String KHS4SIHealth = "HS4S";
    String KBP550BTIHealth = "BP550BT";
    String KBG5SIHealth = "BG5S";

    void startMeasure(String mac);
    void stop();
    String getStartMeasureMessage();
}