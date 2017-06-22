package com.ti.app.telemed.core.btdevices;

public interface IHealtDevice {
    void startMeasure(String mac);
    void stop();
    String getStartMeasureMessage();
}