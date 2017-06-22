package com.ti.app.telemed.core.btmodule;

import com.ti.app.telemed.core.common.Measure;

import java.util.ArrayList;

/**
 * Created by SO000228 on 07/11/2016.
 */

public interface DeviceListener {
    String CONNECTION_ERROR = "E01";
    String DEVICE_NOT_FOUND_ERROR = "E02";
    String COMMUNICATION_ERROR = "E03";
    String MEASUREMENT_ERROR = "E04";
    String DEVICE_DATA_ERROR = "E05";
    String MEASURE_PROCEDURE_ERROR = "E06";
    String NO_HISTORICAL_DATA = "E07";

    void notifyError(String errorCode, String errorMessage);
    void notifyToUi(String message);
    void notifyWaitToUi(String message);
    void setBtMAC(String mac);
    void showMeasurementResults(Measure m);
    void operationCompleted();
}
