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
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.util.GWConst;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Vector;

import com.contec.cms50dj_jar.DeviceCommand;
import com.contec.cms50dj_jar.DevicePackManager;

public class CMS50D extends DeviceHandler implements BTSearcherEventListener {

    private static final String TAG = "CMS50D";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

	private BTSearcher iServiceSearcher;
	private DeviceCommunicationThread commThread = null;
    private DevicePackManager mDevicePackManager = new DevicePackManager();
    private Vector<BluetoothDevice> deviceList = new Vector<>();

	public CMS50D(DeviceListener listener, UserDevice ud) {
        super(listener, ud);
        iServiceSearcher = new BTSearcher();
	}

	// abstract methods of DeviceHandler superclass

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
        Log.d(TAG, "startOperation");
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
        iServiceSearcher.stopSearchDevices();
        iBtDevAddr = bd.getAddress();

        if (commThread != null) {
            commThread.cancel();
            commThread = null;
        }
        commThread = new DeviceCommunicationThread(bd);
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

    private void reset() {
        iState = TState.EWaitingToGetDevice;
    }

    private void stop() {
        iServiceSearcher.stopSearchDevices();
        iServiceSearcher.clearBTSearcherEventListener();
        iServiceSearcher.close();
        if (commThread != null) {
            commThread.cancel();
            commThread = null;
        }
        reset();
    }

    private void delay() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void manageMessage(byte[] buffer, int count, OutputStream os) throws IOException {
        int messageId = mDevicePackManager.arrangeMessage(buffer, count);
        switch (messageId) {
            case 1:// Confirm command success
                delay();
                Log.d(TAG, "Sending time sync command ...");
                os.write(DeviceCommand.correctionDateTime());
                break;
            case 2: // Time sync success
                if (operationType == OperationType.Pair) {
                    Log.d(TAG, "Pairing done");
                    deviceListener.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
                    stop();
                    break;
                } else {
                    deviceListener.notifyToUi(ResourceManager.getResource().getString("KMeasuring"));
                    delay();
                    Log.d(TAG, "Requesting data ...");
                    os.write(DeviceCommand.getDataFromDevice());
                }
                break;
            case 3: // Time sync failed
                Log.e(TAG, "Time sync failed");
                deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR,
                        ResourceManager.getResource().getString("ECommunicationError"));
                stop();
                break;
            case 4:
                Log.d(TAG, "No new measures found");
                deviceListener.notifyError(DeviceListener.NO_MEASURES_FOUND,
                        ResourceManager.getResource().getString("ENoMeasuresFound"));
                stop();
                break;
            case 5:
                Log.d(TAG, "Measure data receive completed");
                delay();
                makeResultData(mDevicePackManager.getDeviceData50dj().getmSp02DataList());
                break;
            case 6:
                Log.d(TAG, "Measure record received");
                delay();
                os.write(DeviceCommand.dataUploadSuccessCommand());
                break;
            case 7:
                Log.e(TAG, "Time sync failed");
                deviceListener.notifyError(DeviceListener.DEVICE_DATA_ERROR,
                        ResourceManager.getResource().getString("EDataReadError"));
                stop();
                break;
        }
    }

    private class DeviceCommunicationThread extends Thread {
        private final BluetoothSocket mmSocket;
        private boolean stop;

        DeviceCommunicationThread(BluetoothDevice device) {
            BluetoothSocket tmp = null;
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.d(TAG,e.toString());
            }
            mmSocket = tmp;
        }

        public void run() {
            if (mmSocket == null) {
                deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
                CMS50D.this.stop();
                return;
            }
            // Make a connection to the BluetoothSocket
            try {
                // Blocking call. Will only return on a successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                // check if the operation was canceled by the user
                if (!stop) {
                    Log.e(TAG, "DeviceCommunicationThread connecton error:", e);
                    // Close the socket
                    try {
                        mmSocket.close();
                    } catch (IOException e2) {
                        Log.e(TAG, "unable to close() socket during connection failure", e2);
                    }
                    deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
                    CMS50D.this.stop();
                } else {
                    Log.d(TAG, "Connection aborted by the User");
                }
                return;
            }

            InputStream mmInStream;
            OutputStream mmOutStream;
            try {
                mmInStream = mmSocket.getInputStream();
                mmOutStream = mmSocket.getOutputStream();
                Log.d(TAG, "send device confirm command");
                mmOutStream.write(DeviceCommand.deviceConfirmCommand());
            } catch (IOException e) {
                if (!stop) {
                    Log.e(TAG, "temp sockets not created", e);
                    deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
                    CMS50D.this.stop();
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
                    // // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    manageMessage(buffer, bytes, mmOutStream);
                } catch (Exception e) {
                    if (!stop) {
                        Log.e(TAG, "disconnected", e);
                        deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
                        CMS50D.this.stop();
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
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }


	private void makeResultData(List<byte[]> dataList) {
        Log.d(TAG, "makeResultData");
        ArrayList<Measure> measureList = new ArrayList<>();

        for (byte[] spo2Data: dataList) {
            // we make the timestamp
            int year, month, day, hour, minute, second, spO2, hr;

            year = spo2Data[0] + 2000;
            month = spo2Data[1];
            day = spo2Data[2];
            hour = spo2Data[3];
            minute = spo2Data[4];
            second = spo2Data[5];
            spO2 = spo2Data[6];
            hr = spo2Data[7];

            String timestamp = String.format(Locale.ITALY,"%04d", year) +
                    String.format(Locale.ITALY,"%02d", month) +
                    String.format(Locale.ITALY,"%02d", day) +
                    String.format(Locale.ITALY,"%02d", hour) +
                    String.format(Locale.ITALY,"%02d", minute) +
                    String.format(Locale.ITALY,"%02d", second);
            Log.d(TAG, "Date:"+timestamp+" O2:"+spO2+" HR:"+hr);

            // Creo un istanza di Misura del tipo PR
            HashMap<String, String> tmpVal = new HashMap<>();
            tmpVal.put(GWConst.EGwCode_07, String.valueOf(spO2));  // O2 Med
            tmpVal.put(GWConst.EGwCode_0F, String.valueOf(hr));  // HR Med
            Measure m = getMeasure();
            m.setTimestamp(timestamp);
            m.setMeasures(tmpVal);
            measureList.add(m);
        }
        if (measureList.isEmpty())
            deviceListener.notifyError("", ResourceManager.getResource().getString("KNoNewMeasure"));
        else
            deviceListener.showMeasurementResults(measureList);
        stop();
	}
}
