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


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.UUID;
import java.util.Vector;

public class MIRSpirodocMiniLab extends DeviceHandler implements BTSearcherEventListener {

	private static final String TAG = "MIRSpirodocMiniLab";
	private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

	private static final byte ACK = 0x06;
	private static final byte NACK = 0x15;
	private static final byte END_OBJECT = 0x03;
	private byte[] getCommand = {85,0,2,0,(byte)210,0,0,0,3,2,0,(byte)255,0,0,0,3,1,(byte)219};
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
        stop();
    }

	@Override
	public void selectDevice(BluetoothDevice bd){
		Log.d(TAG, "selectDevice: addr=" + bd.getAddress());

		// the search is finished and we stop it (we remove from event list)
		iServiceSearcher.stopSearchDevices();
		iServiceSearcher.removeBTSearcherEventListener(this);
		iBtDevAddr = bd.getAddress();

		if (commThread != null) {
			commThread.cancel();
			commThread = null;
		}
		commThread = new MIRSpirodocMiniLab.DeviceCommunicationThread(bd);
		commThread.start();
		deviceListener.notifyToUi(ResourceManager.getResource().getString("KConnectingDev"));
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

		if (commThread != null) {
			commThread.cancel();
			commThread = null;
		}

		reset();
	}

	private void manageMessage(byte[] buffer, int count, OutputStream os) throws IOException {
		try {
			if (parser.parseMessage(buffer, count)) {
				if (parser.error) {
					deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EDataReadError"));
					os.write(NACK);
				} else {
					os.write(ACK);
					if (parser.spiroObjs.isEmpty())
						deviceListener.notifyError(DeviceListener.NO_MEASURES_FOUND, ResourceManager.getResource().getString("ENoMeasuresFound"));
					else
						deviceListener.notifyError(DeviceListener.NO_MEASURES_FOUND, ResourceManager.getResource().getString("ENoMeasurementDone"));
				}
				Thread.sleep(1000);
				stop();
			}
			if (parser.error) {
				deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
				stop();
			}
		} catch (InterruptedException ex) {
			Log.d(TAG, ex.toString());
			stop();
		}
	}

	private static class MsgParser {
		byte[] msg = new byte[16384];
		int length = 0;
		int offset = 0;
		byte[] info = null;
		boolean end = false;
		boolean error = false;
		private Vector<byte[]> spiroObjs = new Vector<>();
		//private Vector<byte[]> curvesObjs = new Vector<>();

		public void reset() {
			offset = 0;
			end = false;
			error = false;
			info = null;
			spiroObjs.clear();
			//curvesObjs.clear();
		}

		public boolean parseMessage(byte[] data, int dataLength) {
			Log.d(TAG, "parseMessage: " + dataLength);
			if ((length + dataLength) > 16384) {
				Log.e(TAG,"ERRORE: messaggio troppo lungo");
				error = true;
				return false;
			}
			System.arraycopy(data,0,msg,length,dataLength);
			length += dataLength;

			// Cerco l'inizio del messaggio (0x55, 0x00) e scarto gli eventuali bytes precedenti
			if ((offset == 0) && (length >= 2)) {
				for (int i = 0; i <= length - 2; i++) {
					if ((msg[i] == 0x55) && (msg[i+1] == 0x0)) {
						for (int j=i; (i>0 && j<length); j++) {
							msg[j-i] = msg[j];
							length = length - i;
						}
						Log.d(TAG,"parseMessage: start of message");
						offset = 2;
						break;
					}
				}
			}

			// Se ho ricevuto l'END FILE Object (fine messaggio) leggo i due bytes di CRC e verifico la correttezza
			if (end && (length-offset) >=2) {
				Log.d(TAG,"parseMessage: checkCRC");
				checkCRC();
				return true;
			}

			// Parsing degli oggetti
			while ((length-offset) >= 7) {
				// obj header disponibile, leggo la lunghezza in bytes dell'oggetto
				int objLength = msg[offset+3]*0x10000+msg[offset+4]*0x100+msg[offset+5];
				// verifico se ho ricevutto tutto l'oggetto
				if ((length-offset) >= (6+objLength+1)) {
					Log.d(TAG,"parseMessage: Object received");
					parseObject(objLength);
				} else
					return false;
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
					break;
				case 2:
					// ricevuta una Spirometria
					Log.d(TAG,"parseMessage: SPIRO Object received");
					spiroObjs.add(Arrays.copyOfRange(msg, offset+6, offset+6+objLength+1));
					break;
				case 6:
					Log.d(TAG,"parseMessage: CURVE Object received");
					// ricevuta un Curva di una spirometria
					//curvesObjs.add(Arrays.copyOfRange(msg, offset+6, offset+6+objLength+1));
					break;
				case (byte)0xff:
					Log.d(TAG,"parseMessage: END FILE Object received");
					// ricevuto END FILE Object
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
		private final BluetoothSocket mmSocket;
		private boolean stop;

		DeviceCommunicationThread(BluetoothDevice device) {
			BluetoothSocket tmp = null;
			try {
				tmp = device.createRfcommSocketToServiceRecord(SPP_UUID);
			} catch (IOException e) {
				Log.d(TAG,e.toString());
			}
			mmSocket = tmp;
		}

		public void run() {
			if (mmSocket == null) {
				deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
				MIRSpirodocMiniLab.this.stop();
				return;
			}
			// Make a connection to the BluetoothSocket
			try {
				// Blocking call. Will only return on a successful connection or an exception
				mmSocket.connect();
			} catch (IOException e) {
				// check if the operation was canceled by the user
				if (!stop) {
					Log.e(TAG, "DeviceCommunicationThread connection error:", e);
					try {
						mmSocket.close();
					} catch (IOException e2) {
						Log.e(TAG, "unable to close() socket during connection failure", e2);
					}
					deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
					MIRSpirodocMiniLab.this.stop();
				} else {
					Log.d(TAG, "Connection aborted by the User");
				}
				return;
			}

			if (operationType == OperationType.Pair) {
				deviceListener.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
				MIRSpirodocMiniLab.this.stop();
				return;
			}

			InputStream mmInStream;
			OutputStream mmOutStream;
			try {
				mmInStream = mmSocket.getInputStream();
				mmOutStream = mmSocket.getOutputStream();
				Log.d(TAG, "send device confirm command");
				mmOutStream.write(getCommand);
			} catch (IOException e) {
				if (!stop) {
					Log.e(TAG, "temp sockets not created", e);
					deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
					MIRSpirodocMiniLab.this.stop();
				} else {
					Log.d(TAG, "Operation aborted by the User");
				}
				return;
			}

			byte[] buffer = new byte[1024];
			int bytes;
			//Ricezione messaggi
			while (!stop) {
				try {
					// Read from the InputStream
					bytes = mmInStream.read(buffer);
					manageMessage(buffer, bytes, mmOutStream);
				} catch (Exception e) {
					if (!stop) {
						Log.e(TAG, "disconnected", e);
						deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
						MIRSpirodocMiniLab.this.stop();
					} else {
						Log.d(TAG, "Operation aborted by the User");
					}
					return;
				}
			}
		}

		public synchronized void cancel() {
			try {
				stop = true;
				mmSocket.close();
			} catch (IOException e) {
				Log.e(TAG, "Socket close() failed", e);
			}
		}
	}
}