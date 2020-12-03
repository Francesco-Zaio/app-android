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

	private static final byte Cod_ON = 0x00;
	private static final byte Cod_FVC = 0x08;
	private static final byte Cod_LAST = (byte)0xA2;

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

				mmInStream  = mmSocket.getInputStream();
				mmOutStream = mmSocket.getOutputStream();

				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				// Invio Cod_ON
				Log.d(TAG, "sending Cod_ON");
				mmOutStream.write(Cod_ON);

				// Ricezione StartupSequence (SDA) 32 byte
				byte[] startupSequence = new byte[32];
				int numRead = 0;
				Util.logFile("MIRLog.log",null, " ** MIR SPP Log ***", false);
				while (numRead < 32) {
					int ret = mmInStream.read(startupSequence, numRead, 32-numRead);
					if (ret < 0) {
						Log.e(TAG, "Read EOF");
						deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
						MIRSpirodoc46.this.stop();
						return;
					}
					numRead += ret;
					Log.d(TAG, "StartupSequence: numRead = " + numRead);
				}
				Util.logFile("MIRLog.log",startupSequence, " -- StartupSequence", true);
				Log.d(TAG, "StartupSequence (SDA) received");


				if (operationType == OperationType.Pair) {
					deviceListener.setBtMAC(iBtDevAddr);
					deviceListener.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
					MIRSpirodoc46.this.stop();
					return;
				}

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
				mmOutStream.write(Cod_FVC);

				// Ricezione Real time test data
				byte[] realTimeData = new byte[3];
				numRead = 0;
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
						if (realTimeData[0] == 0x04) // End of real time test
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

				// Ricezione nr bytes della misura
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
				int crc = (realTimeData[0] & 0xff) + (realTimeData[1] & 0xff) +(realTimeData[2] & 0xff);
				for (int i=1; i<measureData.length-2; i++)
					crc += measureData[i] & 0xff;
				if ((measureData[measureData.length-2] != (byte)(crc >>> 8)) || (measureData[measureData.length-1] != (byte) crc)) {
					Log.e(TAG, "CRC Error");
					deviceListener.notifyError(DeviceListener.DEVICE_DATA_ERROR, ResourceManager.getResource().getString("EDataReadError"));
					MIRSpirodoc46.this.stop();
					return;
				}

				Util.logFile("MIRLog.log",measureData, " -- Measure Data", true);
				mmSocket.close();
				mmSocket = null;
				ReadDataSpiro(measureData);
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

		private void ReadDataSpiro(byte[] data) {
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
			// TODO
			//tmpVal.put(GWConst.EGwCode_BATTERY, Integer.toString(batt)); // livello batteria

			Measure m = getMeasure();
			m.setFile(stream.array());
			m.setFileType(XmlManager.MIR_SPIRO_FILE_TYPE);
			m.setMeasures(tmpVal);
			MIRSpirodoc46.this.stop();

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
}