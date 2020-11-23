package com.ti.app.telemed.core.btdevices;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.util.Log;

import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.BTSearcher;
import com.ti.app.telemed.core.btmodule.BTSearcherEventListener;
import com.ti.app.telemed.core.btmodule.BTSocket;
import com.ti.app.telemed.core.btmodule.BTSocketEventListener;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.common.Patient;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.util.Util;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Set;
import java.util.Vector;

public class MIRSpirodocNew extends DeviceHandler implements
        BTSocketEventListener,
        BTSearcherEventListener {

	private enum CommunicationStatus {
		WaitingConnection,
		SendingStartCmd,
		WaitingStartMsg,
		SendingReadProgCmd,
		WaitingReadProgMsg,
		SendingWriteProgCmd,
		SendingWriteProgRecord,
		SendingGetBestRecord,
		WaitingGetBestAnswer,
		WaitingGetBestnumBytes,
		WaitingGetBestRecord,
		Disconnecting
	}

	private CommunicationStatus comStatus;

	private enum TTypeMessage {
		READY(0x0D),
		START_CONFIG(0x31),
		START_SPIRO(0x35),
		START_OXY(0x45),
		INIT_CONF_STD(0x55),
		INIT_CONF_SMP(0x65),
		DATA_END(0xFF),
		DATA_NULL(0x00),
		DATA_ONE(0x01),
		CURVE_END(0x0A),
		ERR_TX(0x1B),
		ERR_GEN(0x45),
		ERR_CF(0x46),
		ERR_FULL(0x47);

		private int value;

		TTypeMessage(int value) {
			this.value = value;
		}
	}

	private enum TTypeCommand {
		DATA_CONF(0xD0),
		DATA_TEST_SPIRO(0xD1),
		DATA_TEST_OXY(0xD2);

		private int value;

		TTypeCommand(int value) {
			this.value = value;
		}
	}

    private enum TSpirodocType {
		DEV_STANDARD(0xC0),
		DEV_SIMPLE(0xC1);

		private int value;

		TSpirodocType(int value) {
			this.value = value;
		}
	}

	// prefisso chiave di registro dove viene salvato il tipo di device (Standard/Simple)
	private static final String REGKEY = "MIRSpirodoc";

	// iServiceSearcher searches for service this client can
	// connect to (in symbian version the type was CBTUtil) and
	// substitutes RSocketServ and RSocket of symbian version too
    private BTSearcher iServiceSearcher;
	// iIEMBloodPressureSocket is an RSocket in the Symbian version
    private BTSocket iMIRSpiroDocSocket;

    private Vector<BluetoothDevice> deviceList;
    private int currentPos;
	private BluetoothDevice selectedDevice;
	private boolean deviceSearchCompleted;

	private static final String TAG = "MIRSpirodocNEW";

    public static boolean isStandardModel(String btAddr) {
        TSpirodocType spirodocType = TSpirodocType.DEV_SIMPLE;
		if (btAddr != null && !btAddr.isEmpty()) {
			String typeString = Util.getRegistryValue(REGKEY + btAddr);
			if (typeString.length() > 0)
				spirodocType = TSpirodocType.valueOf(typeString);
		}
        return spirodocType == TSpirodocType.DEV_STANDARD;
    }

	public static boolean needConfig(UserDevice userDevice) {
		return true;
	}

	public MIRSpirodocNew(DeviceListener listener, UserDevice ud) {
        super(listener, ud);
		deviceSearchCompleted = false;
		iServiceSearcher = new BTSearcher();
		iMIRSpiroDocSocket = BTSocket.getBTSocket();
	}

    // abstract methods of DeviceHandler

    @Override
    public void confirmDialog() {
    }
    @Override
    public void cancelDialog(){
    }

    @Override
    public boolean startOperation(OperationType ot, BTSearcherEventListener btSearchListener) {
        if (!startInit(ot, btSearchListener))
            return false;

        iServiceSearcher.clearBTSearcherEventListener();
        iServiceSearcher.addBTSearcherEventListener(this);
        if (iCmdCode == TCmd.ECmdConnByUser)
            iServiceSearcher.addBTSearcherEventListener(iBTSearchListener);
        iServiceSearcher.startSearchDevices();
		deviceListener.notifyToUi(ResourceManager.getResource().getString("KSearchingDev"));
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
        iState = TState.EGettingService;
        selectedDevice = bd;
        iBtDevAddr = selectedDevice.getAddress();
        runBTSearcher();
    }


    // BTSearcherEventListener Interface Methods

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
        iBtDevAddr = null;
        deviceSearchCompleted = false;
        // this class object must return to the initial state
        iState = TState.EWaitingToGetDevice;
    }


	private void stop()  {
		iServiceSearcher.stopSearchDevices();
		iServiceSearcher.removeBTSearcherEventListener(this);
		iServiceSearcher.close();
		iMIRSpiroDocSocket.close();
		iMIRSpiroDocSocket.removeBTSocketEventListener(this);

		if (iState == TState.EDisconnectingFromUser) {
			runBTSocket();
		}
		reset();
	}

	private void connectToServer() throws IOException {
		// this function is called when we are in EGettingService state and
		// we are going to EGettingConnection state
		if (operationType == OperationType.Pair) {
			BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
			if (pairedDevices.size() > 0) {
				for (BluetoothDevice device : pairedDevices) {
					if (device.getAddress().equalsIgnoreCase(iBtDevAddr)) {
						try {
							Method m = device.getClass().getMethod("removeBond", (Class[]) null);
							m.invoke(device, (Object[]) null);
						} catch (Exception e) {
							Log.e(TAG, e.getMessage());
						}
					}
				}
			}
		}

		comStatus = CommunicationStatus.WaitingConnection;
		iMIRSpiroDocSocket.addBTSocketEventListener(this);
		iMIRSpiroDocSocket.connect(selectedDevice);
	}

	// BTSocketEventListener Interface Methods

    @Override
	public void openDone() {
		runBTSocket();
	}

    @Override
	public void readDone() {
		runBTSocket();
	}

    @Override	public void writeDone() {
		runBTSocket();
	}

    @Override
    public void errorThrown(int type, String description) {
		Log.d(TAG, "writeErrorThrown type=" + type + " description=" + description);
		
		switch (type) {
		case 0: // thread interrupted
			reset();
			Log.e(TAG, description);
			deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
			break;
		case 1: // bluetooth open error
			Log.e(TAG, description);
			if (iState == TState.EConnected) {
				// if we don't receive any message from the blood pressure at
				// this state
				// means that we have to do the pairing
				iState = TState.EDisconnecting;
				runBTSocket();
			}
			reset();
            deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
			break;
		case 2: // bluetooth read error
		case 3: // bluetooth write error
			Log.e(TAG, description);
			iState = TState.EDisconnecting;
			runBTSocket();
			reset();
            deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
			break;
		case 4: // bluetooth close error
			Log.e(TAG, description);
			reset();
            deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
			break;
		}
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
					// the address is the same
                    selectDevice(deviceList.elementAt(currentPos));
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
				// the selection done by user is managed in the ui class which
				// implements BTSearcherEventListener interface, so here arrive
				// when the selection is already done
				deviceListener.notifyWaitToUi(ResourceManager.getResource().getString("KPairingMsg"));
				break;
			}
		 
			break;

		case EGettingService:
			iState = TState.EConnected;
			Log.i(TAG, "runBtSearcher EGettingService");
			try {
				// the search is finished and we stop it (we remove from event
				// list)
				iServiceSearcher.removeBTSearcherEventListener(this);
				if (iBTSearchListener != null) {
					iServiceSearcher.removeBTSearcherEventListener(iBTSearchListener);
				}
				if (iMIRSpiroDocSocket.isAvailable()) {
					connectToServer();
				} else {
					Log.e(TAG, "Risorsa non disponibile");
				}
			} catch (IOException e) {
                deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
			}
			break;
		default:
			break;

		}
	}

	/************************************************************************************************/

	private static final byte COMMAND_ON = 0x00;
	private static final byte COMMAND_RX_APRO = (byte) 0xC5;
	private static final byte COMMAND_TX_APRO = (byte) 0xC4;
	private static final byte COMMAND_BEST_SPIRO = (byte)0xD1;

	private static final byte CODE_ERROR = 0x45;

	private static final int STARTUP_SEQUENCE_LEN = 32;
	private static final int PROG_AREA_LEN = 718;

	private enum TMyDoctorErrorCode {
		KErrNone, 
		EMyDoctorErrWrongDevice, 
		EMyDoctorErrCommunicationError, 
		EMyDoctorErrBtHidden, 
		EMyDoctorErrBtDeactivated, 
		EMyDoctorErrBtConfiguration, 
		EMyDoctorErrWrongData, 
		EMyDoctorErrNoMeasure, 
		EMyDoctorErrConfigWrong, 
		EMyDoctorErrCFWrong, 
		EMyDoctorErrMemoryFull, 
		EMyDoctorErrPairing, 
		EMyDoctorErrStrip, 
		EMyDoctorErrBtActivation, 
		EMyDoctorErrWrongWalkTest, 
		EMyDoctorBtActivationCancel
	}


    private TMyDoctorErrorCode iErrorCode;

    private byte[] iCmd = new byte[1]; // comandi inviati da smartphone a spirodoc

    // contiene msg di info iniziale inviato da spirodoc
    private byte[] startupSequenceMsg = new byte[STARTUP_SEQUENCE_LEN];
	private byte[] progAreaMsg = new byte[PROG_AREA_LEN];
	private byte[] numBytes = new byte[2];
	private byte[] bestRecord;


	private void runBTSocket() {
		
		Log.d(TAG, "runBTSocket() comStatus=" + comStatus);

		switch (comStatus) {
			case WaitingConnection:
				deviceListener.notifyToUi(ResourceManager.getResource().getString("KConnectingDev"));
				// Invio comando iniziale
				comStatus = CommunicationStatus.SendingStartCmd;
				Fill(iCmd, COMMAND_ON, 1);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				SendData();
				break;

			case SendingStartCmd:
				iErrorCode = TMyDoctorErrorCode.KErrNone;
				comStatus = CommunicationStatus.WaitingStartMsg;
				RequestData();
				break;

			case WaitingStartMsg:
				Log.d(TAG,"StartMessage: " + Util.toHexString(startupSequenceMsg));
				if (operationType == OperationType.Pair) {
					if (iErrorCode == MIRSpirodocNew.TMyDoctorErrorCode.KErrNone) {
						// Pairing eseguito con successo. Salva il BT MAC
						iMIRSpiroDocSocket.removeBTSocketEventListener(this);
						deviceListener.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
						deviceListener.setBtMAC(iBtDevAddr);
						currentPos = 0;
						// Chiude la connessione
						comStatus = CommunicationStatus.Disconnecting;
						DisconnectFromServer();
					}
				} else {
					comStatus = CommunicationStatus.SendingReadProgCmd;
					Fill(iCmd, COMMAND_RX_APRO, 1);
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					SendData();
				}
				break;

			case SendingReadProgCmd:
				iErrorCode = TMyDoctorErrorCode.KErrNone;
				comStatus = CommunicationStatus.WaitingReadProgMsg;
				RequestData();
				break;

			case WaitingReadProgMsg:
				logProgArea();
				if (operationType == OperationType.Measure) {
					Log.d(TAG,"INVIO Configurazone ... ");
					comStatus = CommunicationStatus.SendingGetBestRecord;
					Fill(iCmd, COMMAND_BEST_SPIRO, 1);
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					SendData();
				} else {
					Log.d(TAG,"INVIO Configurazone ... ");
					comStatus = CommunicationStatus.SendingWriteProgCmd;
					Fill(iCmd, COMMAND_TX_APRO, 1);
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					SendData();
				}
				break;

			case SendingWriteProgCmd:
				updateProgArea();
				comStatus = CommunicationStatus.SendingWriteProgRecord;
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				SendData();
				break;

			case SendingWriteProgRecord:
				//deviceListener.configReady();
				deviceListener.notifyError(DeviceListener.MEASUREMENT_ERROR, "");
				currentPos = 0;
				comStatus = CommunicationStatus.Disconnecting;
				DisconnectFromServer();
				break;

			case SendingGetBestRecord:
				iErrorCode = TMyDoctorErrorCode.KErrNone;
				// Attesa della risposta alla richiesta del BEST Spiro Record
				comStatus = CommunicationStatus.WaitingGetBestAnswer;
				RequestData();
				break;

			case WaitingGetBestAnswer:
				Log.d(TAG, "WaitingGetBestAnswer: Answer = " + iCmd[0]);
				if (iCmd[0] == CODE_ERROR) {
					deviceListener.notifyError(DeviceListener.NO_MEASURES_FOUND,  ResourceManager.getResource().getString("KNoNewMeasure"));
					currentPos = 0;
					comStatus = CommunicationStatus.Disconnecting;
					DisconnectFromServer();
				} else {
					comStatus = CommunicationStatus.WaitingGetBestnumBytes;
					RequestData();
				}
				break;

			case WaitingGetBestnumBytes:
				int num = numBytes[0] * 256 + numBytes[1];
				Log.d(TAG, "Num Bytes = " + num);
				bestRecord = new byte[num+8];
				comStatus = CommunicationStatus.WaitingGetBestRecord;
				RequestData();
				break;

			case WaitingGetBestRecord:
				Log.d(TAG, "BEST Spiro Record Ricevuto");
				logMeasure();
				deviceListener.notifyError(DeviceListener.MEASUREMENT_ERROR, "");
				currentPos = 0;
				// Chiude la connessione
				comStatus = CommunicationStatus.Disconnecting;
				DisconnectFromServer();
				break;

			case Disconnecting:
				iState = TState.EWaitingToGetDevice;
				break;

			default:
				break;
		}
	}

	private void logProgArea() {
		Log.d(TAG,"Progammable Area Data:");
		Calendar c = GregorianCalendar.getInstance();
		int year = (progAreaMsg[109] & 255) + ((progAreaMsg[108] & 255) << 8);
		int month = (progAreaMsg[107] & 255) + ((progAreaMsg[106] & 255) << 8) - 1;
		int day = (progAreaMsg[105] & 255) + ((progAreaMsg[104] & 255) << 8);
		int hour = (progAreaMsg[111] & 255) + ((progAreaMsg[110] & 255) << 8);
		int minutes = (progAreaMsg[113] & 255) + ((progAreaMsg[112] & 255) << 8);
		int seconds = (progAreaMsg[115] & 255) + ((progAreaMsg[114] & 255) << 8);
		c.set(year, month, day, hour, minutes, seconds);
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
		String name = new String(Arrays.copyOfRange(progAreaMsg, 238, 258), StandardCharsets.UTF_8).trim();
		String cognome = new String(Arrays.copyOfRange(progAreaMsg, 258, 278), StandardCharsets.UTF_8).trim();
		String id = new String(Arrays.copyOfRange(progAreaMsg, 278, 298), StandardCharsets.UTF_8).trim();
		int progrec = (progAreaMsg[159] & 255) + ((progAreaMsg[158] & 255) << 8);
		String sex = new String(Arrays.copyOfRange(progAreaMsg, 160, 162), StandardCharsets.UTF_8).trim();
		int etnia = (progAreaMsg[162] & 255) + ((progAreaMsg[162] & 255) << 8);
		int age = (progAreaMsg[165] & 255) + ((progAreaMsg[164] & 255) << 8);
		int altezza = (progAreaMsg[167] & 255) + ((progAreaMsg[166] & 255) << 8);
		int peso = (progAreaMsg[169] & 255) + ((progAreaMsg[168] & 255) << 8);
		int p_tipo_PARAM = (progAreaMsg[49] & 255) + ((progAreaMsg[48] & 255) << 8);
		int p_settings = (progAreaMsg[81] & 255) + ((progAreaMsg[80] & 255) << 8);
		Log.d(TAG,"nome="+name+" cognome="+cognome+" id="+id+" Time="+df.format(c.getTime()));
		Log.d(TAG,"data nascita = "+Util.toHexString(Arrays.copyOfRange(progAreaMsg, 232, 238)));
		Log.d(TAG,"Rec.Nr.="+progrec+" Sesso="+sex+" etnia="+etnia+" età="+age+" altezza="+altezza+" peso="+peso);
		Log.d(TAG,"p_tipo_PARAM="+p_tipo_PARAM+" p_settings="+p_settings);
	}

	private void updateProgArea() {
		Patient patient = UserManager.getUserManager().getCurrentPatient();
		byte[] buf = patient.getName().getBytes(StandardCharsets.UTF_8);
		Arrays.fill(progAreaMsg,238,258,(byte)0x00);
		System.arraycopy(buf, 0, progAreaMsg, 238, Math.min(buf.length, 20));
		buf = patient.getSurname().getBytes(StandardCharsets.UTF_8);
		Arrays.fill(progAreaMsg,258,278,(byte)0x00);
		System.arraycopy(buf, 0, progAreaMsg, 258, Math.min(buf.length, 20));
		// Età
		int age = getAge(patient.getBirthdayDate());
		progAreaMsg[164] = (byte)(age >>> 8);
		progAreaMsg[165] = (byte)age;
		// Altezza
		progAreaMsg[166] = (byte)(Integer.parseInt(patient.getHeight()) >>> 8);
		progAreaMsg[167] = (byte)Integer.parseInt(patient.getHeight());
		// Peso
		progAreaMsg[168] = (byte)((int)Float.parseFloat(patient.getWeight()) >>> 8);
		progAreaMsg[169] = (byte)(int)Float.parseFloat(patient.getWeight());

		// Inserimento nuovo Paziente (non necessario)
		progAreaMsg[144] = 0;
		progAreaMsg[145] = 1;

		// Forza Modlaità dottore (non funziona)
		//progAreaMsg[80] = 0;
		//progAreaMsg[81] = 1;
	}

	private void logMeasure() {
		Log.d(TAG,"Dati Misura:");
		Util.logFile("MIRLog.log",bestRecord);
		Calendar c = GregorianCalendar.getInstance();
		int day = (bestRecord[3] & 255) + ((bestRecord[2] & 255) << 8);
		int month = (bestRecord[5] & 255) + ((bestRecord[4] & 255) << 8) - 1;
		int year = (bestRecord[7] & 255) + ((bestRecord[6] & 255) << 8);
		int hour = (bestRecord[9] & 255) + ((bestRecord[8] & 255) << 8);
		int minutes = (bestRecord[11] & 255) + ((bestRecord[10] & 255) << 8);
		int seconds = (bestRecord[13] & 255) + ((bestRecord[12] & 255) << 8);
		c.set(year, month, day, hour, minutes, seconds);
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
		Log.d(TAG,"Timestamp Misura="+df.format(c.getTime()));
		int fvc = (bestRecord[61] & 255) + ((bestRecord[60] & 255) << 8);
		int fev1 = (bestRecord[63] & 255) + ((bestRecord[62] & 255) << 8);
		int fvvc = (bestRecord[75] & 255) + ((bestRecord[74] & 255) << 8);
		int fvv1 = (bestRecord[77] & 255) + ((bestRecord[76] & 255) << 8);
		Log.d(TAG,"fvc="+fvc+" fev1="+fev1);
		Log.d(TAG,"fvvc="+fvvc+" fvv1="+fvv1);
		/*
		for (byte b : bestRecord) {
			int v = b & 0xff;
			Log.d(TAG, "" + v);
		}
		*/
	}

	private int getAge(String birthDate) {
		// validate inputs ...
		Date now = new Date();
		DateFormat formatter = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
		int d1 = Integer.parseInt(birthDate);
		int d2 = Integer.parseInt(formatter.format(now));
		return (d2 - d1) / 10000;
	}

	private void Fill(byte[] b, byte v, int l) {
		for (int i = 0; i < l; i++) {
			b[i] = v;
		}
	}

	private void DisconnectFromServer() {
		iMIRSpiroDocSocket.removeBTSocketEventListener(this);
		iMIRSpiroDocSocket.close();
		runBTSocket();
	}

	private void SendData() {
		Log.d(TAG, "SendDataL() iState" + iState);

		switch (comStatus) {
			case SendingStartCmd:
			case SendingReadProgCmd:
			case SendingWriteProgCmd:
			case SendingGetBestRecord:
				iMIRSpiroDocSocket.write(iCmd);
				break;
			case SendingWriteProgRecord:
				iMIRSpiroDocSocket.write(progAreaMsg);
				break;
			default:
				// errore di programmazione uscire dall'applicazione
				deviceListener.notifyError(DeviceListener.MEASUREMENT_ERROR, "");
				break;
		}
	}

	private void RequestData() {
		Log.d(TAG, "RequestData() comStatus=" + comStatus);
		
		switch (comStatus) {
			case WaitingStartMsg:
				iMIRSpiroDocSocket.read(startupSequenceMsg); // primo pacchetto inviato da 32 byte
				break;
			case WaitingReadProgMsg:
				iMIRSpiroDocSocket.read(progAreaMsg); // primo pacchetto inviato da
				break;
			case WaitingGetBestAnswer:
				iMIRSpiroDocSocket.read(iCmd); // primo pacchetto inviato da
				break;
			case WaitingGetBestnumBytes:
				iMIRSpiroDocSocket.read(numBytes); // primo pacchetto inviato da
				break;
			case WaitingGetBestRecord:
				iMIRSpiroDocSocket.read(bestRecord); // primo pacchetto inviato da
				break;
		}
	}
}
