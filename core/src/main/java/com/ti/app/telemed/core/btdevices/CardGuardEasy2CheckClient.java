package com.ti.app.telemed.core.btdevices;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.BTSearcher;
import com.ti.app.telemed.core.btmodule.BTSocket;
import com.ti.app.telemed.core.btmodule.BTSearcherEventListener;
import com.ti.app.telemed.core.btmodule.BTSocketEventListener;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.util.Util;

import android.bluetooth.BluetoothDevice;
import android.util.Log;

public class CardGuardEasy2CheckClient extends DeviceHandler
		implements	BTSocketEventListener, BTSearcherEventListener {
	
	private static final String TAG = "ForaGlucometer";

	enum CommunicationState {
	    Idle,
		SendingCommandInfo_24,
		WaitingResponse_24,
		SendingReadStorageTime_25,
		WaitingResponse_25,
		SendingReadStorageResult_26,
		WaitingResponse_26,
		SendingDeleteStorage_52,
		WaitingResponse_52,
		SendingPoweOff_50,
        Error
	}
	private CommunicationState commState = CommunicationState.Idle;
	
	enum TTypeCommand {
		kT_22 ((byte)0x22),	//Read word (2 bytes) data from	EEPROM with Address
		kT_23 ((byte)0x23),	//Read System Clock time
		kT_25 ((byte)0x25),	//Read the storage data with Index, part 1.(time)
		kT_26 ((byte)0x26),	//Read the storage data with Index, part 2.(result)
		kT_27 ((byte)0x27),	//Read device serial number
		kT_29 ((byte)0x29),	//Read mask/firmware version with date.(Fixed in ROM)
		kT_33 ((byte)0x33),	//Write System Clock time
		kT_50 ((byte)0x50),	//Force Slave Device goes to Sleep
        kT_52 ((byte)0x52),	//Clear/Delete all memory storages in slave device
		kT_init ((byte)0x51),	//init
		kT_end ((byte)0xA3),
		kT_24 ((byte)0x24);	//end
		
		private final byte val;

		TTypeCommand(byte val) {
            this.val = val;
        }
		
		static byte getVal(TTypeCommand tuc) {
			return tuc.val;
		}
	}

    // iServiceSearcher searches for service this client can
    // connect to (in symbian version the type was CBTUtil) and
    // substitutes RSocketServ and RSocket of symbian version too
    private BTSearcher iServiceSearcher;
    // iCGEasy2CheckSocket is an RSocket in the Symbian version
    private BTSocket iCGEasy2CheckSocket;

    // BT address of the found device (we can have not received bt address from
    // scheduler or it can be changed, so we take this value from the searcher
    // after the device finding)
    private BluetoothDevice selectedDevice;

    private Vector<BluetoothDevice> deviceList;
    private int currentPos;
    private boolean deviceSearchCompleted;
    
    private int iEvent = -1;
        
    // used for sending message
    private byte[] messageToSend;
    // used for receiving message
    private byte[] messageReceived;
    // used for type of measure awaited
    private byte measureTypeCode;
    
    // info into main message
    private int systolic;
	private int diastolic;
	private int heartRate;
	private int glycaemia;
    
    private boolean dataFound;
    private Timer timer;
	private boolean retryOnceToReconnect = true;
    private final static int kTimeOut = 2000;
	private static final byte MEASURE_POSITION_NUMBER = 0x00;

	/*
	public static final String FORA_DEVICE_D40b = "D40b - Fora";
	public static final String FORA_DEVICE_G31b = "G31b - Fora";
	public static final String FORA_DEVICE_W310b = "W310b - Fora";
	public static final String FORA_DEVICE_DM30b = "DM30b - Fora";

	public static String getForaDeviceName() {
		return "FORA_DEVICE_NAME"; // + "_" + DbManager.getDbManager().getActiveUser().getId();
	}
	*/


    class TimerExpired extends TimerTask {
        @Override
		public void run(){
        	Log.i(TAG, "TIMER SCADUTO");
        	commState = CommunicationState.Error;
			runBTSocket();
        }
    }


    public CardGuardEasy2CheckClient(DeviceListener listener, UserDevice ud) {
        super(listener, ud);
        deviceSearchCompleted = false;
        dataFound = false;
        messageReceived = new byte[8];
        if (GWConst.KMsrGlic.equals(ud.getMeasure())) {
        	measureTypeCode = (byte)0x00;
        } else if (GWConst.KMsrPres.equals(ud.getMeasure())) {
        	measureTypeCode = (byte)0x80;        	
        } else {
        	measureTypeCode = (byte)0xFF;
        }
        iServiceSearcher = new BTSearcher();
        iCGEasy2CheckSocket = BTSocket.getBTSocket();
    }


    // methods of DeviceHandler interface

    @Override
    public void confirmDialog() {
	    iEvent = 2;
        makeResultData();
    }

    @Override
    public void cancelDialog() {
	    iEvent = 1;
        makeResultData();
    }

    @Override
    public boolean startOperation(OperationType ot, BTSearcherEventListener btSearchListener) {
        if (!startInit(ot, btSearchListener))
            return false;

        Log.d(TAG,"startOperation: iBtDevAddr="+iBtDevAddr + " iCmdCode="+iCmdCode.toString());
        iServiceSearcher.clearBTSearcherEventListener();
        if (iCmdCode == TCmd.ECmdConnByUser && iBTSearchListener != null)
            iServiceSearcher.addBTSearcherEventListener(iBTSearchListener);
        iServiceSearcher.addBTSearcherEventListener(this);
        iServiceSearcher.startSearchDevices();
        return true;
    }

    @Override
    public void stopOperation() {
        Log.d(TAG, "stopOperation");
        stop();
    }

    @Override
    public void selectDevice(BluetoothDevice bd){
        Log.d(TAG, "selectDevice: iBtDevAddr="+bd.getAddress());
        iServiceSearcher.stopSearchDevices();
        selectedDevice = bd;
        iBtDevAddr = selectedDevice.getAddress();
        iState = TState.EGettingService;
        runBTSearcher();
    }


    /**
     *
     */
    private void connectToServer() {
        // this function is called when we are in EGettingService state and
		// we are going to EGettingConnection state
		iCGEasy2CheckSocket.addBTSocketEventListener(this);
		iCGEasy2CheckSocket.connectInsecureUUID(selectedDevice);
    }
    

    /**
     * Make the checksum of argument
     */
    private byte checkSumCalc(byte[] aCmd) {
    	byte cs = 0x00;
    	for (int i=0; i<7; i++) {
    		cs+=aCmd[i];
    	}
    	return cs;
    }
    
    /**
     * Sending message
     */
    private void sendCmd(TTypeCommand cmd, boolean setTimer) {
        Log.i(TAG, "sendCmd:" + TTypeCommand.getVal(cmd));
        if (setTimer) {
            timer = new Timer();
            timer.schedule(new TimerExpired(), kTimeOut);
        }
        messageToSend = new byte[8];
        messageToSend[0] = TTypeCommand.getVal(TTypeCommand.kT_init);
        messageToSend[1] = TTypeCommand.getVal(cmd);
        messageToSend[2] = 0x00;
        messageToSend[3] = 0x00;
        messageToSend[4] = 0x00;
        messageToSend[5] = 0x00;
        messageToSend[6] = TTypeCommand.getVal(TTypeCommand.kT_end);
        messageToSend[7] = checkSumCalc( messageToSend );
    	iCGEasy2CheckSocket.write(messageToSend);
    }
    
    /**
     * Reading ACK
     */
    private void readAck() {
    	Log.d(TAG, "readAck");
    	messageReceived = new byte[8];
    	iCGEasy2CheckSocket.read(messageReceived);    	
    }
        
    /**
     *
     */
    private void parseMainFrame() {
    	if( measureTypeCode == (byte)0x00 ) { //Glycaemia
    		glycaemia = ((messageReceived[3]& 0x000000ff)<<8) |  (messageReceived[2]& 0x000000ff);
    		
    		iEvent = ((messageReceived[5] & 0x000000ff)>>6);
    		
    		Log.i(TAG, "Glucosio: " +glycaemia ); 
    	} else if( measureTypeCode == (byte)0x80 ) { //Blood pressure
    		Log.i(TAG, "parser: 3-" +(messageReceived[3]|0x80) +" 2-" +(messageReceived[2]|0x80));
    		int sysHigh,sysLow;
    		if ( messageReceived[3] < 0 ) {
    			sysHigh = (messageReceived[3]&0x7F) + 128;
    		} else {
    			sysHigh = messageReceived[3];
    		}
    		
    		if ( messageReceived[2] < 0 ) {
    			Log.i(TAG, "MAGGIORE");
    			sysLow = (messageReceived[2]&0x7F) + 128;
    		} else {
    			sysLow = messageReceived[2];
    		}
    		
    		systolic = sysHigh + sysLow;
    		
    		if ( messageReceived[4] < 0 ) {
    			diastolic = (messageReceived[4]&0x7F) + 128;
    		} else {
    			diastolic = messageReceived[4];
    		}
    		
    		if ( messageReceived[5] < 0 ) {
    			heartRate = (messageReceived[5]&0x7F) + 128;
    		} else {
    			heartRate = messageReceived[5];
    		}
    		
    		Log.i(TAG, "Sistolica: " +systolic );
    		Log.i(TAG, "Diastolica: " +diastolic );
    		Log.i(TAG, "Frequenza: " +heartRate );    		 
    	}
    }
    
    /**
     * 
     */
    private void makeResultData() {
    	if (dataFound) {
            HashMap<String,String> tmpVal = new HashMap<>();
    		if( measureTypeCode == (byte)0x00 ) { //Glycaemia
                if (iEvent == 1) {
                    tmpVal.put(GWConst.EGwCode_0E, Integer.toString(glycaemia));  // glicemia Pre-prandiale
                } else {
                    tmpVal.put(GWConst.EGwCode_0T, Integer.toString(glycaemia));  // glicemia Post-prandiale
                }
        	} else if( measureTypeCode == (byte)0x80 ) { //Blood pressure
                tmpVal.put(GWConst.EGwCode_03, Integer.toString(diastolic));
                tmpVal.put(GWConst.EGwCode_04, Integer.toString(systolic));
                tmpVal.put(GWConst.EGwCode_06, Integer.toString(heartRate));
        	}

            Measure m = getMeasure();
            m.setMeasures(tmpVal);
            deviceListener.showMeasurementResults(m);
            stop();
    	} else {
    		Log.e(TAG, "Dati non trovati");
    		deviceListener.notifyError(DeviceListener.NO_MEASURES_FOUND,ResourceManager.getResource().getString("ENoMeasuresFound"));
    	}
    }

    public void reset() {
        iState = TState.EWaitingToGetDevice;
        commState = CommunicationState.Idle;
        iBtDevAddr = null;
        deviceList.clear();
        currentPos=0;
        iBTSearchListener = null;
		deviceSearchCompleted = false;
        dataFound = false;
        messageReceived = new byte[8];
    }

    public void stop() {
        iServiceSearcher.stopSearchDevices();
        iServiceSearcher.clearBTSearcherEventListener();
        iServiceSearcher.close();
        iCGEasy2CheckSocket.removeBTSocketEventListener(this);
        if (iState == TState.EConnected)
            iCGEasy2CheckSocket.close();
        reset();
    }


    // methods of BTSearchEventListener interface

    @Override
    public void deviceDiscovered(Vector<BluetoothDevice> devList) {
    	deviceList = devList;
        // we recall runBTSearcher, because every time we find a device, we have
        // to check if it is the device we want
        runBTSearcher();
    }

    @Override
    public void deviceSearchCompleted() {
        deviceSearchCompleted = true;
        currentPos = 0;
    }

	
	// methods of BTSocketEventListener interface    

    @Override
    public void openDone(){
    	runBTSocket();
    }

    @Override
	public void readDone(){
		Log.d(TAG, Util.toHexString(messageReceived));
		runBTSocket();
	}

    @Override
	public void writeDone(){
		Log.d(TAG, Util.toHexString(messageToSend));
		runBTSocket();
	}

	@Override
	public void errorThrown(int type, String description){
		switch (type) {
    	case 0: //thread interrupted
        case 2: //bluetooth read error
        case 3: //bluetooth write error
        case 4: //bluetooth close error
    		Log.e(TAG, description);
            commState = CommunicationState.Error;
            runBTSocket();
    		break;
    	case 1: //bluetooth open error
    		Log.d(TAG, "retryOnceToReconnect=" + retryOnceToReconnect);
    		if (retryOnceToReconnect) {
    			try {
					Thread.sleep(1500);
				} catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
    			retryOnceToReconnect = false;
                connectToServer();
    		}
    		else {
	    		Log.e(TAG, description);
	    		if (iState == TState.EConnected) {
	    			// if we don't receive any message from the blood pressure at this state
	    			// means that we have to do the pairing
                    commState = CommunicationState.Error;
	    			runBTSocket();    			
	    		}    		
    		}
    		break;
		}	
	}
	
	/**
     * 
     */
    private void runBTSearcher(){
    	switch (iState){
            case EGettingDevice:
                switch (iCmdCode) {
                    case ECmdConnByAddr:
                        String tmpBtDevAddr;
                        tmpBtDevAddr = deviceList.elementAt(currentPos).getAddress();
                        if (tmpBtDevAddr.equals(iBtDevAddr)) {
                            // we pass the position of the selected device into the devices vector
                            selectDevice(deviceList.elementAt(currentPos));
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
                        deviceListener.notifyWaitToUi(ResourceManager.getResource().getString("KConnectingDev"));
                        break;
                }
                break;
            case EGettingService:
                Log.d(TAG, "runBtSearcher EGettingService");
                iServiceSearcher.removeBTSearcherEventListener(this);
                iState = TState.EConnected;
                connectToServer();
                break;
    	}
    }

    private synchronized void runBTSocket(){
        switch(commState) {
            case Idle:
                Log.d(TAG, "runBTSocket: SendingCommandInfo_24");
                commState = CommunicationState.SendingCommandInfo_24;
                iEvent = -1;
                sendCmd(TTypeCommand.kT_24, true);
                if(operationType == OperationType.Pair)
                    deviceListener.notifyWaitToUi(ResourceManager.getResource().getString("KPairingMsg"));
                else
                    deviceListener.notifyWaitToUi(ResourceManager.getResource().getString("KMeasuring"));
                break;
            case SendingCommandInfo_24:
                Log.d(TAG, "runBTSocket: WaitingResponse_24");
                commState = CommunicationState.WaitingResponse_24;
                readAck();
                break;
            case WaitingResponse_24:
                if (messageReceived[1] == 0x54) {
                    Log.d(TAG, "runBTSocket: 0x54 State. Ignoring .... ");
                    readAck();
                    return;
                }
                timer.cancel();
    		    /*
    		        TODO salvare il nome del device per inserirlo poi nell'XML inviato alla piattaforma
    				if (((int)messageReceived[3] & 0xff) == 0x32 && ((int)messageReceived[2] & 0xff) == 0x61) {
    					Util.setRegistryValue(getForaDeviceName(), FORA_DEVICE_D40b);
    				}
    				if (((int)messageReceived[3] & 0xff) == 0x42 && ((int)messageReceived[2] & 0xff) == 0x56) {
    					Util.setRegistryValue(getForaDeviceName(), FORA_DEVICE_G31b);
    				}
    				if (((int)messageReceived[3] & 0xff) == 0x25 && ((int)messageReceived[2] & 0xff) == 0x51) {
    					Util.setRegistryValue(getForaDeviceName(), FORA_DEVICE_W310b);
    				}
    				if (((int)messageReceived[3] & 0xff) == 0x42 && ((int)messageReceived[2] & 0xff) == 0x83) {
    					Util.setRegistryValue(getForaDeviceName(), FORA_DEVICE_DM30b);
    				}
    		    */
                if(operationType == OperationType.Pair) {
                    Log.d(TAG, "runBTSocket: SendingPoweOff_50");
                    commState = CommunicationState.SendingPoweOff_50;
                    sendCmd(TTypeCommand.kT_50, false);
                } else {
                    Log.d(TAG, "runBTSocket: SendingReadStorageTime_25");
                    commState = CommunicationState.SendingReadStorageTime_25;
                    sendCmd(TTypeCommand.kT_25, true);
                }
                break;
            case SendingReadStorageTime_25:
                Log.d(TAG, "runBTSocket: WaitingResponse_25");
                commState = CommunicationState.WaitingResponse_25;
                readAck();
                break;
            case WaitingResponse_25:
                if (messageReceived[1] == 0x54) {
                    Log.d(TAG, "runBTSocket: 0x54 State. Ignoring .... ");
                    readAck();
                    return;
                }
                timer.cancel();
                if (messageReceived[1] != 0x25) {
                    Log.d(TAG, "runBTSocket: Wrong response. Waiting 37 but received " + messageReceived[1]);
                    stop();
                    deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR ,ResourceManager.getResource().getString("ECommunicationError"));
                    break;
                }
                if (messageReceived[7] == checkSumCalc(messageReceived)) {
                    if ((messageReceived[2] + messageReceived[3] + messageReceived[4] + messageReceived[5]) == 0) {
                        //No data
                        stop();
                        deviceListener.notifyError(DeviceListener.NO_MEASURES_FOUND, ResourceManager.getResource().getString("ENoMeasuresFound"));
                    } else if (((byte) (messageReceived[4] & 0x80)) != measureTypeCode) {
                        Log.d(TAG, "runBTSocket measureType: " + String.valueOf(measureTypeCode));
                        Log.d(TAG, "runBTSocket measureReceived: " + (messageReceived[4] & 0x80));
                        // wrong measure type code
                        stop();
                        deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
                    } else {
                        Log.d(TAG, "runBTSocket: SendingReadStorageResult_26");
                        commState = CommunicationState.SendingReadStorageResult_26;
                        sendCmd(TTypeCommand.kT_26, true);
                    }
                } else {
                    Log.d(TAG, "runBTSocket: Wrong checksum");
                    stop();
                    deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR ,ResourceManager.getResource().getString("ECommunicationError"));
                }
                break;
            case SendingReadStorageResult_26:
                Log.d(TAG, "runBTSocket: WaitingResponse_26");
                commState = CommunicationState.WaitingResponse_26;
                readAck();
                break;
            case WaitingResponse_26:
                if (messageReceived[1] == 0x54) {
                    Log.d(TAG, "runBTSocket: 0x54 message. Ignoring .... ");
                    readAck();
                    return;
                }
                timer.cancel();
                if (messageReceived[1] != 0x26) {
                    Log.d(TAG, "runBTSocket: Wrong response. Waiting 38 but received " + messageReceived[1]);
                    stop();
                    deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR ,ResourceManager.getResource().getString("ECommunicationError"));
                    break;
                }
                if (messageReceived[7] == checkSumCalc(messageReceived)) {
                    Log.d(TAG, "runBTSocket: SendingDeleteStorage_52");
                    dataFound = true;
                    parseMainFrame();
                    commState = CommunicationState.SendingDeleteStorage_52;
                    sendCmd(TTypeCommand.kT_52, true);
                } else {
                    Log.d(TAG, "runBTSocket: Wrong checksum");
                    stop();
                    deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR ,ResourceManager.getResource().getString("ECommunicationError"));
                }
                break;
            case SendingDeleteStorage_52:
                Log.d(TAG, "runBTSocket: WaitingResponse_52");
                commState = CommunicationState.WaitingResponse_52;
                readAck();
                break;
            case WaitingResponse_52:
                if (messageReceived[1] == 0x54) {
                    Log.d(TAG, "runBTSocket: 0x54 message. Ignoring .... ");
                    readAck();
                    return;
                }
                timer.cancel();
                Log.d(TAG, "runBTSocket: SendingPoweOff_50");
                commState = CommunicationState.SendingPoweOff_50;
                sendCmd(TTypeCommand.kT_50, false);
                break;
            case SendingPoweOff_50:
                Log.d(TAG, "runBTSocket: PowerOff Sent");
                iCGEasy2CheckSocket.removeBTSocketEventListener(this);
                iCGEasy2CheckSocket.close();
                if(operationType == OperationType.Pair) {
                    deviceListener.setBtMAC(iBtDevAddr);
                    deviceListener.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
                    stop();
                } else {
                    if ((measureTypeCode != (byte)0x00) || (iEvent == 1) || (iEvent == 2))
                        makeResultData();
                    else
                        deviceListener.askSomething(ResourceManager.getResource().getString("KPrePostMsg"),
                                ResourceManager.getResource().getString("MeasureGlyPOSTBtn"),
                                ResourceManager.getResource().getString("MeasureGlyPREBtn"));
                }
                break;
            case Error:
                Log.d(TAG, "runBTSocket: Error");
                deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR,ResourceManager.getResource().getString("ECommunicationError"));
                stop();
                break;
        }
    }
}