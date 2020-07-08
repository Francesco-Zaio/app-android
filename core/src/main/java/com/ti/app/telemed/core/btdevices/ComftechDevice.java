package com.ti.app.telemed.core.btdevices;

import android.bluetooth.BluetoothDevice;

import android.util.Log;

import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.BTSearcherEventListener;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.common.UserDevice;

import static com.ti.app.telemed.core.btmodule.DeviceListener.COMMUNICATION_ERROR;
import static com.ti.app.telemed.core.btmodule.DeviceListener.MEASUREMENT_ERROR;
import static com.ti.app.telemed.core.btmodule.DeviceListener.USER_CFG_ERROR;


public class ComftechDevice extends DeviceHandler implements ComftechManager.ResultListener{
    private static final String TAG = "ComftechDevice";

    public ComftechDevice(DeviceListener listener, UserDevice ud) {
        super(listener, ud);
    }

    private boolean isStart;

    // DeviceHandler interface Methods

    @Override
    public void confirmDialog() {
        if (isStart)
            ComftechManager.getInstance().startMonitoring(patient.getId(), this);
        else
            ComftechManager.getInstance().stopMonitoring(this);
    }

    @Override
    public void cancelDialog() {
        deviceListener.notifyError(MEASUREMENT_ERROR, ResourceManager.getResource().getString("KAbortMeasure"));
        stop();
    }

    @Override
    public boolean startOperation(OperationType ot, BTSearcherEventListener btSearchListener) {
        if (!startInit(ot, btSearchListener))
            return false;
        Log.d(TAG,"startOperation: iBtDevAddr="+iBtDevAddr + " iCmdCode="+iCmdCode.toString());
        return startActivity();
    }

    @Override
    public void stopOperation() {
        Log.d(TAG, "stopOperation");
        stop();
    }

    @Override
    public void selectDevice(BluetoothDevice bd){
        Log.d(TAG, "selectDevice: addr=" + bd.getAddress());
        iState = TState.EGettingService;
    }

    private boolean startActivity() {
        String patientId = ComftechManager.getInstance().getMonitoringUserId();
        String message;
        if (patientId.isEmpty()) {
            // start monitoring
            isStart = true;
            message = ResourceManager.getResource().getString("DoStartMonitoring");
            deviceListener.askSomething(message,
                    ResourceManager.getResource().getString("KMsgYes"),
                    ResourceManager.getResource().getString("KMsgNo"));
        } else if (patientId.equals(patient.getId())){
            // stop monitoring
            isStart = false;
            message = ResourceManager.getResource().getString("DoStopMonitoring");
            deviceListener.askSomething(message,
                    ResourceManager.getResource().getString("KMsgYes"),
                    ResourceManager.getResource().getString("KMsgNo"));
        } else {
            deviceListener.notifyError(USER_CFG_ERROR, "Wrong User");
            stop();
            return false;
        }
        return true;
    }

    private void reset() {
        iState = TState.EWaitingToGetDevice;
    }

    private void stop() {
        reset();
    }

    // ComftechManager listener
    @Override
    public void result(int resultCode) {
        switch (resultCode) {
            case 0:
                if (isStart)
                    deviceListener.configReady(ResourceManager.getResource().getString("KMonitoringOn"));
                else
                    deviceListener.configReady(ResourceManager.getResource().getString("KMonitoringOff"));
                stop();
                break;
            case -1:
            case -2:
            // TODO
            default:
                deviceListener.notifyError(COMMUNICATION_ERROR, String.valueOf(resultCode));
                stop();
                break;
        }
    }
}
