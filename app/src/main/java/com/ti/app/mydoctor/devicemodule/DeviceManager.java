package com.ti.app.mydoctor.devicemodule;

import java.lang.reflect.Method;

import android.bluetooth.BluetoothAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.ti.app.mydoctor.AppResourceManager;
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
    public static final int START_ECG_DRAW = 8;

    public static final int REFRESH_LIST = 10;

    public static final String MEASURE = "MEASURE";
	
	private boolean operationRunning;
	
	public DeviceManager() {		
	}

	// usa la reflection per invocare il metodo statico needPairing della classe che gestisce il device
    public boolean needPairing(UserDevice ud) {
        try {
            Class<?> c = Class.forName("com.ti.app.telemed.core.btdevices." + ud.getDevice().getClassName());
            Method m = c.getMethod("needPairing", UserDevice.class);
            return (boolean) m.invoke(null, ud);
        } catch (ClassNotFoundException e) {
            Log.e(TAG,"Class Not Found!! : " + ud.getDevice().getClassName());
            return false;
        } catch (NoSuchMethodException e) {
            Log.e(TAG,"Method Not Found!! : " + ud.getDevice().getClassName());
            return false;
        } catch (Exception e) {
            Log.e(TAG,"Method Invocation Exception! : " + ud.getDevice().getClassName());
            return false;
        }
    }

    // usa la reflection per invocare il metodo statico needCfg della classe che gestisce il device
    public boolean needCfg(UserDevice ud) {
        try {
            Class<?> c = Class.forName("com.ti.app.telemed.core.btdevices." + ud.getDevice().getClassName());
            Method m = c.getMethod("needConfig", UserDevice.class);
            return (boolean) m.invoke(null, ud);
        } catch (ClassNotFoundException e) {
            Log.e(TAG,"Class Not Found!! : " + ud.getDevice().getClassName());
            return false;
        } catch (NoSuchMethodException e) {
            Log.e(TAG,"Method Not Found!! : " + ud.getDevice().getClassName());
            return false;
        } catch (Exception e) {
            Log.e(TAG,"Method Invocation Exception! : " + ud.getDevice().getClassName());
            return false;
        }
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

        Class<?> c = Class.forName("com.ti.app.telemed.core.btdevices." + currentDevice.getDevice().getClassName());
        currentDeviceHandler = (DeviceHandler) c.getDeclaredConstructor(DeviceListener.class).newInstance(this);

        DeviceHandler.OperationType op;
        if (isConfig)
            op = DeviceHandler.OperationType.Config;
        else if (pairingMode)
            op = DeviceHandler.OperationType.Pair;
        else
            op = DeviceHandler.OperationType.Measure;

        currentDeviceHandler.start(op, currentDevice, btSearcherListener);
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

    // DeviceListener methods
    @Override
    public void setBtMAC(String aMac) {
        Log.i(TAG, "setBtMac: " + aMac);
        currentDevice.setBtAddress(aMac);
        DbManager.getDbManager().updateBtAddressDevice(currentDevice);
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

    @Override
    public void startEcgDraw() {
        Message message = handler.obtainMessage(START_ECG_DRAW);
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
