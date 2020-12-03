package com.ti.app.telemed.core.btdevices;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.BTSearcher;
import com.ti.app.telemed.core.btmodule.BTSearcherEventListener;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.util.Util;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Calendar;
import java.util.UUID;
import java.util.Vector;

public class MIRSpirodocMiniLab extends DeviceHandler implements BTSearcherEventListener {

	private static final String TAG = "MIRSpirodocMiniLab";
	private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

	private static final byte ACK = 0x06;
	private static final byte NACK = 0x15;
	private static final byte END_OBJECT = 0x03;

	private static final byte[] getCommand = {(byte)85,0,2,0,(byte)210,0,0,0,3,2,0,(byte)255,0,0,0,3,1,(byte)219};
	private static final byte[] readConfigCommand = {(byte)85,0,2,0,(byte)128,0,0,0,3,2,0,(byte)255,0,0,0,3,1,(byte)137};

	private MsgParser parser = new MsgParser();

    private BTSearcher iServiceSearcher;
	private Vector<BluetoothDevice> deviceList = new Vector<>();
	private MIRSpirodocMiniLab.DeviceCommunicationThread commThread = null;

	public static boolean needConfig(UserDevice userDevice) {
		return false;
	}

	public MIRSpirodocMiniLab(DeviceListener listener, UserDevice ud) {
        super(listener, ud);
		iServiceSearcher = new BTSearcher();
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
			commThread.interrupt();
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

		commThread = new MIRSpirodocMiniLab.DeviceCommunicationThread(bd);
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
        // this class object must return to the initial state
        iState = TState.EWaitingToGetDevice;
    }

	private void stop()  {
		iServiceSearcher.stopSearchDevices();
		iServiceSearcher.clearBTSearcherEventListener();
		iServiceSearcher.close();

		reset();
	}

	private static class MsgParser {
		byte[] msg = new byte[16384];
		int length = 0;
		int offset = 0;
		boolean end = false;
		boolean error = false;
		boolean ack = false;
		byte[] info = null;
		byte[] devConfig = null;
		byte[] dateTimeConfig = null;
		byte[] spiroConfig = null;
		byte[] endOfFile = null;
		private Vector<byte[]> spiroObjs = new Vector<>();
		//private Vector<byte[]> curvesObjs = new Vector<>();

		public void reset() {
			offset = 0;
			end = false;
			error = false;
			ack = false;
			info = null;
			spiroObjs.clear();
			//curvesObjs.clear();
		}

		public boolean parseMessage(byte[] data, int dataLength) {
			Log.d(TAG, "parseMessage: " + dataLength);
			if ((length + dataLength) > 16384) {
				Log.e(TAG,"ERRORE: messaggio troppo lungo");
				error = true;
				return true;
			}

			System.arraycopy(data,0,msg,length,dataLength);
			length += dataLength;

			if (offset == 0)
				if (data[0] == ACK) {
					ack = true;
					return true;
				} else if (length >= 2) {
					if ((msg[0] != 0x55) || (msg[1] != 0x0)) {
						Log.e(TAG,"parseMessage: start of message not found");
						error = true;
						return true;
					} else {
						Log.d(TAG,"parseMessage: start of message found");
						offset = 2;
					}
				}

			// Parsing degli oggetti
			while ((length-offset) >= 7) {
				// obj header disponibile, leggo la lunghezza in bytes dell'oggetto
				int objLength = ((msg[offset+3] & 0xff) << 16) + ((msg[offset+4] & 0xff) << 8) + (msg[offset+5] & 0xff);
				// verifico se ho ricevuto tutto l'oggetto
				if ((length-offset) >= (6+objLength+1)) {
					Log.d(TAG,"parseMessage: Object received");
					parseObject(objLength);
				} else
					return false;
			}

			// Se ho ricevuto l'END FILE Object (fine messaggio) leggo i due bytes di CRC e verifico la correttezza
			if (end && (length-offset) >=2) {
				Log.d(TAG,"parseMessage: checkCRC");
				checkCRC();
				return true;
			}

			return false;
		}

		private void parseObject(int objLength) {
			// verifico se l'ultimo byte dell'oggetto è il valore END OBJECT
			if (msg[offset+6+objLength] != END_OBJECT) {
				Log.e(TAG,"ERRORE END OBJECT BYTE");
			}
			// salvo i dati in base al tipo di oggetto
			switch (msg[offset+2]) {
				case 1: // INFO Object
					Log.d(TAG,"parseMessage: INFO Object received");
					info = Arrays.copyOfRange(msg, offset+6, offset+6+objLength+1);
					Util.logFile("MIRLog.log",info, "- INFO", true);
					break;
				case 2:
					// ricevuta una Spirometria
					Log.d(TAG,"parseMessage: SPIRO Object received");
					spiroObjs.add(Arrays.copyOfRange(msg, offset+6, offset+6+objLength+1));
					Util.logFile("MIRLog.log",Arrays.copyOfRange(msg, offset+6, offset+6+objLength+1), "- SPIRO", true);
					break;
				case 6:
					Log.d(TAG,"parseMessage: CURVE Object received");
					Util.logFile("MIRLog.log",Arrays.copyOfRange(msg, offset+6, offset+6+objLength+1), "- CURVE", true);
					// ricevuta un Curva di una spirometria
					//curvesObjs.add(Arrays.copyOfRange(msg, offset+6, offset+6+objLength));
					break;
				case (byte)0x90:
					// DEVICE_CONFIG
					Log.d(TAG,"parseMessage: DEVICE_CONFIG Object received");
					devConfig = Arrays.copyOfRange(msg, offset, offset+6+objLength+1);
					Util.logFile("MIRLog.log",devConfig, "- DEVICE_CONFIG", true);
					break;
				case (byte)0x91:
					// SPIROMETRY_CONFIG
					Log.d(TAG,"parseMessage: SPIROMETRY_CONFIG Object received");
					spiroConfig = Arrays.copyOfRange(msg, offset, offset+6+objLength+1);
					Util.logFile("MIRLog.log",Arrays.copyOfRange(msg, offset+6, offset+6+objLength+1), "- SPIROMETRY_CONFIG", true);
					break;
				case (byte)0x92:
					// OXIMETRY_CONFIG
					Log.d(TAG,"parseMessage: OXIMETRY_CONFIG Object received");
					Util.logFile("MIRLog.log",Arrays.copyOfRange(msg, offset+6, offset+6+objLength+1), "- OXIMETRY_CONFIG", true);
					break;
				case (byte)0x93:
					// PHYSICAL_ACTIVITY_CONFIG
					Log.d(TAG,"parseMessage: PHYSICAL_ACTIVITY_CONFIG Object received");
					Util.logFile("MIRLog.log",Arrays.copyOfRange(msg, offset+6, offset+6+objLength+1), "- PHYSICAL_ACTIVITY_CONFIG", true);
					break;
				case (byte)0x94:
					// eDIARY_CONFIG
					Log.d(TAG,"parseMessage: eDIARY_CONFIG Object received");
					Util.logFile("MIRLog.log",Arrays.copyOfRange(msg, offset+6, offset+6+objLength+1), "- eDIARY_CONFIG", true);
					break;
				case (byte)0x95:
					// WAKEUP_CONFIG
					Log.d(TAG,"parseMessage: WAKEUP_CONFIG Object received");
					Util.logFile("MIRLog.log",Arrays.copyOfRange(msg, offset+6, offset+6+objLength+1), "- WAKEUP_CONFIG", true);
					break;
				case (byte)0x96:
					// DATETIME_CONFIG
					Log.d(TAG,"parseMessage: DATETIME_CONFIG Object received");
					dateTimeConfig = Arrays.copyOfRange(msg, offset, offset+6+objLength+1);
					Util.logFile("MIRLog.log",dateTimeConfig, "- DATETIME_CONFIG", true);
					break;
				case (byte)0xB0:
					// FACTORY_DEFAULT_CONFIG
					Log.d(TAG,"parseMessage: FACTORY_DEFAULT_CONFIG Object received");
					Util.logFile("MIRLog.log",Arrays.copyOfRange(msg, offset+6, offset+6+objLength+1), "- FACTORY_DEFAULT_CONFIG", true);
					break;
				case (byte)0xff:
					// ricevuto END FILE Object
					Log.d(TAG,"parseMessage: END FILE Object received");
					endOfFile = Arrays.copyOfRange(msg, offset, offset+6+objLength+1);
					end = true;
					break;
			}
			// aggiorno l'offset al prossimo oggetto
			offset += (6+objLength+1);
		}

		private void checkCRC() {
			error = false;
		}
	}

	private class DeviceCommunicationThread extends Thread {
		private BluetoothSocket mmSocket = null;
		private BluetoothDevice device;

		DeviceCommunicationThread(BluetoothDevice device) {
			this.device = device;
		}

		@Override
		public void run() {
			deviceListener.notifyToUi(ResourceManager.getResource().getString("KConnectingDev"));

			if (operationType == OperationType.Pair)
				runCfg();
			else
				runGetMeasures();
		}

		private void runGetMeasures() {
			try {
				mmSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
				mmSocket.connect();

				InputStream mmInStream  = mmSocket.getInputStream();
				OutputStream mmOutStream = mmSocket.getOutputStream();

				Log.d(TAG, "sending getCommand");
				mmOutStream.write(getCommand);

				byte[] buffer = new byte[1024];
				int numRead;
				//Ricezione messaggi
				Util.logFile("MIRLog.log",null, " ** Objects Log ***", false);
				boolean endMessage = false;
				while (!endMessage) {
					// Read from the InputStream
					Log.d(TAG, "waiting data...");
					numRead = mmInStream.read(buffer);
					endMessage = parser.parseMessage(buffer, numRead);
				}

				if (parser.error) {
					mmOutStream.write(NACK);
					deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EDataReadError"));
				} else {
					mmOutStream.write(ACK);
					if (parser.spiroObjs.isEmpty())
						deviceListener.notifyError(DeviceListener.NO_MEASURES_FOUND, ResourceManager.getResource().getString("ENoMeasuresFound"));
					else
						// TODO gestire e invare misure
						deviceListener.notifyError(DeviceListener.NO_MEASURES_FOUND, ResourceManager.getResource().getString("ENoMeasurementDone"));
				}
			} catch (IOException e) {
				Log.e(TAG, "disconnected", e);
				deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
				MIRSpirodocMiniLab.this.stop();
			} finally {
				try {
					if (mmSocket != null)
						mmSocket.close();
				} catch (Exception e2) {
					e2.printStackTrace();
				}
			}
		}

		private void runCfg() {
			// Make a connection to the BluetoothSocket
			try {
				mmSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
				mmSocket.connect();

				InputStream mmInStream  = mmSocket.getInputStream();
				OutputStream mmOutStream = mmSocket.getOutputStream();

				Log.d(TAG, "sending readConfigCommand");
				mmOutStream.write(readConfigCommand);
				int val = mmInStream.read();
				if (val != ACK) {
					deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
					MIRSpirodocMiniLab.this.stop();
					return;
				}

				Log.d(TAG, "ACK received");
				mmSocket.close();
				mmSocket = null;
				Thread.sleep(2500);
				mmSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
				mmSocket.connect();

				deviceListener.notifyToUi(ResourceManager.getResource().getString("KMeasuring"));

				mmInStream = mmSocket.getInputStream();
				mmOutStream = mmSocket.getOutputStream();
				Log.d(TAG, "sending getCommand");
				mmOutStream.write(getCommand);

				byte[] buffer = new byte[1024];
				int numRead;
				//Ricezione messaggi
				Util.logFile("MIRLog.log",null, " ** Objects Log ***", false);
				boolean endMessage = false;
				while (!endMessage) {
					// Read from the InputStream
					Log.d(TAG, "waiting data...");
					numRead = mmInStream.read(buffer);
					endMessage = parser.parseMessage(buffer, numRead);
				}

				// Invio NACK per poter ricevere di nuovo eventuali misure presenti
				mmOutStream.write(NACK);
				
				if (parser.error) {
					deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EDataReadError"));
					MIRSpirodocMiniLab.this.stop();
					return;
				} else {
					if (parser.dateTimeConfig == null || parser.devConfig == null || parser.spiroConfig == null) {
						deviceListener.notifyError(DeviceListener.NO_MEASURES_FOUND, ResourceManager.getResource().getString("EWrongConfiguration"));
						MIRSpirodocMiniLab.this.stop();
						return;
					}
				}

				mmSocket.close();
				mmSocket = null;
				Thread.sleep(2500);
				mmSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
				mmSocket.connect();
				mmInStream = mmSocket.getInputStream();
				mmOutStream = mmSocket.getOutputStream();

				Log.d(TAG, "sending new CFG");
				mmOutStream.write(getCfgMessage());
				Log.d(TAG, "waiting ack");
				val = mmInStream.read();
				if (val != ACK) {
					Log.e(TAG, "ACK Not received: " + val);
					deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
					MIRSpirodocMiniLab.this.stop();
					return;
				}
				Log.d(TAG, "ACK received");

				mmSocket.close();
				mmSocket = null;
				Thread.sleep(2500);
				mmSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
				mmSocket.connect();
				mmInStream = mmSocket.getInputStream();
				mmOutStream = mmSocket.getOutputStream();

				Log.d(TAG, "setting Time");
				mmOutStream.write(getTimeMessage());
				Log.d(TAG, "waiting ack");
				val = mmInStream.read();
				if (val != ACK) {
					Log.e(TAG, "ACK Not received: " + val);
					deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
					MIRSpirodocMiniLab.this.stop();
					return;
				}
				Log.d(TAG, "ACK received");

				deviceListener.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
				deviceListener.setBtMAC(iBtDevAddr);
				MIRSpirodocMiniLab.this.stop();
			} catch (IOException e) {
				Log.e(TAG, "disconnected", e);
				deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
				MIRSpirodocMiniLab.this.stop();
			} catch (InterruptedException e) {
				Log.d(TAG, "Operation aborted by the User");
			} finally {
				try {
					if (mmSocket != null)
						mmSocket.close();
				} catch (Exception e2) {
					e2.printStackTrace();
				}
			}
		}

		private byte[] getCfgMessage() {
			byte[] msg = new byte[
					2
					+parser.devConfig.length
					+parser.spiroConfig.length
					+parser.endOfFile.length
					+2];

			msg[0] = (byte)85;
			msg[1] = 0;
			int offset = 2;
			parser.devConfig[11] = 1; // Abilita invio Curve
			System.arraycopy(parser.devConfig,0,msg,offset,parser.devConfig.length);
			offset += parser.devConfig.length;
			parser.spiroConfig[16] = 7; // Invio di tutte e 3 le curve
			System.arraycopy(parser.spiroConfig,0,msg,offset,parser.spiroConfig.length);
			offset += parser.spiroConfig.length;
			System.arraycopy(parser.endOfFile,0,msg,offset,parser.endOfFile.length);

			int crc = 0;
			for (int i=1; i<msg.length-2; i++)
				crc += msg[i] & 0xff;
			msg[msg.length-2] = (byte) (crc >>> 8);
			msg[msg.length-1] = (byte) crc;
			return msg;
		}
	}

	private byte[] getTimeMessage() {
		byte[] msg = new byte[
				2
				+parser.dateTimeConfig.length
				+parser.endOfFile.length
				+2];

		msg[0] = (byte)85;
		msg[1] = 0;
		int offset = 2;
		Calendar now = Calendar.getInstance();
		parser.dateTimeConfig[6] = 1; // formato dd/mm/aaaa
		parser.dateTimeConfig[7] = 1; // enable time setting
		parser.dateTimeConfig[8] = (byte)now.get(Calendar.DAY_OF_MONTH);
		parser.dateTimeConfig[9] = (byte)(now.get(Calendar.MONTH) + 1);
		parser.dateTimeConfig[10] = (byte)(now.get(Calendar.YEAR) >>> 8);
		parser.dateTimeConfig[11] = (byte)now.get(Calendar.YEAR);
		parser.dateTimeConfig[12] = (byte)now.get(Calendar.HOUR_OF_DAY);
		parser.dateTimeConfig[13] = (byte)now.get(Calendar.MINUTE);
		parser.dateTimeConfig[14] = (byte)now.get(Calendar.SECOND);
		System.arraycopy(parser.dateTimeConfig,0,msg,offset,parser.dateTimeConfig.length);
		offset += parser.dateTimeConfig.length;
		System.arraycopy(parser.endOfFile,0,msg,offset,parser.endOfFile.length);

		int crc = 0;
		for (int i=1; i<msg.length-2; i++)
			crc += msg[i] & 0xff;
		msg[msg.length-2] = (byte) (crc >>> 8);
		msg[msg.length-1] = (byte) crc;
		return msg;
	}
}
