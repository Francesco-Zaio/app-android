package com.ti.app.telemed.core.btdevices;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.ihealth.communication.control.Bg5sControl;
import com.ihealth.communication.control.Bg5sProfile;
import com.ihealth.communication.manager.iHealthDevicesCallback;
import com.ihealth.communication.manager.iHealthDevicesManager;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.util.GWConst;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.HashMap;


class IHealthBG5S extends Handler implements IHealtDevice{

    private static final String TAG = "IHealthBG5S";

    private IHealth iHealth = null;

    private static final int HANDLER_STATUS_INFO = 100;
    private static final int HANDLER_SET_UNIT = 101;
    private static final int HANDLER_END_MEASUREMENT = 102;
    private static final int HANDLER_CONNECTED = 103;
    private static final int HANDLER_DISCONNECTED = 104;
    private static final int HANDLER_ERROR = 105;

    private boolean endOperation = true;
    private int callbackId = -1;
    private Bg5sControl mBg5sControl = null;
    private Measure measure;
    private int batteryLevel;
    private boolean prePrandial;


    IHealthBG5S(IHealth iHealth, Measure m, boolean prePrandial) {
        this.iHealth = iHealth;
        this.measure = m;
        this.prePrandial = prePrandial;
    }

    private iHealthDevicesCallback mIHealthDevicesCallback = new iHealthDevicesCallback() {
        @Override
        public void onDeviceNotify(String mac, String deviceType, String action, String message) {
            Log.d(TAG, "onDeviceNotify: " + action + " - " + message);
            Bundle bundle = new Bundle();
            Message msg = new Message();
            bundle.putString("message", message);
            msg.setData(bundle);
            switch (action) {
                case Bg5sProfile.ACTION_GET_STATUS_INFO:
                    msg.what = HANDLER_STATUS_INFO;
                    sendMessage(msg);
                    break;
                case Bg5sProfile.ACTION_SET_UNIT:
                    msg.what = HANDLER_SET_UNIT;
                    sendMessage(msg);
                    break;
                case Bg5sProfile.ACTION_RESULT:
                    msg.what = HANDLER_END_MEASUREMENT;
                    sendMessage(msg);
                    break;
                case Bg5sProfile.ACTION_ERROR:
                    msg.what = HANDLER_ERROR;
                    sendMessage(msg);
                    break;
            }
        }
        @Override
        public void onDeviceConnectionStateChange(String mac, String deviceType, int status, int errorID) {
            Log.d(TAG, "iHealthDevicesCallback:onDeviceConnectionStateChange ++ " + " CallbackId=" + callbackId + " - MAC=" + mac + " - DevType=" + deviceType
                    + " - Status=" + status + " - ErrorID=" + errorID);
            Message msg = new Message();
            Bundle bundle = new Bundle();
            bundle.putString("mac", mac);
            bundle.putString("type", deviceType);
            msg.setData(bundle);
            switch (status) {
                case iHealthDevicesManager.DEVICE_STATE_CONNECTED:
                    msg.what = HANDLER_CONNECTED;
                    sendMessage(msg);
                    break;
                case iHealthDevicesManager.DEVICE_STATE_DISCONNECTED:
                    msg.what = HANDLER_DISCONNECTED;
                    sendMessage(msg);
                    break;
            }
        }
    };

    @Override
    public void startMeasure(String mac){
        Log.d(TAG, "startMeasure: deviceMac=" + mac + " - mBg5sControl=" + mBg5sControl);
        if (iHealth == null) {
            Log.e(TAG, "startMeasure: iHealth is NULL!");
            return;
        }

        measure.setBtAddress(mac);
        endOperation = false;

        callbackId = iHealthDevicesManager.getInstance().registerClientCallback(mIHealthDevicesCallback);
     /* Limited wants to receive notification specified device */
        iHealthDevicesManager.getInstance().addCallbackFilterForDeviceType(callbackId, iHealthDevicesManager.TYPE_BG5S);

        /* Get controller */
        mBg5sControl = iHealthDevicesManager.getInstance().getBg5sControl(mac.replace(":", ""));
        mBg5sControl.getStatusInfo();
    }

    @Override
    public synchronized void stop() {
        if (mBg5sControl != null)
            mBg5sControl.disconnect();
        mBg5sControl = null;
    }

    @Override
    public String getStartMeasureMessage() {
        return ResourceManager.getResource().getString("KMsgConfiguring");
    }

    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);
        switch (msg.what) {
            case HANDLER_STATUS_INFO:
                String message = msg.getData().getString("message");
                int unit;
                try {
                    JSONObject reader = new JSONObject(message);
                    batteryLevel = reader.getInt(Bg5sProfile.INFO_BATTERY_LEVEL);
                    unit = reader.getInt(Bg5sProfile.INFO_UNIT);
                } catch (JSONException e) {
                    iHealth.notifyError(DeviceListener.DEVICE_DATA_ERROR,
                            ResourceManager.getResource().getString("EDataReadError"));
                    Log.e(TAG, "HANDLER_STATUS_INFO", e);
                    break;
                }
                if (unit != Bg5sProfile.UNIT_MG) {
                    iHealth.scheduleTimer();
                    mBg5sControl.setUnit(Bg5sProfile.UNIT_MG);
                } else {
                    iHealth.resetTimer();
                    iHealth.notifyToUi(ResourceManager.getResource().getString("DoMeasureGL"));
                    mBg5sControl.startMeasure(Bg5sProfile.MEASURE_BLOOD);
                }
                break;
            case HANDLER_SET_UNIT:
                iHealth.resetTimer();
                iHealth.notifyToUi(ResourceManager.getResource().getString("DoMeasureGL"));
                mBg5sControl.startMeasure(Bg5sProfile.MEASURE_BLOOD);
                break;
            case HANDLER_END_MEASUREMENT:
                Log.d(TAG, "HANDLER_END_MEASUREMENT: - endOperation="+endOperation);
                // for unknown reasons the device could sends two times the end measure message, only the first should be managed
                if (!endOperation) {
                    endOperation = true;
                    iHealth.resetTimer();
                    notifyResultData(msg.getData().getString("message"));
                }
                break;
            case HANDLER_ERROR:
                // for unknown reasons the device sends two times the error message, only the first should be managed
                if (!endOperation) {
                    endOperation = true;
                    Log.d(TAG, "HANDLER_ERROR ++++++++++++++++");
                    iHealth.notifyError(DeviceListener.MEASUREMENT_ERROR,
                            ResourceManager.getResource().getString("KWrongMeasure"));
                }
                break;
            case HANDLER_DISCONNECTED:
                Log.d(TAG, "HANDLER_DISCONNECTED ++++++++++++++++");
                if (callbackId != -1)
                    iHealthDevicesManager.getInstance().unRegisterClientCallback(callbackId);
                callbackId = -1;
                break;
        }
    }

    private void notifyResultData(String message) {
        int value;

        try {
            JSONTokener jsonTokener = new JSONTokener(message);
            JSONObject jsonObject = (JSONObject) jsonTokener.nextValue();
            value = jsonObject.getInt(Bg5sProfile.RESULT_VALUE);
        } catch (JSONException e) {
            Log.e(TAG, "notifyResultData(): ", e);
            iHealth.notifyError(DeviceListener.DEVICE_DATA_ERROR,
                    ResourceManager.getResource().getString("EDataReadError"));
            return;
        }

        HashMap<String,String> tmpVal = new HashMap<>();
        if (prePrandial) {
            tmpVal.put(GWConst.EGwCode_0E, Integer.toString(value));  // glicemia Pre-prandiale
        } else {
            tmpVal.put(GWConst.EGwCode_0T, Integer.toString(value));  // glicemia Post-prandiale
        }
        tmpVal.put(GWConst.EGwCode_BATTERY, Integer.toString(batteryLevel)); // livello batteria
        measure.setMeasures(tmpVal);
        iHealth.notifyEndMeasurement(measure);
    }
}
