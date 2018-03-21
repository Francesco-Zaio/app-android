package com.ti.app.telemed.core.btdevices;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.ihealth.communication.control.Bg5lControl;
import com.ihealth.communication.control.Bg5Profile;
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

    private static final int HANDLER_BATTERY = 100;
    private static final int HANDLER_SET_UNIT = 101;
    private static final int HANDLER_END_MEASUREMENT = 102;
    private static final int HANDLER_CONNECTED = 103;
    private static final int HANDLER_DISCONNECTED = 104;
    private static final int HANDLER_ERROR = 105;

    private boolean endOperation = true;
    private int callbackId = -1;
    private Bg5lControl mBg5lControl = null;
    private Measure measure;
    private String batteryLevel="";
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
                case Bg5Profile.ACTION_BATTERY_BG:
                    msg.what = HANDLER_BATTERY;
                    sendMessage(msg);
                    break;
                case Bg5Profile.ACTION_SET_UNIT:
                    msg.what = HANDLER_SET_UNIT;
                    sendMessage(msg);
                    break;
                case Bg5Profile.ACTION_ONLINE_RESULT_BG:
                    msg.what = HANDLER_END_MEASUREMENT;
                    sendMessage(msg);
                    break;
                case Bg5Profile.ACTION_ERROR_BG:
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
        Log.d(TAG, "startMeasure: deviceMac=" + mac + " - mBg5lControl=" + mBg5lControl);
        if (iHealth == null) {
            Log.e(TAG, "startMeasure: iHealth is NULL!");
            return;
        }

        measure.setBtAddress(mac);
        endOperation = false;

        callbackId = iHealthDevicesManager.getInstance().registerClientCallback(mIHealthDevicesCallback);
     /* Limited wants to receive notification specified device */
        iHealthDevicesManager.getInstance().addCallbackFilterForDeviceType(callbackId, iHealthDevicesManager.TYPE_BG5l);

        /* Get controller */
        mBg5lControl = iHealthDevicesManager.getInstance().getBG5lControl(mac.replace(":", ""));
        mBg5lControl.getBattery();
    }

    @Override
    public synchronized void stop() {
        if (mBg5lControl != null)
            mBg5lControl.disconnect();
        mBg5lControl = null;
    }

    @Override
    public String getStartMeasureMessage() {
        return ResourceManager.getResource().getString("KMsgConfiguring");
    }

    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);
        switch (msg.what) {
            case HANDLER_BATTERY:
                String messageBattery = msg.getData().getString("message");
                try {
                    JSONObject reader = new JSONObject(messageBattery);
                    batteryLevel = reader.getString(Bg5Profile.BATTERY_BG);
                } catch (JSONException e) {
                    iHealth.notifyError(DeviceListener.DEVICE_DATA_ERROR,
                            ResourceManager.getResource().getString("EDataReadError"));
                    Log.e(TAG, "HANDLER_BATTERY", e);
                    break;
                }
                iHealth.scheduleTimer();
                mBg5lControl.setUnit(2);
                break;
            case HANDLER_SET_UNIT:
                iHealth.resetTimer();
                iHealth.notifyToUi(ResourceManager.getResource().getString("DoMeasureGL"));
                mBg5lControl.startMeasure(1);
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
            value = jsonObject.getInt(Bg5Profile.ONLINE_RESULT_BG);
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
        tmpVal.put(GWConst.EGwCode_BATTERY, batteryLevel); // livello batteria
        measure.setMeasures(tmpVal);
        iHealth.notifyEndMeasurement(measure);
    }
}
