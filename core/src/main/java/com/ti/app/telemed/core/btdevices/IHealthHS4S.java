package com.ti.app.telemed.core.btdevices;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.ihealth.communication.control.Hs4sControl;
import com.ihealth.communication.control.HsProfile;
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

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.HashMap;


class IHealthHS4S extends Handler implements IHealtDevice{

    private static final String TAG = "IHealthHS4S";

    private IHealth iHealth = null;

    private static final int HANDLER_LIVE_MEASUREMENT = 100;
    private static final int HANDLER_END_MEASUREMENT = 101;
    private static final int HANDLER_CONNECTED = 102;
    private static final int HANDLER_DISCONNECTED = 103;
    private static final int HANDLER_ERROR = 104;

    private boolean firstRead = true;
    private boolean endOperation = true;
    private int callbackId = -1;
    private Hs4sControl mHs4sControl = null;
    private Measure measure;

    IHealthHS4S(IHealth iHealth, Measure m) {
        this.iHealth = iHealth;
        this.measure = m;
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
                case HsProfile.ACTION_LIVEDATA_HS:
                    // message:{"value":24.1}
                    msg.what = HANDLER_LIVE_MEASUREMENT;
                    sendMessage(msg);
                    break;
                case HsProfile.ACTION_ONLINE_RESULT_HS:
                    // message:{"value":23.200000762939453,"dataID":"F6FB1B71C7F086CF2C2E7ABCDA63083F"}
                    msg.what = HANDLER_END_MEASUREMENT;
                    sendMessage(msg);
                    break;
                case HsProfile.ACTION_ERROR_HS:
                    // message={???}
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
        Log.d(TAG, "startMeasure: deviceMac=" + mac + " - mHs4sControl=" + mHs4sControl);
        if (iHealth == null) {
            Log.e(TAG, "startMeasure: iHealth is NULL!");
            return;
        }

        measure.setBtAddress(mac);

        firstRead = true;
        endOperation = false;

        callbackId = iHealthDevicesManager.getInstance().registerClientCallback(mIHealthDevicesCallback);
     /* Limited wants to receive notification specified device */
        iHealthDevicesManager.getInstance().addCallbackFilterForDeviceType(callbackId, iHealthDevicesManager.TYPE_HS4S);

        /* Get BP5 controller */
        mHs4sControl = iHealthDevicesManager.getInstance().getHs4sControl(mac.replace(":", ""));
        mHs4sControl.measureOnline(1, 123);
    }

    @Override
    public synchronized void stop() {
        if (mHs4sControl != null)
            mHs4sControl.disconnect();
        mHs4sControl = null;
    }

    @Override
    public String getStartMeasureMessage() {
        return ResourceManager.getResource().getString("KMeasStartMsgHS4S");
    }

    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);
        switch (msg.what) {
            case HANDLER_LIVE_MEASUREMENT:
                if (mHs4sControl != null) // check if disconnect was called but not yet done
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
        String strWeight;

        try {
            JSONTokener jsonTokener = new JSONTokener(message);
            JSONObject jsonObject = (JSONObject) jsonTokener.nextValue();
            double weight = jsonObject.getDouble(HsProfile.WEIGHT_HS);
            DecimalFormat df = new DecimalFormat("#.#");
            df.setRoundingMode(RoundingMode.HALF_EVEN);
            strWeight = df.format(weight);
            strWeight = strWeight.replace ('.', ',');
        } catch (JSONException e) {
            Log.e(TAG, "notifyResultData(): ", e);
            iHealth.notifyError(DeviceListener.DEVICE_DATA_ERROR,
                    ResourceManager.getResource().getString("EGWDeviceDataError"));
            return;
        }

        // Creo un istanza di Misura del tipo PR
        HashMap<String,String> tmpVal = new HashMap<>();
        tmpVal.put(GWConst.EGwCode_01, strWeight);  // peso

        measure.setMeasures(tmpVal);
        measure.setFile(null);
        measure.setFileType(null);
        measure.setFailed(false);

        iHealth.notifyEndMeasurement(measure);
    }
}
