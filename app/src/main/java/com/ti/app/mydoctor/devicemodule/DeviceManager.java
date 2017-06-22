package com.ti.app.mydoctor.devicemodule;

import android.bluetooth.BluetoothAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btdevices.IHealth;
import com.ti.app.telemed.core.btmodule.BTSearcherEventListener;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.mydoctor.gui.DeviceScanActivity;
import com.ti.app.mydoctor.util.GWConst;
import com.ti.app.mydoctor.util.Util;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

import java.util.List;

public class DeviceManager implements DeviceListener {

	private static final String TAG = "DeviceManager";

    private UserDevice currentDevice;
	private User currentUser;
	private DeviceHandler currentDeviceHandler;
	private BTSearcherEventListener btSearcherListener;
    private Measure m;
	
	private boolean pairingModeOn;
	private boolean isConfig;
	
	private Handler handler;
	public static final int MESSAGE_STATE = 1;
	public static final int MESSAGE_STATE_WAIT = 2;
	public static final int ERROR_STATE = 3;
	public static final int MEASURE_RESULT = 4;

	public static final int ASK_SOMETHING = 7;
	public static final int SEND_ALL = 8;
	public static final int STOP_BACKGROUND = 9;

	
	public static final String RESULT_TYPE = "RESULT_TYPE";
    public static final String MEASURE = "MEASURE";
	
	private boolean operationRunning;
	
	public DeviceManager() {		
	}
	
	public void setHandler(Handler handler){
		this.handler = handler;
	}

	public void startDiscovery(DeviceScanActivity listener){
		if(!operationRunning){
	        pairingModeOn = true;
			setConfig(false);
	        setBtSearcherListener(listener);
	        startOperation();	
		} else {
			Log.i(TAG, "Operation already RUNNING");
		}
	}
	
	public void startMeasure() {		
		if(!operationRunning){
			if(isGlucoTelDevicePairing()){
                pairingModeOn = true;
			} else {
                pairingModeOn = false;
			}
			setConfig(false);
			startOperation();
		} else {
			Log.i(TAG, "Operation already RUNNING");
		}
	}
	
	public void startConfig() {	
		if(!operationRunning){
            pairingModeOn = false;
			setConfig(true);
			startOperation();
		} else {
			Log.i(TAG, "Operation already RUNNING");
		}
	}
	
	public void startAlternativeConfig() {	
		if(!operationRunning){
            pairingModeOn = false;
			setConfig(true);
			startOperation();
		} else {
			Log.i(TAG, "Operation already RUNNING");
		}
	}
	
	private void startOperation() {
		Log.i(TAG, "DeviceManager: startOperation");
		if (BluetoothAdapter.getDefaultAdapter().isEnabled()) {
			if (currentDevice != null) {
				if (isPairingModeOn() || (currentUser != null)) {
					try {
						operationRunning = true;
						executeOp();
					} catch (Exception e) {
						showError(ResourceManager.getResource().getString("EGWNursePairingError"));
					}
				} else {
					showError(ResourceManager.getResource().getString("KNoPatientSelected"));
				}
			} else {
				showError(ResourceManager.getResource().getString("KNoCurrentDevice"));
			}
		} else {
			showError(ResourceManager.getResource().getString("KNoBluetooth"));
		}
	}
	
	public boolean isPairingModeOn() {
		return pairingModeOn;
	}

	private void executeOp() throws Exception {
		switch (currentDevice.getDevice().getModel()) {
			case GWConst.KPO3IHealth:
            case GWConst.KBP5IHealth:
            case GWConst.KHS4SIHealth:
            case GWConst.KBP550BTIHealth:


                m = new Measure();
                m.setMeasureType(currentDevice.getDevice().getMeasure());
                m.setStandardProtocol(true);
                m.setDeviceDesc(currentDevice.getDevice().getDescription());
                m.setTimestamp(XmlManager.getXmlManager().getTimestamp(null));
                m.setFile(null);
                m.setFileType(null);
                m.setIdUser(UserManager.getUserManager().getCurrentUser().getId());
                m.setIdPatient(UserManager.getUserManager().getCurrentUser().getId());
                currentDeviceHandler = new IHealth(this, m, currentDevice.getDevice().getMeasure());
				startMeasure(ResourceManager.getResource().getString("KInitMsgOxiNonin"));
				break;
		}
	}

	public boolean isConfig() {
		return isConfig;
	}		

	public void setConfig(boolean isConfig) {
		this.isConfig = isConfig;
	}

	private void startPairing() throws Exception {
		currentDeviceHandler.start(btSearcherListener);
	}
	
	private void startConfiguration() throws Exception {
		currentDeviceHandler.start(currentDevice.getBtAddress());
	}

	private void startMeasure(String msg) throws Exception {
		Log.d(TAG, "startMeasure " + msg);
		if (currentDevice.getBtAddress()!= null && 
				currentDevice.getBtAddress().length() > 0) {
			notifyToUi(msg);
			
			Log.d(TAG, "search by address: " + currentDevice.getBtAddress());
		    // search by address			
			currentDeviceHandler.start(currentDevice.getBtAddress());
		} else {
			Log.d(TAG, "search by user");
		    // search by user
			currentDeviceHandler.start(btSearcherListener);
		}	
	}

	public void stopDeviceOperation(int selected) {
    	try {
    		// Se selected è > 0 è terminata la fase di search device ma deve
    		// partire la misura, quindi operationRunning deve restare true
        	if(selected < 0){
        		operationRunning = false;
        	}
			currentDeviceHandler.stopDeviceOperation(selected);
		} catch (Exception e) {
			showError(ResourceManager.getResource().getString("EGWNurseBtSearchError"));
		}
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

	public User getCurrentUser() {
		return currentUser;
	}

	public void setCurrentUser(User currentUser) {
		this.currentUser = currentUser;
	}

	public boolean checkIfAnotherSpirodocIsPaired(String kspirodoc) {
		
		Log.d(TAG, "checkIfAnotherSpirodocIsPaired() " + kspirodoc + "=" + currentDevice.getBtAddress());
		boolean result = false;
		try {
			
			if (currentDevice.getBtAddress() == null) {
			
				if (kspirodoc.equals(GWConst.KSpirodocOS)) {
					
					List<UserDevice> udl = DbManager.getDbManager().getCurrentUserDevices();
					for (UserDevice userDevice : udl) {
						if (userDevice.getDevice().getModel().equals(GWConst.KSpirodocSP)) {
							if (userDevice.isActive() && userDevice.getBtAddress() != null) {
								
								Log.d(TAG, "copy=" + userDevice.getBtAddress());
								
								currentDevice.setBtAddress(userDevice.getBtAddress());
								DbManager.getDbManager().updateBtAddressDevice(currentDevice);
								result = true;
							}
						}
					}
					
				} else if (kspirodoc.equals(GWConst.KSpirodocSP)) {
										
					List<UserDevice> udl = DbManager.getDbManager().getCurrentUserDevices();
					for (UserDevice userDevice : udl) {
						if (userDevice.getDevice().getModel().equals(GWConst.KSpirodocOS)) {
							if (userDevice.isActive() && userDevice.getBtAddress() != null) {
								
								Log.d(TAG, "copy=" + userDevice.getBtAddress());
								
								currentDevice.setBtAddress(userDevice.getBtAddress());
								DbManager.getDbManager().updateBtAddressDevice(currentDevice);
								result = true;
							}
						}
					}
				}
			}
		}
		catch(Exception e) {
			Log.e(TAG, "checkIfAnotherSpirodocIsPaired() " + e);
			result = false;
		}
		
		Log.d(TAG, "checkIfAnotherSpirodocIsPaired() result=" + result);
		
		return result;
	}

	private void cleanBtAddressSpirodoc() {
		
		Log.d(TAG, "cleanBtAddressSpirodoc()");
		try {
			if (currentDevice.getDevice().getModel().equals(GWConst.KSpirodocOS)) {
			
				List<UserDevice> udl = DbManager.getDbManager().getCurrentUserDevices();
				for (UserDevice userDevice : udl) {
					if (userDevice.getDevice().getModel().equals(GWConst.KSpirodocSP)) {
						if (userDevice.isActive()) {
							
							Log.d(TAG, "clean=" + userDevice.getDevice().getModel());
							DbManager.getDbManager().cleanBtAddressDevice(userDevice);
						}
					}
				}
			} else if (currentDevice.getDevice().getModel().equals(GWConst.KSpirodocSP)) {
			
				List<UserDevice> udl = DbManager.getDbManager().getCurrentUserDevices();
				for (UserDevice userDevice : udl) {
					if (userDevice.getDevice().getModel().equals(GWConst.KSpirodocOS)) {
						if (userDevice.isActive()) {
							
							Log.d(TAG, "clean=" + userDevice.getDevice().getModel());
							DbManager.getDbManager().cleanBtAddressDevice(userDevice);
						}
					}
				}
			}
		}
		catch(Exception e) {
			Log.e(TAG, "cleanBtAddressSpirodoc() " + e);
		}
	}

    // DeviceListener methods
    @Override
    public void setBtMAC(String aMac) {
        Log.i(TAG, "setBtMac: " + aMac);
        currentDevice.setBtAddress(aMac);
        DbManager.getDbManager().updateBtAddressDevice(currentDevice);
        cleanBtAddressSpirodoc();
    }

    @Override
    public void showMeasurementResults(Measure m) {
        Message message = handler.obtainMessage(MEASURE_RESULT);
        Bundle bundle = new Bundle();
        bundle.putSerializable(MEASURE, m);
        message.setData(bundle);
        handler.sendMessage(message);
    }

    @Override
    public void notifyError(String errorCode, String errorMessage) {
        Log.e(TAG, "notifyError: " + errorCode + " - " + errorMessage);
        operationRunning = false;
        sendMessageToHandler(errorCode + " - " + errorMessage, ERROR_STATE, GWConst.MESSAGE);
    }

    @Override
    public void operationCompleted() {
		Log.i(TAG, "operationCompleted");
		operationRunning = false;
	}

    @Override
	public void notifyToUi(String msg) {
		Log.i(TAG, "notifyToUi: " +msg);
		sendMessageToHandler(msg, MESSAGE_STATE, GWConst.MESSAGE);
    }

    @Override
	public void notifyWaitToUi(String msg) {
		Log.i(TAG, "notifyWaitToUi: "+msg);
		sendMessageToHandler(msg, MESSAGE_STATE_WAIT, GWConst.MESSAGE);
    }



    public void showError(String msg) {
        Log.e(TAG, "showError: " +msg);
        operationRunning = false;
        sendMessageToHandler(msg, ERROR_STATE, GWConst.MESSAGE);
    }

	public void sendMessageToHandler(String msgText, int messageType, String messageKey) {
		Message message = handler.obtainMessage(messageType);
        Bundle bundle = new Bundle();
        bundle.putString(messageKey, msgText);
        if(messageType == MESSAGE_STATE_WAIT){
        	bundle.putBoolean(GWConst.MESSAGE_CANCELLABLE, false);
        } else {
        	bundle.putBoolean(GWConst.MESSAGE_CANCELLABLE, true);
        }
        message.setData(bundle);
        handler.sendMessage(message);
	}
	
	public void setBtSearcherListener(BTSearcherEventListener btSearcherListener) {
		this.btSearcherListener = btSearcherListener;
	}

	public boolean isOperationRunning() {
		return operationRunning;
	}
	
	private boolean isGlucoTelDevicePairing() {
		UserDevice device = getCurrentDevice();
		return Util.isGlucoTelDevice(device.getDevice()) && Util.isEmptyString(device.getBtAddress());
	}

	public void finalizeMeasure() {

		Log.d(TAG, "finalizeMeasure()");
		try {
			currentDeviceHandler.stop();
		} catch (Exception e) {
			showError(ResourceManager.getResource().getString(
			"EGWNurseBtDeviceDisconnError"));
		}
	}

	public void confirmDialog() {

		try {
			//currentDeviceHandler.getClass().getDeclaredMethod("confirmDialog").invoke(new Object[0]);
		}
		catch (Exception e) {
			Log.w(TAG, "confirmDialog=" + e);
		}

	}


	public void askSomething(String messageText, String positiveText, String negativeText) {

		Message message = handler.obtainMessage(ASK_SOMETHING);
        Bundle bundle = new Bundle();
        bundle.putBoolean(GWConst.MESSAGE_CANCELLABLE, false);
        bundle.putString(GWConst.ASK_MESSAGE, messageText);
        bundle.putString(GWConst.ASK_POSITIVE, positiveText);
        bundle.putString(GWConst.ASK_NEGATIVE, negativeText);

        message.setData(bundle);
        handler.sendMessage(message);
	}

	public void cancelDialog() {
		try {
			currentDeviceHandler.stop();
		} catch (Exception e) {
			Log.w(TAG, "cancelDialog=" + e);
		}
		//showError(ResourceManager.getResource().getString("showSettingsOperationCancelled"));
	}

	public void askSendAllMeasures(String measureType, String resultType) {
		Message message = handler.obtainMessage(SEND_ALL);
        Bundle bundle = new Bundle();
        bundle.putBoolean(GWConst.MESSAGE_CANCELLABLE, false);
        bundle.putString(GWConst.SEND_MEASURE_TYPE, measureType);

        bundle.putString(RESULT_TYPE, resultType);
        //bundle.putStringArrayList(LABELS, tmpLab);
        //bundle.putStringArrayList(VALUES, tmpVal);

        message.setData(bundle);
        handler.sendMessage(message);
	}

	public void saveAllMeasure() {

		Log.d(TAG, "saveAllMeasure()");
	}

	public void stopBackgroundOperation() {

		Message message = handler.obtainMessage(STOP_BACKGROUND);
        Bundle bundle = new Bundle();

        message.setData(bundle);
        handler.sendMessage(message);
	}

}
