package com.ti.app.telemed.core.btdevices;

import java.io.IOException;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.BTSearcher;
import com.ti.app.telemed.core.btmodule.BTSocket;
import com.ti.app.telemed.core.btmodule.events.BTSearcherEvent;
import com.ti.app.telemed.core.btmodule.events.BTSearcherEventListener;
import com.ti.app.telemed.core.btmodule.events.BTSocketEvent;
import com.ti.app.telemed.core.btmodule.events.BTSocketEventListener;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

import android.bluetooth.BluetoothDevice;
import android.util.Log;

public class ForaThermometerClient implements DeviceHandler,												  
												BTSocketEventListener, 
												BTSearcherEventListener {
	
	private static final String TAG = "ForaThermometerClient";
	
	private enum TTypeCommand {
		kT_22 ((byte)0x22),	//Read word (2 bytes) data from	EEPROM with Address
		kT_23 ((byte)0x23),	//Read System Clock time
		kT_25 ((byte)0x25),	//Read the storage data with Index, part 1.(time)
		kT_26 ((byte)0x26),	//Read the storage data with Index, part 2.(result)
		kT_27 ((byte)0x27),	//Read device serial number
		kT_29 ((byte)0x29),	//Read mask/firmware version with date.(Fixed in ROM)
		kT_33 ((byte)0x33),	//Write System Clock time
		kT_50 ((byte)0x50),	//Force Slave Device goes to Sleep
		kT_init ((byte)0x51),	//init
		kT_end ((byte)0xA3);	//end
		
		private final byte val;

		TTypeCommand(byte val) {
            this.val = val;
        }
		
		static byte getVal(TTypeCommand tuc) {
			return tuc.val;
		}
	}

    private enum TState {
		EWaitingToGetDevice,
        EGettingDevice,
        EGettingService,            
        EConnected,
		ESendingReqLast,
		EWaitingLastMeasure,
		ESendingCmdOff,
		EDisconnectingOK,
		EDisconnectingPairing,
		EDisconnecting,
	}
	
	// State of the active object
    private TState iState;
    // Type of search the scheduler requires
    private TCmd iCmdCode;

    // iServiceSearcher searches for service this client can
    // connect to (in symbian version the type was CBTUtil) and
    // substitutes RSocketServ and RSocket of symbian version too
    private BTSearcher iServiceSearcher;
    // iForaThermometerSocket is an RSocket in the Symbian version
    private BTSocket iForaThermometerSocket;

    // BT address obtained from scheduler (in symbian version the
    // type was TBTDevAddr)
    private String iBtDevAddr;
    // BT address of the found device (we can have not received bt address from
    // scheduler or it can be changed, so we take this value from the searcher
    // after the device finding)
    private String iBTAddress;

    private Vector<BluetoothDevice> deviceList;
    private int currentPos;
    private boolean deviceSearchCompleted;

    // used for sending message
    private byte[] messageToSend;
    // used for receiving message
    private byte[] messageReceived;
    
	// Dati relativi alle misure
	private int iBodyTemperature;
	private int  iAmbientTemperature;

    private boolean dataFound;
    private boolean operationDeleted;
    private boolean serverOpenFailed;
    // a pointer to scheduler
    private DeviceListener iScheduler;
    private UserDevice iUserDevice;

    // Indicates that the current request is not a measure but a connection request
    private boolean iPairingMode = false;

    private boolean waitforAck;
    private Timer timer;
    private final static int kTimeOut = 1000;

    private Logger logger = Logger.getLogger(ForaThermometerClient.class.getName());

	public static boolean needPairing(UserDevice userDevice) {
		return false;
	}

	public static boolean needConfig(UserDevice userDevice) {
		return false;
	}

    public ForaThermometerClient(DeviceListener deviceListener) {
    	iState = TState.EWaitingToGetDevice;
        iScheduler = deviceListener;
        deviceSearchCompleted = false;
        dataFound = false;
        
        messageToSend = new byte[8];
        messageReceived = new byte[8];

        operationDeleted = false;
        serverOpenFailed = false;
        
        waitforAck = true;
        
        iServiceSearcher = new BTSearcher();
        iServiceSearcher.addBTSearcherEventListener(this);
        iForaThermometerSocket = BTSocket.getBTSocket();
    }


    // methods of DeviceHandler interface

    @Override
    public void confirmDialog() {
    }

    @Override
    public void cancelDialog(){
    }

    @Override
    public void start(OperationType ot, UserDevice ud, BTSearcherEventListener btSearchListener) {
        if (iState == TState.EWaitingToGetDevice) {
            iPairingMode = ot == OperationType.Pair;
            iUserDevice = ud;
            iBtDevAddr = iUserDevice.getBtAddress();
            iServiceSearcher.clearBTSearcherEventListener();
            iServiceSearcher.addBTSearcherEventListener(this);
            if (iBtDevAddr != null && !iBtDevAddr.isEmpty()) {
                iCmdCode = TCmd.ECmdConnByAddr;
            } else {
                iCmdCode = TCmd.ECmdConnByUser;
                iServiceSearcher.addBTSearcherEventListener(btSearchListener);
            }
            iState = TState.EGettingDevice;
            iServiceSearcher.setSearchType(iCmdCode);
            // it launch the automatic procedures for the manual device search
            iServiceSearcher.startSearchDevices();
        }
    }

    @Override
    public void stopDeviceOperation(int selected) {
        if (selected == -1) {
            stop();
        } else if (selected == -2) {
            iServiceSearcher.stopSearchDevices(-1);
            if (iState != TState.EGettingDevice) {
                // we advise the scheduler of the end of the activity on the device
                iScheduler.operationCompleted();
            }
        } else {
            iServiceSearcher.stopSearchDevices(selected);
        }
    }


    // methods of BTSearchEventListener interface

    @Override
    public void deviceDiscovered(BTSearcherEvent evt, Vector<BluetoothDevice> devList) {
        deviceList = devList;
        // we recall runBTSearcher, because every time we find a device, we have
        // to check if it is the device we want
        runBTSearcher();
    }

    @Override
    public void deviceSearchCompleted(BTSearcherEvent evt) {
        deviceSearchCompleted = true;
        currentPos = 0;
    }

    @Override
    public void deviceSelected(BTSearcherEvent evt){
        logger.log(Level.INFO, "CGE2C: deviceSelected");
        // we change status
        iState = TState.EGettingService;

        runBTSearcher();
    }


    // methods of BTSocketEventListener interface

    @Override
    public void openDone(BTSocketEvent evt){
        runBTSocket();
    }

    @Override
    public void readDone(BTSocketEvent evt){
        logger.log(Level.INFO, "ack received: "+toHexString(messageReceived));
        runBTSocket();
    }

    @Override
    public void writeDone(BTSocketEvent evt){
        logger.log(Level.INFO, "command sent"+toHexString(messageToSend));
        runBTSocket();
    }

    @Override
    public void errorThrown(BTSocketEvent evt, int type, String description){
        Log.e(TAG,  description);
        switch (type) {
            case 0: //thread interrupted
                reset();
                iScheduler.notifyError(DeviceListener.COMMUNICATION_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
                break;
            case 1: //bluetooth open error
                if (iState == TState.EConnected) {
                    // if we don't receive any message from the blood pressure at this state
                    // means that we have to do the pairing
                    iState = TState.EDisconnecting;
                    operationDeleted = true;
                    serverOpenFailed = true;
                    runBTSocket();
                }
                reset();
                iScheduler.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
                break;
            case 2: //bluetooth read error
            case 3: //bluetooth write error
                iState = TState.EDisconnecting;
                operationDeleted = true;
                runBTSocket();
                reset();
                iScheduler.notifyError(DeviceListener.COMMUNICATION_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
                break;
            case 4: //bluetooth close error
                reset();
                iScheduler.notifyError(DeviceListener.COMMUNICATION_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
                break;
        }
    }

    public void reset() {
        // we free all buffer, descriptor and array
        iBTAddress = null;
        deviceSearchCompleted = false;
        dataFound = false;
        operationDeleted = false;
        serverOpenFailed = false;

        messageReceived = new byte[8];

        waitforAck = true;

        // this class object must return to the initial state
        iState = TState.EWaitingToGetDevice;
    }


    public void stop() {
        if (iState == TState.EGettingDevice) {
            iServiceSearcher.stopSearchDevices(-1);
            iServiceSearcher.close();
            // we advise the scheduler of the end of the activity on the device
            iScheduler.operationCompleted();
        } else if (iState == TState.EGettingService) {
            iServiceSearcher.close();
            // we advise the scheduler of the end of the activity on the device
            iScheduler.operationCompleted();
        } else if (iState == TState.EDisconnectingOK) {
            if(iCmdCode.equals(TCmd.ECmdConnByUser)){
                iScheduler.setBtMAC(iBTAddress);
            }
            iForaThermometerSocket.close();
            iForaThermometerSocket.removeBTSocketEventListener(this);
            iState = TState.EWaitingToGetDevice;
            makeResultData();
            currentPos = 0;
        } else if (iState == TState.EDisconnectingPairing) {
            iForaThermometerSocket.close();
            iForaThermometerSocket.removeBTSocketEventListener(this);
            runBTSocket();
            // we advise the scheduler of the end of the activity on the device
            iScheduler.operationCompleted();
        } else {
            if (!serverOpenFailed) {
                // cancels all outstanding operations on socket
                iForaThermometerSocket.close();
                iForaThermometerSocket.removeBTSocketEventListener(this);
            }
            // we advise the scheduler of the end of the activity on the device
            iScheduler.operationCompleted();
        }
    }


    private void connectToServer() throws IOException {
        // this function is called when we are in EGettingService state and
		// we are going to EGettingConnection state
		iBTAddress = iServiceSearcher.getCurrBTDevice().getAddress();
		iForaThermometerSocket.addBTSocketEventListener(this);
		iForaThermometerSocket.connect(iServiceSearcher.getCurrBTDevice());
    }
    
    /**
     * Make the checksum of argument
     */
    private byte checkSumCalc(byte[] aCmd) {
        byte kT_checkSumControl = ((byte)0xFF);

        byte cs = 0x00;
    	for (int i=0; i<7; i++) {
    		cs+=aCmd[i];
    	}

    	cs = (byte) (cs & kT_checkSumControl);

    	return cs;
    }
    
    /**
     * Sending message
     */
    private void sendCmd() {    	
    	logger.log(Level.INFO, "sendCmd");    	
    	iForaThermometerSocket.write(messageToSend);    	
    }
    
    /**
     * Reading ACK
     */
    private void readAck() {
    	logger.log(Level.INFO, "readAck");
    	iForaThermometerSocket.read(messageReceived);    	
    }

    private static String toHexString(byte[] array){
    	String tmp = "";
        for (byte b : array)
            tmp += " " + Integer.toHexString(b & 0x000000ff);
    	return tmp;
    }
    
	private void parseMainFrame() {
    	iBodyTemperature = ((messageReceived[3] & 0xFF)<<8) |  (messageReceived[2] & 0xFF);
    	iAmbientTemperature = ((messageReceived[5] & 0xFF)<<8) | (messageReceived[4] & 0xFF);							
    }
    
    private void makeResultData() {    	
    	if (dataFound) {
            HashMap<String,String> tmpVal = new HashMap<>();
            tmpVal.put(GWConst.EGwCode_0R, formatTemp(iBodyTemperature));
            tmpVal.put(GWConst.EGwCode_0U, formatTemp(iAmbientTemperature));
            Measure m = new Measure();
            User u = UserManager.getUserManager().getCurrentUser();
            m.setMeasureType(iUserDevice.getMeasure());
            m.setDeviceDesc(iUserDevice.getDevice().getDescription());
            m.setTimestamp(XmlManager.getXmlManager().getTimestamp(null));
            m.setFile(null);
            m.setFileType(null);
            if (u != null) {
                m.setIdUser(u.getId());
                if (u.getIsPatient())
                    m.setIdPatient(u.getId());
            }
            m.setMeasures(tmpVal);
            m.setFailed(false);
            m.setBtAddress(iBTAddress);

            iScheduler.showMeasurementResults(m);
        } else {
        	Log.e(TAG,  "Dati non trovati");
        	iScheduler.notifyError(DeviceListener.NO_MEASURES_FOUND, ResourceManager.getResource().getString("ENoMeasuresFound"));
        }
    }
    
    private String formatTemp(int temperature) {
		String temp = String.valueOf(temperature);
		return temp.substring(0,2)+","+temp.substring(2);
	}

    private void runBTSearcher(){
    	switch (iState){
    	case EGettingDevice:
            // we have found a device (not necessarily the device that we need)
            // in this case the application is the master into bluetooth communication:
            // when the master has bluetooth off can't communicate with the slave, so
            // the framework asks user to activate bluetooth. We arrive here when the
            // user has activated and a device is found; but the device scheduler don't
            // still know that bluetooth is active and so we must advice it with setting
            // a new state of bluetooth
            //iScheduler.BTActivated();
            // the automatic search, find all available addresses and when we arrive
            // here we must check if the found address is the same of the searched
            // (in the case of search by name we must check if the device of found
            // address has the same name of the searched name)
            switch (iCmdCode) {                
                case ECmdConnByAddr:
                    String tmpBtDevAddr;
                    tmpBtDevAddr = deviceList.elementAt(currentPos).getAddress();
                    if ( tmpBtDevAddr.equals(iBtDevAddr) ) {
                        // the btAddress is the same
                        // we pass the position of the selected device into the devices vector
                    	iServiceSearcher.stopSearchDevices(currentPos);  
                    } else {
                        // the name is different, so we must wait that the searcher
                        // find another device
                        if (deviceSearchCompleted) {
                        	deviceSearchCompleted = false;
                            // the search is completed and we have all the devices into
                            // the vector
                            if (currentPos <= deviceList.size() - 1) {
                                currentPos++;
                            } else {
                                // is the last found device and there isn't the one we
                                // wanted: we restart the device search                               
								iServiceSearcher.startSearchDevices();								
                            }
                        } else {
                            // some devices lack into the vector because the search is
                            // not completed
                            if (currentPos <= deviceList.size() - 1) {
                                // we increment the current position only if there are
                                // other elements into the vector (otherwise we remain
                                // on the same position waiting the finding of new devices)
                                currentPos++;
                            }
                        }
                    }
                    break;
                case ECmdConnByUser:
                    // the selection done by user is managed in the ui class which
                    // implements BTSearcherEventListener interface, so here arrive
                    // when the selection is already done
                	iScheduler.notifyWaitToUi(ResourceManager.getResource().getString("KConnectingDev"));
                    break;
            }
            break;
            
    	case EGettingService:
            iState = TState.EConnected;
            logger.log(Level.INFO, "CGE2C: runBtSearcher EGettingService");
            try {
            	connectToServer();
			} catch (IOException e) {
				iScheduler.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
			}
            break;
    	
    	}
    }
    
    private void runBTSocket(){
    	switch(iState) {
    	case EConnected:
    		logger.log(Level.INFO, "runBTSocket: EConnected");
			//Device find
    		if (iPairingMode) {
	    		iScheduler.notifyWaitToUi(ResourceManager.getResource().getString("KConnectingDev"));
	    	} else {
	    		iScheduler.notifyWaitToUi(ResourceManager.getResource().getString("KMeasuring"));
	    	}
    		
			//Device connected, sending request for last measure
			iState = TState.ESendingReqLast;
			messageToSend = new byte[8];
			messageToSend[0] = TTypeCommand.getVal(TTypeCommand.kT_init);
			messageToSend[1] = TTypeCommand.getVal(TTypeCommand.kT_25);
			messageToSend[2] = 0x00;
			messageToSend[3] = 0x00;
			messageToSend[4] = 0x00;
			messageToSend[5] = 0x00;
			messageToSend[6] = TTypeCommand.getVal(TTypeCommand.kT_end);
			messageToSend[7] = checkSumCalc( messageToSend );

            TimerExpired timerExpired = new TimerExpired();
	    	timer = new Timer();
	    	timer.schedule(timerExpired, kTimeOut);
	    	
	    	waitforAck = true;
	    	
			sendCmd();
			
    		break;
    		
    	case ESendingReqLast:
    		logger.log(Level.INFO, "runBTSocket: ESendingReqLast");
    		iState = TState.EWaitingLastMeasure;
    		if(waitforAck){
    			logger.log(Level.INFO, "waiting for ack... ");
    			readAck();    			
    		}
    		break;
    		
    	case EWaitingLastMeasure:
    		logger.log(Level.INFO, "runBTSocket: EWaitingLastMeasure");
    		timer.cancel();
    		// Type of response:
    		// 0x25: time measure
    		// 0x26: data measure
    		switch (messageReceived[1]) {
    		
    		// Skip del pacchetto di conferma (Mod. 28-Lug-14)
    		case 0x54:
    			Log.i(TAG, "CGE2G runBTSocket: 0x54 State");
        		iState = TState.EConnected;
    			runBTSocket();		
    			break;
    			
    		case 0x25:
    			//Checksum control
    			if (messageReceived[7] == checkSumCalc(messageReceived)) {
    				if ((messageReceived[2] + messageReceived[3] + messageReceived[4] + messageReceived[5]) == 0){
    					//No data
    					operationDeleted = true;
    					iForaThermometerSocket.removeBTSocketEventListener(this);
    					iForaThermometerSocket.close();
    					reset();
                        iScheduler.notifyError(DeviceListener.NO_MEASURES_FOUND, ResourceManager.getResource().getString("ENoMeasuresFound"));
    				} else {
    					//Correct, sending request for data measure
    					iState = TState.ESendingReqLast;
    					messageToSend = new byte[8];
    					messageToSend[0] = TTypeCommand.getVal(TTypeCommand.kT_init);
    					messageToSend[1] = TTypeCommand.getVal(TTypeCommand.kT_26);
    					messageToSend[2] = 0x00;
    					messageToSend[3] = 0x00;
    					messageToSend[4] = 0x00;
    					messageToSend[5] = 0x00;
    					messageToSend[6] = TTypeCommand.getVal(TTypeCommand.kT_end);
    					messageToSend[7] = checkSumCalc( messageToSend );

    					waitforAck = true;
    					
    					sendCmd();
    				}
    			} else {
    				operationDeleted = true;
    				iForaThermometerSocket.removeBTSocketEventListener(this);
    				iForaThermometerSocket.close();
					reset();
                    iScheduler.notifyError(DeviceListener.COMMUNICATION_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
    			}
    			break;
    		case 0x26:
    			//Checksum control
    			if (messageReceived[7] == checkSumCalc(messageReceived)) {
    				dataFound = true;
    				parseMainFrame();
    				
    				//Correct, sending command for sleep
					iState = TState.ESendingCmdOff;
					messageToSend = new byte[8];
					messageToSend[0] = TTypeCommand.getVal(TTypeCommand.kT_init);
					messageToSend[1] = TTypeCommand.getVal(TTypeCommand.kT_50);
					messageToSend[2] = 0x00;
					messageToSend[3] = 0x00;
					messageToSend[4] = 0x00;
					messageToSend[5] = 0x00;
					messageToSend[6] = TTypeCommand.getVal(TTypeCommand.kT_end);
					messageToSend[7] = checkSumCalc( messageToSend );

					waitforAck = false;
					
					sendCmd();
    			} else {
    				operationDeleted = true;
    				iForaThermometerSocket.removeBTSocketEventListener(this);
    				iForaThermometerSocket.close();
					reset();
                    iScheduler.notifyError(DeviceListener.COMMUNICATION_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
    			}
    			break;
    			
    		default:
    			operationDeleted = true;
				iForaThermometerSocket.removeBTSocketEventListener(this);
				iForaThermometerSocket.close();
				reset();
                iScheduler.notifyError(DeviceListener.COMMUNICATION_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
    			break;
    		}
    		break;
    		
    	case ESendingCmdOff:
    		logger.log(Level.INFO, "runBTSocket: ESendingCmdOff");
    		  		
    		if(iPairingMode){
    			logger.log(Level.INFO, "runBTSocket: ESendingCmdOff -> EDisconnectingPairing");
    			iState = TState.EDisconnectingPairing;
    		} else {
    			logger.log(Level.INFO, "runBTSocket: ESendingCmdOff -> EDisconnectingOk");
    			iState = TState.EDisconnectingOK;
    		}
            stop();
    		break;
    		
    	case EDisconnectingOK:
    		logger.log(Level.INFO, "ForaTM: EDisconnectingOK");
    		break;
    		
    	case EDisconnectingPairing:
    		logger.log(Level.INFO, "ForaTM: runBTSocket EDisconnectingPairing");
			//Pairing eseguito con successo. Salva il BT MAC
    		iForaThermometerSocket.removeBTSocketEventListener(this);
    		iScheduler.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
			iScheduler.setBtMAC(iBTAddress);	
			currentPos = 0;	
    		break;
    		
    	case EDisconnecting:
    		logger.log(Level.INFO, "CGE2C: EDisconnecting in runBTSocket");
			if (operationDeleted) {				
				//Error detected, we close communication
				iForaThermometerSocket.removeBTSocketEventListener(this);
				iForaThermometerSocket.close();				
	        	iState = TState.EWaitingToGetDevice;
			}
    		break;
    	}
    }

	private class TimerExpired extends TimerTask {
		public void run(){
			logger.log(Level.INFO, "TIMER SCADUTO iState = "+iState);
			if(iState == TState.EWaitingLastMeasure){
				logger.log(Level.INFO, "re-sending request for last measure");
				//Device connected, re-sending request for last measure
				waitforAck = false;
				iState = TState.ESendingReqLast;
				messageToSend = new byte[8];
				messageToSend[0] = TTypeCommand.getVal(TTypeCommand.kT_init);
				messageToSend[1] = TTypeCommand.getVal(TTypeCommand.kT_25);
				messageToSend[2] = 0x00;
				messageToSend[3] = 0x00;
				messageToSend[4] = 0x00;
				messageToSend[5] = 0x00;
				messageToSend[6] = TTypeCommand.getVal(TTypeCommand.kT_end);
				messageToSend[7] = checkSumCalc( messageToSend );

				waitforAck = false;

				sendCmd();
			}
		}
	}
}

