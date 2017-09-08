package com.ti.app.telemed.core.btdevices;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.ihealth.communication.control.Bp550BTControl;
import com.ihealth.communication.control.BpProfile;
import com.ihealth.communication.manager.iHealthDevicesCallback;
import com.ihealth.communication.manager.iHealthDevicesManager;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;


class IHealthBP550BT extends Handler implements IHealtDevice{

    private static final String TAG = "IHealthBP550BT";

    private IHealth iHealth = null;
    private Measure measure;

    private static final int HANDLER_HISTORICALDATA= 100;
    private static final int HANDLER_FUNCTIONINFO= 101;
    private static final int HANDLER_BATTERY = 102;
    private static final int HANDLER_CONNECTED = 103;
    private static final int HANDLER_DISCONNECTED = 104;
    private static final int HANDLER_ERROR = 105;

    private String batteryLevel="";
    private int callbackId = -1;
    private Bp550BTControl bp550BTControl = null;


    IHealthBP550BT(IHealth iHealth, Measure m) {
        this.measure = m;
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
                case BpProfile.ACTION_FUNCTION_INFORMATION_BP:
                    msg.what = HANDLER_FUNCTIONINFO;
                    sendMessage(msg);
                    break;
                case BpProfile.ACTION_BATTERY_BP:
                    // message: {"battery":100}
                    msg.what = HANDLER_BATTERY;
                    sendMessage(msg);
                    break;
                case BpProfile.ACTION_HISTORICAL_DATA_BP:
                    // message: {"highpressure":141,"lowpressure":97,"heartrate":69,"arrhythmia":false,"hsd":false,"dataID":"8BD2C7B840ED5FBBE075679885310BDE"}
                    msg.what = HANDLER_HISTORICALDATA;
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
        Log.d(TAG, "startMeasure: deviceMac=" + mac + " - bp550BTControl=" + bp550BTControl);
        if (iHealth == null) {
            Log.e(TAG, "startMeasure: iHealth is NULL!");
            return;
        }

        measure.setBtAddress(mac);

        callbackId = iHealthDevicesManager.getInstance().registerClientCallback(mIHealthDevicesCallback);
     /* Limited wants to receive notification specified device */
        iHealthDevicesManager.getInstance().addCallbackFilterForDeviceType(callbackId, iHealthDevicesManager.TYPE_550BT);

        /* Get BP5 controller */
        bp550BTControl = iHealthDevicesManager.getInstance().getBp550BTControl(mac.replace(":", ""));
        bp550BTControl.getFunctionInfo();

    }

    @Override
    public synchronized void stop() {
        if (bp550BTControl != null) {
            bp550BTControl.disconnect();
            bp550BTControl = null;
        }
    }

    @Override
    public String getStartMeasureMessage() {
        return ResourceManager.getResource().getString("KMeasuring");
    }

    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);
        switch (msg.what) {
            case HANDLER_FUNCTIONINFO:
                iHealth.scheduleTimer();
                bp550BTControl.getBattery();
                break;
            case HANDLER_BATTERY:
                String messageBattery = msg.getData().getString("message");
                try {
                    JSONObject reader = new JSONObject(messageBattery);
                    batteryLevel = reader.getString(BpProfile.BATTERY_BP);
                } catch (JSONException e) {
                    iHealth.notifyError(DeviceListener.DEVICE_DATA_ERROR,
                            ResourceManager.getResource().getString("EDataReadError"));
                    Log.e(TAG, "HANDLER_BATTERY", e);
                    break;
                }
                iHealth.scheduleTimer();
                bp550BTControl.getOfflineData();
                break;
            case HANDLER_HISTORICALDATA:
                Log.d(TAG, "HANDLER_HISTORICALDATA");
                iHealth.resetTimer();
                notifyResultData(msg.getData().getString("message"));
                break;
            case HANDLER_ERROR:
                Log.d(TAG, "HANDLER_ERROR ++++++++++++++++");
                try {
                    JSONObject info = new JSONObject(msg.getData().getString("message"));
                    String num =info.getString(BpProfile.ERROR_NUM_BP);
                    iHealth.notifyError(DeviceListener.MEASUREMENT_ERROR, "error num: " + num);
                } catch (JSONException e) {
                    iHealth.notifyError(DeviceListener.DEVICE_DATA_ERROR,
                            ResourceManager.getResource().getString("EDataReadError"));
                    Log.e(TAG, "HANDLER_ERROR", e);
                    break;
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
        String highPressure;
        String lowPressure;
        String heartRate;
        String timestamp;

        try {
            JSONObject info = new JSONObject(message);
            if (info.has(BpProfile.HISTORICAL_DATA_BP)) {
                JSONArray array = info.getJSONArray(BpProfile.HISTORICAL_DATA_BP);
                if (array.length() <= 0) {
                    iHealth.notifyError(DeviceListener.NO_MEASURES_FOUND, ResourceManager.getResource().getString("ENoMeasuresFound"));
                    return;
                }
                JSONObject obj = array.getJSONObject(0);
                highPressure = obj.getString(BpProfile.HIGH_BLOOD_PRESSURE_BP);
                lowPressure = obj.getString(BpProfile.LOW_BLOOD_PRESSURE_BP);
                heartRate = obj.getString(BpProfile.PULSE_BP);
                String date = obj.getString(BpProfile.MEASUREMENT_DATE_BP);
                Calendar cal = Calendar.getInstance();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
                try {
                    cal.setTime(sdf.parse(date));
                    timestamp = XmlManager.getXmlManager().getTimestamp(cal);
                } catch (ParseException e) {
                    Log.e(TAG, "notifyResultData(): ", e);
                    iHealth.notifyError(DeviceListener.DEVICE_DATA_ERROR,
                            ResourceManager.getResource().getString("EDataReadError"));
                    return;
                }

            } else {
                iHealth.notifyError(DeviceListener.NO_MEASURES_FOUND, ResourceManager.getResource().getString("ENoMeasuresFound"));
                return;
            }
        } catch (JSONException e) {
            Log.e(TAG, "notifyResultData(): ", e);
            iHealth.notifyError(DeviceListener.DEVICE_DATA_ERROR,
                    ResourceManager.getResource().getString("EDataReadError"));
            return;
        }

        // Creo un istanza di Misura del tipo PR
        HashMap<String,String> tmpVal = new HashMap<>();
        tmpVal.put(GWConst.EGwCode_03, lowPressure);
        tmpVal.put(GWConst.EGwCode_04, highPressure);
        tmpVal.put(GWConst.EGwCode_06, heartRate);
        tmpVal.put(GWConst.EGwCode_BATTERY, batteryLevel); // livello batteria

        measure.setTimestamp(timestamp);
        measure.setMeasures(tmpVal);
        measure.setFile(null);
        measure.setFileType(null);
        measure.setFailed(false);

        iHealth.notifyEndMeasurement(measure);
    }
}
