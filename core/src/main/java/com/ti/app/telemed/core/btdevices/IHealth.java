package com.ti.app.telemed.core.btdevices;

import java.lang.ref.WeakReference;
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
import com.ti.app.telemed.core.btmodule.BTSearcherEventListener;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.Patient;
import com.ti.app.telemed.core.common.UserDevice;

import com.ihealth.communication.manager.iHealthDevicesCallback;
import com.ihealth.communication.manager.iHealthDevicesManager;


public class IHealth extends DeviceHandler {

    private static final String TAG = "IHealth";
    // Timeout for device operations
    private static final int KTimeOut = 25 * 1000; // milliseconds (20 sec)

    private String deviceModel;
    private Vector<BluetoothDevice> deviceList;
    private Vector<String> deviceTypes;
    private int callbackId = -1;
    private String deviceType = "";
    private IHealtDevice deviceController = null;
    private Timer timer;


    public IHealth(DeviceListener listener, UserDevice ud) {
        super(listener, ud);
        deviceList = new Vector<>();
        deviceTypes = new Vector<>();
    }

    // methods of DeviceHandler interface

    @Override
    public void confirmDialog() {
        deviceController = new IHealthBG5S(this, getMeasure(), false);
        deviceController.startMeasure(iBtDevAddr);
        deviceListener.notifyToUi(deviceController.getStartMeasureMessage());
    }

    @Override
    public void cancelDialog() {
        deviceController = new IHealthBG5S(this, getMeasure(), true);
        deviceController.startMeasure(iBtDevAddr);
        deviceListener.notifyToUi(deviceController.getStartMeasureMessage());
    }

    @Override
    public boolean startOperation(OperationType ot, BTSearcherEventListener btSearchListener) {
        if (!startInit(ot, btSearchListener))
            return false;
        try {
            deviceModel = iUserDevice.getDevice().getModel();
            Log.d(TAG, "startOperation: deviceModel=" + deviceModel + " iBtDevAddr=" + iBtDevAddr + " iCmdCode=" + iCmdCode.toString());
            // Register Callback for iHealt library operations
            if (callbackId == -1) {
                iHealthDevicesManager.getInstance().init(MyApp.getContext(), 5, 7);
                //iHealthDevicesManager.getInstance().init(MyApp.getContext());
                callbackId = iHealthDevicesManager.getInstance().registerClientCallback(mainCallbackInstance);
            }

            switch (deviceModel) {
                case IHealtDevice.KBP550BTIHealth:
                    iHealthDevicesManager.getInstance().startDiscovery(iHealthDevicesManager.DISCOVERY_BP550BT);
                    break;
                case IHealtDevice.KBP5IHealth:
                    iHealthDevicesManager.getInstance().startDiscovery(iHealthDevicesManager.DISCOVERY_BP5);
                    break;
                case IHealtDevice.KPO3IHealth:
                    iHealthDevicesManager.getInstance().startDiscovery(iHealthDevicesManager.DISCOVERY_PO3);
                    break;
                case IHealtDevice.KHS4SIHealth:
                    iHealthDevicesManager.getInstance().startDiscovery(iHealthDevicesManager.DISCOVERY_HS4S);
                    break;
                case IHealtDevice.KBG5SIHealth:
                    iHealthDevicesManager.getInstance().startDiscovery(iHealthDevicesManager.DISCOVERY_BG5S);
                    break;
            }
            deviceListener.notifyToUi(ResourceManager.getResource().getString("KSearchingDev"));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    @Override
    public void stopOperation() {
        Log.d(TAG, "stopOperation");
        stop();
    }

    @Override
    public void selectDevice(BluetoothDevice bd){
        Log.d(TAG, "selectDevice: addr=" + bd.getAddress());
        iBtDevAddr = bd.getAddress();
        deviceType = deviceTypes.elementAt(deviceList.indexOf(bd));
        devOpHandler.sendEmptyMessage(HANDLER_DEVICE_SELECTED);
    }


    private void stop() {
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

        reset();
    }

    private void reset() {
        Log.d(TAG, "reset");
        iState = TState.EWaitingToGetDevice;
        iUserDevice = null;
        deviceList.clear();
        deviceTypes.clear();
    }

    Patient getPatient() {
        return patient;
    }

    void notifyToUi(String message) {
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
            String s = macConvert(mac);
            switch (iCmdCode) {
                case ECmdConnByUser:
                    if (iBTSearchListener != null) {
                        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(s);
                        if (device != null) {
                            deviceList.add(device);
                            deviceTypes.add(devType);
                        }
                        devOpHandler.sendEmptyMessage(HANDLER_DEVICE_DISCOVERED);
                    }
                    break;
                case ECmdConnByAddr:
                    if (s.equals(iBtDevAddr))  {
                        deviceType = devType;
                        devOpHandler.sendEmptyMessage(HANDLER_DEVICE_SELECTED);
                    }
                    break;
            }
        }

        @Override
        public void onScanFinish() {
            Log.d(TAG, "iHealthDevicesCallback:onScanFinish");
            if (iCmdCode == TCmd.ECmdConnByUser && iBTSearchListener != null)
                iBTSearchListener.deviceSearchCompleted();
            devOpHandler.sendEmptyMessage(HANDLER_DISCOVERY_FINISH);
        }

        @Override
        public void onUserStatus(String username, int userStatus) {
            Log.d(TAG, "iHealthDevicesCallback:onUserStatus ++ " + "Username=" + username + " - UserStatus=" + userStatus);
            Bundle bundle = new Bundle();
            bundle.putInt("userstatus", userStatus);
            Message msg = devOpHandler.obtainMessage(HANDLER_USER_STATUS);
            msg.setData(bundle);
            devOpHandler.sendMessage(msg);
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
                    devOpHandler.sendMessage(msg);
                    break;
                case iHealthDevicesManager.DEVICE_STATE_DISCONNECTED:
                    msg.what = HANDLER_DISCONNECTED;
                    devOpHandler.sendMessage(msg);
                    break;
                case iHealthDevicesManager.DEVICE_STATE_CONNECTIONFAIL:
                    devOpHandler.sendEmptyMessage(HANDLER_ERROR);
                    break;
            }
        }
    };

    private static final int HANDLER_DEVICE_DISCOVERED =100;
    private static final int HANDLER_DEVICE_SELECTED =101;
    private static final int HANDLER_USER_STATUS = 102;
    private static final int HANDLER_CONNECTED = 103;
    private static final int HANDLER_DISCONNECTED = 104;
    private static final int HANDLER_ERROR = 105;
    private static final int HANDLER_DISCOVERY_FINISH = 106;

    private final MyHandler devOpHandler = new MyHandler(this);

    private static class MyHandler extends Handler {
        private final WeakReference<IHealth> mOuter;

        private MyHandler(IHealth outer) {
            mOuter = new WeakReference<>(outer);
        }

        @Override
        public void handleMessage(Message msg) {
            IHealth outer = mOuter.get();
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_DEVICE_DISCOVERED:
                    outer.iBTSearchListener.deviceDiscovered(outer.deviceList);
                    break;
                case HANDLER_DEVICE_SELECTED:
                    String mac = outer.iBtDevAddr.replace(":", "");
                    Log.d(TAG, "iHealthDevicesManager.getInstance().connectDevice: " + "MAC=" + mac);
                    boolean req = iHealthDevicesManager.getInstance().connectDevice("", mac, outer.deviceType);
                    if (!req) {
                        outer.resetTimer();
                        Log.e(TAG, "iHealthDevicesManager.getInstance().connectDevice: ERROR");
                        String message = ResourceManager.getResource().getString("EBtDeviceConnError");
                        outer.deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, message);
                        outer.stop();
                        break;
                    }
                    outer.iState = TState.EGettingConnection;
                    outer.scheduleTimer();
                    outer.deviceListener.notifyToUi(ResourceManager.getResource().getString("KConnectingDev"));
                    break;
                case HANDLER_CONNECTED:
                    outer.deviceListener.setBtMAC(outer.iBtDevAddr);
                    if (outer.operationType == OperationType.Pair) {
                        outer.iState = TState.EDisconnecting;
                        iHealthDevicesManager.getInstance().disconnectDevice(outer.iBtDevAddr.replace(":", ""), outer.deviceType);
                    } else {
                        outer.iState = TState.EConnected;
                        outer.startMeasure();
                    }
                    break;
                case HANDLER_DISCONNECTED:
                    if (outer.operationType == OperationType.Pair) {
                        outer.deviceListener.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
                        outer.stop();
                    }
                    break;
                case HANDLER_DISCOVERY_FINISH:
                    if (outer.iState == TState.EGettingDevice && outer.iCmdCode == TCmd.ECmdConnByAddr) {
                        // Bluetooth discovery finished without finding the device
                        String message = ResourceManager.getResource().getString("EDeviceNotFound");
                        outer.deviceListener.notifyError(DeviceListener.DEVICE_NOT_FOUND_ERROR, message);
                        outer.stop();
                    }
                    break;
                case HANDLER_ERROR:
                    String message = ResourceManager.getResource().getString("ECommunicationError");
                    outer.deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR, message);
                    outer.stop();
                    break;
            }
        }
    }

    private void startMeasure()  {
        switch (deviceModel) {
            case IHealtDevice.KPO3IHealth:
                deviceController = new IHealthPO3(this, getMeasure());
                break;
            case IHealtDevice.KBP5IHealth:
                deviceController = new IHealthBP5(this, getMeasure());
                break;
            case IHealtDevice.KHS4SIHealth:
                deviceController = new IHealthHS4S(this, getMeasure());
                break;
            case IHealtDevice.KBP550BTIHealth:
                deviceController = new IHealthBP550BT(this, getMeasure());
                break;
            case IHealtDevice.KBG5SIHealth:
                String message = ResourceManager.getResource().getString("KPrePostMsg").concat("\n\n");
                deviceListener.askSomething(message,
                        ResourceManager.getResource().getString("MeasureGlyPOSTBtn"),
                        ResourceManager.getResource().getString("MeasureGlyPREBtn"));
                return;
        }
        deviceController.startMeasure(iBtDevAddr);
        deviceListener.notifyToUi(deviceController.getStartMeasureMessage());
    }

    private class TimerExpired extends TimerTask {
        @Override
        public void run(){
            Log.d(TAG, "Timer scaduto");
            devOpHandler.sendEmptyMessage(HANDLER_ERROR);
        }
    }

    void scheduleTimer() {
        resetTimer();
        TimerExpired timerExpired = new TimerExpired();
        timer = new Timer();
        timer.schedule(timerExpired, KTimeOut);
    }

    void resetTimer() {
        if (timer!=null) {
            timer.cancel();
        }
    }
}