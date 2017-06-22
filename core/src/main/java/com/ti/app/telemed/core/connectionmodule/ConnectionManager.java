package com.ti.app.telemed.core.connectionmodule;

import java.util.logging.Level;
import java.util.logging.Logger;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;


public class ConnectionManager {
	
	private static ConnectionManager connectionManager;
	
	private Logger logger = Logger.getLogger(ConnectionManager.class.getName());
	
	private static TelephonyManager tManager;
	private static ConnectivityManager cMananger;
	
	private Handler handler;
	
	private static final String TAG = "ConnectionManager";
	
	public static final int CONNECTION_ERROR = 0;
	public static final int CONNECTION_SUCCESS = 1;
	public static final int APN_ERROR = 2;
	public static final int DATA_CONNECTION_ERROR = 3;
	public static final int OPERATION_CANCELLED = 4;
	
	private int disconnectedMsgCounter;
	private int connectingMsgCounter;
	private static final int MAX_DISCONNECTED_MSG_COUNTER = 20;
	
	private boolean operationRunning;
	
	private boolean notify;
	
	public ConnectionManager() {
	}

	public static ConnectionManager getConnectionManager(Context _context) {
		if (connectionManager == null) {
			connectionManager = new ConnectionManager();
		}
			
		tManager = (TelephonyManager) _context.getSystemService(Activity.TELEPHONY_SERVICE);
		cMananger = (ConnectivityManager) _context.getSystemService(Activity.CONNECTIVITY_SERVICE);
		
		return connectionManager;
	}
	
	public void setHandler(Handler handler){
		this.handler = handler;
	}

	public Handler getHandler() {
		return this.handler;
	}

	public void changeConnection(boolean isConfiguration) {
		changeConnection(isConfiguration, true);
	}
	
	public void changeConnection(boolean isConfiguration, boolean notify) {
					
		Log.d(TAG, "changeConnection() CONNECTION_SUCCESS");				
		
		this.notify = notify;
		operationRunning = true;
		sendMessageToHandler(CONNECTION_SUCCESS);
	}
	

	private int checkActiveNetworkConnected() {		
		try {
			int count = 0;
			NetworkInfo nInfo = cMananger.getActiveNetworkInfo();
			Log.i(TAG, "checkActiveNetworkInfo: isConnected ? "
					+ (nInfo == null ? "null" : nInfo.isConnected()));
			while (count < 15 && ((nInfo == null) || (!nInfo.isConnected()))) {
				Thread.sleep(500);
				if(operationRunning){
					nInfo = cMananger.getActiveNetworkInfo();
					Log.i(TAG, "checkActiveNetworkInfo: isConnected ? "
							+ (nInfo == null ? "null" : nInfo.isConnected()));
					count++;
				} else {
					return OPERATION_CANCELLED;
				}
			}
			
			if(count == 15){				
				return DATA_CONNECTION_ERROR;
			} else {
				return CONNECTION_SUCCESS;
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
			logger.log(Level.SEVERE, e.getMessage());
			Log.e(TAG, e.getMessage());
			return DATA_CONNECTION_ERROR;
		}
	}

	public void stopOperation(){
		operationRunning = false;
	}
	
	public Runnable apnManangerRunnable = new Runnable() {
		@Override
		public void run() {
			if(checkActiveNetworkConnected() == CONNECTION_SUCCESS){
				Log.i(TAG, "Runnable: invio notifica al chiamante");
				sendMessageToHandler(CONNECTION_SUCCESS);
			} else {
				Log.e(TAG, "NetworkInfo non è connected");
				sendMessageToHandler(CONNECTION_ERROR);
			}
		}
	};
	
	private PhoneStateListener phone_state_listener = new PhoneStateListener() {
		public void onDataConnectionStateChanged(int state, int networkType) {
			switch(state) {
			case TelephonyManager.DATA_CONNECTED:
				Log.i(TAG, "DATA_CONNECTED: disconnectedMsgCounter "+disconnectedMsgCounter);
				if(disconnectedMsgCounter != 0) {
					Log.i(TAG, "onDataConnectionStateChanged: reset del PhoneStateListener LISTEN_NONE");
					tManager.listen(phone_state_listener, PhoneStateListener.LISTEN_NONE);
					Log.i(TAG, "Invio notifica al gestore dei messaggi");
					Thread t = new Thread(apnManangerRunnable);
					t.start();
				}
				break;
    		case TelephonyManager.DATA_CONNECTING:
    			Log.i(TAG, "DATA_CONNECTING connectingMsgCounter: "+connectingMsgCounter);
    			connectingMsgCounter++;
    			break;
    		case TelephonyManager.DATA_DISCONNECTED:
    			Log.i(TAG, "DATA_DISCONNECTED disconnectedMsgCounter: "+disconnectedMsgCounter);
    			if(disconnectedMsgCounter > MAX_DISCONNECTED_MSG_COUNTER) {
					Log.i(TAG, "Impossibile connettersi all'APN");
					Log.i(TAG, "onDataConnectionStateChanged: reset del PhoneStateListener LISTEN_NONE");
					tManager.listen(phone_state_listener, PhoneStateListener.LISTEN_NONE);
					//Avviso dell'impossibilità di connettersi all'APN. Controllare le impostazioni relative all'APN
					sendMessageToHandler(APN_ERROR);
    			}
    			
    			disconnectedMsgCounter++;
    			break;
    		case TelephonyManager.DATA_SUSPENDED:
    			Log.i(TAG, "DATA_SUSPENDED");
    			break;
    		}
    	}
    };

	private void sendMessageToHandler(int msgType) {
		if(notify){
			handler.sendEmptyMessage(msgType);
		}
    }
}
