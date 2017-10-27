package com.ti.app.telemed.core.btdevices;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;


import com.creative.base.BaseDate.ECGData;
import com.creative.base.InputStreamReader;
import com.creative.base.Ireader;
import com.creative.base.Isender;
import com.creative.base.OutputStreamSender;
import com.creative.bluetooth.BluetoothOpertion;
import com.creative.bluetooth.IBluetoothCallBack;
import com.creative.ecg.ECG;
import com.creative.ecg.IECGCallBack;
import com.creative.filemanage.FileOperation;
import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.btmodule.events.BTSearcherEvent;
import com.ti.app.telemed.core.btmodule.events.BTSearcherEventListener;
import com.ti.app.telemed.core.common.ECGDrawData;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.util.Util;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

public class GIMAPC80B
        extends Handler
        implements DeviceHandler, IBluetoothCallBack, IECGCallBack {

	private static final String TAG = "GIMAPC80B";

    // Handler messages
    private static final int MSG_DEVICE_DISCOVERED = 0x10;
    private static final int MSG_DEVICE_SELECTED = 0x11;
    private static final int MSG_DEVICE_CONNECTED = 0x12;
    private static final int MSG_DEVICE_ERROR = 0x13;
    private static final int MSG_DISCOVERY_FINISH = 0x14;
    private static final int MSG_REAL_TIME_RESULT = 0x15;
    private static final int MSG_REAL_TIME_MEASURE = 0x16;
    private static final int MSG_REAL_TIME_PREPARE = 0x17;
    private static final int MSG_ON_GET_REQUEST = 0x18;
    private static final int MSG_ON_GET_FILE_TRANSMIT = 0x19;
    private static final int MSG_ON_RECEIVE_TIMEOUT = 0x20;
    private static final int MSG_DATA_BATTERY = 0x21;
    private static final int MSG_DATA_DISCON = 0x22;


    private static final int[] battValues = {10,40,70,100};

    private enum TState {
        WaitingToGetDevice, // default
        GettingDevice,      // discovery in corso
        Connecting,         // chiamata a connectDevice()
        Connected,           // callabck connessione avvenuta OK o fine Misura
        Disconnecting
    }

	// State of the active object
    private TState iState;
    // Type of search the scheduler requires
    private TCmd iCmdCode;

    private BTSearcherEventListener scanActivityListener;

    // BT address of the connected device or the device to connect to
    private String iBtDevAddr;
    private UserDevice iUserDevice;

    private BluetoothDevice selectedDevice;
    private BluetoothOpertion  bluetoothOper = null;
    private BluetoothSocket devSocket = null;
    private  ECG ecg = null;

    // list of dicovered BT devices
    private Vector<BluetoothDevice> deviceList;

    private String fileName;
    private String filePath;
    private int HWMajor, HWMinor, SWMajor, SWMinor, ALMajor, ALMinor;

	// Dati relativi alle misure
	private String iBodyTemperature;
    private int iBattery = 0;

    private DeviceListener deviceListener;

    // Indicates that the current request is not a measure but a connection request
    private boolean iPairingMode = false;

    public static boolean needPairing(UserDevice userDevice) {
        return true;
    }

    public static boolean needConfig(UserDevice userDevice) {
        return false;
    }

    public GIMAPC80B(DeviceListener aScheduler) {
        deviceList = new Vector<>();
        bluetoothOper = new BluetoothOpertion(MyApp.getContext(),this);
        iState = TState.WaitingToGetDevice;
        deviceListener = aScheduler;
    }


    // IBluetoothCallBack methods

    @Override
    public void onFindDevice(BluetoothDevice device) {
        Log.d(TAG, "onFindDevice");
        switch (iCmdCode) {
            case ECmdConnByUser:
                if (scanActivityListener != null) {
                    if (device != null) {
                        deviceList.add(device);
                    }
                    sendEmptyMessage(MSG_DEVICE_DISCOVERED);
                }
                break;
            case ECmdConnByAddr:
                if (device.getAddress().equals(iBtDevAddr))  {
                    selectedDevice = device;
                    sendEmptyMessage(MSG_DEVICE_SELECTED);
                }
                break;
        }
    }

    @Override
    public void onDiscoveryCompleted(List<BluetoothDevice> var1) {
        Log.d(TAG, "onDiscoveryCompleted");
        sendEmptyMessage(MSG_DISCOVERY_FINISH);
    }

    @Override
    public void onConnected(BluetoothSocket socket) {
        Log.d(TAG, "onConnected");
        devSocket = socket;
        sendEmptyMessage(MSG_DEVICE_CONNECTED);
    }

    @Override
    public void onConnectFail(String var1){
        Log.d(TAG, "onConnectFail: " + var1);
        sendEmptyMessage(MSG_DEVICE_ERROR);
    }

    @Override
    public void onException(int var1){
        Log.d(TAG, "onException: " + var1);
        sendEmptyMessage(MSG_DEVICE_ERROR);
    }

    @Override
    public void onConnectLocalDevice(BluetoothSocket var1){
        Log.d(TAG, "onConnectLocalDevice");
    }




    // IECGCallBack methods

    @Override
    public void OnConnectLose() {
        Log.d(TAG, "OnConnectLose");
        sendEmptyMessage(MSG_DATA_DISCON);
    }

    @Override
    public void OnGetDeviceVer(int nHWMajor, int nHWMinor, int nSWMajor,
                               int nSWMinor, int nALMajor, int nALMinor) {
        HWMajor = nHWMajor;
        HWMinor = nHWMinor;
        SWMajor = nSWMajor;
        SWMinor = nSWMinor;
        ALMajor = nALMajor;
        ALMinor = nALMinor;
    }

    // Il terminale risponde con una risposta: inviare messaggi di battito cardiaco, informazioni di potenza ogni 1 secondo.
    // terminal responsed, send powerInfo per-second
    @Override
    public void OnGetPower(int nPower) {
        obtainMessage(MSG_DATA_BATTERY, nPower, 0).sendToTarget();
    }

    @Override
    public void OnGetRealTimeResult(String time, int nTransMode,
                                    int nResult, int arg3) {
        Log.d(TAG, "OnGetRealTimeResult: time="+time+" nTransMode="+nTransMode+" nResult="+nResult+" arg3="+arg3);

        //obtainMessage(MSG_DATA_ECG_STATUS_CH,6);
        Message msg = obtainMessage(MSG_REAL_TIME_RESULT);
        Bundle data = new Bundle();
        data.putInt("nTransMode", nTransMode);
        data.putInt("nResult", nResult);
        data.putInt("nHR", arg3);
        data.putString("time", time);
        msg.setData(data);
        sendMessage(msg);
    }

    @Override
    public void OnGetRealTimeMeasure(boolean arg0, ECGData arg1, int arg2,
                                     int arg3, int arg4, int arg5) {
        ArrayList<int[]> l = new ArrayList<>();
        for (int i=0; i<arg1.data.size();i++) {
            int[] v = new int[]{arg1.data.get(i).data};
            l.add(v);
        }
        // Update drawing data drawing
        ECGDrawData.addData(l);
        ECGDrawData.gain=arg5;

        Log.d(TAG, "OnGetECGRealTime: nHR="+arg3+" nTransMode="+arg2+" bLeadoff="+(arg0?"true":"false")+" frame# "+arg1.frameNum+" L="+arg1.data.size()+" nPower="+arg4+" nGain="+arg5);

        //obtainMessage(MSG_DATA_ECG_STATUS_CH,5);
        /*
        Message msg = obtainMessage(MSG_REAL_TIME_MEASURE);
        Bundle data = new Bundle();
        data.putBoolean("bLeadoff", arg0);
        data.putInt("nTransMode", arg2);
        data.putInt("nHR", arg3);
        data.putInt("nPower", arg4);
        data.putInt("nGain", arg5);
        msg.setData(data);
        sendMessage(msg);
        */
    }

    @Override
    public void OnGetRealTimePrepare(boolean arg0, ECGData arg1, int arg2) {

        ArrayList<int[]> l = new ArrayList<>();
        for (int i=0; i<arg1.data.size();i++) {
            int[] v = new int[]{arg1.data.get(i).data};
            l.add(v);
        }
        // Update drawing data drawing
        ECGDrawData.addData(l);
        ECGDrawData.gain=arg2;

        Log.d(TAG, "OnGetRealTimePrepare: bLeadoff="+(arg0?"true":"false")+"frame# "+arg1.frameNum+" L="+arg1.data.size()+" nGain="+arg2);

        //obtainMessage(MSG_DATA_ECG_STATUS_CH,4);
        /*
        Message msg = obtainMessage(MSG_REAL_TIME_PREPARE);
        msg.arg1 = 4;
        Bundle data = new Bundle();
        data.putBoolean("bLeadoff", arg0);
        data.putInt("nGain", arg2);
        msg.setData(data);
        sendMessage(msg);
        */
    }

    @Override
    public void OnGetRequest(String sDeviceID, String sProductID,
                             int nSmoothingMode, int nTransMode) {
        Log.d(TAG, "OnGetRequest: sDeviceID="+sDeviceID+" sProductID="+sProductID+" nSmoothingMode="+nSmoothingMode+" nTransMode="+nTransMode);
        obtainMessage(MSG_ON_GET_REQUEST,nSmoothingMode,nTransMode).sendToTarget();
        // obtainMessage(MSG_DATA_ECG_STATUS_CH, 7, nSmoothingMode,nTransMode).sendToTarget();
    }

    @Override
    public void OnGetFileTransmit(int arg0, Vector<Integer> arg1) {
        Log.d(TAG, "OnGetFileTransmit: arg0="+arg0+" L="+ ((arg1!=null) ? arg1.size():"null"));
        if (arg0 == 1)
            obtainMessage(MSG_ON_GET_FILE_TRANSMIT,arg0,0,arg1).sendToTarget();
        else
            obtainMessage(MSG_ON_GET_FILE_TRANSMIT,arg0,0).sendToTarget();
        //mHandler.obtainMessage(MSG_DATA_ECG_STATUS_CH, arg0, 0, arg1).sendToTarget();
    }

    @Override
    public void OnReceiveTimeOut(int code) {
        Log.d(TAG, "OnReceiveTimeOut: code="+code);
        obtainMessage(MSG_ON_RECEIVE_TIMEOUT,code).sendToTarget();
        //obtainMessage(MSG_DATA_ECG_STATUS_CH, MSG_DATA_TIMEOUT (0x210), 0, 0).sendToTarget();
    }


    // DeviceHandler Methods

    @Override
    public void confirmDialog() {
    }

    @Override
    public void cancelDialog(){
    }

    @Override
    public void start(OperationType ot, UserDevice ud, BTSearcherEventListener scanActivityListener) {
        if (iState == TState.WaitingToGetDevice) {
            iPairingMode = ot == OperationType.Pair;
            iUserDevice = ud;
            this.scanActivityListener = scanActivityListener;
            iBtDevAddr = iUserDevice.getBtAddress();
            if (iBtDevAddr != null && !iBtDevAddr.isEmpty()) {
                iCmdCode = TCmd.ECmdConnByAddr;
            } else {
                iCmdCode = TCmd.ECmdConnByUser;
            }
            iState = TState.GettingDevice;

            filePath = Util.getMeasuresDir().getAbsolutePath() + "/";
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
            Date curDate = new Date(System.currentTimeMillis());
            fileName = "PC80B-" + formatter.format(curDate);

            bluetoothOper.discovery();
        }
    }

    @Override
    public void stopDeviceOperation(int selected) {
    	if (selected < 0) {
            stop();
		} else {
            selectedDevice = deviceList.elementAt(selected);
            iBtDevAddr = selectedDevice.getAddress();
            sendEmptyMessage(MSG_DEVICE_SELECTED);
		}
    }


    public void reset() {
        iState = TState.WaitingToGetDevice;

        deviceList.clear();
        selectedDevice = null;
        devSocket = null;
        iBtDevAddr = null;
        ecg = null;
    }

    public void stop() {
        Log.d(TAG, "stop");
        iState = TState.Disconnecting;
        if (ecg != null)
            ecg.Stop();

        if (devSocket != null) {
            if (devSocket.isConnected())
                bluetoothOper.disConnect(devSocket);
        }

        reset();
        deviceListener.operationCompleted();
    }


    // Handler methods

    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);
        Bundle d;
        switch (msg.what) {
            case MSG_DATA_BATTERY:
                if (msg.arg1 >= 0 && msg.arg1 < 4)
                    iBattery = battValues[msg.arg1];
                break;
            case MSG_DATA_DISCON:
                if (iState == TState.Connected) {
                    deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
                    stop();
                }
                break;
            case MSG_DEVICE_ERROR:
                deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
                stop();
                break;
            case MSG_DEVICE_DISCOVERED:
                if (scanActivityListener != null)
                    scanActivityListener.deviceDiscovered(new BTSearcherEvent(this), deviceList);
                break;
            case MSG_DISCOVERY_FINISH:
                if (iState == TState.GettingDevice && iCmdCode == TCmd.ECmdConnByAddr) {
                    // Bluetooth discovery finished without finding the device
                    deviceListener.notifyError(DeviceListener.DEVICE_NOT_FOUND_ERROR, ResourceManager.getResource().getString("EDeviceNotFound"));
                    stop();
                } else if (iCmdCode == TCmd.ECmdConnByUser && scanActivityListener != null)
                    scanActivityListener.deviceSearchCompleted(new BTSearcherEvent(this));
                break;
            case MSG_DEVICE_SELECTED:
                // for some unknown reasons the discovered could notify the same device more times
                if (iState == TState.GettingDevice) {
                    bluetoothOper.connect(selectedDevice);
                    deviceListener.notifyWaitToUi(ResourceManager.getResource().getString("KConnectingDev"));
                    iState = TState.Connecting;
                }
                break;
            case MSG_DEVICE_CONNECTED:
                deviceListener.setBtMAC(iBtDevAddr);
                if (iPairingMode) {
                    deviceListener.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
                    stop();
                } else {
                    startDevice();
                }
                break;
            case MSG_REAL_TIME_RESULT:
                MSG_REAL_TIME_MEASURE:
                MSG_REAL_TIME_PREPARE:
                MSG_ON_GET_REQUEST:
                break;
            case MSG_ON_GET_FILE_TRANSMIT:
                switch (msg.arg1) {
                    case 0:
                        // inizio trasferimento
                        break;
                    case 1: // fine trasferiemento con successo
                        Vector<Integer> v;
                        try {
                            v = (Vector<Integer>) msg.obj;
                        } catch (ClassCastException e) {
                            deviceListener.notifyError(DeviceListener.DEVICE_DATA_ERROR, ResourceManager.getResource().getString("EDataReadError"));
                            stop();
                            break;
                        }
                        makeResultData(v);
                        break;
                    case 3:
                        // errore durante il trasferimento
                        deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EDataReadError"));
                        stop();
                        break;
                }
                break;
         }
    }

    private void startDevice() {
        iState = TState.Connected;
        try {
            Ireader reader = new InputStreamReader(devSocket.getInputStream());
            Isender sender = new OutputStreamSender(devSocket.getOutputStream());
            ecg = new ECG(reader, sender, this);
            ecg.Start();
            ecg.QueryDeviceVer();
        } catch (IOException e) {
            deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR,ResourceManager.getResource().getString("ECommunicationError"));
            stop();
        }
        ECGDrawData.baseline = 2047;
        ECGDrawData.maxVal = 4095;
        ECGDrawData.nLead = 1;
        ECGDrawData.samplingRate = 150;
        ECGDrawData.lables = new String[]{"I"};
        ECGDrawData.setProgress(0);
        deviceListener.startEcgDraw();
    }

    private void makeResultData(Vector<Integer> v) {
        byte[] bytearray = new byte[v.size()];
        for (int i=0; i<v.size(); i++)
            bytearray[i] = (byte)(v.get(i) & 0xff);

        if (!Util.storeFile(filePath + fileName + ".scp", bytearray)) {
            deviceListener.notifyError(DeviceListener.INTERNAL_ERROR,ResourceManager.getResource().getString("EFileError"));
            stop();
            return;
        }

        if (Util.zipFile(filePath + fileName + ".scp", filePath + fileName + ".zip")) {
            try {
                File file = new File(filePath + fileName + ".zip");
                byte[] fileData = new byte[(int) file.length()];
                DataInputStream dis = new DataInputStream(new FileInputStream(file));
                dis.readFully(fileData);
                dis.close();

                Measure m = new Measure();
                User u = UserManager.getUserManager().getCurrentUser();
                if (u != null) {
                    m.setIdUser(u.getId());
                    if (u.getIsPatient())
                        m.setIdPatient(u.getId());
                }
                m.setMeasureType(iUserDevice.getMeasure());
                m.setDeviceDesc(iUserDevice.getDevice().getDescription());
                m.setTimestamp(XmlManager.getXmlManager().getTimestamp(null));
                String ecgFileName = fileName + ".scp";
                HashMap<String, String> tmpVal = new HashMap<>();
                tmpVal.put(GWConst.EGwCode_0G, ecgFileName);  // filename
                tmpVal.put(GWConst.EGwCode_BATTERY, Integer.toString(iBattery)); // livello batteria
                m.setMeasures(tmpVal);
                m.setFile(fileData);
                m.setFileType(XmlManager.ECG_FILE_TYPE);
                m.setFailed(false);
                m.setBtAddress(iBtDevAddr);

                deviceListener.showMeasurementResults(m);
                stop();
            } catch (IOException e) {
                deviceListener.notifyError(DeviceListener.INTERNAL_ERROR,ResourceManager.getResource().getString("EFileError"));
                stop();
            }
        }
    }
}

