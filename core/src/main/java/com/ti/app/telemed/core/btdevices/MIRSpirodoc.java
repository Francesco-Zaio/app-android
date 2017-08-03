package com.ti.app.telemed.core.btdevices;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Set;
import java.util.Vector;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.util.Log;

import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.BTSearcher;
import com.ti.app.telemed.core.btmodule.BTSocket;
import com.ti.app.telemed.core.btmodule.events.BTSearcherEvent;
import com.ti.app.telemed.core.btmodule.events.BTSearcherEventListener;
import com.ti.app.telemed.core.btmodule.events.BTSocketEvent;
import com.ti.app.telemed.core.btmodule.events.BTSocketEventListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.Patient;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.xmlmodule.XmlManager;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.util.Util;

public class MIRSpirodoc implements DeviceHandler,
        BTSocketEventListener,
        BTSearcherEventListener {

	private enum TState {
		EWaitingToGetDevice, 
		EGettingDevice, 
		EGettingService, 
		EConnected, 
		EWaitingInitPacket, 
		EWaitingInfoPacket, 
		EWaitingControlPacket, 
		EWaitingInfoPatientPacket, 
		EWaitingInfoCurvePacket, 
		EWaitingCurve, 
		EWaitingCurveEnd, 
		EWaitingTheorSpiroPacket, 
		ESendingStartCmd, 
		ESendingConf, 
		ESendingReady, 
		EDisconnecting, 
		EDisconnectingFromUser, 
		EDisconnectingOK
	}

	private enum TTypeMessage {
		READY(0x0D), START_CONFIG(0x31), START_SPIRO(0x35), START_OXY(0x45), INIT_CONF_STD(0x55), INIT_CONF_SMP(0x65), DATA_END(0xFF), DATA_NULL(0x00), DATA_ONE(0x01), CURVE_END(0x0A), ERR_TX(0x1B), ERR_GEN(0x45), ERR_CF(0x46), ERR_FULL(0x47);

		private int value;

		TTypeMessage(int value) {
			this.value = value;
		}
	}

	private enum TTypeCommand {
		DATA_CONF(0xD0), DATA_TEST_SPIRO(0xD1), DATA_TEST_OXY(0xD2);

		private int value;

		TTypeCommand(int value) {
			this.value = value;
		}
	}

    private enum TSpirodocType {
		DEV_STANDARD(0xC0), DEV_SIMPLE(0xC1);

		private int value;

		TSpirodocType(int value) {
			this.value = value;
		}
	}

	// prefisso chiave di registro dove viene salvato il tipo di device (Standard/Simple)
	private static final String REGKEY = "MIRSpirodoc";

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
	// iIEMBloodPressureSocket is an RSocket in the Symbian version
    private BTSocket iMIRSpiroDocSocket;

	// BT address obtained from scheduler (in symbian version the
	// type was TBTDevAddr)
    private String iBtDevAddr;

	// BT address of the found device (we can have not received bt address from
	// scheduler or it can be changed, so we take this value from the searcher
	// after the device finding)
	private String iBTAddress;

    private Measure iMeasure;

    private Vector<BluetoothDevice> deviceList;
    private int currentPos;
    private boolean deviceSearchCompleted;
    private boolean serverOpenFailed;

	// a pointer to scheduler
    private DeviceListener iScheduler;
	private BTSearcherEventListener scanActivityListener;
	private String deviceModel;

	private static final String TAG = "MIRSpirodoc";

    public static boolean isStandardModel(String btAddr) {
        TSpirodocType spirodocType = TSpirodocType.DEV_SIMPLE;
        String typeString = Util.getRegistryValue(REGKEY+btAddr);
        if (typeString.length() > 0)
            spirodocType = TSpirodocType.valueOf(typeString);
        return spirodocType == TSpirodocType.DEV_STANDARD;
    }

	public MIRSpirodoc(DeviceListener aScheduler, Measure m, int aMsrType, String deviceModel) {

        iScheduler = aScheduler;
        iMeasure = m;
		this.deviceModel = deviceModel;
				
		switch (aMsrType) {
			case 1:
				iCommand = TTypeCommand.DATA_CONF;
				break;
			case 2:
				iCommand = TTypeCommand.DATA_TEST_SPIRO;
				break;
			case 3:
				iCommand = TTypeCommand.DATA_TEST_OXY;
				break;
		}

		iState = TState.EWaitingToGetDevice;
		iScheduler = aScheduler;
		deviceSearchCompleted = false;
		serverOpenFailed = false;
		iServiceSearcher = new BTSearcher();
		iMIRSpiroDocSocket = BTSocket.getBTSocket();
	}

	private void connectToServer() throws IOException {
		// this function is called when we are in EGettingService state and
		// we are going to EGettingConnection state
		iBTAddress = iServiceSearcher.getCurrBTDevice().getAddress();
		
		if (iPairingMode) {
            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    if (device.getAddress().equalsIgnoreCase(iBTAddress)) {
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
		
		// iCGBloodPressureSocket is an RSocket in the Symbian version
		iMIRSpiroDocSocket.addBTSocketEventListener(this);
		iMIRSpiroDocSocket.connectInsecure(iServiceSearcher.getCurrBTDevice());
	}

	// methods of DeviceHandler interface

    @Override
    public void confirmDialog() {
    }
    @Override
    public void cancelDialog(){
    }

    @Override
	public void start(String deviceInfo, boolean pairingMode) {

        iBtDevAddr = deviceInfo;
        iPairingMode = pairingMode;
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
        iCmdCode = TCmd.ECmdConnByUser;
        iPairingMode = pairingMode;
		if (iState == TState.EWaitingToGetDevice) {
			// it changes state
			iState = TState.EGettingDevice;
			scanActivityListener = listener;
			// in questo caso ci sono 2 listener... uno è DeviceScanActivity,
			// l'altro è la classe stessa
			iServiceSearcher.addBTSearcherEventListener(listener);
			iServiceSearcher.addBTSearcherEventListener(this);
			iServiceSearcher.setSearchType(iCmdCode);
			// it launch the automatic procedures for the manual device search
			iServiceSearcher.startSearchDevices();
		}
	}

	@Override
	public void reset() {
		iBTAddress = null;
		deviceSearchCompleted = false;
		serverOpenFailed = false;
		// this class object must return to the initial state
		iState = TState.EWaitingToGetDevice;
	}

    @Override
	public void stop()  {
		if (iState == TState.EGettingDevice) {
			iServiceSearcher.stopSearchDevices(-1);
			iServiceSearcher.removeBTSearcherEventListener(this);
			if (scanActivityListener != null) {
				iServiceSearcher.removeBTSearcherEventListener(scanActivityListener);
			}
			iServiceSearcher.close();
			// we advise the scheduler of the end of the activity on the device
			iScheduler.operationCompleted();
		} else if (iState == TState.EGettingService) {
			iServiceSearcher.close();
			// we advise the scheduler of the end of the activity on the device
			iScheduler.operationCompleted();
		} else if (iState == TState.EDisconnectingFromUser) {
			iMIRSpiroDocSocket.close();
			runBTSocket();
			// we advise the scheduler of the end of the activity on the device
			iScheduler.operationCompleted();
		} else if (iState == TState.EDisconnectingOK) {
			iMIRSpiroDocSocket.close();
			iMIRSpiroDocSocket.removeBTSocketEventListener(this);
			iState = TState.EWaitingToGetDevice;
			//makeResultData();
		} else {
			if (!serverOpenFailed) {
				// cancels all outstanding operations on socket
				iMIRSpiroDocSocket.close();
				iMIRSpiroDocSocket.removeBTSocketEventListener(this);
			}
			// we advise the scheduler of the end of the activity on the device
			iScheduler.operationCompleted();
		}
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


	// BTSearcherEventListener Interface Methods

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
	public void deviceSelected(BTSearcherEvent evt) {
		Log.i(TAG, "IEMBP: deviceSelected");
		// we change status
		iState = TState.EGettingService;

		runBTSearcher();
	}


	// BTSocketEventListener Interface Methods

    @Override
	public void openDone(BTSocketEvent evt) {
		runBTSocket();
	}

    @Override
	public void readDone(BTSocketEvent evt) {
		runBTSocket();
	}

    @Override	public void writeDone(BTSocketEvent evt) {
		runBTSocket();
	}

    @Override
    public void errorThrown(BTSocketEvent evt, int type, String description) {
		
		Log.d(TAG, "errorThrown type=" + type + " description=" + description);
		
		switch (type) {
		case 0: // thread interrupted
			reset();
			Log.e(TAG, description);
			iScheduler.notifyError(description,"");
			break;
		case 1: // bluetooth open error
			Log.e(TAG, description);
			if (iState == TState.EConnected) {
				// if we don't receive any message from the blood pressure at
				// this state
				// means that we have to do the pairing
				iState = TState.EDisconnecting;
				serverOpenFailed = true;
				runBTSocket();
			}
			reset();
			iScheduler.notifyError(ResourceManager.getResource().getString("ECommunicationError"),"");
			break;
		case 2: // bluetooth read error
			Log.e(TAG, description);
			iState = TState.EDisconnecting;
			runBTSocket();
			reset();
			iScheduler.notifyError(ResourceManager.getResource().getString("ECommunicationError"),"");
			break;
		case 3: // bluetooth write error
			Log.e(TAG, description);
			iState = TState.EDisconnecting;
			runBTSocket();
			reset();
			iScheduler.notifyError(ResourceManager.getResource().getString("ECommunicationError"),"");
			break;
		case 4: // bluetooth close error
			Log.e(TAG, description);
			reset();
			iScheduler.notifyError(ResourceManager.getResource().getString("ECommunicationError"),"");
			break;
		}
	}

	/**
     * 
     */
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
					// the address is the same
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
				// the selection done by user is managed in the ui class which
				// implements BTSearcherEventListener interface, so here arrive
				// when the selection is already done
				
				if (deviceModel.equals(GWConst.KSpirodocOS))
					iScheduler.notifyWaitToUi(ResourceManager.getResource().getString("KPairingMsg"));
				else
					iScheduler.notifyWaitToUi(ResourceManager.getResource().getString("KPairingMsg"));
				break;
			}
		 
			break;

		case EGettingService:
			iState = TState.EConnected;
			Log.i(TAG, "IEMBP: runBtSearcher EGettingService");
			try {
				// the search is finished and we stop it (we remove from event
				// list)
				iServiceSearcher.removeBTSearcherEventListener(this);
				if (scanActivityListener != null) {
					iServiceSearcher.removeBTSearcherEventListener(scanActivityListener);
				}
				if (iMIRSpiroDocSocket.isAvailable()) {
					connectToServer();
				} else {
					Log.e(TAG, "Risorsa non disponibile");
				}
			} catch (IOException e) {
				iScheduler.notifyError(ResourceManager.getResource().getString("EBtDeviceConnError"),"");
			}
			break;
		default:
			break;

		}
	}

	/************************************************************************************************/

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

	/* --------------- */
	/*  Dati Generici  */
	/* --------------- */
    private ByteArrayOutputStream iCurve1; // prima curva di spiro o curva di ossimetria
    private int iNumCurve; // numero di curve totali

    private int iCurveRead; // numero di curve già lette
    private int iCurveSize; // byte

    private TMyDoctorErrorCode iErrorCode;
    private TSpirodocType iDeviceType;

    private TTypeMessage iInitPacketCode;
	private int iInitPacketCounter;
    private TTypeCommand iCommand;

    private byte[] iCmd = new byte[1]; // comandi inviati da smartphone a spirodoc
    private byte[] iBuffer = new byte[1]; // buffer di appoggio per letture singole

    // contiene msg di info iniziale inviato da spirodoc
    private byte[] iInfoPacket = new byte[KInfoPacketLen];

    // contiene msg di conf inviato da smartphone
    private ByteBuffer iStandardConfPacket = ByteBuffer.allocate(KStandardConfPacketLen);

    // contiene msg di conf inviato da smartphone
    private ByteBuffer iSimpleConfPacket = ByteBuffer.allocate(KSimpleConfPacketLen);

    // contiene risposta spirodoc a msg di conf
    private byte[] iControlPacket = new byte[KControlPacketLen];

    // contiene i dati paziente iniviati ad inizio misura
    private byte[] iInfoPatientPacket = new byte[KInfoPatientPacketLen];


	/* ------------------ */
	/*  DATI SPIROMETRIA  */
	/* ------------------ */

    private byte[] iInfoCurveSpiroPacket = new byte[KInfoCurveSpiroPacketLen];
	/** read curve data */

    private ByteArrayOutputStream iCurve2;
    private ByteArrayOutputStream iCurve3;

	/** Buffer for FV curve */
	private byte[] iStepFV = new byte[2];
	private byte[] iAmpli = new byte[2];

	/* ----------------- */
	/*  DATI OSSIMETRIA  */
	/* ----------------- */

	private byte[] iInfoCurveOxyPacket = new byte[KInfoCurveOxyPacketLen];
	/** read curve data */

	private static final int KInfoPacketLen = 41;
	private static final int KStandardConfPacketLen = 123;
	private static final int KSimpleConfPacketLen = 29;
	private static final int KInfoPatientPacketLen = 61; // 57 byte di info pat
															// (9 byte in
															// control) + 2 num
															// curve + 2
															// semaforo
	private static final int KInfoCurveSpiroPacketLen = 70; // solo parte fissa
															// curva si
															// spirometria
	private static final int KInfoCurveOxyPacketLen = 127; // solo parte fissa
															// curva si
															// ossimetria

	//private static final int KOxyPacketLen = 197; // 72 + 125 // solo parte fissa, il resto in iCurve
	private static final int KControlPacketLen = 9;
	private static final int KBorderPacketLen = 8; // The oximeter's data
													// message length

	private void runBTSocket() {
		
		Log.d(TAG, "runBTSocket() iState=" + iState); 

		switch (iState) {
		case EWaitingToGetDevice:
			Log.d(TAG, " RunL EWaitingToGetDevice");
			break;

		case EConnected:
			Log.d(TAG, " RunL EConnected");

			iInitPacketCounter = 0;

			if (iPairingMode) {

				iInitPacketCode = TTypeMessage.START_CONFIG;

				// Mettersi in attesa del pacchetto di inizio
				iState = TState.EWaitingInitPacket;

				FillZ(iBuffer, 1);

				RequestData();
			} else {
				String MessageLoader = null;

				switch (iCommand) {
				case DATA_CONF:
					iInitPacketCode = TTypeMessage.START_CONFIG;
					MessageLoader = ResourceManager.getResource().getString("KMsgConfiguring");
					break;
				case DATA_TEST_OXY:
					iInitPacketCode = TTypeMessage.START_OXY;
					MessageLoader = ResourceManager.getResource().getString("KMeasuring");
					break;
				case DATA_TEST_SPIRO:
					iInitPacketCode = TTypeMessage.START_SPIRO;
					MessageLoader = ResourceManager.getResource().getString("KMeasuring");
					break;
				}

				iScheduler.notifyToUi(MessageLoader);

				// Invio comando iniziale
				iState = TState.ESendingStartCmd;
				Fill(iCmd, iCommand.value, 1);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
                    e.printStackTrace();
				}
				SendDataL();
			}

			break;
		case ESendingStartCmd:
			Log.d(TAG, " RunL ESendingStartCmd");
			// Attesa dello stato del sistema
			iState = TState.EWaitingInitPacket;
			iInitPacketCounter = 0;
			FillZ(iBuffer, 1);
			// iTimer.After(KTimeOut);
			RequestData();
			break;
		case EWaitingInitPacket:
			Log.d(TAG, "_FORMAT iBuffer[0]: " + iBuffer[0] + " ? " + iInitPacketCode.value);
			Log.d(TAG, " CMIRSpiroDoc::RunL() EWaitingForInitPacket");
			Log.d(TAG, "iInitPacketCounter=" + iInitPacketCounter);

			if (iInitPacketCode.value == iBuffer[0]) {

				iInitPacketCounter++;

				if (iInitPacketCounter == KBorderPacketLen) {
					iState = TState.EWaitingInfoPacket;
					FillZ(iInfoPacket, KInfoPacketLen);
				}
			}
			RequestData();

			break;
		case EWaitingInfoPacket:
			// iTimer.Cancel();
			Log.d(TAG, " RunL EWaitingForInfoPacket");
			// Util::PrintBuffer Pacchetto Info:"),iInfoPacket);
			iErrorCode = TMyDoctorErrorCode.KErrNone;

			if (IsDataEnd(iInfoPacket, TTypeMessage.DATA_END)) {
				Log.d(TAG, "_FORMAT Device Type " + iInfoPacket[4]);

				// Memorizza il tipo strumento

				if (((byte)TSpirodocType.DEV_SIMPLE.value) == iInfoPacket[4])
					iDeviceType = TSpirodocType.DEV_SIMPLE;
				else
					iDeviceType = TSpirodocType.DEV_STANDARD;
				
				Log.d(TAG, "iDeviceType.value=" + iDeviceType + " (" + (byte)iDeviceType.value + ")");

				if (iDeviceType != TSpirodocType.DEV_SIMPLE && iDeviceType != TSpirodocType.DEV_STANDARD)
					iErrorCode = TMyDoctorErrorCode.EMyDoctorErrWrongDevice;

				if (iPairingMode) {

					if (iErrorCode == TMyDoctorErrorCode.KErrNone) {
						// Pairing eseguito con successo. Salva il BT MAC
					
						Util.setRegistryValue(REGKEY+iBTAddress, iDeviceType.toString());
					
						iMIRSpiroDocSocket.removeBTSocketEventListener(this);
						
						if (deviceModel.equals(GWConst.KSpirodocOS))
							iScheduler.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
						else
							iScheduler.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
						
						iScheduler.setBtMAC(iBTAddress);
						currentPos = 0;

						// Chiude la connessione
						iState = TState.EDisconnecting;
						DisconnectFromServerL();
					}
				} else {

					boolean sendigConf = false;
					/*
					 * iInfoPacket[19] contiene TIPO_PACCHETTO = 0 dati = 1
					 * configurazione = 0x1B dati non disponibili
					 */

					switch (iCommand) {
					case DATA_CONF:
						// In caso di configurazione, tale operazione
						// va sempre eseguita
						if (iInfoPacket[19] == 1)
							sendigConf = true;
						break;
					case DATA_TEST_SPIRO:
						// Preleva parametri di step_FV_base_A e ampli_FLUSSO_A
						iStepFV = new byte[2];
						iStepFV[0] = iInfoPacket[15];
						iStepFV[1] = iInfoPacket[16];

						iAmpli = new byte[2];
						iAmpli[0] = iInfoPacket[17];
						iAmpli[1] = iInfoPacket[18];
					case DATA_TEST_OXY:
						if (iInfoPacket[19] == 0) // c'e' la misura
							sendigConf = true;
						break;
					}

					if (sendigConf) {
						switch (iDeviceType) {
						case DEV_STANDARD:
							FillStandardConf();
							// Util::PrintBuffer Pacchetto di configurazione
							// inviato dal cellulare allo
							// spirossimetro:"),iStandardConfPacket);
							break;
						case DEV_SIMPLE:
							FillSimpleConf();
							// Util::PrintBuffer Pacchetto di configurazione
							// inviato dal cellulare allo
							// spirossimetro:"),iSimpleConfPacket);
							break;
						}

						// Invia configurazione
						iState = TState.ESendingConf;
						SendDataL(); // Send the config data
					} else {
						Log.d(TAG, " TIPO DATI NON PRESENTE");
						if (iInfoPacket[19] == 0x1B)
							iErrorCode = TMyDoctorErrorCode.EMyDoctorErrNoMeasure;
						else
							iErrorCode = TMyDoctorErrorCode.EMyDoctorErrWrongData;
					}

				}
			} else {
				Log.d(TAG, " TERMINATORE NON PRESENTE");
				iErrorCode = TMyDoctorErrorCode.EMyDoctorErrWrongDevice;
			}

			if (iErrorCode != TMyDoctorErrorCode.KErrNone) {

				iState = TState.EDisconnecting;
				DisconnectFromServerL();

				String MessageLoader;

				switch (iErrorCode) {
				case EMyDoctorErrNoMeasure:
					MessageLoader = ResourceManager.getResource().getString("KNoNewMeasure"); //"StringLoader::LoadLC( R_MYDOCTOR_NO_NEW_MEASURE_SPIRODOC )";
					break;
				case EMyDoctorErrWrongData:
					MessageLoader = ResourceManager.getResource().getString("EDataReadError"); //"StringLoader::LoadLC( R_MYDOCTOR_WRONG_DATA )";
					break;
				default:
					MessageLoader = ResourceManager.getResource().getString("ECommunicationError"); //"StringLoader::LoadLC( R_MYDOCTOR_ERROR_COMM )";
					break;
				}

				iScheduler.notifyError(MessageLoader,"");
				// CleanupStack::PopAndDestroy();

			}
			break;
		case ESendingConf:
			iState = TState.EWaitingControlPacket;
			RequestData();
			break;
		case EWaitingControlPacket: // puo' contenere il pacchetto di controllo
									// oppure la parte iniziale del InfoPatient
		{
			Log.d(TAG, " RunL EWaitingControlPacket");
			// Util::PrintBuffer Pacchetto di controllo inviato dallo
			// spirossimetro al cellulare:"),iControlPacket);
			String MessageLoader;
			
			int ccp = CheckControlPacket();
			Log.d(TAG, "CheckControlPacket=" + ccp);
			switch (ccp) {
			case 0: // no terminatore, c'e' misura da prelevare
				iState = TState.EWaitingInfoPatientPacket;
				Fill(iInfoPatientPacket, 0, KInfoPatientPacketLen);
				RequestData();
				break;
			case 1: // Configurazione corretta
				Log.d(TAG, " Configurazione CORRETTA SetConfState a 1");
				// Chiude la connessione
				iState = TState.EDisconnectingOK;
				DisconnectFromServerL();
				break;
			case 2: // Dati configurazione errati
				iState = TState.EDisconnecting;
				DisconnectFromServerL();

				iErrorCode = TMyDoctorErrorCode.EMyDoctorErrConfigWrong;
				MessageLoader = ResourceManager.getResource().getString("EWrongConfiguration"); // "StringLoader::LoadLC( R_MYDOCTOR_WRONG_CONFIG )";
				iScheduler.notifyError(MessageLoader,"");
				break;
			case 3: // cf errato
				iState = TState.EDisconnecting;
				DisconnectFromServerL();

				iErrorCode = TMyDoctorErrorCode.EMyDoctorErrConfigWrong;
				MessageLoader = ResourceManager.getResource().getString("EWrongUser"); // "StringLoader::LoadLC( R_MYDOCTOR_WRONG_CF )";
                iScheduler.notifyError(MessageLoader,"");
				break;
			case 4: // Memoria piena
				iState = TState.EDisconnecting;
				DisconnectFromServerL();

				iErrorCode = TMyDoctorErrorCode.EMyDoctorErrMemoryFull;
				MessageLoader = ResourceManager.getResource().getString("EMemoryExhausted"); // "StringLoader::LoadLC( R_MYDOCTOR_NO_MEMORY )";
                iScheduler.notifyError(MessageLoader,"");
				break;
			default: // Errore generale
				iState = TState.EDisconnecting;
				DisconnectFromServerL();

				iErrorCode = TMyDoctorErrorCode.EMyDoctorErrWrongData;
				MessageLoader = ResourceManager.getResource().getString("ECommunicationError"); // "StringLoader::LoadLC( R_MYDOCTOR_WRONG_GEN )";
                iScheduler.notifyError(MessageLoader,"");
			}

		}
			break;
		case EWaitingInfoPatientPacket:
			Log.d(TAG, " RunL EWaitingInfoPatient");
			// Util::PrintBuffer Pacchetto dati paziente inviato dallo
			// spirossimetro al cellulare:"),iInfoPatientPacket);
			switch (iCommand) {
			case DATA_TEST_SPIRO:
				iNumCurve = (int) iInfoPatientPacket[KInfoPatientPacketLen - 4];
				iNumCurve <<= 8;
				iNumCurve |= iInfoPatientPacket[KInfoPatientPacketLen - 3];
				Log.d(TAG, "_FORMAT num curve: " + iNumCurve);
				iState = TState.EWaitingInfoCurvePacket;
				
				iInfoCurveSpiroPacket = new byte[KInfoCurveSpiroPacketLen];
				Fill(iInfoCurveSpiroPacket, 0, KInfoCurveSpiroPacketLen);
				break;
			case DATA_TEST_OXY:
				Log.d(TAG, " RunL EWaitingInfoTestOxy");
				// 1 test sonno - 2 test cammino - 3 test generico
				iNumCurve = 1;
				iState = TState.EWaitingInfoCurvePacket;
				
				iInfoCurveOxyPacket = new byte[KInfoCurveOxyPacketLen];
				Fill(iInfoCurveOxyPacket, 0, KInfoCurveOxyPacketLen);
				break;
			default:
				Log.d(TAG, " RunL (default case) EWaitingInfoPatient");
				break;
			}
			iCurveRead = 0;
			RequestData();
			break;
		/* ACQUISIZIONE CAMPIONI DELLA CURVA SPIRO/OXY */
		case EWaitingInfoCurvePacket:
			switch (iCommand) {
			case DATA_TEST_SPIRO:
				iCurveRead++;
				Log.d(TAG, " RunL EWaitingInfoTestSpiro");
				// Util::PrintBuffer Pacchetto numero curve e semaforo
				// spirometro inviato dallo spirossimetro al
				// cellulare:"),iInfoCurveSpiroPacket);
				SetDataSpiro(true);
				break;
			case DATA_TEST_OXY:
				iCurveRead++;
				Log.d(TAG, " RunL EWaitingInfoTestOxy");
				// Util::PrintBuffer Pacchetto numero curve e semaforo
				// spirometro inviato dallo spirossimetro al
				// cellulare:"),iInfoCurveOxyPacket);
				SetDataOxy(true);
				break;
			default:
				break;
			}
			iState = TState.EWaitingCurve;
			RequestData();
			break;
		case EWaitingCurve:
			switch (iCommand) {
			case DATA_TEST_SPIRO:
				SetDataSpiro(false);
				break;
			case DATA_TEST_OXY:
				SetDataOxy(false);
				break;
			default:
				break;
			}
			// Log.d(TAG, " RunL EWaitingCurve"));
			RequestData();
			break;
		case EWaitingCurveEnd:
			Log.d(TAG, " RunL EWaitingTerminatorCurveSpiro");

			iErrorCode = TMyDoctorErrorCode.KErrNone;
			switch (iCommand) {
			case DATA_TEST_SPIRO:
				SetDataSpiro(false);
				// Controllo validità trasmissione della curva
				if ((iCurveRead == 1 && IsDataEnd(iCurve1.toByteArray(), TTypeMessage.CURVE_END)) || (iCurveRead == 2 && IsDataEnd(iCurve2.toByteArray(), TTypeMessage.CURVE_END)) || (iCurveRead == 3 && IsDataEnd(iCurve3.toByteArray(), TTypeMessage.CURVE_END))) {

					if (iCurveRead == iNumCurve) {
						// Le curve sono state tutte lette
						iState = TState.EWaitingTheorSpiroPacket;
						RequestData();
					} else {
						iInfoCurveSpiroPacket = new byte[KInfoCurveSpiroPacketLen];
						
						iState = TState.EWaitingInfoCurvePacket;
						RequestData(); // carico iSpiroBuffer<70>
					}

				} else {
					iErrorCode = TMyDoctorErrorCode.EMyDoctorErrWrongData;
				}
				break;
			case DATA_TEST_OXY:
				SetDataOxy(false);
				if (IsDataEnd(iCurve1.toByteArray(), TTypeMessage.DATA_END)) {
					iState = TState.ESendingReady;
					Fill(iCmd, TTypeMessage.READY.value, 1);

					// Util::PrintBuffer Pacchetto finale inviato dal cellulare
					// allo spirometro:"),iCmd);
					SendDataL();
				} else {
					iErrorCode = TMyDoctorErrorCode.EMyDoctorErrWrongData;
				}

				break;
			default:
				break;
			}

			// Controllo validità trasmissione della curva
			if (iErrorCode != TMyDoctorErrorCode.KErrNone) {
				iState = TState.EDisconnecting;
				DisconnectFromServerL();

				String MessageLoader = ResourceManager.getResource().getString("ECommunicationError"); // "StringLoader::LoadLC( R_MYDOCTOR_WRONG_GEN )";
				iScheduler.notifyError(MessageLoader, "");
			}

			break;
		case EWaitingTheorSpiroPacket:
			Log.d(TAG, " RunL EWaitingTheorSpiroPacket");
						
			if (IsDataEnd(iInfoCurveSpiroPacket, TTypeMessage.DATA_END)) {
				iState = TState.ESendingReady;
				Fill(iCmd, TTypeMessage.READY.value, 1);

				// Util::PrintBuffer Pacchetto finale inviato dal cellulare allo
				// spirometro:"),iCmd);
				SendDataL();
			} else {
				iState = TState.EDisconnecting;
				DisconnectFromServerL();

				iErrorCode = TMyDoctorErrorCode.EMyDoctorErrWrongData;
				String MessageLoader = ResourceManager.getResource().getString("ECommunicationError"); // "StringLoader::LoadLC( R_MYDOCTOR_WRONG_GEN )";
				iScheduler.notifyError(MessageLoader, "");
				// CleanupStack::PopAndDestroy();

			}
			break;
		case ESendingReady:
			Log.d(TAG, " RunL ESendingReady");
			// Chiude la connessione
			iState = TState.EDisconnectingOK;
			DisconnectFromServerL();
			break;
		case EDisconnectingOK:
			Log.d(TAG, " RunL EDisconnectingOK");
			// GetBTAddress();

			switch (iCommand) {
			case DATA_TEST_SPIRO:
				ReadDataSpiro(); // riempio text, leggo i dati delle misure e li
									// gestisco
				break;
			case DATA_TEST_OXY:
				ReadDataOxy();
				break;
			case DATA_CONF:
				String MessageLoader = ResourceManager.getResource().getString("KMsgConfOK"); // "StringLoader::LoadLC( R_MYDOCTOR_RIGHT_CONFIG )";
				iScheduler.configReady(MessageLoader);
				// CleanupStack::PopAndDestroy();
				break;

			}

			iMIRSpiroDocSocket.close();
			iState = TState.EWaitingToGetDevice;
			DisconnectFromServerL();

			break;
		case EDisconnecting:
		case EDisconnectingFromUser:
			iMIRSpiroDocSocket.close();
			iState = TState.EWaitingToGetDevice;
			DisconnectFromServerL();
			break;
		default:
			break;
		}
	}

	private void FillZ(byte[] b, int l) {

		for (int i = 0; i < l; i++) {
			b[i] = 0;
		}
	}

	private void Fill(byte[] b, int v, int l) {
		Fill(b, (byte) v, l);
	}

	private void Fill(byte[] b, byte v, int l) {
		for (int i = 0; i < l; i++) {
			b[i] = v;
		}
	}

	private void ReadDataOxy() {
		ByteBuffer textBuffer = ByteBuffer.wrap(iCurve1.toByteArray(), 0, iCurve1.size() - 8); // eliminazione 0xFF finali

		//-------------------------------------------------------------------------------SPO media
		double measurementSPOmed = (double)(getIntValue(textBuffer.get(94),textBuffer.get(95)))/10;
		
		//-------------------------------------------------------------------------------BPM media 
		double measurementBPMmed = (double)(getIntValue(textBuffer.get(102),textBuffer.get(103)))/10;

		//-------------------------------------------------------------------------------SPO min
		double measurementSPOmin = getIntValue(textBuffer.get(92),textBuffer.get(93));

		//-------------------------------------------------------------------------------BPM min
		double measurementBPMmin = getIntValue(textBuffer.get(100),textBuffer.get(101));

		//-------------------------------------------------------------------------------SPO max
		double measurementSPOmax = getIntValue(textBuffer.get(96),textBuffer.get(97));

		//-------------------------------------------------------------------------------BPM max
		double measurementBPMmax = getIntValue(textBuffer.get(104),textBuffer.get(105));

		//-------------------------------------------------------------------------------SPO bas
		double measurementSPObas = (double)(getIntValue(textBuffer.get(90),textBuffer.get(91)))/10;

		//-------------------------------------------------------------------------------BPM bas
		double measurementBPMbas = (double)(getIntValue(textBuffer.get(98),textBuffer.get(99)))/10;
		
		//--------------------------------------------------------------------------Durata Test		
		int sec = textBuffer.get(84)*3600 + textBuffer.get(85)*60 + textBuffer.get(86);
		String durata = Integer.toString(sec);
//		DecimalFormat dfb = new DecimalFormat("00");
//		String durata = dfb.format(textBuffer.get(84)) + dfb.format(textBuffer.get(85)) + dfb.format(textBuffer.get(86));

		//--------------------------------------------------------------------------Batteria
		int batt = (int) iInfoPacket[13] * 256 + (int) iInfoPacket[14];
				
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
        
		DecimalFormat df = new DecimalFormat("0.0");

        String oxyFileName = "oxy-"+ year + month + day + "-" + hour + minute + second +".oxy";

        // Creo un istanza di Misura del tipo PR
        HashMap<String,String> tmpVal = new HashMap<>();
        tmpVal.put(GWConst.EGwCode_07, df.format(measurementSPOmed).replace ('.', ','));  // O2 Med
        tmpVal.put(GWConst.EGwCode_1B, df.format(measurementSPOmin).replace ('.', ','));  // O2 Min
        tmpVal.put(GWConst.EGwCode_1D, df.format(measurementSPOmax).replace ('.', ','));  // O2 Max
        tmpVal.put(GWConst.EGwCode_1F, df.format(measurementSPObas).replace ('.', ','));
        tmpVal.put(GWConst.EGwCode_0F, df.format(measurementBPMmed).replace ('.', ','));  // HR Med
        tmpVal.put(GWConst.EGwCode_1A, df.format(measurementBPMmin).replace ('.', ','));  // HR Min
        tmpVal.put(GWConst.EGwCode_1C, df.format(measurementBPMmax).replace ('.', ','));  // HR Max
        tmpVal.put(GWConst.EGwCode_1E, df.format(measurementBPMbas).replace ('.', ','));
        tmpVal.put(GWConst.EGwCode_1G, durata);
        tmpVal.put(GWConst.EGwCode_1L, oxyFileName);  // filename
        tmpVal.put(GWConst.EGwCode_BATTERY, Integer.toString(batt)); // livello batteria

        iMeasure.setMeasures(tmpVal);
        iMeasure.setFile(textBuffer.array());
        iMeasure.setFileType(XmlManager.MIR_OXY_FILE_TYPE);
        iMeasure.setFailed(false);

        iScheduler.showMeasurementResults(iMeasure);
	}
	
	private void ReadDataSpiro() {
		Log.d(TAG, "ReadDataSpiro");
		
		int streamLength,curveLength;
		int batt;
		
		curveLength = iCurve1.size();
		
		if (iNumCurve>1) {
			curveLength += iCurve2.size();
			if (iNumCurve>2) 
				curveLength += iCurve3.size();
		}

		streamLength = 	iControlPacket.length  
						+ iInfoPatientPacket.length
						+ curveLength  
						+ iInfoCurveSpiroPacket.length - 8  /* terminatore */
					    + 4 /* iAmpli e iStepFV */ ;
						

		ByteBuffer stream = ByteBuffer.allocate(streamLength);
		
		stream.put(iControlPacket); // 9
		stream.put(iInfoPatientPacket, 0, 66-9); // 61
		stream.put(iAmpli);
		stream.put(iStepFV);
		stream.put(iInfoPatientPacket, 66-9, 61-(66-9));

		stream.put(iCurve1.toByteArray());

		if (iNumCurve>1) {
			stream.put(iCurve2.toByteArray());
			if (iNumCurve>2) 
				stream.put(iCurve3.toByteArray());
		}

		stream.put(iInfoCurveSpiroPacket, 0, iInfoCurveSpiroPacket.length-8);

		byte[] textPtr = stream.array();
		
        DecimalFormat df1 = new DecimalFormat("0.0");
        DecimalFormat df2 = new DecimalFormat("0.00");

        HashMap<String,String> tmpVal = new HashMap<>();

        // FVC
        double bufFVC = (double)getIntValue(textPtr[82],textPtr[83])/100;
        tmpVal.put(GWConst.EGwCode_0A, df2.format(bufFVC).replace ('.', ','));
        // tmpVal.add(df2.format(bufFVC));

        // FEV1
        double bufFEV1 = (double)getIntValue(textPtr[84],textPtr[85])/100;
        tmpVal.put(GWConst.EGwCode_09, df2.format(bufFEV1).replace ('.', ','));
        //tmpVal.add(df2.format(bufFEV1));

        // FEV1%
        double bufFev1FvcRatio = (double)getIntValue(textPtr[100],textPtr[101])/10;
        tmpVal.put(GWConst.EGwCode_0C, df1.format(bufFev1FvcRatio).replace ('.', ','));
        //tmpVal.add(df1.format(bufFev1FvcRatio));

        // PEF
        double bufPEF = (double)getIntValue(textPtr[86],textPtr[87])/100;
        tmpVal.put(GWConst.EGwCode_08, df2.format(bufPEF).replace ('.', ','));
        //tmpVal.add(df2.format(bufPEF));

        // F2575
        double bufF2575 = (double)getIntValue(textPtr[90],textPtr[91])/100;
        tmpVal.put(GWConst.EGwCode_0D, df2.format(bufF2575).replace ('.', ','));
        //tmpVal.add(df2.format(bufF2575));

        // FET
        double bufFET = (double)getIntValue(textPtr[92],textPtr[93])/100;
        tmpVal.put(GWConst.EGwCode_0L, df2.format(bufFET).replace ('.', ','));
        //tmpVal.add(df2.format(bufFET));     		//FET

        if (iDeviceType == TSpirodocType.DEV_STANDARD) {
            // TPEF
            double bufTPEF = (double)getIntValue(textPtr[streamLength-50],textPtr[streamLength-49])/100;
            tmpVal.put(GWConst.EGwCode_B8, df2.format(bufTPEF).replace ('.', ','));
            //tmpVal.add(df2.format(bufTPEF));     		//TPEF

            // TFEV1
            double bufTFEV1 = (double)getIntValue(textPtr[streamLength-52],textPtr[streamLength-51])/100;
            tmpVal.put(GWConst.EGwCode_B9, df2.format(bufTFEV1).replace ('.', ','));
            //tmpVal.add(df2.format(bufTFEV1));     		//TFEV1

            // TFVC
            double bufTFVC = (double)getIntValue(textPtr[streamLength-54],textPtr[streamLength-53])/100;
            tmpVal.put(GWConst.EGwCode_BA, df2.format(bufTFVC).replace ('.', ','));
            //tmpVal.add(df2.format(bufTFVC));     		//TFVC

            // TFEV1%
            double bufTFev1FvcRatio = (double)getIntValue(textPtr[streamLength-36],textPtr[streamLength-35])/10;
            tmpVal.put(GWConst.EGwCode_BC, df2.format(bufTFev1FvcRatio).replace ('.', ','));
            //tmpVal.add(df1.format(bufTFev1FvcRatio));   //TFEV1%

            // TFEF25-75
            double bufTF2575 = (double)getIntValue(textPtr[streamLength-46],textPtr[streamLength-45])/100;
            tmpVal.put(GWConst.EGwCode_BD, df2.format(bufTF2575).replace ('.', ','));
            //tmpVal.add(df2.format(bufTF2575));     		//TFEF25-75

            // TFET
            double bufTFET = (double)getIntValue(textPtr[streamLength-44],textPtr[streamLength-43])/100;
            tmpVal.put(GWConst.EGwCode_BL, df2.format(bufTFET).replace ('.', ','));
            //tmpVal.add(df2.format(bufTFET));     		//TFET
        }

        // Batteria
        batt = (int) iInfoPacket[13] * 256 + (int) iInfoPacket[14];
		
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
        
		String spiroFileName = "spiro-"+ year + month + day + "-" + hour + minute + second +".spi";
        tmpVal.put(GWConst.EGwCode_0M, spiroFileName);  // filename
        tmpVal.put(GWConst.EGwCode_BATTERY, Integer.toString(batt)); // livello batteria

        iMeasure.setMeasures(tmpVal);
        iMeasure.setFile(stream.array());
        iMeasure.setFileType(XmlManager.MIR_SPIRO_FILE_TYPE);
        iMeasure.setFailed(false);

        iScheduler.showMeasurementResults(iMeasure);
	}

	private int getIntValue(byte a, byte b){
		int high, low;
		if ( a < 0 ) {
			high = (a &0x7F) + 128;
		} else {
			high = a;
		}
		if ( b < 0 ) {
			low = (b &0x7F) + 128;
		} else {
			low = b;
		}
		return high *256 + low;
	}
	
	private void SetDataOxy(boolean aFirst) {
		
		Log.d(TAG, "SetDataOxy(" + aFirst + ")");
		
		int sample_num = 0;

		if (aFirst) {

			// Ricava i numeri di campioni
			for (int i = 4; i > 0; i--) {
				sample_num |= (iInfoCurveOxyPacket[KInfoCurveOxyPacketLen - i] & 0xff);
				sample_num <<= 8 * (i - 1);
			}

			Log.d(TAG, "oxy sample num: " + sample_num);

			iCurveSize = (sample_num) * 12 + 8;

			if (iCurve1 != null)
				iCurve1 = null;

			iCurve1 = new ByteArrayOutputStream(KControlPacketLen + KInfoPatientPacketLen + KInfoCurveOxyPacketLen + iCurveSize);
			iCurve1.write(iControlPacket, 0, iControlPacket.length);
			iCurve1.write(iInfoPatientPacket, 0, iInfoPatientPacket.length);
			iCurve1.write(iInfoCurveOxyPacket, 0, iInfoCurveOxyPacket.length);
		} else {
			iCurve1.write(iInfoCurveOxyPacket, 0, iInfoCurveOxyPacket.length);
		}
	}

	private void SetDataSpiro(boolean aFirst) {

		int appo;

		Log.d(TAG, "SetDataSpiro() aFirst=" + aFirst);

		if (aFirst) {
			Log.d(TAG, "CMIRSpiroDoc::SetDataSpiro FIRST iInfoCurveSpiroPacket.len=" + iInfoCurveSpiroPacket.length + " / KInfoCurveSpiroPacketLen=" + KInfoCurveSpiroPacketLen);
			iCurveSize = 8;

			// num vt
			appo = (iInfoCurveSpiroPacket[KInfoCurveSpiroPacketLen - 8] & 0xff);
			appo <<= 8;
			appo |= (iInfoCurveSpiroPacket[KInfoCurveSpiroPacketLen - 7] & 0xff);
			iCurveSize += appo * 2;

			// num exp
			appo = (iInfoCurveSpiroPacket[KInfoCurveSpiroPacketLen - 4] & 0xff);
			appo <<= 8;
			appo |= (iInfoCurveSpiroPacket[KInfoCurveSpiroPacketLen - 3] & 0xff);
			iCurveSize += appo * 2;

			// num ins
			appo = (iInfoCurveSpiroPacket[KInfoCurveSpiroPacketLen - 2] & 0xff);
			appo <<= 8;
			appo |= (iInfoCurveSpiroPacket[KInfoCurveSpiroPacketLen - 1] & 0xff);
			iCurveSize += appo * 2;

			Log.d(TAG, "curve dim: " + iCurveSize);

			switch (iCurveRead) {
			case 1:
				Log.d(TAG, "CMIRSpiroDoc::SetDataSpiro FIRST CURVE 1");

				iCurve1 = new ByteArrayOutputStream(KInfoCurveSpiroPacketLen + iCurveSize);
				iCurve1.write(iInfoCurveSpiroPacket, 0, iInfoCurveSpiroPacket.length);
				break;
			case 2:
				iCurve2 = new ByteArrayOutputStream(KInfoCurveSpiroPacketLen + iCurveSize);
				iCurve2.write(iInfoCurveSpiroPacket, 0, iInfoCurveSpiroPacket.length);
				break;
			case 3:
				iCurve3 = new ByteArrayOutputStream(KInfoCurveSpiroPacketLen + iCurveSize);
				iCurve3.write(iInfoCurveSpiroPacket, 0, iInfoCurveSpiroPacket.length);
				break;
			default:
				break;
			}

		} else {
			Log.d(TAG, "CMIRSpiroDoc::SetDataSpiro OTHER");
			switch (iCurveRead) {
			case 1:
				Log.d(TAG, "CMIRSpiroDoc::SetDataSpiro OTHER CURVE 1");
				iCurve1.write(iInfoCurveSpiroPacket, 0, iInfoCurveSpiroPacket.length);
				break;
			case 2:
				iCurve2.write(iInfoCurveSpiroPacket, 0, iInfoCurveSpiroPacket.length);
				break;
			case 3:
				iCurve3.write(iInfoCurveSpiroPacket, 0, iInfoCurveSpiroPacket.length);
				break;
			default:
				break;
			}
		}
	}

	private int CheckControlPacket() {

		Log.d(TAG, "CMIRSpiroDoc::CheckControlPacket");

		if (iControlPacket.length < KControlPacketLen)
			return -1;

		if (!IsDataEnd(iControlPacket, TTypeMessage.DATA_END)) { // non c'e' il
																	// terminatore
			switch (iCommand) {
			case DATA_TEST_SPIRO:
			case DATA_TEST_OXY:
				return 0;
			default:
				return 5;
			}
		}

		if (iControlPacket[0] == (byte)TTypeMessage.READY.value)
			return 1;
		if (iControlPacket[0] == (byte)TTypeMessage.ERR_GEN.value)
			return 2;
		if (iControlPacket[0] == (byte)TTypeMessage.ERR_CF.value)
			return 3;
		if (iControlPacket[0] == (byte)TTypeMessage.ERR_FULL.value)
			return 4;

		return 5;
	}

	private void FillSimpleConf() {
		Log.d(TAG, "CMIRSpiroDoc::FillSimpleConf");

		FillZ(iSimpleConfPacket.array(), iSimpleConfPacket.array().length);

		/* Header */
		for (int i = 0; i < KBorderPacketLen; i++)
			iSimpleConfPacket.put((byte)TTypeMessage.INIT_CONF_SMP.value);

		/* prog_lingua */
		iSimpleConfPacket.put((byte)TTypeMessage.DATA_NULL.value);
		// iConfPacket.Append(DATA_ONE);
		/* lingua */
		iSimpleConfPacket.put((byte)TTypeMessage.DATA_NULL.value);
		// iConfPacket.Append(DATA_ONE);
		/* prog_unità */
		iSimpleConfPacket.put((byte)TTypeMessage.DATA_ONE.value);
		/* unità */
		iSimpleConfPacket.put((byte)TTypeMessage.DATA_ONE.value);
		/* prog_formato_data */
		iSimpleConfPacket.put((byte)TTypeMessage.DATA_ONE.value);
		/* formato data */
		iSimpleConfPacket.put((byte)TTypeMessage.DATA_NULL.value);
		/* prog_data_ora */
		iSimpleConfPacket.put((byte)TTypeMessage.DATA_ONE.value);
		
		Calendar cal = Calendar.getInstance();

		String yearStr = String.valueOf(cal.get(Calendar.YEAR));
		yearStr = yearStr.substring(yearStr.length() - 2);
		int year = Integer.parseInt(yearStr);
		int month = cal.get(Calendar.MONTH) + 1;
		int day = cal.get(Calendar.DAY_OF_MONTH);
		int hour = cal.get(Calendar.HOUR_OF_DAY);
		int minute = cal.get(Calendar.MINUTE);
		int second = cal.get(Calendar.SECOND);
		
		/* giorno */
		iSimpleConfPacket.put((byte) day);
		/* mese */
		iSimpleConfPacket.put((byte) month);
		/* anno */
		iSimpleConfPacket.put((byte) year);
		/* ore */
		iSimpleConfPacket.put((byte) hour);
		/* minuti */
		iSimpleConfPacket.put((byte) minute);
		/* secondi */
		iSimpleConfPacket.put((byte) second);

		/* Terminator */
		for (int i = 0; i < KBorderPacketLen; i++)
			iSimpleConfPacket.put((byte)TTypeMessage.DATA_END.value);
	}
	
	private void FillStandardConf() {
		
		byte[] header = new byte[8];
		byte[] terminator = new byte[8];

		Patient patient = UserManager.getUserManager().getCurrentPatient();

		// Prepara l'header del messaggio da inviare
		for (int i = 0; i < 8; i++) {
			header[i] = (byte)TTypeMessage.INIT_CONF_STD.value;
			terminator[i] = (byte)TTypeMessage.DATA_END.value;
		}
		
		int K_CONFIG_MESSAGE = 123;

		ByteBuffer messageBuff = ByteBuffer.allocate(K_CONFIG_MESSAGE);

		/* Header */
		messageBuff.put(header);

		/* prog_nuovo_paz */
		messageBuff.put((byte)(TTypeMessage.DATA_ONE.value));

		/* sesso */
		messageBuff.put((byte)(TTypeMessage.DATA_NULL.value));
		messageBuff.put(patient.getSex().getBytes());

		/* statura */
		messageBuff.put((byte)(TTypeMessage.DATA_NULL.value));
		//messageBuff.put((byte) Integer.parseInt(patient.getHeight()));
		String height = truncString(patient.getHeight());
		messageBuff.put((byte) (Integer.parseInt(height)& 0x000000ff));

		/* peso */
		messageBuff.put((byte)(TTypeMessage.DATA_NULL.value));
		//messageBuff.put((byte) Integer.parseInt(patient.getWeight()));
		String weight = truncString(patient.getWeight());
		messageBuff.put((byte) (Integer.parseInt(weight)& 0x000000ff));

		/* data di nascita - giorno */
		String birthdayDate = patient.getBirthdayDate();
		int birthYear = Integer.parseInt(birthdayDate.substring(0, 4));
		int birthMonth = Integer.parseInt(birthdayDate.substring(4, 6));
		int birthDay = Integer.parseInt(birthdayDate.substring(6, 8));
		int firstByte = birthYear / 256;
		int secondByte = birthYear % 256;

		messageBuff.put((byte)(TTypeMessage.DATA_NULL.value));
		messageBuff.put((byte) birthDay);
		messageBuff.put((byte)(TTypeMessage.DATA_NULL.value));
		messageBuff.put((byte) birthMonth);

		messageBuff.put((byte) firstByte);
		messageBuff.put((byte) secondByte);

		/* cognome */
		String surname = patient.getSurname();
		byte[] surnameBytes;
		if (surname.length() > 18) {
			surname = surname.substring(0, 18);
			surnameBytes = surname.getBytes();
		} else if (surname.length() == 18) {
			surnameBytes = surname.getBytes();
		} else {
			ByteBuffer tmp = ByteBuffer.allocate(18);
			tmp.put(surname.getBytes());
			surnameBytes = tmp.array();
			Arrays.fill(surnameBytes, surname.length(), surnameBytes.length,
					(byte) 0x00);
		}
		messageBuff.put(surnameBytes);

		/* nome */
		String name = patient.getName();
		byte[] nameBytes;
		if (name.length() > 18) {
			name = name.substring(0, 18);
			nameBytes = name.getBytes();
		} else if (name.length() == 18) {
			nameBytes = name.getBytes();
		} else {
			ByteBuffer tmp = ByteBuffer.allocate(18);
			tmp.put(name.getBytes());
			nameBytes = tmp.array();
			Arrays.fill(nameBytes, name.length(), nameBytes.length,
							(byte) 0x00);
		}
		messageBuff.put(nameBytes);

		/* etnia */
		messageBuff.put((byte) Integer.parseInt(patient.getEthnic()));

		/* autore */
		messageBuff.put((byte) Integer.parseInt(patient.getAdditionalData()));

		/* codice fiscale */
		ByteBuffer tmpCF = ByteBuffer.allocate(16);
		tmpCF.put(patient.getId().getBytes());
		byte[] cfBytes = tmpCF.array();
		Arrays.fill(cfBytes, patient.getId().length(), cfBytes.length,
				(byte) 0x41);
		messageBuff.put(cfBytes);
		
		/* prog_p_best */
		messageBuff.put((byte)(TTypeMessage.DATA_ONE.value));
		/* personal_best */
		messageBuff.put((byte)(TTypeMessage.DATA_NULL.value));
		messageBuff.put((byte)(TTypeMessage.DATA_NULL.value));
		/* param_rif */
		messageBuff.put((byte)(TTypeMessage.DATA_NULL.value));
		/* prog_lingua */
		messageBuff.put((byte)(TTypeMessage.DATA_ONE.value));
		/* lingua */
		messageBuff.put((byte)(TTypeMessage.DATA_ONE.value));
		/* prog_unità */
		messageBuff.put((byte)(TTypeMessage.DATA_ONE.value));
		/* unità */
		messageBuff.put((byte)(TTypeMessage.DATA_ONE.value));
		/* prog_formato_data */
		messageBuff.put((byte)(TTypeMessage.DATA_ONE.value));
		/* formato data */
		messageBuff.put((byte)(TTypeMessage.DATA_NULL.value));
		/* prog_data_ora */
		messageBuff.put((byte)(TTypeMessage.DATA_ONE.value));

		Calendar cal = Calendar.getInstance();

		String yearStr = String.valueOf(cal.get(Calendar.YEAR));
		yearStr = yearStr.substring(yearStr.length() - 2);
		int year = Integer.parseInt(yearStr);
		int month = cal.get(Calendar.MONTH) + 1;
		int day = cal.get(Calendar.DAY_OF_MONTH);
		int hour = cal.get(Calendar.HOUR_OF_DAY);
		int minute = cal.get(Calendar.MINUTE);
		int second = cal.get(Calendar.SECOND);

		/* giorno */
		messageBuff.put((byte) day);
		/* mese */
		messageBuff.put((byte) month);
		/* anno */
		messageBuff.put((byte) year);
		/* ore */
		messageBuff.put((byte) hour);
		/* minuti */
		messageBuff.put((byte) minute);
		/* secondi */
		messageBuff.put((byte) second);

		/* prog_sintomi */
		messageBuff.put((byte)(TTypeMessage.DATA_ONE.value));
		String symptoms = patient.getSymptoms();
		String binarySymptoms = "";
		if(symptoms!=null && symptoms.length()>0){
			binarySymptoms = Integer.toBinaryString(Integer.parseInt(
					symptoms, 16));
		}
		
		while (binarySymptoms.length() < 6) {
			binarySymptoms = "0" + binarySymptoms;
		}
		for (int i = 5; i >= 0; i--) {
			if (binarySymptoms.charAt(i) == '0') {
				messageBuff.put((byte)(TTypeMessage.DATA_NULL.value));
			} else {
				messageBuff.put((byte)(TTypeMessage.DATA_ONE.value));
			}
		}

		/* questions */
		String questions = patient.getQuestions();		
		String binaryQuestions = "";
		if(questions != null && questions.length()>0){
			binaryQuestions = Integer.toBinaryString(Integer.parseInt(
				questions, 16));
		}
		while (binaryQuestions.length() < 16) {
			binaryQuestions = "0" + binaryQuestions;
		}
		for (int i = 15; i >= 0; i--) {
			if (binaryQuestions.charAt(i) == '0') {
				messageBuff.put((byte)(TTypeMessage.DATA_NULL.value));
			} else {
				messageBuff.put((byte)(TTypeMessage.DATA_ONE.value));
			}
		}

		/* Terminator */
		messageBuff.put(terminator);
		
		iStandardConfPacket.clear();
		iStandardConfPacket.put(messageBuff.array());
	}
	
	private String truncString(String s) {
		// Se presente il punto decimale, tronca per ottenere solo la parte intera		
		if(s != null && s.length()>0){		
			int pos = s.indexOf(".");		
			if(pos!= -1){
				s = s.substring(0, pos);
			}
		}
		return s;
	}

	private void DisconnectFromServerL() {
		iMIRSpiroDocSocket.removeBTSocketEventListener(this);
		iMIRSpiroDocSocket.close();
		//reset();
		runBTSocket();
	}

	private void SendDataL() {
		
		Log.d(TAG, "SendDataL() iState" + iState);

		switch (iState) {
		case ESendingReady:
		case ESendingStartCmd:
			iMIRSpiroDocSocket.write(iCmd);

			break;

		case ESendingConf:
			switch (iDeviceType) {
			case DEV_STANDARD:
				Log.d(TAG, "write byte=" + iStandardConfPacket.array().length);
				iMIRSpiroDocSocket.write(iStandardConfPacket.array());
				break;
			case DEV_SIMPLE:
				Log.d(TAG, "write byte=" + iSimpleConfPacket.array().length);
				iMIRSpiroDocSocket.write(iSimpleConfPacket.array());
				break;
			}
			break;
		default:
			// errore di programmazione uscire dall'applicazione
			iScheduler.notifyError("Error", "");
			break;
		}
	}

	private void RequestData() {

		Log.d(TAG, "RequestData() iState=" + iState);
		
		switch (iState) {
		case EWaitingInitPacket:
			iMIRSpiroDocSocket.read(iBuffer); // primo pacchetto inviato da 36
												// byte

			break;
		case EWaitingInfoPacket:
			iMIRSpiroDocSocket.read(iInfoPacket); // primo pacchetto inviato da
													// 36 byte
			// SetActive();
			break;
		case EWaitingControlPacket:
			iMIRSpiroDocSocket.read(iControlPacket); // primo pacchetto inviato
														// da 36 byte
			// SetActive();
			break;
		case EWaitingInfoPatientPacket:
			iMIRSpiroDocSocket.read(iInfoPatientPacket); // primo pacchetto
															// inviato da 36
															// byte
			// SetActive();
			break;
		case EWaitingInfoCurvePacket:
			switch (iCommand) {
			case DATA_TEST_SPIRO:
				iMIRSpiroDocSocket.read(iInfoCurveSpiroPacket); // primo
																// pacchetto
																// inviato da 36
																// byte
				break;
			case DATA_TEST_OXY:
				iMIRSpiroDocSocket.read(iInfoCurveOxyPacket); // primo pacchetto
																// inviato da 36
																// byte
				break;
			default:
				break;
			}
			// SetActive();
			break;
		case EWaitingCurve:
			switch (iCommand) {
			case DATA_TEST_SPIRO:
				if (iCurveSize > KInfoCurveSpiroPacketLen) {
					// iInfoCurveSpiroPacket = new byte[KInfoCurveSpiroPacketLen];
					iMIRSpiroDocSocket.read(iInfoCurveSpiroPacket);
					iCurveSize -= KInfoCurveSpiroPacketLen;
				} else {
					// TPtr8 tempBuf((TUint8 *) iInfoCurveSpiroPacket.Ptr(),
					// iCurveSize); // ultima ricezione da n byte
					// iInfoCurveSpiroPacket.SetLength(iCurveSize);
					iInfoCurveSpiroPacket = new byte[iCurveSize];
					iMIRSpiroDocSocket.read(iInfoCurveSpiroPacket);
					iState = TState.EWaitingCurveEnd;
				}
				break;
			case DATA_TEST_OXY:
				if (iCurveSize > KInfoCurveOxyPacketLen) {
					iMIRSpiroDocSocket.read(iInfoCurveOxyPacket);
					iCurveSize -= KInfoCurveOxyPacketLen;
				} else {
					// TPtr8 tempBuf((TUint8 *) iInfoCurveOxyPacket.Ptr(),
					// iCurveSize); // ultima ricezione da n byte
					// iInfoCurveOxyPacket.SetLength(iCurveSize);
					iInfoCurveOxyPacket = new byte[iCurveSize];
					iMIRSpiroDocSocket.read(iInfoCurveOxyPacket);
					iState = TState.EWaitingCurveEnd;
				}
				break;
			default:
				break;
			}
			// SetActive();
			break;
		case EWaitingTheorSpiroPacket:
			
			Log.d(TAG, "EWaitingTheorSpiroPacket: read iInfoCurveSpiroPacketlength=" + iInfoCurveSpiroPacket.length);		
			iInfoCurveSpiroPacket = new byte[KInfoCurveSpiroPacketLen];
			iMIRSpiroDocSocket.read(iInfoCurveSpiroPacket);
			// SetActive();
			break;

		default:
			Log.d(TAG, "RequestData (default)");
			
			iErrorCode = TMyDoctorErrorCode.EMyDoctorErrCommunicationError;
			String MessageLoader = ResourceManager.getResource().getString("ECommunicationError"); // "StringLoader::LoadLC( R_MYDOCTOR_ERROR_COMM )";
			iScheduler.notifyError(MessageLoader,"");
			// CleanupStack::PopAndDestroy();

			iState = TState.EDisconnecting;
			DisconnectFromServerL();

			break;
		}
	}

	private boolean IsDataEnd(byte[] aBuffer, TTypeMessage aData) {

		Log.d(TAG, "IsDataEnd() aData=" + aData + ", aBuffer.length=" + aBuffer.length);

		boolean check = true;
		int index = aBuffer.length - KBorderPacketLen;

		for (int i = index; i < (KBorderPacketLen + index) && check; i++) {
		
			Log.d(TAG, " aData=" + ((byte)aData.value) + " ? aBuffer[" + i + "]=" + aBuffer[i]);
		
			if (((byte)aData.value) != aBuffer[i]) {
				check = false;
			}
		}
		Log.d(TAG, "IsDataEnd=" + check);

		return check;

	}
}
