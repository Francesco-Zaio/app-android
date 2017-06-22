package com.ti.app.telemed.core.btdevices;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.BTSearcherEvent;
import com.ti.app.telemed.core.btmodule.BTSearcherEventListener;
import com.ti.app.telemed.core.btmodule.DeviceHandler;

import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.util.GWConst;

import com.ihealth.communication.manager.iHealthDevicesCallback;
import com.ihealth.communication.manager.iHealthDevicesManager;


public class IHealth extends Handler implements DeviceHandler {

    private enum TState {
        EWaitingToGetDevice,    // default e notifica disconnessione
        EGettingDevice,         // chiamata di start(...)
        EGettingConnection,     // chaimata a connect()
        EConnected,             // callabck connessione avvenuta OK o fine Misura
        EGettingMeasures       // chiamata startMeasures
    }

    private String deviceModel;
    private DeviceListener deviceListener;

    private Vector<BluetoothDevice> deviceList;
    private Vector<String> deviceTypes;
    private BTSearcherEventListener scanActivityListener;
    // device iHealth PO3 doesn't require pairing
    private TCmd iCmdCode = TCmd.ECmdConnByUser;

    private TState iState;
    private int callbackId = -1;
    private String iBTAddress = "";
    private String deviceType = "";
    private IHealtDevice deviceController = null;

    private static final String TAG = "IHealth";

    // Timeout for device operations
    private static final int KTimeOut = 20 * 1000; // milliseconds (20 sec)
    private Timer timer;


    public IHealth(DeviceListener aScheduler, Measure m, String deviceModel) {
        Log.d(TAG, "IHealth");
        this.deviceModel = deviceModel;
        iState = TState.EWaitingToGetDevice;
        deviceListener = aScheduler;
        deviceList = new Vector<>();
        deviceTypes = new Vector<>();
        switch (deviceModel) {
            case GWConst.KPO3IHealth:
                deviceController = new IHealthPO3(this, m);
                break;
            case GWConst.KBP5IHealth:
                deviceController = new IHealthBP5(this, m);
                break;
            case GWConst.KHS4SIHealth:
                deviceController = new IHealthHS4S(this, m);
                break;
            case GWConst.KBP550BTHealth:
                deviceController = new IHealthBP550BT(this, m);
                break;
        }
    }

    @Override
    public void start(String btAddr) {
        Log.d(TAG, "start(String btAddr)");
        iBTAddress = btAddr;
        iCmdCode = TCmd.ECmdConnByAddr;
        if (iState == TState.EWaitingToGetDevice) {
            scanActivityListener = null;

            // Register Callback for iHealt library operations
            if (callbackId == -1) {
                iHealthDevicesManager.getInstance().init(MyApp.getContext());
                callbackId = iHealthDevicesManager.getInstance().registerClientCallback(mainCallbackInstance);
            }

            switch(deviceModel) {
                case GWConst.KBP550BTHealth:
                    iHealthDevicesManager.getInstance().startDiscovery(iHealthDevicesManager.DISCOVERY_BP550BT);
                    break;
                case GWConst.KBP5IHealth:
                    iHealthDevicesManager.getInstance().startDiscovery(iHealthDevicesManager.DISCOVERY_BP5);
                    break;
                case GWConst.KPO3IHealth:
                    iHealthDevicesManager.getInstance().startDiscovery(iHealthDevicesManager.DISCOVERY_PO3);
                    break;
                case GWConst.KHS4SIHealth:
                    iHealthDevicesManager.getInstance().startDiscovery(iHealthDevicesManager.DISCOVERY_HS4S);
                    break;
            }
            iState = TState.EGettingDevice;
        }
    }

    @Override
    public void start(BTSearcherEventListener listener) {
        Log.d(TAG, "start(BTSearcherEventListener listener)");
        iCmdCode = TCmd.ECmdConnByUser;
        if (iState == TState.EWaitingToGetDevice) {
            scanActivityListener = listener;

            // Register Callback for iHealt library operations
            if (callbackId == -1) {
                iHealthDevicesManager.getInstance().init(MyApp.getContext());
                callbackId = iHealthDevicesManager.getInstance().registerClientCallback(mainCallbackInstance);
            }

            switch(deviceModel) {
                case GWConst.KBP550BTHealth:
                    iHealthDevicesManager.getInstance().startDiscovery(iHealthDevicesManager.DISCOVERY_BP550BT);
                    break;
                case GWConst.KBP5IHealth:
                    iHealthDevicesManager.getInstance().startDiscovery(iHealthDevicesManager.DISCOVERY_BP5);
                    break;
                case GWConst.KPO3IHealth:
                    iHealthDevicesManager.getInstance().startDiscovery(iHealthDevicesManager.DISCOVERY_PO3);
                    break;
                case GWConst.KHS4SIHealth:
                    iHealthDevicesManager.getInstance().startDiscovery(iHealthDevicesManager.DISCOVERY_HS4S);
                    break;
            }
            iState = TState.EGettingDevice;
        }
    }

    @Override
    public void stop() {
        Log.d(TAG, "stop");

        resetTimer();

        if (deviceController != null)
            deviceController.stop();

        if (callbackId != -1) {
            iHealthDevicesManager.getInstance().unRegisterClientCallback(callbackId);
            callbackId = -1;
        }

        if (iState == TState.EGettingDevice)
            iHealthDevicesManager.getInstance().stopDiscovery();

        // we advise the scheduler of the end of the activity on the device
        deviceListener.operationCompleted();

        reset();
    }

    @Override
    public void stopDeviceOperation(int selected) {
        Log.d(TAG, "stopDeviceOperation: selected=" + selected);

        // selected == -1 Normal end of operation
        // selected == -2 Operation interrupted
        // selected >= the user has selected the device at the 'selected' position in the list of discovered devices
        if (selected < 0) {
            resetTimer();
            stop();
        }  else {
            iBTAddress = deviceList.elementAt(selected).getAddress();
            deviceType = deviceTypes.elementAt(selected);
            Message msg = new Message();
            msg.what = HANDLER_DEVICE_SELECTED;
            sendMessage(msg);
        }
    }

    @Override
    public void reset() {
        Log.d(TAG, "reset");
        iState = TState.EWaitingToGetDevice;
        scanActivityListener = null;
        deviceList.clear();
        deviceTypes.clear();
    }

    void notifyIncomingMeasures(String message) {
        iState = TState.EGettingMeasures;
        deviceListener.notifyToUi(message);
    }

    void notifyError(String errorCode, String errorMessage) {
        resetTimer();
        deviceListener.notifyError(errorCode, errorMessage);
        stop();
    }

    void notifyEndMeasurement(Measure m) {
        stop();
        deviceListener.showMeasurementResults(m);
    }

    private String macConvert(String mac) {
        String s = mac.toUpperCase();
        if (s.length() == 12) {
            String s1 = s.substring(0, 2);
            String s2 = s.substring(2, 4);
            String s3 = s.substring(4, 6);
            String s4 = s.substring(6, 8);
            String s5 = s.substring(8, 10);
            String s6 = s.substring(10, 12);
            s = s1 + ':' + s2 + ':' + s3 + ':' + s4 + ':' + s5 + ':' + s6;
        }
        return s;
    }

    private iHealthDevicesCallback mainCallbackInstance = new iHealthDevicesCallback() {

        @Override
        public void onScanDevice(String mac, String devType, int rssi, Map manufactorData) {
            Log.d(TAG, "iHealthDevicesCallback:onScanDevice ++ " + "Mac=" + mac + " - DevType=" + devType);
            Log.d(TAG, "iHealthDevicesCallback:onScanDevice ++ " + "manufactorData=" + manufactorData);

            String s = macConvert(mac);
            switch (iCmdCode) {
                case ECmdConnByUser:
                    if (scanActivityListener != null) {
                        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(s);
                        if (device != null) {
                            deviceList.add(device);
                            deviceTypes.add(devType);
                        }
                        scanActivityListener.deviceDiscovered(new BTSearcherEvent(this), deviceList);
                    }
                    break;
                case ECmdConnByAddr:
                    if (s.equals(iBTAddress))  {
                        deviceType = devType;
                        Message msg = new Message();
                        msg.what = HANDLER_DEVICE_SELECTED;
                        sendMessage(msg);
                    }
                    break;
            }
        }

        @Override
        public void onScanFinish() {
            Log.d(TAG, "iHealthDevicesCallback:onScanFinish");
            if (scanActivityListener != null)
                scanActivityListener.deviceSearchCompleted(new BTSearcherEvent(this));
            Message msg = new Message();
            msg.what = HANDLER_DISCOVERY_FINISH;
            sendMessage(msg);
        }

        @Override
        public void onUserStatus(String username, int userStatus) {
            Log.d(TAG, "iHealthDevicesCallback:onUserStatus ++ " + "Username=" + username + " - UserStatus=" + userStatus);
            Bundle bundle = new Bundle();
            bundle.putInt("userstatus", userStatus);
            Message msg = new Message();
            msg.what = HANDLER_USER_STATUS;
            msg.setData(bundle);
            sendMessage(msg);
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
            }
        }
    };

    private static final int HANDLER_DEVICE_SELECTED =100;
    private static final int HANDLER_USER_STATUS = 101;
    private static final int HANDLER_CONNECTED = 102;
    private static final int HANDLER_ERROR = 104;
    private static final int HANDLER_DISCOVERY_FINISH = 105;

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_DEVICE_SELECTED:
                    String mac = iBTAddress.replace(":", "");
                    Log.d(TAG, "iHealthDevicesManager.getInstance().connectDevice: " + "MAC=" + mac);
                    boolean req = iHealthDevicesManager.getInstance().connectDevice("", mac, deviceType);
                    if (!req) {
                        resetTimer();
                        Log.e(TAG, "iHealthDevicesManager.getInstance().connectDevice: ERROR");
                        String message = ResourceManager.getResource().getString("EBtDeviceConnError");
                        deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, message);
                        stop();
                        break;
                    }
                    iState = TState.EGettingConnection;
                    scheduleTimer();
                    switch (deviceModel) {
                        case GWConst.KPO3IHealth:
                            String message = ResourceManager.getResource().getString("KConnectingDev");
                            deviceListener.notifyWaitToUi(message);
                            break;
                        case GWConst.KBP5IHealth:
                            deviceListener.notifyWaitToUi(ResourceManager.getResource().getString("KConnectingDev"));
                            break;
                        default:
                            deviceListener.notifyWaitToUi(ResourceManager.getResource().getString("KConnectingDev"));
                            break;
                    }
                    break;
                case HANDLER_CONNECTED:
                    iState = TState.EConnected;
                    deviceListener.setBtMAC(iBTAddress);
                    deviceController.startMeasure(iBTAddress);
                    deviceListener.notifyToUi(deviceController.getStartMeasureMessage());
                    break;
                case HANDLER_DISCOVERY_FINISH:
                    if (iState == TState.EGettingDevice && iCmdCode == TCmd.ECmdConnByAddr) {
                        // Bluetooth discovery finished without finding the device
                        String message = ResourceManager.getResource().getString("EGWDeviceNotFound");
                        deviceListener.notifyError(DeviceListener.DEVICE_NOT_FOUND_ERROR, message);
                        stop();
                    }
                    break;
                case HANDLER_ERROR:
                    String message = ResourceManager.getResource().getString("EGWCommunicationError");
                    deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR,message);
                    stop();
                    break;
            }
        }

    private class TimerExpired extends TimerTask {
        @Override
        public void run(){
            Log.d(TAG, "Timer scaduto");
            Message msg = new Message();
            msg.what = HANDLER_ERROR;
            sendMessage(msg);
        }
    }

    void scheduleTimer() {
        Log.d(TAG, "scheduleTimer");
        resetTimer();
        TimerExpired timerExpired = new TimerExpired();
        timer = new Timer();
        timer.schedule(timerExpired, KTimeOut);
    }

    void resetTimer() {
        if (timer!=null) {
            Log.d(TAG, "resetTimer");
            timer.cancel();
        }
    }
}
