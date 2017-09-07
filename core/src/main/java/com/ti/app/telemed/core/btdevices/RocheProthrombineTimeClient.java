package com.ti.app.telemed.core.btdevices;

import java.io.IOException;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

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
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.util.Util;

import android.bluetooth.BluetoothDevice;
import android.util.Log;

public class RocheProthrombineTimeClient implements DeviceHandler,
		BTSocketEventListener, BTSearcherEventListener {

    private enum TTypeControl {
		kT_STX((byte) 0x02), // Start Of Data transfer block
		kT_ETX((byte) 0x03), // End Of Data transfer block
		kT_EOT((byte) 0x04), // End Of Data transfer
		kT_ACK((byte) 0x06), // Acknowledged
		kT_TAB((byte) 0x09), // Separatore
		kT_CR((byte) 0x0D), // Command terminator
		kT_DC1((byte) 0x11), // Start of binary data transfer block
		kT_NAK((byte) 0x15), // Information not received
		kT_CAN((byte) 0x18), // Abort data transfer or wake-up
		kT_SP((byte) 0x20); // Component separator

		private final byte val;

		TTypeControl(byte val) {
			this.val = val;
		}

		static byte getVal(TTypeControl tc) {
			return tc.val;
		}
	}

    private enum TTypeUserCommand {
		kT_CS((byte) 0x0C), // Change setup
		kT_CBT((byte) 0x55), // Change to Binary Transfer Mode
		kT_TO((byte) 0x5A), // Enable or disable Time Out
		kT_GEL((byte) 0x46), // Get error Log
		kT_RES((byte) 0x60), // Get Number of Results
		kT_STR((byte) 0x23), // Get Strip Counter
		kT_NAM((byte) 0x49), // Instrument Name
		kT_PD((byte) 0x1D), // Power Down
		kT_STA((byte) 0x0B), // Read and Clear Status
		kT_CON((byte) 0x43), // Read Configuration
		kT_SET((byte) 0x53), // Read Setup
		kT_MEM((byte) 0x52), // Reset Results Memory
		kT_TES((byte) 0x08), // Self-Test
		kT_SEN((byte) 0x61), // Send Results from x to y
		kT_IRT((byte) 0x44); // Set IR Dongle Time

		private final byte val;

		TTypeUserCommand(byte val) {
			this.val = val;
		}

		static byte getVal(TTypeUserCommand tuc) {
			return tuc.val;
		}
	}

	private enum TTypeParameter {
		kT_30((byte) 0x30), kT_31((byte) 0x31), kT_32((byte) 0x32), kT_33(
				(byte) 0x33), kT_34((byte) 0x34), kT_38((byte) 0x38), kT_39(
				(byte) 0x39);

		private final byte val;

		TTypeParameter(byte val) {
			this.val = val;
		}

		static byte getVal(TTypeParameter tp) {
			return tp.val;
		}
	}

	/**
	 * TState The state of the active object, determines behavior within the
	 * RunL method.
	 */
	private enum TState {
		EWaitingToGetDevice, EGettingDevice, EGettingService, EConnected, ESendingHS, // HS:
																						// HandShaking
		ESendPowerUpSeq, // Power Up Sequence (sending)
		ENumbMeasReq, // Richiesta numero misure in memoria
		ELastMeasReq, // Richiesta ultima misura
		EConfDeviceReq, // Richiesta ID device
		ESendingClosure, // FASE di chiusura
		ESendingRecovery, // Stato di recupero della Power Up Sequence
		ELastSending, ESendReadClear, // Read and Clear after error (sending)
		EWaitingAckHS, // HS: HandShaking
		EWaitPowerUpSeq, // Power Up Sequence (waiting)
		ENumbMeasRes, // Risposta numero misure in memoria
		ELastMeasRes, // Risposta ultima misura
		EConfDeviceRes, // Risposta ID device
		EWaitingClosure, // FASE di chiusura
		EWaitReadClear, // Read and Clear after error (waiting)
		EDisconnectingOK, 
		EDisconnectingPairing,
		EDisconnecting
	}

	// Indicates that the current request is not a measure but a connection request
    private boolean iPairingMode = false;

    // State of the active object
	private TState iState;
	// Type of search the scheduler requires
    private TCmd iCmdCode;

	// iServiceSearcher searches for service this client can
	// connect to (in symbian version the type was CBTUtil) and
	// substitutes RSocketServ and RSocket of symbian version too
    private BTSearcher iServiceSearcher;
	// iCGBloodPressureSocket is an RSocket in the Symbian version
    private BTSocket iPTRSocket;

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

	/* Buffer utilizzati per la trasmissione e ricezione dati. */
	private byte[] iSendDataCmd;
	private byte[] iAckAwaited;
	private byte[] iAckReceived;
	private byte[] iByteReceived;
	private String iDataReceived;
	private String iLastMeasure;

	/* Sequenzalizzazione dei comandi. */
	private byte[] iCmdSequence;
	private int iPosCmd;

	/* Contatori per le varie fasi del protocollo. */
	private int iCountHS;
	private int iCountPowerUpSeq;
	private int iCountNMR;
	private int iCountClosure;
	private int iCountReadClear;

	/** CRC. */
	private int iCRC;

	/** Dati relativi alle misure */
	private String iINR;
	private String iSec;
	private String iQ;

    private boolean operationDeleted;
    private boolean serverOpenFailed;
	// a pointer to scheduler
    private DeviceListener iScheduler;
    private Measure iMeasure;

    private boolean isTimerExpired;
    private TimerExpired timerExpired;
	private Timer timer;
	private BTSearcherEventListener scanActivityListener;
	private boolean demoMode = false;

	private static final String TAG = "RocheProthrombineClient";


	public static boolean needPairing(UserDevice userDevice) {
		return false;
	}

	public static boolean needConfig(UserDevice userDevice) {
		return false;
	}


	public RocheProthrombineTimeClient(DeviceListener aScheduler, Measure m) {
		
		demoMode  = Util.isDemoRocheMode();
		iState = TState.EWaitingToGetDevice;
		iScheduler = aScheduler;
        iMeasure = m;
		deviceSearchCompleted = false;
		/* Buffer utilizzati per la trasmissione e ricezione dati. */
		iSendDataCmd = new byte[1];
		iAckAwaited = new byte[1];
		iAckReceived = new byte[1];
		iByteReceived = new byte[1];
		iDataReceived = null;
		iLastMeasure = null;
		// Contatori
		iCountHS = 0;
		iCountPowerUpSeq = 0;
		iCountNMR = 0;
		iCountClosure = 0;
		iCountReadClear = 0;
		// Sequenzializzatore comandi
		iCmdSequence = null;
		iPosCmd = 0;
		// Dati relativi alle misure
		iINR = null;
		iSec = null;
		iQ = null;
		// CRC
		iCRC = 0;

		isTimerExpired = false;

		operationDeleted = false;
		serverOpenFailed = false;
		iServiceSearcher = new BTSearcher();

		iPTRSocket = BTSocket.getBTSocket();
	}


	// methods of DeviceListener interface

    @Override
    public void confirmDialog() {

        makeSendData(TTypeControl.getVal(TTypeControl.kT_CAN));
        iCountHS = 0;

        iState = TState.ESendingHS;
        sendCmd();
    }

    @Override
    public void cancelDialog(){
        stop();
    }

	@Override
	public void reset() {
		if (timer != null) {
			timer.cancel();
		}
		// we free all buffer, descriptor and array
		deviceSearchCompleted = false;
		/* Buffer utilizzati per la trasmissione e ricezione dati. */
		iSendDataCmd = new byte[1];
		iAckAwaited = new byte[1];
		iAckReceived = new byte[1];
		iByteReceived = new byte[1];
		iDataReceived = null;
		iLastMeasure = null;
		// Contatori
		iCountHS = 0;
		iCountPowerUpSeq = 0;
		iCountNMR = 0;
		iCountClosure = 0;
		iCountReadClear = 0;
		// Sequenzializzatore comandi
		iCmdSequence = null;
		iPosCmd = 0;
		// Dati relativi alle misure
		iINR = null;
		iSec = null;
		iQ = null;
		// CRC
		iCRC = 0;

		isTimerExpired = false;

		operationDeleted = false;
		serverOpenFailed = false;

		// this class object must return to the initial state
		iState = TState.EWaitingToGetDevice;
	}

	@Override
	public void start(String deviceInfo, boolean pairingMode) {
        iPairingMode = pairingMode;
        iBtDevAddr = deviceInfo;
        iCmdCode = TCmd.ECmdConnByAddr;
		if (iState == TState.EWaitingToGetDevice) {
			// it changes state
			iState = TState.EGettingDevice;
			iServiceSearcher.addBTSearcherEventListener(this);
			iServiceSearcher.setSearchType(iCmdCode);
			// it launch the automatic procedures for the manual device search
			iServiceSearcher.startSearchDevices();
		}
	}

	@Override
    public void start(BTSearcherEventListener listener, boolean pairingMode) {
        iPairingMode = pairingMode;
        iCmdCode = TCmd.ECmdConnByUser;
        if (iState == TState.EWaitingToGetDevice) {
			// it changes state
			iState = TState.EGettingDevice;
			// in questo caso ci sono 2 listener... uno è DeviceScanActivity,
			// l'altro è la classe stessa
			scanActivityListener = listener;
			iServiceSearcher.addBTSearcherEventListener(listener);
			iServiceSearcher.addBTSearcherEventListener(this);
			iServiceSearcher.setSearchType(iCmdCode);
			// it launch the automatic procedures for the manual device search
			iServiceSearcher.startSearchDevices();
		}
	}

	@Override
	public void stop() {
		Log.i(TAG, "PTR: stop");
		if (iState == TState.EGettingDevice) {
			iServiceSearcher.stopSearchDevices(-1);
			iServiceSearcher.removeBTSearcherEventListener(this);
			if (scanActivityListener != null) {
				iServiceSearcher
						.removeBTSearcherEventListener(scanActivityListener);
			}
			iServiceSearcher.close();
			iState = TState.EWaitingToGetDevice;
			// we advise the scheduler of the end of the activity on the device
			iScheduler.operationCompleted();
		} else if (iState == TState.EGettingService) {
			iServiceSearcher.close();
			iState = TState.EWaitingToGetDevice;
			// we advise the scheduler of the end of the activity on the device
			iScheduler.operationCompleted();
		} else if (iState == TState.EDisconnectingOK) {
			if (iCmdCode.equals(TCmd.ECmdConnByUser)) {
				iScheduler.setBtMAC(iBTAddress);
			}
			iPTRSocket.close();
			iPTRSocket.removeBTSocketEventListener(this);
			iState = TState.EWaitingToGetDevice;
						
			Log.d(TAG, "iLastMeasure [48]=" + iLastMeasure.charAt(48) + " ? " + 0x31);
			if (!demoMode && iLastMeasure.charAt(48) == 0x31) {
                String msg = ResourceManager.getResource().getString("KNoNewMeasure");
				iScheduler.notifyError(msg,msg);
			}
			else {
				makeResultData();
			}			
		} else if (iState == TState.EDisconnectingPairing) {
			iPTRSocket.close();
        	iPTRSocket.removeBTSocketEventListener(this);
        	runBTSocket();
        	// we advise the scheduler of the end of the activity on the device
            iScheduler.operationCompleted();
		} else {
			Log.i(TAG, "PTR: stop ELSE");
			if (!serverOpenFailed) {
				Log.i(TAG, "PTR: stop !serverOpenFailed");
				if (timer != null)
					timer.cancel();
				// cancels all outstanding operations on socket
				iPTRSocket.close();
				iPTRSocket.removeBTSocketEventListener(this);
			}
			iState = TState.EWaitingToGetDevice;
			// we advise the scheduler of the end of the activity on the device
			iScheduler.operationCompleted();
		}
		iServiceSearcher.removeBTSearcherEventListener(this);
	}

	@Override
	public void stopDeviceOperation(int selected) {
		if (selected == -1) {
			stop();
		} else if (selected == -2) {
			iServiceSearcher.stopSearchDevices(-1);
			iServiceSearcher.removeBTSearcherEventListener(this);
			if (iState != TState.EGettingDevice) {
				// we advise the scheduler of the end of the activity on the
				// device
				iScheduler.operationCompleted();
			}
		} else {
			iServiceSearcher.stopSearchDevices(selected);
		}
	}


    // methods of BTSearchEventListener interface

    @Override
    public void deviceSelected(BTSearcherEvent evt) {
        Log.i(TAG, "ROCHE: deviceSelected");
        // we change status
        iState = TState.EGettingService;
        runBTSearcher();
    }

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


	// methods of BTSocketEventListener interface

	@Override
	public void errorThrown(BTSocketEvent evt, int type, String description) {
		switch (type) {
		case 0: // thread interrupted
			reset();
			Log.e(TAG, description);
			iScheduler.notifyError(description,description);
			break;
		case 1: // bluetooth open error
			Log.e(TAG, description);
			if (iState == TState.EConnected) {
				// if we don't receive any message from the blood pressure at
				// this state
				// means that we have to do the pairing
				iState = TState.EDisconnecting;
				operationDeleted = true;
				serverOpenFailed = true;
				runBTSocket();
			}
			reset();
            String msg = ResourceManager.getResource().getString("ECommunicationError");
			iScheduler.notifyError(msg,msg);
			break;
		case 2: // bluetooth read error
        case 3: // bluetooth write error
			Log.e(TAG, description);
			iState = TState.EDisconnecting;
			operationDeleted = true;
			runBTSocket();
			reset();
            msg = ResourceManager.getResource().getString("ECommunicationError");
			iScheduler.notifyError(msg,msg);
			break;
		case 4: // bluetooth close error
			Log.e(TAG, description);
			reset();
            msg = ResourceManager.getResource().getString("ECommunicationError");
            iScheduler.notifyError(msg,msg);
			break;
		}
	}

	@Override
	public void openDone(BTSocketEvent evt) {
		runBTSocket();
	}

	@Override
	public void readDone(BTSocketEvent evt) {
		runBTSocket();
	}

	@Override
	public void writeDone(BTSocketEvent evt) {
		runBTSocket();
	}


	// methods for parsing data

	/**
	 * Costruisce il messaggio XML da restituire allo scheduler e prepara i dati
	 * per la visualizzazione
	 */
	private void makeResultData() {
		dataParser();

        HashMap<String,String> tmpVal = new HashMap<>();
        tmpVal.put(GWConst.EGwCode_0V, iSec);
        tmpVal.put(GWConst.EGwCode_0Z, iINR);
        tmpVal.put(GWConst.EGwCode_0X, iQ);
        iMeasure.setMeasures(tmpVal);
        iMeasure.setFile(null);
        iMeasure.setFileType(null);
        iMeasure.setFailed(false);
		iMeasure.setBtAddress(iBTAddress);

        iScheduler.showMeasurementResults(iMeasure);
	}

	/**
	 * Parsifica il buffer dei dati ricevuti per ricavare le misure corrette
	 */
	private void dataParser() {
		int pos;

		String tempBuf = iLastMeasure;
		String tempMeasure;
		
		pos = tempBuf.indexOf(TTypeControl.getVal(TTypeControl.kT_TAB));

		// INR
		tempBuf = tempBuf.substring(pos + 1);
		tempMeasure = tempBuf.substring(0, 4);
		iINR = tempMeasure.substring(0, 3) + "," + tempMeasure.substring(3);
		while (iINR.charAt(0) == 48) {
			iINR = iINR.substring(1);
		}
		Log.i(TAG, "Data parser. INR = " + iINR);

		pos = tempBuf.indexOf(TTypeControl.getVal(TTypeControl.kT_TAB));

		// %Q
		tempBuf = tempBuf.substring(pos + 1);
		tempMeasure = tempBuf.substring(0, 4);
		iQ = tempMeasure;
		while (iQ.charAt(0) == 48) {
			iQ = iQ.substring(1);
		}
		Log.i(TAG, "Data parser. %Q = " + iQ);

		pos = tempBuf.indexOf(TTypeControl.getVal(TTypeControl.kT_TAB));

		// sec
		tempBuf = tempBuf.substring(pos + 1);
		tempMeasure = tempBuf.substring(0, 4);
		iSec = tempMeasure.substring(0, 3) + "," + tempMeasure.substring(3);
		while (iSec.charAt(0) == 48) {
			iSec = iSec.substring(1);
		}
		Log.i(TAG, "Data parser. sec = " + iSec);
	}

	// methods for socket

	/**
	 * Costruisce il buffer dei dati da inviare.
	 */
	private void makeSendData(byte aType) {
		iSendDataCmd[0] = aType;
	}

	/**
	 * Costruisce il buffer dei dati in attesa.
	 */
	private void makeAwaitedData(byte aType) {
		iAckAwaited[0] = aType;
	}

	/**
	 * Invio comando
	 */
	private void sendCmd() {
		Log.i(TAG, "ROCHE sendCmd " + Integer.toHexString(iSendDataCmd[0]));
		iPTRSocket.write(iSendDataCmd);
	}

	private void readAck() {
		try {
			timerExpired = new TimerExpired();
			timer = new Timer();
			timer.schedule(timerExpired, 1000);
			Log.i(TAG, "readAck...");
			iPTRSocket.read(iAckReceived);
		} catch (Exception e) {
			Log.e(TAG, "EXCEPTION reading ack: " + e.getMessage());
            stop();
		}
	}

	private void readData() {
		timerExpired = new TimerExpired();
		timer = new Timer();
		timer.schedule(timerExpired, 1000);
		iPTRSocket.read(iByteReceived);
	}

	/**
	 * Preleva dal primo pacchetto il CRC per il controllo dei pacchetti
	 * successivi
	 */
	private void getCRC() {
		int tempCRC = 0;

		Log.i(TAG, iDataReceived);
		if ((iDataReceived.charAt(9) >= 48) && (iDataReceived.charAt(9) <= 57)) {
			tempCRC = iDataReceived.charAt(9) - 48;
		} else if ((iDataReceived.charAt(9) >= 65)
				&& (iDataReceived.charAt(9) <= 70)) {
			tempCRC = iDataReceived.charAt(9) - 55;
		}

		iCRC = tempCRC << 4;

		if ((iDataReceived.charAt(10) >= 48)
				&& (iDataReceived.charAt(10) <= 57)) {
			tempCRC = iDataReceived.charAt(10) - 48;
		} else if ((iDataReceived.charAt(10) >= 65)
				&& (iDataReceived.charAt(10) <= 70)) {
			tempCRC = iDataReceived.charAt(10) - 55;
		}

		iCRC = iCRC | tempCRC;
	}

	private boolean calculateCheckSum() {
		Log.i(TAG, iDataReceived);

		int dataLen = 0;
		int checkSumCalculated = iCRC;
		int checkSumReceived = 0;

		if ((iDataReceived.charAt(1) >= 48) && (iDataReceived.charAt(1) <= 57)) {
			dataLen = (iDataReceived.charAt(1) - 48) << 4;
		} else if ((iDataReceived.charAt(1) >= 65)
				&& (iDataReceived.charAt(1) <= 70)) {
			dataLen = (iDataReceived.charAt(1) - 55) << 4;
		}

		if ((iDataReceived.charAt(2) >= 48) && (iDataReceived.charAt(2) <= 57)) {
			dataLen = dataLen | (iDataReceived.charAt(2) - 48);
		} else if ((iDataReceived.charAt(2) >= 65)
				&& (iDataReceived.charAt(2) <= 70)) {
			dataLen = dataLen | (iDataReceived.charAt(2) - 55);
		}

		for (int index = 0; index < dataLen; index++) {
			checkSumCalculated = checkSumCalculated
					^ iDataReceived.charAt(3 + index);
		}

		if ((iDataReceived.charAt(3 + dataLen) >= 48)
				&& (iDataReceived.charAt(3 + dataLen) <= 57)) {
			checkSumReceived = (iDataReceived.charAt(3 + dataLen) - 48) << 4;
		} else if ((iDataReceived.charAt(3 + dataLen) >= 65)
				&& (iDataReceived.charAt(3 + dataLen) <= 70)) {
			checkSumReceived = (iDataReceived.charAt(3 + dataLen) - 55) << 4;
		}

		if ((iDataReceived.charAt(3 + dataLen + 1) >= 48)
				&& (iDataReceived.charAt(3 + dataLen + 1) <= 57)) {
			checkSumReceived = checkSumReceived
					| (iDataReceived.charAt(3 + dataLen + 1) - 48);
		} else if ((iDataReceived.charAt(3 + dataLen + 1) >= 65)
				&& (iDataReceived.charAt(3 + dataLen + 1) <= 70)) {
			checkSumReceived = checkSumReceived
					| (iDataReceived.charAt(3 + dataLen + 1) - 55);
		}

		return (checkSumReceived == checkSumCalculated);
	}

	/**
	 * Segnala errore di allineamento e resetta connessione
	 */
	private void disconnectProtocolError() {
		
		Log.d(TAG, "disconnectProtocolError()");
		
		// Invia messaggio di errore allo scheduler
		//if (!isRecoveryStatus)
			iScheduler.notifyToUi(ResourceManager.getResource().getString(
                    "KErrAlignDevices"));

		/*
		iCountReadClear = 0;

		makeSendData(TTypeControl.getVal(TTypeControl.kT_CAN));

		iState = TState.ESendReadClear;
		sendCmd();
		*/
		//isRecoveryStatus = true;
		recoveryFromError();
		
		makeSendData(TTypeControl.getVal(TTypeControl.kT_CAN));

		iState = TState.ESendingHS;
		sendCmd();
	}

    private void connectToServer() throws IOException {
        // this function is called when we are in EGettingService state and
        // we are going to EGettingConnection state
        iBTAddress = iServiceSearcher.getCurrBTDevice().getAddress();
        // iPTRSocket is an RSocket in the Symbian version
        iPTRSocket.addBTSocketEventListener(this);
        iPTRSocket.connect(iServiceSearcher.getCurrBTDevice());
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
			// iScheduler.BTActivated();
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
					// we pass the position of the selected device into the
					// devices vector
					iServiceSearcher.stopSearchDevices(currentPos);
				} else {
					// the address is different, so we must wait that the
					// searcher
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
                iScheduler.notifyWaitToUi(ResourceManager.getResource().getString("KConnectingDev"));
				break;
			}
			break;

		case EGettingService:
			iState = TState.EConnected;
			try {
				// the search is finished and we stop it (we remove from event
				// list)
				iServiceSearcher.removeBTSearcherEventListener(this);
				if (scanActivityListener != null) {
					iServiceSearcher
							.removeBTSearcherEventListener(scanActivityListener);
				}
				connectToServer();
			} catch (IOException e) {
                String msg = ResourceManager.getResource().getString("EBtDeviceConnError");
				iScheduler.notifyError(msg,msg);
			}
			break;

		}
	}

	/**
* 
*/
	private void runBTSocket() {
		String tmpString;
		switch (iState) {
		case EConnected:
			Log.i(TAG, "EConnected");
			
			if(iPairingMode){
    			iState = TState.EDisconnectingPairing;
                stop();
    		}  else {
    			if (iCmdCode.equals(TCmd.ECmdConnByUser)) {
    				Log.i(TAG, "iBTAddress: " + iBTAddress);
        			iScheduler.setBtMAC(iBTAddress);
    			}
    			// Strumento trovato si procede all'allineamento dei dispositivi
    			iScheduler.notifyToUi(ResourceManager.getResource().getString(
                        "KAlignDevices"));
    						
    			iScheduler.askSomething(
    					ResourceManager.getResource().getString("KAlignDevicesSend"),
    	    			ResourceManager.getResource().getString("confirmButton"), 
    	    			ResourceManager.getResource().getString("cancelButton"));
    		}						
			break;

		case ESendingHS:
			Log.i(TAG, "ESendingHS");
			iState = TState.EWaitingAckHS;
			if (!isTimerExpired) {
				Log.i(TAG, "Timer not expired");
				makeAwaitedData(TTypeControl.getVal(TTypeControl.kT_NAK));
				Log.i(TAG, "Reading ACK");
				readAck();
			} else {
				Log.i(TAG, "Timer expired");
				isTimerExpired = false;
				timerExpired = new TimerExpired();
				timer = new Timer();
				Log.i(TAG, "Scheduling new Timer");
				timer.schedule(timerExpired, 1000);
			}
			break;

		case ESendPowerUpSeq:
			Log.i(TAG, "PTR: ESendPowerUpSeq" + iCountPowerUpSeq);
			if (iCountPowerUpSeq == 0 || iCountPowerUpSeq == 2
					|| iCountPowerUpSeq == 4 || iCountPowerUpSeq == 6) {
				makeAwaitedData(TTypeControl.getVal(TTypeControl.kT_ACK));
			} else if (iCountPowerUpSeq == 3) {
				makeAwaitedData(TTypeUserCommand
						.getVal(TTypeUserCommand.kT_STA));
			}
			iState = TState.EWaitPowerUpSeq;
			readAck();
			break;

		case ENumbMeasReq:
			Log.i(TAG, "PTR: ENumbMeasReq");
			if (iCountNMR == 0) {
				makeAwaitedData(iSendDataCmd[0]);
			} else {
				makeAwaitedData(TTypeControl.getVal(TTypeControl.kT_ACK));
			}
			iState = TState.ENumbMeasRes;
			readAck();
			break;

		case ELastMeasReq:
			Log.i(TAG, "PTR: ELastMeasReq");
			if (iPosCmd == 7) {
				makeAwaitedData(TTypeControl.getVal(TTypeControl.kT_ACK));
			} else {
				makeAwaitedData(iSendDataCmd[0]);
			}
			iState = TState.ELastMeasRes;
			readAck();
			break;

		case EConfDeviceReq:
			Log.i(TAG, "PTR: EConfDeviceReq");
			if (iPosCmd == 3) {
				makeAwaitedData(TTypeControl.getVal(TTypeControl.kT_ACK));
			} else {
				makeAwaitedData(iSendDataCmd[0]);
			}
			iState = TState.EConfDeviceRes;
			readAck();
			break;

		case ESendingClosure:
			Log.i(TAG, "PTR: ESendingClosure");
			if (iCountClosure == 0) {
				makeAwaitedData(TTypeUserCommand.getVal(TTypeUserCommand.kT_PD));
			} else if (iCountClosure == 1) {
				makeAwaitedData(TTypeControl.getVal(TTypeControl.kT_ACK));
			}
			iState = TState.EWaitingClosure;
			readAck();
			break;

		case ELastSending:
			Log.i(TAG, "PTR: ELastSending");
			iState = TState.EDisconnectingOK;
            stop();
			break;

		case ESendReadClear:
			Log.i(TAG, "PTR: ESendReadClear");
			iState = TState.EWaitReadClear;
			//isRecoveryStatus = true;
			if (!isTimerExpired) {
				if (iCountReadClear == 0) {
					makeAwaitedData(TTypeControl.getVal(TTypeControl.kT_NAK));
				} else if (iCountReadClear == 1) {
					makeAwaitedData(TTypeUserCommand
							.getVal(TTypeUserCommand.kT_STA));
				} else if (iCountReadClear == 2 || iCountReadClear == 4
						|| iCountReadClear == 6) {
					makeAwaitedData(TTypeControl.getVal(TTypeControl.kT_ACK));
				} else if (iCountReadClear == 5) {
					makeAwaitedData(TTypeUserCommand
							.getVal(TTypeUserCommand.kT_PD));
				}

				readAck();
			} else {
				isTimerExpired = false;
				timerExpired = new TimerExpired();
				timer = new Timer();
				timer.schedule(timerExpired, 1000);
			}
			break;

		case ESendingRecovery:
			Log.i(TAG, "ROCHE ESendingRecovery ");
			makeAwaitedData(TTypeControl.getVal(TTypeControl.kT_ACK));
			iState = TState.EWaitPowerUpSeq;
			readAck();
			break;

		case EWaitingAckHS:
			Log.i(TAG, "ROCHE EWaitingAckHS " + iCountHS + " - "
					+ iAckReceived[0]);
			timer.cancel();

			if (iCountHS == 0) {
				if (iAckAwaited[0] == iAckReceived[0]) {
					makeSendData(TTypeUserCommand
							.getVal(TTypeUserCommand.kT_STA));
					iCountHS = 1;
				}
				iState = TState.ESendingHS;
				sendCmd();
			} else if (iCountHS == 1) {
				if (iAckAwaited[0] == iAckReceived[0]) {
					makeAwaitedData(TTypeUserCommand
							.getVal(TTypeUserCommand.kT_STA));
					iCountHS = 2;
					iState = TState.EWaitingAckHS;
					readAck();
				} else if (iAckReceived[0] == TTypeUserCommand
						.getVal(TTypeUserCommand.kT_STA)) {
					makeSendData(TTypeControl.getVal(TTypeControl.kT_CR));
					iScheduler.notifyWaitToUi(ResourceManager.getResource()
							.getString("KMeasuring"));
					iState = TState.ESendPowerUpSeq;
					sendCmd();
				} else if (iAckReceived[0] == TTypeControl
						.getVal(TTypeControl.kT_NAK)) {
					makeSendData(TTypeControl.getVal(TTypeControl.kT_CAN));
					iCountHS = 0;

					iState = TState.ESendingHS;
					sendCmd();
				} else {
					disconnectProtocolError();
				}
			} else if (iCountHS == 2) {
				if (iAckAwaited[0] == iAckReceived[0]) {
					makeSendData(TTypeControl.getVal(TTypeControl.kT_CR));
					iState = TState.ESendPowerUpSeq;
					sendCmd();
				} else if (iAckReceived[0] == TTypeControl
						.getVal(TTypeControl.kT_NAK)) {
					makeSendData(TTypeControl.getVal(TTypeControl.kT_CAN));
					iCountHS = 0;

					iState = TState.ESendingHS;
					sendCmd();
				} else {
					disconnectProtocolError();
				}
			}
			break;

		case EWaitPowerUpSeq:
			Log.i(TAG, "ROCHE EWaitPowerUpSeq" + iCountPowerUpSeq);
			timer.cancel();

			if (iCountPowerUpSeq == 0) {
				Log.i(TAG, "EWaitPowerUpSeq 0 " + iAckReceived[0]);
				if (iAckAwaited[0] == iAckReceived[0]) {
					Log.i(TAG, "EWaitPowerUpSeq 0.1 ");
					iCountPowerUpSeq = 1;
					readData();
				} else if (iAckReceived[0] == TTypeUserCommand
						.getVal(TTypeUserCommand.kT_STA)) {
					Log.i(TAG, "EWaitPowerUpSeq 0.2 ");
					isTimerExpired = false;
					timerExpired = new TimerExpired();
					timer = new Timer();
					timer.schedule(timerExpired, 1000);

					makeSendData(TTypeUserCommand
							.getVal(TTypeUserCommand.kT_STA));
					iCountHS = 0;
					iCountClosure = 0;
					iState = TState.ESendingRecovery;
					sendCmd();

					// readData();
				} else {
					Log.i(TAG, "EWaitPowerUpSeq 0.3 ");
					disconnectProtocolError();
				}
			} else if (iCountPowerUpSeq == 1) {
				Log.i(TAG, "EWaitPowerUpSeq 1 " + iByteReceived[0]);
				if (iByteReceived[0] == TTypeControl
						.getVal(TTypeControl.kT_EOT)) {
					tmpString = new String(iByteReceived);
					iDataReceived = iDataReceived.concat(tmpString);

					// Dal primo pacchetto dati ricevuto
					// preleva due byte per il calcolo delle
					// della CheckSum dei pacchetti futuri
					//getCRC();

					makeSendData(TTypeControl.getVal(TTypeControl.kT_ACK));

					iCountPowerUpSeq = 2;

					iState = TState.ESendPowerUpSeq;
					sendCmd();
				} else {
					if (iDataReceived == null) {
						iDataReceived = new String(iByteReceived);
					} else if (iDataReceived.length() == 0) {
						iDataReceived = new String(iByteReceived);
					} else {
						tmpString = new String(iByteReceived);
						iDataReceived = iDataReceived.concat(tmpString);
					}
					readData();
				}
			} else if (iCountPowerUpSeq == 2) {
				Log.i(TAG, "EWaitPowerUpSeq 2 " + iAckReceived[0]);
				if (iAckAwaited[0] == iAckReceived[0]) {
					makeSendData(TTypeUserCommand
							.getVal(TTypeUserCommand.kT_STA));

					iCountPowerUpSeq = 3;

					iState = TState.ESendPowerUpSeq;
					sendCmd();
				} else {
					disconnectProtocolError();
				}
			} else if (iCountPowerUpSeq == 3) {
				Log.i(TAG, "EWaitPowerUpSeq 2 " + iAckReceived[0]);
				if (iAckAwaited[0] == iAckReceived[0]) {
					makeSendData(TTypeControl.getVal(TTypeControl.kT_CR));

					iCountPowerUpSeq = 4;

					iState = TState.ESendPowerUpSeq;
					sendCmd();
				} else {
					disconnectProtocolError();
				}
			} else if (iCountPowerUpSeq == 4) {
				Log.i(TAG, "EWaitPowerUpSeq 2 " + iAckReceived[0]);
				if (iAckAwaited[0] == iAckReceived[0]) {
					iCountPowerUpSeq = 5;
					iDataReceived = "";
					readData();
				} else {
					disconnectProtocolError();
				}
			} else if (iCountPowerUpSeq == 5) {
				Log.i(TAG, "EWaitPowerUpSeq 2 " + iByteReceived[0]);
				if (iByteReceived[0] == TTypeControl
						.getVal(TTypeControl.kT_EOT)) {
					tmpString = new String(iByteReceived);
					iDataReceived = iDataReceived.concat(tmpString);

					if (checkStatusRegister()) {
						getCRC();
						
						if (calculateCheckSum()) {
							makeSendData(TTypeControl.getVal(TTypeControl.kT_ACK));

							iCountPowerUpSeq = 6;

							iState = TState.ESendPowerUpSeq;
							sendCmd();
						} else {
							disconnectProtocolError();
						}
					}
					else {
						iPTRSocket.close();
						iPTRSocket.removeBTSocketEventListener(this);
						iState = TState.EWaitingToGetDevice;
                        String msg = ResourceManager.getResource().getString("ECommunicationError");
						iScheduler.notifyError(msg,msg);
					}
				} else {
					if (iDataReceived == null) {
						iDataReceived = new String(iByteReceived);
					} else if (iDataReceived.length() == 0) {
						iDataReceived = new String(iByteReceived);
					} else {
						tmpString = new String(iByteReceived);
						iDataReceived = iDataReceived.concat(tmpString);
					}
					readData();
				}
			} else if (iCountPowerUpSeq == 6) {
				Log.i(TAG, "EWaitPowerUpSeq 2 " + iAckReceived[0]);
				if (iAckAwaited[0] == iAckReceived[0]) {
					// Richiede ultima misura
					iCountNMR = 0;
					
					//isRecoveryStatus = false;

					makeSendData(TTypeUserCommand
							.getVal(TTypeUserCommand.kT_RES));

					iState = TState.ENumbMeasReq;
					sendCmd();
				} else {
					disconnectProtocolError();
				}
			}
			break;

		case ENumbMeasRes:
			timer.cancel();

			if (iCountNMR == 0) {
				if (iAckAwaited[0] == iAckReceived[0]) {
					iCountNMR++;
					makeSendData(TTypeControl.getVal(TTypeControl.kT_CR));

					iState = TState.ENumbMeasReq;
					sendCmd();
				} else {
					disconnectProtocolError();
				}
			} else if (iCountNMR == 1) {
				if (iAckAwaited[0] == iAckReceived[0]) {
					iCountNMR++;

					iState = TState.ENumbMeasRes;
					readData();
				} else {
					disconnectProtocolError();
				}
			} else if (iCountNMR == 2) {
				if (iByteReceived[0] == TTypeControl
						.getVal(TTypeControl.kT_EOT)) {
					iCountNMR++;

					tmpString = new String(iByteReceived);
					iDataReceived = iDataReceived.concat(tmpString);

					if (calculateCheckSum()) {
						makeSendData(TTypeControl.getVal(TTypeControl.kT_ACK));

						iState = TState.ENumbMeasReq;
						sendCmd();
					} else {
						disconnectProtocolError();
					}
				} else {
					if (iDataReceived == null) {
						iDataReceived = new String(iByteReceived);
					} else if (iDataReceived.length() == 0) {
						iDataReceived = new String(iByteReceived);
					} else {
						tmpString = new String(iByteReceived);
						iDataReceived = iDataReceived.concat(tmpString);
					}
					readData();
				}
			} else if (iCountNMR == 3) {
				if (iAckAwaited[0] == iAckReceived[0]) {
					iDataReceived = "";

					iPosCmd = 0;

					iCmdSequence = new byte[9];
					iCmdSequence[0] = TTypeUserCommand
							.getVal(TTypeUserCommand.kT_SEN);
					iCmdSequence[1] = TTypeControl.getVal(TTypeControl.kT_TAB);
					iCmdSequence[2] = TTypeParameter
							.getVal(TTypeParameter.kT_31);
					iCmdSequence[3] = TTypeControl.getVal(TTypeControl.kT_TAB);
					iCmdSequence[4] = TTypeParameter
							.getVal(TTypeParameter.kT_30);
					iCmdSequence[5] = TTypeParameter
							.getVal(TTypeParameter.kT_30);
					iCmdSequence[6] = TTypeParameter
							.getVal(TTypeParameter.kT_31);
					iCmdSequence[7] = TTypeControl.getVal(TTypeControl.kT_CR);
					iCmdSequence[8] = TTypeControl.getVal(TTypeControl.kT_ACK);

					makeSendData(iCmdSequence[iPosCmd]);

					iState = TState.ELastMeasReq;
					sendCmd();
				} else {
					disconnectProtocolError();
				}
			}
			break;

		case ELastMeasRes:
			timer.cancel();

			if (iPosCmd == 7) {
				if (iAckAwaited[0] == iAckReceived[0]) {
					iPosCmd++;

					iState = TState.ELastMeasRes;
					readData();
				} else {
					disconnectProtocolError();
				}
			} else if (iPosCmd == 8) {
				if (iByteReceived[0] == TTypeControl
						.getVal(TTypeControl.kT_EOT)) {
					tmpString = new String(iByteReceived);
					iDataReceived = iDataReceived.concat(tmpString);

					if (calculateCheckSum()) {
						makeSendData(iCmdSequence[iPosCmd]);

						iPosCmd++;

						iState = TState.ELastMeasReq;
						sendCmd();
					} else {
						disconnectProtocolError();
					}
				} else {
					if (iDataReceived == null) {
						iDataReceived = new String(iByteReceived);
					} else if (iDataReceived.length() == 0) {
						iDataReceived = new String(iByteReceived);
					} else {
						tmpString = new String(iByteReceived);
						iDataReceived = iDataReceived.concat(tmpString);
					}
					readData();
				}
			} else if (iPosCmd == 9) {
				if (iAckAwaited[0] == iAckReceived[0]) {
					iPosCmd = 0;

					iLastMeasure = iDataReceived;
					iDataReceived = "";

					iCmdSequence = new byte[5];
					iCmdSequence[0] = TTypeUserCommand
							.getVal(TTypeUserCommand.kT_CON);
					iCmdSequence[1] = TTypeControl.getVal(TTypeControl.kT_TAB);
					iCmdSequence[2] = TTypeParameter
							.getVal(TTypeParameter.kT_33);
					iCmdSequence[3] = TTypeControl.getVal(TTypeControl.kT_CR);
					iCmdSequence[4] = TTypeControl.getVal(TTypeControl.kT_ACK);

					makeSendData(iCmdSequence[iPosCmd]);

					iState = TState.EConfDeviceReq;
					sendCmd();
				} else {
					disconnectProtocolError();
				}
			} else {
				if (iAckAwaited[0] == iAckReceived[0]) {
					iPosCmd++;
					makeSendData(iCmdSequence[iPosCmd]);

					iState = TState.ELastMeasReq;
					sendCmd();
				} else {
					disconnectProtocolError();
				}
			}
			break;

		case EConfDeviceRes:
			timer.cancel();

			if (iPosCmd == 3) {
				if (iAckAwaited[0] == iAckReceived[0]) {
					iPosCmd++;

					iState = TState.EConfDeviceRes;
					readData();
				} else {
					disconnectProtocolError();
				}
			} else if (iPosCmd == 4) {
				if (iByteReceived[0] == TTypeControl
						.getVal(TTypeControl.kT_EOT)) {
					tmpString = new String(iByteReceived);
					iDataReceived = iDataReceived.concat(tmpString);

					if (calculateCheckSum()) {
						iDataReceived = "";

						makeSendData(iCmdSequence[iPosCmd]);

						iPosCmd++;

						iState = TState.EConfDeviceReq;
						sendCmd();
					} else {
						disconnectProtocolError();
					}
				} else {
					if (iDataReceived == null) {
						iDataReceived = new String(iByteReceived);
					} else if (iDataReceived.length() == 0) {
						iDataReceived = new String(iByteReceived);
					} else {
						tmpString = new String(iByteReceived);
						iDataReceived = iDataReceived.concat(tmpString);
					}
					readData();
				}
			} else if (iPosCmd == 5) {
				if (iAckAwaited[0] == iAckReceived[0]) {
					iCountClosure = 0;

					makeSendData(TTypeUserCommand
							.getVal(TTypeUserCommand.kT_PD));

					iState = TState.ESendingClosure;
					sendCmd();
				} else {
					disconnectProtocolError();
				}
			} else {
				if (iAckAwaited[0] == iAckReceived[0]) {
					iPosCmd++;
					makeSendData(iCmdSequence[iPosCmd]);

					iState = TState.EConfDeviceReq;
					sendCmd();
				} else {
					disconnectProtocolError();
				}
			}
			break;

		case EWaitingClosure:
			Log.i(TAG, "PTR: EWaitingClosure");
			Log.i(TAG, "PTR: " + iAckReceived[0]);

			timer.cancel();

			if (iAckAwaited[0] == iAckReceived[0]) {
				if (iCountClosure == 0) {
					makeSendData(TTypeControl.getVal(TTypeControl.kT_CR));
					iState = TState.ESendingClosure;
				} else if (iCountClosure == 1) {
					makeSendData(TTypeControl.getVal(TTypeControl.kT_ACK));
					iState = TState.ELastSending;
				}

				iCountClosure++;

				sendCmd();
			} else {
				disconnectProtocolError();
			}
			break;

		case EDisconnectingOK:
			Log.i(TAG, "PTR: EDisconnectingOK");
			// EditXMLMessage();
			break;
			
		case EDisconnectingPairing:
			Log.i(TAG, "PTR: EDisconnectingPairing");
			//Pairing eseguito con successo. Salva il BT MAC
    		iPTRSocket.removeBTSocketEventListener(this);
    		iScheduler.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
			iScheduler.setBtMAC(iBTAddress);				
			currentPos = 0;
			break;

		case EWaitReadClear:
			Log.i(TAG, "PTR: EWaitReadClear");

			timer.cancel();

			if (iAckAwaited[0] == iAckReceived[0]) {
				Log.i(TAG, "iCountReadClear " + iCountReadClear);
				Log.i(TAG, "EWaitReadClear " + iAckReceived[0]);
				if (iCountReadClear == 0) {
					makeSendData(TTypeUserCommand
							.getVal(TTypeUserCommand.kT_STA));
					iState = TState.ESendReadClear;
					iCountReadClear++;
					sendCmd();
				} else if (iCountReadClear == 1) {
					makeSendData(TTypeControl.getVal(TTypeControl.kT_CR));
					iState = TState.ESendReadClear;
					iCountReadClear++;
					iDataReceived = "";
					sendCmd();
				} else if (iCountReadClear == 2) {
					iCountReadClear++;
					readData();
				} else if (iCountReadClear == 3) {
					if (iByteReceived[0] == TTypeControl
							.getVal(TTypeControl.kT_EOT)) {
						tmpString = new String(iByteReceived);
						iDataReceived = iDataReceived.concat(tmpString);

						for (int i = 0; i < iDataReceived.length(); i++)
							Log.i(TAG, "-------rec " + iDataReceived.charAt(i));

						boolean checkResult = calculateCheckSum();
						Log.d(TAG, "checkResult=" + checkResult);
						if (checkResult) {
							makeSendData(TTypeControl
									.getVal(TTypeControl.kT_ACK));

							iCountReadClear++;

							iState = TState.ESendReadClear;
							sendCmd();
						}
						else {							
							disconnectProtocolError();
						}
					} else {
						if (iDataReceived == null) {
							iDataReceived = new String(iByteReceived);
						} else if (iDataReceived.length() == 0) {
							iDataReceived = new String(iByteReceived);
						} else {
							tmpString = new String(iByteReceived);
							iDataReceived = iDataReceived.concat(tmpString);
						}
						readData();
					}
				} else if (iCountReadClear == 4) {
					// Richiede ultima misura
					iCountNMR = 0;
					//isRecoveryStatus = false;
					iScheduler.notifyWaitToUi(ResourceManager.getResource()
							.getString("KConnMsgMsgRochePTR"));

					makeSendData(TTypeUserCommand
							.getVal(TTypeUserCommand.kT_RES));

					iState = TState.ENumbMeasReq;
					sendCmd();
				} else if (iCountReadClear == 5) {
					makeSendData(TTypeControl.getVal(TTypeControl.kT_CR));

					iCountReadClear++;

					iState = TState.ESendReadClear;
					sendCmd();
				} else if (iCountReadClear == 6) {
					// reset();
					recoveryFromError();

					// iScheduler.Connected(KAlignMsgPTR,iScheduler.iUserData->iAudio);

					makeSendData(TTypeControl.getVal(TTypeControl.kT_CAN));

					iCountHS = 0;

					iState = TState.ESendingHS;
					sendCmd();
				}
			} else {
				Log.e(TAG, "EWaitReadClear ERROR " + iAckReceived[0]);
				// DA MODIFICARE
				disconnectProtocolError();
			}
			break;

		case EDisconnecting:
			if (operationDeleted) {
				// Chiusura su procedura di errore
				iPTRSocket.removeBTSocketEventListener(this);
				iPTRSocket.close();
				iState = TState.EWaitingToGetDevice;
			}
			break;
		}
	}

	private void recoveryFromError() {

		// Buffer
		iSendDataCmd = new byte[1];
		iAckAwaited = new byte[1];
		iAckReceived = new byte[1];
		iByteReceived = new byte[1];
		iDataReceived = null;
		iLastMeasure = null;

		// Contatori
		iCountHS = 0;
		iCountPowerUpSeq = 0;
		iCountNMR = 0;
		iCountClosure = 0;

		// Sequenzializzatore comandi
		iCmdSequence = null;
		iPosCmd = 0;

		// Dati relativi alle misure
		iINR = null;
		iSec = null;
		iQ = null;
	}

	
	private boolean checkStatusRegister() {
		
		return ((iDataReceived.charAt(4) == 0x30) && 
				(iDataReceived.charAt(5) == 0x30) && 
				(iDataReceived.charAt(6) == 0x30) &&
				(iDataReceived.charAt(7) == 0x30));
	}


	private class TimerExpired extends TimerTask {
		@Override
		public void run() {
			isTimerExpired = true;
			if (iState == TState.EWaitingAckHS) {
				Log.i(TAG, "TIMER SCADUTO");
				iState = TState.ESendingHS;

				makeSendData(TTypeControl.getVal(TTypeControl.kT_CAN));
				sendCmd();
			} else {
				Log.i(TAG, "TIMER SCADUTO disconnectProtocolError");
				disconnectProtocolError();
			}
		}
	}
}
