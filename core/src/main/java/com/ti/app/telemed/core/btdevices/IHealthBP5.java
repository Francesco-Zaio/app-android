package com.ti.app.telemed.core.btdevices;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.ihealth.communication.control.Bp5Control;
import com.ihealth.communication.control.BpProfile;
import com.ihealth.communication.manager.iHealthDevicesCallback;
import com.ihealth.communication.manager.iHealthDevicesManager;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.HashMap;


class IHealthBP5 extends Handler implements IHealtDevice{

    private static final String TAG = "IHealthBP5";

    private IHealth iHealth = null;
    private Measure measure;

    private static final int HANDLER_LIVE_MEASUREMENT = 100;
    private static final int HANDLER_END_MEASUREMENT = 101;
    private static final int HANDLER_BATTERY = 102;
    private static final int HANDLER_CONNECTED = 103;
    private static final int HANDLER_DISCONNECTED = 104;
    private static final int HANDLER_ERROR = 105;

    private int batteryLevel;

    private boolean firstRead = true;
    private boolean endOperation = true;
    private int callbackId = -1;
    private Bp5Control mBp5Control = null;


    IHealthBP5(IHealth iHealth, Measure m) {
        this.measure = m;
        this.measure.setDeviceType(XmlManager.TDeviceType.BLOODPRESSURE_DT);
        this.iHealth = iHealth;
    }

    private iHealthDevicesCallback mIHealthDevicesCallback = new iHealthDevicesCallback() {
        @Override
        public void onDeviceNotify(String mac, String deviceType, String action, String message) {
            Log.d(TAG, "iHealthDevicesCallback:onDeviceNotify ++ " + "MAC=" + mac + " - DevType=" + deviceType
                    + " - Action=" + action + " - Message=" + message);
            Bundle bundle = new Bundle();
            Message msg = new Message();
            bundle.putString("message", message);
            msg.setData(bundle);
            switch (action) {
                case BpProfile.ACTION_ZOREING_BP:
                    // message: {"pressure":2}
                    msg.what = HANDLER_LIVE_MEASUREMENT;
                    sendMessage(msg);
                    break;
                case BpProfile.ACTION_ONLINE_PRESSURE_BP:
                    // message: {"pressure":2}
                    msg.what = HANDLER_LIVE_MEASUREMENT;
                    sendMessage(msg);
                    break;
                case BpProfile.ACTION_ONLINE_PULSEWAVE_BP:
                    // message: {"pressure":66,"heartbeat":false,"wave":"[19,19,19,19,19,18,17,16]"}
                    msg.what = HANDLER_LIVE_MEASUREMENT;
                    sendMessage(msg);
                    break;
                case BpProfile.ACTION_ONLINE_RESULT_BP:
                    Log.d(TAG, "ACTION_ONLINE_RESULT_BP");
                    // message: {"highpressure":141,"lowpressure":97,"heartrate":69,"arrhythmia":false,"hsd":false,"dataID":"8BD2C7B840ED5FBBE075679885310BDE"}
                    msg.what = HANDLER_END_MEASUREMENT;
                    sendMessage(msg);
                    break;
                case BpProfile.ACTION_BATTERY_BP:
                    // message: {"battery":100}
                    msg.what = HANDLER_BATTERY;
                    sendMessage(msg);
                    break;
                case BpProfile.ACTION_ERROR_BP:
                    // message={"type":"BP5","error":4}
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
        Log.d(TAG, "startMeasure: deviceMac=" + mac + " - mBp5Control=" + mBp5Control);
        if (iHealth == null) {
            Log.e(TAG, "startMeasure: iHealth is NULL!");
            return;
        }

        firstRead = true;
        endOperation = false;

        callbackId = iHealthDevicesManager.getInstance().registerClientCallback(mIHealthDevicesCallback);
     /* Limited wants to receive notification specified device */
        iHealthDevicesManager.getInstance().addCallbackFilterForDeviceType(callbackId, iHealthDevicesManager.TYPE_BP5);

        /* Get BP5 controller */
        mBp5Control = iHealthDevicesManager.getInstance().getBp5Control(mac.replace(":", ""));
        mBp5Control.getBattery();
    }

    @Override
    public synchronized void stop() {
        if (mBp5Control != null) {
            if (!firstRead)
                mBp5Control.interruptMeasure();
            mBp5Control.disconnect();
            mBp5Control = null;
        }
    }

    @Override
    public String getStartMeasureMessage() {
        return ResourceManager.getResource().getString("KMeasStartMsgOK");
    }

    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);
        switch (msg.what) {
            case HANDLER_BATTERY:
                Bundle bundle_battery = msg.getData();
                String messageBattery = bundle_battery.getString("message");
                try {
                    JSONObject reader = new JSONObject(messageBattery);
                    batteryLevel = reader.getInt(BpProfile.BATTERY_BP);
                } catch (JSONException e) {
                    iHealth.notifyError(DeviceListener.DEVICE_DATA_ERROR,
                            ResourceManager.getResource().getString("EGWDeviceDataError"));
                    Log.e(TAG, "HANDLER_BATTERY", e);
                    break;
                }
                iHealth.scheduleTimer();
                mBp5Control.startMeasure();
                break;
            case HANDLER_LIVE_MEASUREMENT:
                if (mBp5Control != null) // check if disconnect was called but not yet done
                    iHealth.scheduleTimer();
                if (firstRead) {
                    iHealth.notifyIncomingMeasures(ResourceManager.getResource().getString("KMeasuring"));
                    firstRead = false;
                }
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
        int highPressure;
        int lowPressure;
        int heartRate;

        try {
            JSONTokener jsonTokener = new JSONTokener(message);
            JSONObject jsonObject = (JSONObject) jsonTokener.nextValue();
            highPressure = jsonObject.getInt(BpProfile.HIGH_BLOOD_PRESSURE_BP);
            lowPressure = jsonObject.getInt(BpProfile.LOW_BLOOD_PRESSURE_BP);
            heartRate = jsonObject.getInt(BpProfile.PULSE_BP);
        } catch (JSONException e) {
            Log.e(TAG, "notifyResultData(): ", e);
            iHealth.notifyError(DeviceListener.DEVICE_DATA_ERROR,
                    ResourceManager.getResource().getString("EGWDeviceDataError"));
            return;
        }

        // Creo un istanza di Misura del tipo PR
        HashMap<String,String> tmpVal = new HashMap<>();
        tmpVal.put(GWConst.EGwCode_03, Integer.toString(lowPressure));
        tmpVal.put(GWConst.EGwCode_04, Integer.toString(highPressure));
        tmpVal.put(GWConst.EGwCode_06, Integer.toString(heartRate));
        tmpVal.put(GWConst.EGwCode_BATTERY, Integer.toString(batteryLevel)); // livello batteria

        measure.setMeasures(tmpVal);
        measure.setFile(null);
        measure.setFileType(null);
        measure.setFailed(false);

        iHealth.notifyEndMeasurement(measure);
    }
}
