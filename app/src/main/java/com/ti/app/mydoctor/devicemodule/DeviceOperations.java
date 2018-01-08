package com.ti.app.mydoctor.devicemodule;

import java.lang.reflect.Method;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.ti.app.mydoctor.AppResourceManager;
import com.ti.app.telemed.core.btmodule.BTSearcherEventListener;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.common.Device;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.mydoctor.gui.DeviceScanActivity;
import com.ti.app.mydoctor.util.AppConst;
import com.ti.app.telemed.core.devicemodule.DeviceManager;
import com.ti.app.telemed.core.measuremodule.MeasureManager;
import com.ti.app.telemed.core.usermodule.UserManager;


public class DeviceOperations implements DeviceListener {

	private static final String TAG = "DeviceOperations";

    private UserDevice currentDevice;
	private DeviceHandler currentDeviceHandler;
	private BTSearcherEventListener btSearcherListener;
	
	private boolean pairingMode;
	private boolean isConfig;
	
	private Handler handler;
	public static final int MESSAGE_STATE = 1;
	public static final int MESSAGE_STATE_WAIT = 2;
	public static final int ERROR_STATE = 3;
	public static final int MEASURE_RESULT = 4;
	public static final int CONFIG_READY = 5;
	public static final int ASK_SOMETHING = 7;
    public static final int START_ECG_DRAW = 8;
    public static final int START_ACTIVITY = 9;

	private boolean operationRunning;
	
	public DeviceOperations() {
	}

    public boolean needPairing(UserDevice ud) {
        return DeviceHandler.needPairing(ud);
    }

    public boolean needCfg(UserDevice ud) {
	    return DeviceHandler.needConfig(ud);
    }


	public void setHandler(Handler handler){
		this.handler = handler;
	}

	public void setPairingMode(Boolean pairingMode) {
        this.pairingMode = pairingMode;
    }

	public void startDiscovery(DeviceScanActivity listener){
		if(!operationRunning){
			isConfig = false;
	        setBtSearcherListener(listener);
	        startOperation();	
		} else {
			Log.i(TAG, "Operation already RUNNING");
		}
	}
	
	public void startMeasure() {		
		if(!operationRunning){
			isConfig = false;
			startOperation();
		} else {
			Log.i(TAG, "Operation already RUNNING");
		}
	}
	
	public void startConfig() {	
		if(!operationRunning){
            pairingMode = false;
			isConfig = true;
			startOperation();
		} else {
			Log.i(TAG, "Operation already RUNNING");
		}
	}
	
	private void startOperation() {
		Log.i(TAG, "DeviceOperations: startOperation");
		if (currentDevice.getDevice().getDevType() != Device.DevType.BT || BluetoothAdapter.getDefaultAdapter().isEnabled()) {
			if (currentDevice != null) {
                User currentUser = UserManager.getUserManager().getCurrentUser();
				if (pairingMode || (currentUser != null && !currentUser.isDefaultUser())) {
					try {
						if (!executeOp())
                            showError(AppResourceManager.getResource().getString("KNoMesurement"));
						else
                            operationRunning = true;
					} catch (Exception e) {
						showError(AppResourceManager.getResource().getString("EGWNursePairingError"));
					}
				} else {
					showError(AppResourceManager.getResource().getString("KNoPatientSelected"));
				}
			} else {
				showError(AppResourceManager.getResource().getString("KNoCurrentDevice"));
			}
		} else {
			showError(AppResourceManager.getResource().getString("KNoBluetooth"));
		}
	}

	private boolean executeOp() throws Exception {
        currentDeviceHandler = DeviceHandler.getInstance(this,currentDevice);
        DeviceHandler.OperationType op;
        boolean result;
        String btAddr = currentDevice.getBtAddress();
        if (isConfig) {
            op = DeviceHandler.OperationType.Config;
            if (btAddr != null && !btAddr.isEmpty())
                result = currentDeviceHandler.startOperation(op, null);
            else
                result = currentDeviceHandler.startOperation(op, btSearcherListener);
        } else if (pairingMode) {
            op = DeviceHandler.OperationType.Pair;
            result = currentDeviceHandler.startOperation(op, btSearcherListener);
        } else {
            op = DeviceHandler.OperationType.Measure;
            if (btAddr != null && !btAddr.isEmpty())
                result = currentDeviceHandler.startOperation(op, null);
            else
                result = currentDeviceHandler.startOperation(op, btSearcherListener);
        }
        if (result)
            notifyToUi(AppResourceManager.getResource().getString("KSearchingDev"));
        return result;
	}

	public void abortOperation() {
        operationRunning = false;
        currentDeviceHandler.stopOperation();
    }

    public void selectDevice(BluetoothDevice bd) {
        currentDeviceHandler.selectDevice(bd);
    }

    public UserDevice getCurrentDevice() {
		return currentDevice;
	}

	public void setCurrentDevice(UserDevice currentDevice) {
		this.currentDevice = currentDevice;
		if(currentDevice == null){
    		operationRunning = false;
    	}
	}

    // DeviceListener methods
    @Override
    public void setBtMAC(String aMac) {
        Log.i(TAG, "setBtMac: " + aMac);
        currentDevice.setBtAddress(aMac);
        DeviceManager.getDeviceManager().updateBtAddressDevice(currentDevice);
    }

    @Override
    public void showMeasurementResults(Measure m) {
        Message message = handler.obtainMessage(MEASURE_RESULT);
        Bundle bundle = new Bundle();
        message.obj = m;
        message.setData(bundle);
        handler.sendMessage(message);
    }

    @Override
    public void configReady(String msg) {
        Log.i(TAG, "configReady: "+ msg);
        sendMessageToHandler(msg, CONFIG_READY, AppConst.MESSAGE);
        operationRunning = false;
    }

    @Override
    public void notifyError(String errorCode, String errorMessage) {
        Log.e(TAG, "notifyError: " + errorCode + " - " + errorMessage);
		String msg = "";
		if ((errorCode != null) && !errorCode.isEmpty())
			msg = errorCode;
		if ((errorMessage != null) && !errorMessage.isEmpty()) {
            if (!msg.isEmpty())
                msg = msg + " - ";
            msg = msg + errorMessage;
        }
        operationRunning = false;
        sendMessageToHandler(msg, ERROR_STATE, AppConst.MESSAGE);
    }

    @Override
	public void notifyToUi(String msg) {
		Log.i(TAG, "notifyToUi: " +msg);
		sendMessageToHandler(msg, MESSAGE_STATE, AppConst.MESSAGE);
    }

    @Override
	public void notifyWaitToUi(String msg) {
		Log.i(TAG, "notifyWaitToUi: "+msg);
		sendMessageToHandler(msg, MESSAGE_STATE_WAIT, AppConst.MESSAGE);
    }

    @Override
    public void askSomething(String messageText, String positiveText, String negativeText) {
        Message message = handler.obtainMessage(ASK_SOMETHING);
        Bundle bundle = new Bundle();
        bundle.putBoolean(AppConst.MESSAGE_CANCELLABLE, false);
        bundle.putString(AppConst.ASK_MESSAGE, messageText);
        bundle.putString(AppConst.ASK_POSITIVE, positiveText);
        bundle.putString(AppConst.ASK_NEGATIVE, negativeText);
        message.setData(bundle);
        handler.sendMessage(message);
    }

    @Override
    public void startEcgDraw() {
        Message message = handler.obtainMessage(START_ECG_DRAW);
        handler.sendMessage(message);
    }
    @Override
    public void startActivity(Intent intent){
        Message message = handler.obtainMessage(START_ACTIVITY);
        message.obj = intent;
        handler.sendMessage(message);
    }

    public void confirmDialog() {
        currentDeviceHandler.confirmDialog();
    }

    public void cancelDialog() {
        currentDeviceHandler.cancelDialog();
    }


    private void showError(String msg) {
        Log.e(TAG, "showError: " +msg);
        operationRunning = false;
        sendMessageToHandler(msg, ERROR_STATE, AppConst.MESSAGE);
    }

	private void sendMessageToHandler(String msgText, int messageType, String messageKey) {
		Message message = handler.obtainMessage(messageType);
        Bundle bundle = new Bundle();
        bundle.putString(messageKey, msgText);
        if(messageType == MESSAGE_STATE_WAIT){
        	bundle.putBoolean(AppConst.MESSAGE_CANCELLABLE, false);
        } else {
        	bundle.putBoolean(AppConst.MESSAGE_CANCELLABLE, true);
        }
        message.setData(bundle);
        handler.sendMessage(message);
	}
	
	private void setBtSearcherListener(BTSearcherEventListener btSearcherListener) {
		this.btSearcherListener = btSearcherListener;
	}

	public boolean isOperationRunning() {
		return operationRunning;
	}
}
