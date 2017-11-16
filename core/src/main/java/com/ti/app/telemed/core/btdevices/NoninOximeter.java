package com.ti.app.telemed.core.btdevices;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import com.ti.app.telemed.core.btmodule.BTSearcher;
import com.ti.app.telemed.core.btmodule.BTSocket;
import com.ti.app.telemed.core.btmodule.BTSearcherEventListener;
import com.ti.app.telemed.core.btmodule.BTSocketEventListener;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.xmlmodule.XmlManager;
import com.ti.app.telemed.core.btmodule.DeviceHandler;

import android.bluetooth.BluetoothDevice;
import android.util.Log;

public class NoninOximeter extends DeviceHandler implements
		BTSearcherEventListener, BTSocketEventListener {

	// iServiceSearcher searches for service this client can
	// connect to (in symbian version the type was CBTUtil) and
	// substitutes RSocketServ and RSocket of symbian version too
	private BTSearcher iServiceSearcher;
	// iCGBloodPressureSocket is an RSocket in the Symbian version
	private BTSocket iNoninOxySocket;

	private boolean serverOpenFailed;

	private Vector<BluetoothDevice> deviceList;
	private int currentPos;
    private BluetoothDevice selectedDevice;
    private boolean deviceSearchCompleted;

    private byte[] iBufferAck; // 1 byte
    private byte[] iBufferData; // 4 byte
    private byte[] iSettingMessage; // 8 byte

    private double iSpO2Med,iHRMed;
    private int	iSpO2Min,iSpO2Max;
    private int	iHRMin,iHRMax;
	
    private int	iEventSpO289; // + 20 sec < 89 %
    private int	iEventSpO289Count;
    private int	iEventBradi; // HR<40
    private int	iEventTachi; // HR>120

    private int	iT90; //tempo SpO2<90%
    private int	iT89; //tempo SpO2<89%
    private int	iT88; //tempo SpO2<88%
    private int	iT87; //tempo SpO2<87%
    private int	iT40; //tempo HR<40 bpm
    private int	iT120; //tempo HR>120 bpm
	
    private int	iAnalysisTime;
	
    private boolean	iLowBattery;
    private boolean	iInitTX;
    private boolean	firstRead;
	
    private Vector<OxyElem> oxyQueue;
    
    private byte[] oxyStream;

	private static final String TAG = "NoninOximeter";

	private static final int NUMBER_OF_MEASUREMENTS = 300; // = 5 min
	private static final int BASE_OXY_STREAM_LENGTH = 189; //
	private static final int K_TIME_OUT = 10 /*sec*/ * 1000;

    private Timer timer;

	public static boolean needPairing(UserDevice userDevice) {
		return false;
	}

	public static boolean needConfig(UserDevice userDevice) {
		return false;
	}

	public NoninOximeter(DeviceListener listener, UserDevice ud) {
        super(listener, ud);

    	deviceSearchCompleted = false;
        serverOpenFailed = false;
        iServiceSearcher = new BTSearcher();        
        iNoninOxySocket = BTSocket.getBTSocket();
        initVar();
	}
	
	private void initVar() {
		iSettingMessage = new byte[8];    	
    	iSettingMessage[0] = 0x02;
    	iSettingMessage[1] = 0x70;
    	iSettingMessage[2] = 0x04;		
    	iSettingMessage[3] = 0x02;		
    	iSettingMessage[4] = 0x08;			
    	iSettingMessage[5] = 0x00;
    	iSettingMessage[6] = 0x7E;
    	iSettingMessage[7] = 0x03;
		
		oxyQueue = new Vector<>(NUMBER_OF_MEASUREMENTS);
		
		ByteBuffer tmpStream = ByteBuffer.allocate(BASE_OXY_STREAM_LENGTH);
		tmpStream.put(67, (byte)0x0A); //STEP_OXY
		tmpStream.put(68, (byte)0x04);
		tmpStream.put(69, (byte)0x20); //FL_TEST
		oxyStream = tmpStream.array();
		
		iBufferAck = new byte[1];
		iBufferData = new byte[4];
		
		iInitTX = false;
		firstRead = true;
		
	    iSpO2Min = 1024;
	    iSpO2Max = 0; 
	    iSpO2Med = 0.0;
		iHRMin = 1024;
		iHRMax = 0;
		iHRMed = 0.0;
		iEventSpO289 = iEventSpO289Count = 0; 
		iEventBradi = iEventTachi = 0; 
		iT90 = iT89 = iT88 = iT87 = 0; 
		iT40 = iT120 = 0;
	}

	// methods of DeviceHandler interface

	@Override
	public void confirmDialog() {
		// Not used for this device
	}
	@Override
	public void cancelDialog(){
		// Not used for this device
	}

    @Override
    public boolean startOperation(OperationType ot, BTSearcherEventListener btSearchListener) {
        if (!startInit(ot, btSearchListener))
            return false;

        iServiceSearcher.clearBTSearcherEventListener();
        iServiceSearcher.addBTSearcherEventListener(this);
        if (iCmdCode == TCmd.ECmdConnByUser && iBTSearchListener != null)
            iServiceSearcher.addBTSearcherEventListener(iBTSearchListener);
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
        Log.d(TAG, "selectDevice: addr=" + bd.getAddress());
        iServiceSearcher.stopSearchDevices();
        selectedDevice = bd;
        iBtDevAddr = selectedDevice.getAddress();
        iState = TState.EGettingService;
        runBTSearcher();
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


    private void reset() {
        if (timer!=null) {
            timer.cancel();
        }
        iState = TState.EWaitingToGetDevice;
        deviceSearchCompleted = false;
        serverOpenFailed = false;
        initVar();
    }

    private void stop() {
        iServiceSearcher.stopSearchDevices();
        iServiceSearcher.clearBTSearcherEventListener();
        iServiceSearcher.close();
        iNoninOxySocket.close();
        iNoninOxySocket.removeBTSocketEventListener(this);

        if (iState == TState.EDisconnectingPairing) {
            runBTSocket();
        } else if (iState == TState.EDisconnectingOK) {
            makeResultData();
        }
        reset();
    }


    // Methods of BTSocketEventListener interface

    @Override
    public void errorThrown(int type, String description) {
        Log.e(TAG, "NoninOximeter writeErrorThrown " + type + " " + description);
        switch (type) {
            case 0: //thread interrupted
            case 4: //bluetooth close error
                reset();
                deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
                break;
            case 1: //bluetooth open error
                serverOpenFailed = true;
                deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
                break;
            case 2: //bluetooth read error
            case 3: //bluetooth write error
                iState = TState.EDisconnecting;
                runBTSocket();
                reset();
                deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
                break;
        }
    }

    @Override
    public void openDone() {
        runBTSocket();
    }

    @Override
    public void readDone() {
        runBTSocket();
    }

    @Override
    public void writeDone() {
        runBTSocket();
    }


    private void connectToServer() throws IOException {
        // this function is called when we are in EGettingService state and
        // we are going to EGettingConnection state
        Log.i(TAG, "NoninOximeter: connectToServer");
        // iCGBloodPressureSocket is an RSocket in the Symbian version
        iNoninOxySocket.addBTSocketEventListener(this);
        iNoninOxySocket.connect(selectedDevice);
    }

    private void disconnectProtocolError() {
        iNoninOxySocket.removeBTSocketEventListener(this);
        iNoninOxySocket.close();
        reset();
        deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
    }


    private void runBTSearcher() {
		switch (iState) {
		case EGettingDevice:			
			// we have found a device (not necessarily the device that we need)
			// in this case the application is the master into bluetooth
			// communication:
			// when the master has bluetooth off can't communicate with the
			// slave, so
			// the framework asks user to activate bluetooth. We arrive here
			// when the
			// user has activated and a device is found; but the device
			// scheduler don't
			// still know that bluetooth is active and so we must advice it with
			// setting
			// a new state of bluetooth
			// deviceListener.BTActivated();
			// the automatic search, find all available addresses and when we
			// arrive
			// here we must check if the found address is the same of the
			// searched
			// (in the case of search by name we must check if the device of
			// found
			// address has the same name of the searched name)
			switch (iCmdCode) {			
			case ECmdConnByAddr:
				String tmpBtDevAddr;
				tmpBtDevAddr = deviceList.elementAt(currentPos).getAddress();
				if (tmpBtDevAddr.equals(iBtDevAddr)) {
					selectDevice(deviceList.elementAt(currentPos));
				} else {
					// the name is different, so we must wait that the searcher
					// find another device
					if (deviceSearchCompleted) {
						deviceSearchCompleted = false;
						// the search is completed and we have all the devices
						// into
						// the vector
						if (currentPos <= deviceList.size() - 1) {
							currentPos++;
						} else {
							// is the last found device and there isn't the one
							// we
							// wanted: we restart the device search
							iServiceSearcher.startSearchDevices();
						}
					} else {
						// some devices lack into the vector because the search
						// is
						// not completed
						if (currentPos <= deviceList.size() - 1) {
							// we increment the current position only if there
							// are
							// other elements into the vector (otherwise we
							// remain
							// on the same position waiting the finding of new
							// devices)
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
			iState = TState.EGettingConnection;
			try {
				connectToServer();
			} catch (IOException e) {
                deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
			}
			break;

		}
	}
	
	private void runBTSocket() {
    	switch(iState) {
             case EGettingConnection:
                Log.i(TAG, "EGettingConnection in runBTSocket");
                
                if(operationType == OperationType.Pair){
                    iState = TState.EDisconnectingPairing;
                    stop();
                }  else {
                    iState = TState.EConnected;
                    
                    //Save BT mac address
                    if( iCmdCode.equals(TCmd.ECmdConnByUser)  ){
                        deviceListener.setBtMAC(iBtDevAddr);
                    }
                    
                    // Catch disconnection event waiting to read socket
                    if (iInitTX) {				
                        scheduleTimer();
                        waitOnConnection();   	
                    } else {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        sendMessage();  
                    }
                }
                break;
            case EConnected:
                Log.i(TAG, "EConnected in runBTSocket");    		
                //Controlla se è scaduto il numero di tentativi
                //di allineamento con lo strumento
                int val;
                if (!iInitTX) {// ricezione ack
                    val = iBufferAck[0];
                    Log.i(TAG, "INIT VAL: " +val);					
                    switch (val) {
                        case (0x06):
                            iInitTX = true;	
                            scheduleTimer();
                            waitOnConnection();
                            break;
                        case (0x15):							
                            sendMessage();
                            break;
                        default:
                            disconnectProtocolError();
                            deviceListener.notifyToUi(ResourceManager.getResource().getString("ECommunicationError"));
                            break;
                    }	
                    Arrays.fill(iBufferAck, (byte)0);
                    
                } else {
                    if(firstRead){
                        deviceListener.notifyToUi(ResourceManager.getResource().getString("KMeasuring"));
                        firstRead = false;
                    }
                    
                    int hr,spO2;
                    Log.d(TAG,"Status: " + iBufferData[0]);						
                    Log.d(TAG,"HR-D: " + iBufferData[1]);
                    Log.d(TAG,"SpO20D: " + iBufferData[2]);						
                    Log.d(TAG,"Status2: " + iBufferData[3]);	
                    
                    if ((iBufferData[0]&0x80)==0x80) { // primo bit a 1
                            
                        hr = (iBufferData[0]<<6) & 0xC0;
                        hr <<= 1;
                        hr |= iBufferData[1];
                        
                        spO2 = iBufferData[2] & 0x7F;
                        
                        Log.i(TAG,"HR: " + hr + " - SpO2: "+ spO2);					
    
                        iLowBattery = ((iBufferData[3]&0x01) == 0x01);

                        if ((iBufferData[3]&0x08) == 0x08) {
                            Log.i(TAG, "Finger Out");
                            
                            // se e' stata fatta almeno una lettura c'e' stato il passaggio di stato
                            if (oxyQueue.size()>0) { // e' stato tolto il dito...stop a letture
                                Log.i(TAG, "Finger Out - Measure Stop");	
                                resetTimer();
                                calcFinalData();
                                iState = TState.EDisconnectingOK;
                                stop();
                            } else {
                                scheduleTimer();
                                waitOnConnection();
                            }
                        } else { // il dito e' inserito
                            Log.i(TAG, "Finger In");
                            
                            //When SpO2 and HR cannot be computed, the system will send a missing data indicator. 
                            //For missing data, the HR equals 511 and the SpO2 equals 127												
                            if (hr!=511 && spO2!=127) { // la lettura e' OK 
                                Log.i(TAG, "Data in Queue");									
                                addNewSample(spO2,hr);		
                            } else {
                                Log.i(TAG, "Missing Data");
                            }
                            scheduleTimer();
                            waitOnConnection();
                        }
                        
                    } else{
                        disconnectProtocolError();	
                        deviceListener.notifyToUi(ResourceManager.getResource().getString("ECommunicationError"));
                    }
                    Arrays.fill(iBufferData, (byte)0);
                }                
                break;
                
            case ESendingData:
                Log.i(TAG, "ESendingData in runBTSocket");
                iState = TState.EConnected;
                // Catch disconnection event 
                // By waiting to read socket
                scheduleTimer();
                waitOnConnection();
                break;    		    
                
            case EDisconnectingOK:
                Log.i(TAG, "EDisconnectingOK in runBTSocket");
                reset();
                break;
                
            case EDisconnecting:
                Log.i(TAG, "EDisconnecting in runBTSocket");	
                iState = TState.EWaitingToGetDevice;
                iNoninOxySocket.removeBTSocketEventListener(this);
                iNoninOxySocket.close();
                reset();            	
                break;
                
            case EDisconnectingFromUser:
                Log.i(TAG, "EDisconnectingFromUser in runBTSocket");	
                iState = TState.EWaitingToGetDevice;
                iNoninOxySocket.removeBTSocketEventListener(this);
                iNoninOxySocket.close();	
                reset(); 
                break;
                
            case EDisconnectingPairing:
                Log.i(TAG, "EDisconnectingPairing in runBTSocket");
                
                //Pairing eseguito con successo. Salva il BT MAC
                iNoninOxySocket.removeBTSocketEventListener(this);
                deviceListener.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
                deviceListener.setBtMAC(iBtDevAddr);
                currentPos = 0;			  		
                break;
    	}
    }


	private void addNewSample(int aSpO2, int aHR) {
		
		if (aSpO2<iSpO2Min)
			iSpO2Min = aSpO2;
		
		if (aSpO2>iSpO2Max)
			iSpO2Max = aSpO2;

		if (aHR<iHRMin)
			iHRMin = aHR;
		
		if (aHR>iHRMax)
			iHRMax = aHR;
		
		if (aSpO2<89) {		
			iEventSpO289Count++;
			if (iEventSpO289Count == 20) 
				iEventSpO289++;
		}
		else {
			iEventSpO289Count=0;
		}
		
		if (aHR<40)
			iEventBradi++;
		if (aHR>120)	
			iEventTachi++;
		if (aSpO2<90)
			iT90++;
		if (aSpO2<89)
			iT89++;
		if (aSpO2<88)
			iT88++;
		if (aSpO2<87)
			iT87++;
		if (aHR<40)
			iT40++;
		if (aHR>120)
			iT120++;
		
		oxyQueue.add(new OxyElem(aSpO2,aHR));
	}
	
	private void calcFinalData() {

		int hrTot=0, spO2Tot=0, sampleCount;
		OxyElem elem;	
		
		sampleCount = oxyQueue.size();
		
		//iOxyStream = iOxyStream->ReAlloc(BASE_OXY_STREAM_LENGTH + SampleCount*2);	
		ByteBuffer tmpStream = ByteBuffer.allocate(BASE_OXY_STREAM_LENGTH + sampleCount*2);
		tmpStream.put(oxyStream);
		oxyStream = tmpStream.array();						
		
		// Sample num
		oxyStream[187] = (byte)((sampleCount>>8) & 0xFF);
		oxyStream[188] = (byte)((sampleCount) & 0xFF);
			
		for (int i=0; i<oxyQueue.size(); i++) {
			elem = oxyQueue.get(i);
			
			hrTot += elem.getIFreq();
			spO2Tot += elem.getISat();

			oxyStream[BASE_OXY_STREAM_LENGTH + (i*2)] =	(byte)(elem.getISat() & 0xFF);		//SpO2
			oxyStream[BASE_OXY_STREAM_LENGTH + (i*2) + 1] = (byte)(elem.getIFreq() & 0xFF);	//HR
		}
		
		iAnalysisTime = sampleCount; // tempo in secondi
		
		iSpO2Med = ((double)spO2Tot/(double)sampleCount);
		iHRMed = ((double)hrTot/(double)sampleCount);
		
	}
	
	private void makeResultData() {
			
        //MISURE
        DecimalFormat df = new DecimalFormat("0.0");

        // SpO2 Media
        int appo = (int) iSpO2Med * 10;
        oxyStream[94] = (byte)((appo>>8) & 0xFF);
        oxyStream[95] = (byte)(appo & 0xFF);

        // HR Media
        appo = (int)  iHRMed * 10;
        oxyStream[102] = (byte)((appo>>8) & 0xFF);
        oxyStream[103] = (byte)(appo & 0xFF);

        // SpO2 Min
        oxyStream[92] = (byte)((iSpO2Min>>8) & 0xFF);
        oxyStream[93] = (byte)(iSpO2Min & 0xFF);

        // HR Min
        oxyStream[100] = (byte)((iHRMin>>8) & 0xFF);
        oxyStream[101] = (byte)(iHRMin & 0xFF);

        // SpO2 Max
        oxyStream[96] = (byte)((iSpO2Max>>8) & 0xFF);
        oxyStream[97] = (byte)(iSpO2Max & 0xFF);

        // HR Max
        oxyStream[104] = (byte)((iHRMax>>8) & 0xFF);
        oxyStream[105] = (byte)(iHRMax & 0xFF);

        //Eventi SpO2 <89%
        oxyStream[110] = (byte)((iEventSpO289>>8) & 0xFF);
        oxyStream[111] = (byte)(iEventSpO289 & 0xFF);

        //Eventi di Bradicardia (HR<40)
        oxyStream[114] = (byte)((iEventBradi>>8) & 0xFF);
        oxyStream[115] = (byte)(iEventBradi & 0xFF);

        //Eventi di Tachicardia (HR>120)
        oxyStream[116] = (byte)((iEventTachi>>8) & 0xFF);
        oxyStream[117] = (byte)(iEventTachi & 0xFF);

        //SpO2 < 90%
        oxyStream[124] = (byte)calcTime(iT90).getHh();
        oxyStream[125] = (byte)calcTime(iT90).getMm();
        oxyStream[126] = (byte)calcTime(iT90).getSs();

        //SpO2 < 89%
        oxyStream[127] = (byte)calcTime(iT89).getHh();
        oxyStream[128] = (byte)calcTime(iT89).getMm();
        oxyStream[129] = (byte)calcTime(iT89).getSs();

        //SpO2 < 88%
        oxyStream[130] = (byte)calcTime(iT88).getHh();
        oxyStream[131] = (byte)calcTime(iT88).getMm();
        oxyStream[132] = (byte)calcTime(iT88).getSs();

        //SpO2 < 87%
        oxyStream[133] = (byte)calcTime(iT87).getHh();
        oxyStream[134] = (byte)calcTime(iT87).getMm();
        oxyStream[135] = (byte)calcTime(iT87).getSs();

        //HR < 40 bpm
        oxyStream[136] = (byte)calcTime(iT40).getHh();
        oxyStream[137] = (byte)calcTime(iT40).getMm();
        oxyStream[138] = (byte)calcTime(iT40).getSs();

        //HR > 120 bpm
        oxyStream[139] = (byte)calcTime(iT120).getHh();
        oxyStream[140] = (byte)calcTime(iT120).getMm();
        oxyStream[141] = (byte)calcTime(iT120).getSs();

        // DURATA
        Time tDurata = calcTime(iAnalysisTime);
        String durata = Integer.toString(iAnalysisTime);
        //String durata = String.format(Locale.ITALY,"%02d%02d%02d", tDurata.getHh(), tDurata.getMm(), tDurata.getSs());

        //Recording Time & Analysis Time
        oxyStream[84] = (byte)tDurata.getHh();
        oxyStream[85] = (byte)tDurata.getMm();
        oxyStream[86] = (byte)tDurata.getSs();
        oxyStream[87] = (byte)tDurata.getHh();
        oxyStream[88] = (byte)tDurata.getMm();
        oxyStream[89] = (byte)tDurata.getSs();

        //LIVELLO BATTERIA
        int batt;
        if (iLowBattery)
            batt = 20;
        else
            batt = 100;

        // we make the timestamp
        int year, month, day, hour, minute, second;
        GregorianCalendar calendar = new GregorianCalendar();
        year = calendar.get(Calendar.YEAR);
        // MONTH begin from 0 to 11, so we need add 1 to use it in the timestamp
        month = calendar.get(Calendar.MONTH) + 1;
        day = calendar.get(Calendar.DAY_OF_MONTH);
        hour = calendar.get(Calendar.HOUR_OF_DAY);
        minute = calendar.get(Calendar.MINUTE);
        second = calendar.get(Calendar.SECOND);

        String oxyFileName = "oxy-"+ year + month + day + "-" + hour + minute + second +".oxy";

        // Creo un istanza di Misura del tipo PR
        HashMap<String,String> tmpVal = new HashMap<>();
        tmpVal.put(GWConst.EGwCode_07, df.format(iSpO2Med).replace ('.', ','));  // O2 Med
        tmpVal.put(GWConst.EGwCode_1B, df.format(iSpO2Min).replace ('.', ','));  // O2 Min
        tmpVal.put(GWConst.EGwCode_1D, df.format(iSpO2Max).replace ('.', ','));  // O2 Max
        tmpVal.put(GWConst.EGwCode_1F, "0");
        tmpVal.put(GWConst.EGwCode_0F, df.format(iHRMed).replace ('.', ','));  // HR Med
        tmpVal.put(GWConst.EGwCode_1A, df.format(iHRMin).replace ('.', ','));  // HR Min
        tmpVal.put(GWConst.EGwCode_1C, df.format(iHRMax).replace ('.', ','));  // HR Max
        tmpVal.put(GWConst.EGwCode_1E, "0");
        tmpVal.put(GWConst.EGwCode_1G, durata);
        tmpVal.put(GWConst.EGwCode_1H, oxyFileName);  // filename
        tmpVal.put(GWConst.EGwCode_BATTERY, Integer.toString(batt)); // livello batteria

        Measure m = getMeasure();
        m.setFile(oxyStream);
        m.setFileType(XmlManager.MIR_OXY_FILE_TYPE);
        m.setMeasures(tmpVal);
        deviceListener.showMeasurementResults(m);
	}
	
	private Time calcTime(int aSec) {
		int hh = aSec/3600;
		int mm = (aSec%3600)/60;
		int ss = (aSec%3600)%60;
		return new Time(hh, mm, ss);
	}
	
	private void waitOnConnection() {		    		
	    if (!iInitTX){
	    	Log.i(TAG, "waitOnConnection reading iBufferAck ... ");
	    	iNoninOxySocket.read(iBufferAck);
	    } else {
	    	Log.i(TAG, "waitOnConnection reading iBufferData ... ");
	    	iNoninOxySocket.read(iBufferData);
	    }
    }

	private void sendMessage() {
		Log.i(TAG, "sendMessage writing iSettingMessage ... ");
	    iState = TState.ESendingData;
	    iNoninOxySocket.write(iSettingMessage);
    }
	
	private class OxyElem {
		private int iSat;
		private int iFreq;

		OxyElem(int sat, int freq) {
			iSat = sat;
			iFreq = freq;
		}
		int getISat() {
			return iSat;
		}
		int getIFreq() {
			return iFreq;
		}
	}
	
	private class Time {
		private int hh;
		private int mm;
		private int ss;
		
		public Time(int hh, int mm, int ss) {
			this.hh = hh;
			this.mm = mm;
			this.ss = ss;
		}
		
		int getHh() {
			return hh;
		}
		int getMm() {
			return mm;
		}
		int getSs() {
			return ss;
		}
	}
	
	private void scheduleTimer() {
		resetTimer();
		TimerExpired timerExpired = new TimerExpired();
		timer = new Timer();
		timer.schedule(timerExpired, K_TIME_OUT);
	}
	
	private void resetTimer() {
		if (timer!=null) {
			timer.cancel();
		}		
	}

	private class TimerExpired extends TimerTask {
		@Override
		public void run(){
			Log.i(TAG, "Timer scaduto");
			if ( iState.equals(TState.EGettingConnection) && iBufferAck[0] == 0x00 ) {
				//Ack non ricevuto --> Si disconnette
				Log.i(TAG, "Current state EGettingConnection - ACK not received - disconnecting");
				disconnectProtocolError();
			} else if ( iState.equals(TState.EConnected) && iBufferData[0] == 0x00 ) {
				//Dati non ricevuti --> Si disconnette
				Log.i(TAG, "Current state EConnected - DATA not received - disconnecting");
				disconnectProtocolError();
			}
		}
	}
}
