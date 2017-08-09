package com.ti.app.mydoctor.devicemodule;

import android.bluetooth.BluetoothAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.ti.app.mydoctor.AppResourceManager;
import com.ti.app.telemed.core.btdevices.EcgProtocol;
import com.ti.app.telemed.core.btdevices.ForaThermometerClient;
import com.ti.app.telemed.core.btdevices.IHealth;
import com.ti.app.telemed.core.btdevices.NoninOximeter;
import com.ti.app.telemed.core.btdevices.RocheProthrombineTimeClient;
import com.ti.app.telemed.core.btdevices.MIRSpirodoc;
import com.ti.app.telemed.core.btmodule.events.BTSearcherEventListener;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.mydoctor.gui.DeviceScanActivity;
import com.ti.app.mydoctor.util.AppConst;
import com.ti.app.mydoctor.util.AppUtil;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

import java.util.List;

public class DeviceManager implements DeviceListener {

	private static final String TAG = "DeviceManager";

    private UserDevice currentDevice;
	private User currentUser;
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
    public static final int REFRESH_LIST = 10;

    public static final String MEASURE = "MEASURE";
	
	private boolean operationRunning;
	
	public DeviceManager() {		
	}
	
	public void setHandler(Handler handler){
		this.handler = handler;
	}

	public void startDiscovery(DeviceScanActivity listener){
		if(!operationRunning){
	        pairingMode = true;
			setConfig(false);
	        setBtSearcherListener(listener);
	        startOperation();	
		} else {
			Log.i(TAG, "Operation already RUNNING");
		}
	}
	
	public void startMeasure() {		
		if(!operationRunning){
            pairingMode = AppUtil.isEmptyString(currentDevice.getBtAddress());
			setConfig(false);
			startOperation();
		} else {
			Log.i(TAG, "Operation already RUNNING");
		}
	}
	
	public void startConfig() {	
		if(!operationRunning){
            pairingMode = false;
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
				if (pairingMode || (currentUser != null)) {
					try {
						operationRunning = true;
						executeOp();
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

	private void executeOp() throws Exception {

        User u = UserManager.getUserManager().getCurrentUser();
        Measure m = new Measure();
        m.setMeasureType(currentDevice.getDevice().getMeasure());
        m.setStandardProtocol(true);
        m.setDeviceDesc(currentDevice.getDevice().getDescription());
        m.setTimestamp(XmlManager.getXmlManager().getTimestamp(null));
        m.setFile(null);
        m.setFileType(null);
		if (u != null) {
			m.setIdUser(u.getId());
			if (u.getIsPatient())
				m.setIdPatient(u.getId());
		}

		switch (currentDevice.getDevice().getModel()) {
			case GWConst.KPO3IHealth:
				currentDeviceHandler = new IHealth(this, m, currentDevice.getDevice().getModel());
				startMeasure(AppResourceManager.getResource().getString("KInitMsgOS"));
				break;
            case GWConst.KBP5IHealth:
            case GWConst.KBP550BTIHealth:
                currentDeviceHandler = new IHealth(this, m, currentDevice.getDevice().getModel());
				startMeasure(AppResourceManager.getResource().getString("KInitMsgPR"));
				break;
			case GWConst.KHS4SIHealth:
				currentDeviceHandler = new IHealth(this, m, currentDevice.getDevice().getModel());
				startMeasure(AppResourceManager.getResource().getString("KInitMsgPS"));
				break;
			case GWConst.KEcgMicro:
				currentDeviceHandler = new EcgProtocol(this, m);
				startMeasure(AppResourceManager.getResource().getString("KInitMsgECG"));
				break;
			case GWConst.KCcxsRoche:
				currentDeviceHandler = new RocheProthrombineTimeClient(this, m);
                startMeasure(AppResourceManager.getResource().getString("KInitMsgPT"));
				break;
            case GWConst.KFORATherm:
				currentDeviceHandler = new ForaThermometerClient(this, m);
				startMeasure(AppResourceManager.getResource().getString("KInitMsgTC"));
				break;
			case GWConst.KSpirodocOS:
				if (isConfig()) {
					currentDeviceHandler = new MIRSpirodoc(this, m, 1, GWConst.KSpirodocOS);
					notifyToUi(AppResourceManager.getResource().getString("KInitMsgConfOxy"));
                    currentDeviceHandler.start(currentDevice.getBtAddress(), pairingMode);
				} else {
					if (pairingMode) {
						currentDeviceHandler = new MIRSpirodoc(this, m, 0, GWConst.KSpirodocOS);
                        currentDeviceHandler.start(btSearcherListener, pairingMode);
					} else {
						currentDeviceHandler = new MIRSpirodoc(this, m, 3, GWConst.KSpirodocOS);
						startMeasure(AppResourceManager.getResource().getString("KInitMsgOS"));
					}
				}
				break;
			case GWConst.KSpirodocSP:
				if (isConfig()) {
					currentDeviceHandler = new MIRSpirodoc(this, m, 1, GWConst.KSpirodocSP);
					notifyToUi(AppResourceManager.getResource().getString("KInitMsgConfSpiro"));
                    currentDeviceHandler.start(currentDevice.getBtAddress(), pairingMode);
				} else {
					if (pairingMode) {
						currentDeviceHandler = new MIRSpirodoc(this, m, 0, GWConst.KSpirodocSP);
                        currentDeviceHandler.start(btSearcherListener, pairingMode);
					} else {
						currentDeviceHandler = new MIRSpirodoc(this, m, 2, GWConst.KSpirodocSP);
						startMeasure(AppResourceManager.getResource().getString("KInitMsgSP"));
					}
				}
				break;
            case GWConst.KOximeterNon:
                currentDeviceHandler = new NoninOximeter(this, m);
                startMeasure(AppResourceManager.getResource().getString("KInitMsgOS"));
                break;
		}
	}

	private boolean isConfig() {
		return isConfig;
	}

    private void setConfig(boolean isConfig) {
		this.isConfig = isConfig;
	}

	private void startMeasure(String msg) throws Exception {
		Log.d(TAG, "startMeasure " + msg);
		if (currentDevice.getBtAddress()!= null && 
				currentDevice.getBtAddress().length() > 0) {
			notifyToUi(msg);
			
			Log.d(TAG, "search by address: " + currentDevice.getBtAddress());
		    // search by address			
			currentDeviceHandler.start(currentDevice.getBtAddress(), pairingMode);
		} else {
			Log.d(TAG, "search by user");
		    // search by user
			currentDeviceHandler.start(btSearcherListener, pairingMode);
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
			showError(AppResourceManager.getResource().getString("EGWNurseBtSearchError"));
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
    public void configReady(String msg) {
        Log.i(TAG, "configReady: "+ msg);
        sendMessageToHandler(msg, CONFIG_READY, GWConst.MESSAGE);
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
    public void operationCompleted() {
		Log.i(TAG, "operationCompleted");
		operationRunning = false;
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

	public void finalizeMeasure() {

		Log.d(TAG, "finalizeMeasure()");
		try {
			currentDeviceHandler.stop();
		} catch (Exception e) {
			showError(AppResourceManager.getResource().getString(
			"EGWNurseBtDeviceDisconnError"));
		}
	}
}
