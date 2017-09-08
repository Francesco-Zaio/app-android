package com.ti.app.telemed.core.btmodule;

import com.ti.app.telemed.core.common.Measure;

public interface DeviceListener {
    String CONNECTION_ERROR = "E01";
    String DEVICE_NOT_FOUND_ERROR = "E02";
    String COMMUNICATION_ERROR = "E03";
    String MEASUREMENT_ERROR = "E04";
    String DEVICE_DATA_ERROR = "E05";
    String MEASURE_PROCEDURE_ERROR = "E06";
    String NO_MEASURES_FOUND = "E07";
    String DEVICE_CFG_ERROR = "E08";
    String DEVICE_MEMORY_EXHAUSTED = "E09";
    String USER_CFG_ERROR = "E10";

    void notifyError(String errorCode, String errorMessage);
    void notifyToUi(String message);
    void notifyWaitToUi(String message);
    void askSomething(String messageText, String positiveText, String negativeText);
    void setBtMAC(String mac);
    void showMeasurementResults(Measure m);
    void configReady(String msg);
    void operationCompleted();
}
