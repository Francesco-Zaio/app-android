package com.ti.app.telemed.core.btdevices;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.BTSearcher;
import com.ti.app.telemed.core.btmodule.BTSearcherEventListener;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.Patient;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.util.Util;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;
import java.util.Vector;

public class MIRSpirodoc46 extends DeviceHandler implements BTSearcherEventListener {

	private static final String TAG = "MIRSpirodoc46";

	private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

	// Comandi da inviare al dispositivo MIR
	private static final byte Cod_ON = 0x00;
	private static final byte Cod_RX_APRO = (byte) 0xC5;
	//private static final byte Cod_FVC = 0x08; // Comando per avviare Spirometria FVC con turbina riusabile
	private static final byte Cod_FVC_M = (byte)0x88; // Comando per avviare Spirometria FVC con turbina usa e getta
	private static final byte Cod_OSS = 0x10; // Comando per avviare Ossimetria
	private static final byte Cod_ESC = (byte)0x1B; // Comando per terminare l'Ossimetria
	private static final byte Cod_LAST = (byte)0xA2; // Comando per ricevere l'ultima Spirometria FVC Real Time

	private static final int STARTUP_SEQUENCE_LEN = 32;
	private static final int PROG_AREA_LEN = 718;

	private static final int MIN_OXY_SAMPLES = 14;
	private static final int MAX_OXY_SAMPLES = 27;
	private static final int BASE_OXY_STREAM_LENGTH = 189;

	int batteryLevel;

	private BTSearcher iServiceSearcher;
	private Vector<BluetoothDevice> deviceList = new Vector<>();
	private MIRSpirodoc46.DeviceCommunicationThread commThread = null;

	public MIRSpirodoc46(DeviceListener listener, UserDevice ud) {
        super(listener, ud);
		iServiceSearcher = new BTSearcher();
	}

    // abstract methods of DeviceHandler

    @Override
    public void confirmDialog() {
		synchronized (commThread) {
			commThread.notifyAll();
		}
    }

    @Override
    public void cancelDialog(){
		synchronized (commThread) {
			commThread.stopOperation();
			commThread.notifyAll();
		}
    }

    @Override
    public boolean startOperation(OperationType ot, BTSearcherEventListener btSearchListener) {
        if (!startInit(ot, btSearchListener))
            return false;
		deviceList.clear();
		iServiceSearcher.clearBTSearcherEventListener();
		iServiceSearcher.addBTSearcherEventListener(this);
		iServiceSearcher.startSearchDevices();
		deviceListener.notifyToUi(ResourceManager.getResource().getString("KSearchingDev"));
		return true;
    }

    @Override
    public void stopOperation() {
        Log.d(TAG, "stopOperation");
		if (commThread != null) {
			commThread.stopOperation();
			commThread = null;
		}
        stop();
    }

	@Override
	public void selectDevice(BluetoothDevice bd){
		Log.d(TAG, "selectDevice: addr=" + bd.getAddress());

		// the search is finished and we stop it (we remove from event list)
		iServiceSearcher.stopSearchDevices();
		iServiceSearcher.removeBTSearcherEventListener(this);
		iBtDevAddr = bd.getAddress();

		commThread = new MIRSpirodoc46.DeviceCommunicationThread(bd);
		commThread.start();
	}


	// methods of BTSearchEventListener interface

	@Override
	public void deviceDiscovered(Vector<BluetoothDevice> devList) {
		Log.d(TAG,"deviceDiscovered:");
		switch (iCmdCode) {
			case ECmdConnByAddr:
				for (int i = 0; i < devList.size(); i++)
					if (iBtDevAddr.equalsIgnoreCase(devList.get(i).getAddress())) {
						selectDevice(devList.get(i));
					}
				break;
			case ECmdConnByUser:
				BluetoothDevice d = devList.get(devList.size()-1);
				// Evita device duplicati
				int i;
				for (i = 0; i < deviceList.size(); i++)
					if (deviceList.elementAt(i).getAddress().equals(d.getAddress()))
						break;
				if (i >= deviceList.size()) {
					deviceList.addElement(d);
					if (iBTSearchListener != null)
						iBTSearchListener.deviceDiscovered(deviceList);
				}
				break;
		}
	}

    @Override
    public void deviceSearchCompleted() {
		Log.d(TAG,"deviceSearchCompleted:");
		if (iCmdCode == TCmd.ECmdConnByUser && iBTSearchListener != null) {
			Log.d(TAG, "BT scan completed");
			iBTSearchListener.deviceSearchCompleted();
			stop();
		} else if (iCmdCode == TCmd.ECmdConnByAddr) {
			Log.d(TAG, "Restarting BT Scan...");
			iServiceSearcher.startSearchDevices();
		}
    }


    // Class Methods

    private void reset() {
        iBtDevAddr = null;
		commThread = null;
		deviceList.clear();
    }

	private void stop()  {
		iServiceSearcher.stopSearchDevices();
		iServiceSearcher.clearBTSearcherEventListener();
		iServiceSearcher.close();
		reset();
	}

	private class DeviceCommunicationThread extends Thread {
		private BluetoothSocket mmSocket = null;
		private BluetoothDevice device;
		InputStream mmInStream = null;
		OutputStream mmOutStream = null;
		private boolean stopped = false;

		DeviceCommunicationThread(BluetoothDevice device) {
			this.device = device;
		}

		@Override
		public void run() {
			try {
				deviceListener.notifyToUi(ResourceManager.getResource().getString("KConnectingDev"));

				mmSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
				Log.d(TAG, "Connecting ...");
				mmSocket.connect();
				Log.d(TAG, "Connected");
				// E' necessario attendere un po' prima di inviare i comandi altrimenti non risponde
				Thread.sleep(500);
				mmInStream = mmSocket.getInputStream();
				mmOutStream = mmSocket.getOutputStream();

				// Ricezione Info record (in caso di errore stop)
				if (getInfo())
					return;

				// Se l'operazione richiesta è il pairing fine
				if (operationType == OperationType.Pair) {
					deviceListener.setBtMAC(iBtDevAddr);
					deviceListener.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
					MIRSpirodoc46.this.stop();
					return;
				}

				if (GWConst.KMsrOss.equals(iUserDevice.getMeasure()))
					startOxy();
				else if (GWConst.KMsrSpir.equals(iUserDevice.getMeasure()))
					startSpiro();
			} catch (IOException e) {
				if (!stopped) {
					Log.e(TAG, "disconnected", e);
					deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
					MIRSpirodoc46.this.stop();
				} else
					Log.d(TAG, "Connection Stopped");
			} catch (InterruptedException e) {
				if (!stopped) {
					Log.e(TAG, "interrupted", e);
					deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
					MIRSpirodoc46.this.stop();
				} else {
					Log.d(TAG, "Operation Stopped");
				}
			} finally {
				try {
					if (mmSocket != null)
						mmSocket.close();
				} catch (Exception e2) {
					e2.printStackTrace();
				}
			}
		}

		private boolean getInfo() {
			try {
				// Invio Cod_ON
				Log.d(TAG, "sending Cod_ON");
				mmOutStream.write(Cod_ON);

				// Ricezione StartupSequence (SDA)
				byte[] startupSequence = new byte[STARTUP_SEQUENCE_LEN];
				int numRead = 0;
				Util.logFile("MIRLog.log", null, " ** MIR SPP Log ***", false);
				while (numRead < STARTUP_SEQUENCE_LEN) {
					int ret = mmInStream.read(startupSequence, numRead, STARTUP_SEQUENCE_LEN - numRead);
					if (ret < 0) {
						Log.e(TAG, "Read EOF");
						deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
						MIRSpirodoc46.this.stop();
						return true;
					}
					numRead += ret;
					//Log.d(TAG, "StartupSequence: numRead = " + numRead);
				}
				Util.logFile("MIRLog.log", startupSequence, " -- StartupSequence", true);
				Log.d(TAG, "StartupSequence (SDA) received");

				// Invio COD_RX_APRO
				Log.d(TAG, "sending COD_RX_APRO");
				mmOutStream.write(Cod_RX_APRO);

				// Ricezione Prog Area
				byte[] progArea = new byte[PROG_AREA_LEN];
				numRead = 0;
				while (numRead < PROG_AREA_LEN) {
					int ret = mmInStream.read(progArea, numRead, PROG_AREA_LEN - numRead);
					if (ret < 0) {
						Log.e(TAG, "Read EOF");
						deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
						MIRSpirodoc46.this.stop();
						return true;
					}
					numRead += ret;
				}
				Util.logFile("MIRLog.log", progArea, " -- Prog Area", true);
				Log.d(TAG, "Programmable Area received");

				batteryLevel = (progArea[97] & 255) + ((progArea[96] & 255) << 8);
				Log.d(TAG,"BatteryLevel="+batteryLevel);

				return false;
			} catch (IOException e) {
				if (!stopped) {
					Log.e(TAG, "disconnected", e);
					deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
					MIRSpirodoc46.this.stop();
				} else
					Log.d(TAG, "Connection Stopped");
				return true;
			}
		}

		private void startSpiro() {
			try {
				// Mostra messaggio avvio misura e attende OK
				String lcdMessageString = ResourceManager.getResource().getString("mirMeasureMessage");
				deviceListener.askSomething(lcdMessageString + "\n\n" + ResourceManager.getResource().getString("KMeasStartMsgOK"),
						ResourceManager.getResource().getString("confirmButton"),
						ResourceManager.getResource().getString("cancelButton"));
				synchronized (this) {
					this.wait();
				}
				if (stopped) {
					deviceListener.notifyError(DeviceListener.MEASUREMENT_ERROR, ResourceManager.getResource().getString("KAbortOperation"));
					MIRSpirodoc46.this.stop();
					return;
				}
				deviceListener.notifyToUi(lcdMessageString);

				// Invio Cod_FVC (Avvio Spirometria FVC)
				Log.d(TAG, "sending Cod_FVC");
				mmOutStream.write(Cod_FVC_M);

				// Ricezione dati real time
				byte[] realTimeData = new byte[3];
				int numRead = 0;
				boolean end = false;
				boolean flag = true;
				while (!end) {
					int ret = mmInStream.read(realTimeData, numRead, 3-numRead);
					if (ret < 0) {
						Log.e(TAG, "Read EOF");
						deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
						MIRSpirodoc46.this.stop();
						return;
					}
  					numRead +=ret;
					if (numRead == 3) {
						if (realTimeData[0] == 0x04)
							// header id = 4 -> fine dati real time
							end = true;
						else {
							numRead = 0;
							// Log.d(TAG, "Real Time Data: " + (realTimeData[0] & 255));
						}
						if (flag && (realTimeData[0] == 0x10)) {
							deviceListener.notifyToUi(ResourceManager.getResource().getString("KMeasuring"));
							flag = false;
						}
					}
				}

				// Invio Cod_LAST (Ricezione record ultima misura)
				Log.d(TAG, "sending Cod_LAST");
				mmOutStream.write(Cod_LAST);

				// Ricezione nr bytes della misura (primi 3 bytes)
				numRead = 0;
				while (numRead < 3) {
					int ret = mmInStream.read(realTimeData, numRead, 3-numRead);
					if (ret < 0) {
						Log.e(TAG, "Read EOF");
						deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
						MIRSpirodoc46.this.stop();
						return;
					}
					numRead += ret;
				}
				int numBytes = ((realTimeData[0] & 255) << 16) + ((realTimeData[1] & 255) << 8) + (realTimeData[2] & 255);
				numBytes -= 3; // tolgo i primi tre bytes già letti
				Log.d(TAG, "Numero bytes misura: " + numBytes);

				// Ricezione dati misura
				byte[] measureData = new byte[numBytes];
				numRead = 0;
				while (numRead < numBytes) {
					int ret = mmInStream.read(measureData, numRead, numBytes-numRead);
					if (ret < 0) {
						Log.e(TAG, "Read EOF");
						deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
						MIRSpirodoc46.this.stop();
						return;
					}
					numRead += ret;
					// Log.d(TAG, "Misura: numRead = " + numRead);
				}
				Log.d(TAG, "Dati Misura letti");

				// verifica crc
				int crc = (realTimeData[0] & 0xff) + (realTimeData[1] & 0xff) +(realTimeData[2] & 0xff);
				for (int i=1; i<measureData.length-2; i++)
					crc += measureData[i] & 0xff;
				if ((measureData[measureData.length-2] != (byte)(crc >>> 8)) || (measureData[measureData.length-1] != (byte) crc)) {
					Log.e(TAG, "CRC Error");
					deviceListener.notifyError(DeviceListener.DEVICE_DATA_ERROR, ResourceManager.getResource().getString("EDataReadError"));
					MIRSpirodoc46.this.stop();
					return;
				}

				Util.logFile("MIRLog.log",measureData, " -- SPIRO Measure Data", true);

				// Gestione dati misura
				makeSpiroResultData(measureData);
				MIRSpirodoc46.this.stop();
			} catch (IOException e) {
				if (!stopped) {
					Log.e(TAG, "disconnected", e);
					deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
					MIRSpirodoc46.this.stop();
				} else
					Log.d(TAG, "Connection Stopped");
			} catch (InterruptedException e) {
				if (!stopped) {
					Log.e(TAG, "interrupted", e);
					deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
					MIRSpirodoc46.this.stop();
				} else {
					Log.d(TAG, "Operation Stopped");
				}
			}
		}

		private void startOxy() {
			try {
				initOxyData();

				// Mostra messaggio avvio misura e attende OK
				deviceListener.askSomething(ResourceManager.getResource().getString("KMeasStartMsgOK"),
						ResourceManager.getResource().getString("confirmButton"),
						ResourceManager.getResource().getString("cancelButton"));
				synchronized (this) {
					this.wait();
				}
				if (stopped) {
					deviceListener.notifyError(DeviceListener.MEASUREMENT_ERROR, ResourceManager.getResource().getString("KAbortOperation"));
					MIRSpirodoc46.this.stop();
					return;
				}
				deviceListener.notifyToUi(ResourceManager.getResource().getString("DoMeasureOS"));

				// Invio Cod_FVC (Avvio Spirometria FVC)
				Log.d(TAG, "sending Cod_OSS");
				mmOutStream.write(Cod_OSS);

				// Ricezione dati real time
				byte[] realTimeData = new byte[3];
				int numRead = 0;
				int numSamples = 0;
				boolean end = false;
				boolean stopSended = false;
				long startTime = 0;

				while (!end) {
					int ret = mmInStream.read(realTimeData, numRead, 3-numRead);
					if (ret < 0) {
						Log.e(TAG, "Read EOF");
						deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
						MIRSpirodoc46.this.stop();
						return;
					}
					numRead +=ret;
					if (stopSended) {
						if (realTimeData[0] == 0x04) // 4 = fine dati real time (inviato solo 1 byte invece di 3!!!)
							end = true;
					}
					if (numRead == 3) {
						if (realTimeData[0] == (byte)0xF9) {
							// Sensore SPO2 scollegato
							deviceListener.notifyError(DeviceListener.MEASUREMENT_ERROR, ResourceManager.getResource().getString("ESensorUnplugged"));
							MIRSpirodoc46.this.stop();
							return;
						} else if ((realTimeData[0] == (byte) 0xFA) && (numSamples > 0) && !stopSended) {
							// Dito disinserito
							if (numSamples < MIN_OXY_SAMPLES) {
								deviceListener.notifyError(DeviceListener.MEASUREMENT_ERROR, ResourceManager.getResource().getString("KMinMeasuresMsg"));
								MIRSpirodoc46.this.stop();
								return;
							}
							// invio comando di termine misura
							Log.d(TAG,"Dito fuori: Sending Cod_ESC");
							mmOutStream.write(Cod_ESC);
							stopSended = true;
						} else if (realTimeData[0] == (byte)0xED) {
							// misura di SPO2
							if (addOxySample(realTimeData)) {
								deviceListener.notifyError(DeviceListener.DEVICE_DATA_ERROR, ResourceManager.getResource().getString("EDataReadError"));
								MIRSpirodoc46.this.stop();
								return;
							}
							if (numSamples == 0) {
								startTime = System.currentTimeMillis();
								deviceListener.notifyToUi(ResourceManager.getResource().getString("KMeasuring"));
							}
							numSamples++;
							if (numSamples > 1)
								Log.d(TAG,"Num Samples="+numSamples+" value="+realTimeData[2]+"" +
										" Step Oxy="+((System.currentTimeMillis()-startTime) / (numSamples-1)));

							if (numSamples == MAX_OXY_SAMPLES) {
								// invio comando di termine misura
								Log.d(TAG,"Sending Cod_ESC");
								mmOutStream.write(Cod_ESC);
								stopSended = true;
							}
						} else if (realTimeData[0] == (byte)0xEE) {
							// misura di HR
							if (addOxySample(realTimeData)) {
								deviceListener.notifyError(DeviceListener.DEVICE_DATA_ERROR, ResourceManager.getResource().getString("EDataReadError"));
								MIRSpirodoc46.this.stop();
								return;
							}
						}
						numRead = 0;
					}
				}

				// Gestione dati misura
				makeOxyResultData(startTime, System.currentTimeMillis());
				MIRSpirodoc46.this.stop();
			} catch (IOException e) {
				if (!stopped) {
					Log.e(TAG, "disconnected", e);
					deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
					MIRSpirodoc46.this.stop();
				} else
					Log.d(TAG, "Connection Stopped");
			} catch (InterruptedException e) {
				if (!stopped) {
					Log.e(TAG, "interrupted", e);
					deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
					MIRSpirodoc46.this.stop();
				} else {
					Log.d(TAG, "Operation Stopped");
				}
			}
		}

		public void stopOperation() {
			stopped = true;
			try {
				if (mmSocket != null) {
					mmSocket.close();
					Log.d(TAG, "Socket Closed");
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void makeSpiroResultData(byte[] data) {
		Log.d(TAG, "ReadDataSpiro");

		int streamLength;
		int numVT  = getIntValue(data[112], data[113]);
		int numEXP = getIntValue(data[116], data[117]);
		int numINS = getIntValue(data[118], data[119]);
		int size = numVT*2+numEXP*2+numINS*2;
		if (size != (data.length-2-120)) {
			deviceListener.notifyError(DeviceListener.DEVICE_DATA_ERROR, ResourceManager.getResource().getString("EDataReadError"));
			MIRSpirodoc46.this.stop();
			return;
		}

		Log.d(TAG, "Num VT="+numVT+" Num EXP="+numEXP+" Num INS="+numINS);
		Log.d(TAG, "Size="+size);

		streamLength = 70 + 4 + 62 + 8 + size;
		Log.d(TAG, "Stream Length="+streamLength);
		ByteBuffer stream = ByteBuffer.allocate(streamLength);
		stream.order(ByteOrder.BIG_ENDIAN);

		int year, month, day, hour, minute, second;
		GregorianCalendar calendar = new GregorianCalendar();
		year = calendar.get(Calendar.YEAR);
		month = calendar.get(Calendar.MONTH) + 1; // MONTH is from 0 to 11
		day = calendar.get(Calendar.DAY_OF_MONTH);
		hour = calendar.get(Calendar.HOUR_OF_DAY);
		minute = calendar.get(Calendar.MINUTE);
		second = calendar.get(Calendar.SECOND);

		stream.put(data,0,2); // Temperatura
		stream.putShort((short)day); // Data Misura
		stream.putShort((short)month);
		stream.putShort((short)year);
		stream.putShort((short)hour);
		stream.putShort((short)minute);
		stream.putShort((short)second);
		stream.putShort((short)0); // Sintomi

		Patient patient = UserManager.getUserManager().getCurrentPatient();
		int age = getAge(patient.getBirthdayDate());
		int height = Integer.parseInt(patient.getHeight());
		int weight = (int)Float.parseFloat(patient.getWeight());
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.ITALY);
		Calendar c = Calendar.getInstance();
		try {
			c.setTime(sdf.parse(patient.getBirthdayDate()));
		} catch (ParseException e) {
			Log.d(TAG,e.toString());
		}
		day = c.get(Calendar.DATE);
		month = c.get(Calendar.MONTH) + 1;
		year = c.get(Calendar.YEAR);
		stream.put((byte)0);  // Dati Paziente
		switch (patient.getSex()) {
			case "M":
			case "m":
				stream.put((byte)77); // 'M'
				break;
			case "F":
			case "f":
				stream.put((byte)70); // 'F'
				break;
		}
		stream.putShort((short)age);
		stream.putShort((short)height);
		stream.putShort((short)weight);
		stream.putShort((short)day);  // Data Nascita
		stream.putShort((short)month);
		stream.putShort((short)year);
		ByteBuffer tmpBuf = ByteBuffer.allocate(18);
		byte[] tmpArr = patient.getSurname().getBytes(StandardCharsets.UTF_8);
		tmpBuf.put(tmpArr, 0, Math.min(tmpArr.length, 18));
		stream.put(tmpBuf.array());  // Cognome
		tmpArr = patient.getName().getBytes(StandardCharsets.UTF_8);
		tmpBuf.clear();
		tmpBuf.put(tmpArr, 0, Math.min(tmpArr.length, 18));
		stream.put(tmpBuf.array());  // Nome

		stream.put(data, 2, 4); // step_FV_base_A, ampli_FLUSSO_A

		stream.putShort((short)1); // Num Curve
		stream.putShort((short)0); // Semaforo

		stream.put(data,6,62); // Parametri misurati
		stream.put(data,112,8); // Numero punti VT, EXP, INS
		Log.d(TAG, "Stream available=" + stream.remaining());
		Log.d(TAG, "Remainig data size=" + (data.length-2-120));
		stream.put(data,120,data.length-2-120); // Valori curve VT, EXP, INS (meno due bytes CRC)

		byte[] textPtr = stream.array();

		DecimalFormat df1 = new DecimalFormat("0.0");
		DecimalFormat df2 = new DecimalFormat("0.00");

		HashMap<String,String> tmpVal = new HashMap<>();

		// FVC
		double bufFVC = (double)getIntValue(textPtr[82],textPtr[83])/100;
		tmpVal.put(GWConst.EGwCode_0A, df2.format(bufFVC).replace ('.', ','));

		// FEV1
		double bufFEV1 = (double)getIntValue(textPtr[84],textPtr[85])/100;
		tmpVal.put(GWConst.EGwCode_09, df2.format(bufFEV1).replace ('.', ','));

		// FEV1%
		double bufFev1FvcRatio = (double)getIntValue(textPtr[100],textPtr[101])/10;
		tmpVal.put(GWConst.EGwCode_0C, df1.format(bufFev1FvcRatio).replace ('.', ','));

		// PEF
		double bufPEF = (double)getIntValue(textPtr[86],textPtr[87])/100;
		tmpVal.put(GWConst.EGwCode_08, df2.format(bufPEF).replace ('.', ','));

		// F2575
		double bufF2575 = (double)getIntValue(textPtr[90],textPtr[91])/100;
		tmpVal.put(GWConst.EGwCode_0D, df2.format(bufF2575).replace ('.', ','));

		// FET
		double bufFET = (double)getIntValue(textPtr[92],textPtr[93])/100;
		tmpVal.put(GWConst.EGwCode_0L, df2.format(bufFET).replace ('.', ','));

		if ((bufFVC == 0.) || (bufFET == 0.)) {
			deviceListener.notifyError(DeviceListener.MEASUREMENT_ERROR, ResourceManager.getResource().getString("ENoMeasurementDone"));
			MIRSpirodoc46.this.stop();
			return;
		}

		// Batteria
		// TODO
		//batt = getIntValue(textPtr[92],textPtr[93])/100;

		// Timestamp
		calendar = new GregorianCalendar();
		year = calendar.get(Calendar.YEAR);
		// MONTH begin from 0 to 11, so we need add 1 to use it in the timestamp
		month = calendar.get(Calendar.MONTH) + 1;
		day = calendar.get(Calendar.DAY_OF_MONTH);
		hour = calendar.get(Calendar.HOUR_OF_DAY);
		minute = calendar.get(Calendar.MINUTE);
		second = calendar.get(Calendar.SECOND);

		String spiroFileName = "spiro-"+ year + month + day + "-" + hour + minute + second +".spi";
		tmpVal.put(GWConst.EGwCode_0M, spiroFileName);  // filename
		tmpVal.put(GWConst.EGwCode_BATTERY, Integer.toString(batteryLevel)); // livello batteria

		Measure m = getMeasure();
		m.setFile(stream.array());
		m.setFileType(XmlManager.MIR_SPIRO_FILE_TYPE);
		m.setMeasures(tmpVal);
		MIRSpirodoc46.this.stop();

		deviceListener.showMeasurementResults(m);
	}


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
	private Vector<OxyElem> oxyQueue;
	private byte[] oxyStream;	private OxyElem currElem = null;

	private static class OxyElem {
		public int iSat = 1000;
		public int iHR = 1000;
	}

	private void initOxyData() {
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

		oxyQueue = new Vector<>();
		ByteBuffer tmpStream = ByteBuffer.allocate(BASE_OXY_STREAM_LENGTH);
		tmpStream.put(66, (byte)0x00); //STEP_OXY MSB tempo di campionamento in 1/10 secondo
		tmpStream.put(67, (byte)0x0A); //STEP_OXY LSB
		tmpStream.put(68, (byte)0x00); //FL_TEST
		tmpStream.put(69, (byte)0x20);
		oxyStream = tmpStream.array();
	}

	private boolean addOxySample(byte[] data) {
		if (currElem == null)
			currElem = new OxyElem();

		switch (data[0]) {
			case (byte)0xED:
				if (currElem.iSat != 1000)
					return true;
				int aSpO2 = data[2] & 0xff;
				currElem.iSat = aSpO2;

				if (aSpO2 < iSpO2Min)
					iSpO2Min = aSpO2;
				if (aSpO2 > iSpO2Max)
					iSpO2Max = aSpO2;
				if (aSpO2 < 90)
					iT90++;
				if (aSpO2 < 89)
					iT89++;
				if (aSpO2 < 88)
					iT88++;
				if (aSpO2 < 87)
					iT87++;
				if (aSpO2 < 89) {
					iEventSpO289Count++;
					if (iEventSpO289Count == 20)
						iEventSpO289++;
				} else {
					iEventSpO289Count = 0;
				}
				break;
			case (byte)0xEE:
				if (currElem.iHR != 1000)
					return true;
				int aHR = data[2] & 0xff;
				currElem.iHR = aHR;

				if (aHR < iHRMin)
					iHRMin = aHR;
				if (aHR > iHRMax)
					iHRMax = aHR;
				if (aHR < 40)
					iEventBradi++;
				if (aHR > 120)
					iEventTachi++;
				if (aHR < 40)
					iT40++;
				if (aHR > 120)
					iT120++;
				break;
		}

		if ((currElem.iHR != 1000) && (currElem.iSat != 1000)) {
			oxyQueue.add(currElem);
			currElem = null;
		}
		return false;
	}

	private void makeOxyResultData(long startTime, long endTime) {
		int hrTot=0, spO2Tot=0, sampleCount;
		OxyElem elem;

		sampleCount = oxyQueue.size();
		ByteBuffer tmpStream = ByteBuffer.allocate(BASE_OXY_STREAM_LENGTH + sampleCount*2);
		tmpStream.put(oxyStream);
		oxyStream = tmpStream.array();
		// Sample num
		oxyStream[187] = (byte)((sampleCount>>8) & 0xFF);
		oxyStream[188] = (byte)((sampleCount) & 0xFF);
		for (int i=0; i<oxyQueue.size(); i++) {
			elem = oxyQueue.get(i);
			hrTot += elem.iHR;
			spO2Tot += elem.iSat;
			oxyStream[BASE_OXY_STREAM_LENGTH + (i*2)] =	(byte)(elem.iSat & 0xFF);		//SpO2
			oxyStream[BASE_OXY_STREAM_LENGTH + (i*2) + 1] = (byte)(elem.iHR & 0xFF);	//HR
		}
		iSpO2Med = ((double)spO2Tot/(double)sampleCount);
		iHRMed = ((double)hrTot/(double)sampleCount);

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
		int iAnalysisTime = (int)((endTime-startTime)/1000L); // tempo in secondi
		Time tDurata = calcTime(iAnalysisTime);
		String durata = Integer.toString(iAnalysisTime);

		//Recording Time & Analysis Time
		oxyStream[84] = (byte)tDurata.getHh();
		oxyStream[85] = (byte)tDurata.getMm();
		oxyStream[86] = (byte)tDurata.getSs();
		oxyStream[87] = (byte)tDurata.getHh();
		oxyStream[88] = (byte)tDurata.getMm();
		oxyStream[89] = (byte)tDurata.getSs();

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
		tmpVal.put(GWConst.EGwCode_BATTERY, Integer.toString(batteryLevel)); // livello batteria

		Measure m = getMeasure();
		m.setFile(oxyStream);
		m.setFileType(XmlManager.MIR_OXY_FILE_TYPE);
		m.setMeasures(tmpVal);
		deviceListener.showMeasurementResults(m);
	}

	private int getIntValue(byte msb, byte lsb) {
		return ((msb & 0xff) << 8) + (lsb & 0xff);
	}

	private int getAge(String birthDate) {
		// birthDate in formato yyyyMMdd
		DateFormat formatter = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
		int d1 = Integer.parseInt(birthDate);
		int d2 = Integer.parseInt(formatter.format(new Date()));
		return (d2 - d1) / 10000;
	}

	private static class Time {
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

	private Time calcTime(int aSec) {
		int hh = aSec/3600;
		int mm = (aSec%3600)/60;
		int ss = (aSec%3600)%60;
		return new Time(hh, mm, ss);
	}
}