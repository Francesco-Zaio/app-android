package com.ti.app.telemed.core.btdevices;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Message;
import android.os.Handler;
import android.util.Log;

import com.creative.base.InputStreamReader;
import com.creative.base.OutputStreamSender;
import com.creative.SpotCheck.SpotSendCMDThread;
import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.btmodule.events.BTSearcherEvent;
import com.ti.app.telemed.core.btmodule.events.BTSearcherEventListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.util.GWConst;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

import com.creative.bluetooth.IBluetoothCallBack;
import com.creative.SpotCheck.ISpotCheckCallBack;
import com.creative.bluetooth.BluetoothOpertion;
import com.creative.SpotCheck.SpotCheck;
import com.creative.base.BaseDate.ECGData;
import com.creative.base.BaseDate.Wave;
import com.creative.base.Ireader;
import com.creative.base.Isender;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

public class GIMAPC300SpotCheck
        extends Handler
        implements DeviceHandler, IBluetoothCallBack, ISpotCheckCallBack {

	private static final String TAG = "GIMAPC300SpotCheck";

    // Handler messages
    private static final int MSG_DEVICE_DISCOVERED = 0x10;
    private static final int MSG_DEVICE_SELECTED = 0x11;
    private static final int MSG_DEVICE_CONNECTED = 0x12;
    private static final int MSG_DEVICE_ERROR = 0x13;
    private static final int MSG_DISCOVERY_FINISH = 0x14;
    private static final int MSG_DATA_BATTERY = 0x20;
    private static final int MSG_DATA_TEMP = 0x21;
    private static final int MSG_DATA_DISCON = 0x22;
    private static final int MSG_DATA_OXY = 0x23;

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
    private SpotCheck spotCheck = null;

    // list of dicovered BT devices
    private Vector<BluetoothDevice> deviceList;

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

    public GIMAPC300SpotCheck(DeviceListener aScheduler) {
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


    // ISpotCheckCallBack methods

    @Override
    public void OnConnectLose() {
        Log.d(TAG, "OnConnectLose");
        sendEmptyMessage(MSG_DATA_DISCON);
    }

    @Override
    public void OnGetDeviceID(final String sDeviceID) {
        Log.d(TAG, "OnGetDeviceID: sDeviceID="+sDeviceID);
    }

    @Override
    public void OnGetDeviceVer(int nHWMajor, int nHWMinor, int nSWMajor, int nSWMinor, int nPower, int nBattery) {
        Log.d(TAG, "OnGetDeviceVer: nHWMajor="+nHWMajor);
        obtainMessage(MSG_DATA_BATTERY, nPower, nBattery).sendToTarget();
    }

    @Override
    public void OnGetECGAction(int status) {
        Log.d(TAG, "OnGetECGAction->"+status);
        if(status==1) {
            // misura avviata
            // TODO
        } else if (status == 2) {
            // misura interrotta
            sendEmptyMessage(MSG_DATA_DISCON);
            // TODO
        }
    }

    @Override
    public void OnGetECGRealTime(ECGData waveData, int nHR, boolean bLeadoff, int nMax) {
        int flag = 0;
        for (int i=0; i<waveData.data.size();i++)
            if (waveData.data.get(i).flag!=0) {
                flag=waveData.data.get(i).flag;
                break;
            }

        Log.d(TAG, "OnGetECGRealTime: nHR="+nHR+" nMax="+nMax+" OK="+(bLeadoff?"true":"false")+"frame# "+waveData.frameNum+" flag="+flag+" L="+waveData.data.size());
    }

    @Override
    public void onGetECGGain(int arg0, int arg1) {
        Log.d(TAG, "onGetECGGain: arg0="+arg0+" arg1="+arg1);
    }

    @Override
    public void OnGetECGResult(int nResult, int nHR) {
        Log.d(TAG, "OnGetECGResult: nResult="+nResult+" nHR="+nHR);
        sendEmptyMessage(MSG_DATA_DISCON);
    }

    @Override
    public void OnGetECGVer(int nHWMajor, int nHWMinor, int nSWMajor, int nSWMinor) {
        Log.d(TAG, "OnGetECGVer");
    }

    @Override
    public void OnGetGlu(float nGlu, int nGluStatus, int unit) {
        Log.d(TAG, "OnGetGlu");
    }

    @Override
    public void OnGetGluStatus(int nStatus, int nHWMajor, int nHWMinor, int nSWMajor, int nSWMinor) {
        Log.d(TAG, "OnGetGluStatus");
    }

    @Override
    public void OnGetNIBPAction(int bStart) {
        Log.d(TAG, "OnGetNIBPAction");
    }

    @Override
    public void OnGetNIBPRealTime(boolean bHeartbeat, int nBldPrs) {
        Log.d(TAG, "OnGetNIBPRealTime");
    }

    @Override
    public void OnGetNIBPResult(boolean bHR, int nPulse, int nMAP, int nSYS, int nDIA, int nGrade, int nBPErr) {
        Log.d(TAG, "OnGetNIBPResult");
    }

    @Override
    public void OnGetNIBPStatus(int nStatus, int nHWMajor, int nHWMinor, int nSWMajor, int nSWMinor) {
        Log.d(TAG, "OnGetNIBPStatus");
    }

    @Override
    public void OnGetPowerOff() { //bluetooth disconnect , call it first
        Log.d(TAG, "OnGetPowerOff");
        sendEmptyMessage(MSG_DATA_DISCON);
    }

    @Override
    public void OnGetSpO2Param(int nSpO2, int nPR, float nPI, boolean bProbe, int nMode) {
        Log.d(TAG, "OnGetSpO2Param");
        Message msg = obtainMessage(MSG_DATA_OXY);
        Bundle data = new Bundle();
        data.putInt("nSpO2", nSpO2);
        data.putInt("nPR", nPR);
        data.putBoolean("bProbe", bProbe);
        data.putInt("nMode", nMode);
        msg.setData(data);
        sendMessage(msg);
    }

    @Override
    public void OnGetSpO2Status(int nStatus, int nHWMajor, int nHWMinor, int nSWMajor, int nSWMinor) {
        Log.d(TAG, "OnGetSpO2Status");
    }

    @Override
    public void OnGetSpO2Wave(List<Wave> waveData) {
    }

    @Override
    public void OnGetTmp(boolean bManualStart, boolean bProbeOff, float nTmp, int nTmpStatus, int nResultStatus) {
        Log.d(TAG, "OnGetTmp");
        Message msg = obtainMessage(MSG_DATA_TEMP);
        Bundle data = new Bundle();
        data.putBoolean("bManualStart", bManualStart);
        data.putBoolean("bProbeOff", bProbeOff);
        data.putFloat("nTmp", nTmp);
        data.putInt("nTmpStatus", nTmpStatus);
        data.putInt("nResultStatus", nResultStatus);
        msg.setData(data);
        sendMessage(msg);
    }

    @Override
    public void OnGetTmpStatus(int nStatus, int nHWMajor, int nHWMinor, int nSWMajor, int nSWMinor) {
        Log.d(TAG, "OnGetTmpStatus");
    }

    @Override
    public void OnGetNIBPMode(int arg0) {
        Log.d(TAG, "OnGetNIBPMode");
    }

    @Override
    public void OnGetSpO2Action(int action) {
        Log.d(TAG, "OnGetSpO2Action");
    }

    @Override
    public void NIBP_StartStaticAdjusting() {
        Log.d(TAG, "NIBP_StartStaticAdjusting");
    }

    @Override
    public void OnGetGLUAction(int status) {
        Log.d(TAG, "OnGetGLUAction");
    }

    @Override
    public void OnGetTMPAction(int status) {
        Log.d(TAG, "OnGetTMPAction");
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
        spotCheck = null;
    }

    public void stop() {
        Log.d(TAG, "stop");
        iState = TState.Disconnecting;
        if (spotCheck != null)
            spotCheck.Stop();

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
            case MSG_DATA_TEMP:
                d = msg.getData();
                if (d.getInt("nResultStatus") == 0) {
                    iBodyTemperature = String.format(Locale.ITALY, "%.2f", msg.getData().getFloat("nTmp"));
                    makeTemperatureResultData();
                } else {
                    // measure out of range
                    deviceListener.notifyError(DeviceListener.MEASUREMENT_ERROR, ResourceManager.getResource().getString("KWrongMeasure"));
                    stop();
                }
                break;
            case MSG_DATA_OXY:
                if (firstRead) {
                    deviceListener.notifyToUi(ResourceManager.getResource().getString("KMeasuring"));
                    firstRead = false;
                }
                d = msg.getData();
                oxySample(d);
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
         }
    }

    private void startDevice() {
        iState = TState.Connected;
        try {
            Ireader reader = new InputStreamReader(devSocket.getInputStream());
            Isender sender = new OutputStreamSender(devSocket.getOutputStream());
            spotCheck = new SpotCheck(reader, sender, this);
            spotCheck.Start();
            spotCheck.QueryDeviceVer();
        } catch (IOException e) {
            deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR,ResourceManager.getResource().getString("ECommunicationError"));
            stop();
        }
        if (GWConst.KMsrOss.equalsIgnoreCase(iUserDevice.getMeasure()))
            initOxyData();
        else if (GWConst.KMsrEcg.equalsIgnoreCase(iUserDevice.getMeasure()))
            SpotSendCMDThread.Send12BitECG();
        switch (iUserDevice.getMeasure()) {
            case GWConst.KMsrTemp:
                deviceListener.notifyToUi(ResourceManager.getResource().getString("DoMeasureTC"));
                break;
            case GWConst.KMsrOss:
                deviceListener.notifyToUi(ResourceManager.getResource().getString("DoMeasureOS"));
                break;
            case GWConst.KMsrEcg:
                deviceListener.notifyToUi(ResourceManager.getResource().getString("DoMeasureECG"));
                break;
        }
    }


    // Temperature Measure metohds

    private void makeTemperatureResultData() {
        if (!GWConst.KMsrTemp.equals(iUserDevice.getMeasure())) {
            deviceListener.notifyError(DeviceListener.MEASURE_PROCEDURE_ERROR,ResourceManager.getResource().getString("KWrongMeasure"));
        } else {
            HashMap<String, String> tmpVal = new HashMap<>();
            tmpVal.put(GWConst.EGwCode_0R, iBodyTemperature);
            tmpVal.put(GWConst.EGwCode_BATTERY, Integer.toString(iBattery)); // livello batteria

            Measure m = new Measure();
            User u = UserManager.getUserManager().getCurrentUser();
            m.setMeasureType(iUserDevice.getMeasure());
            m.setDeviceDesc(iUserDevice.getDevice().getDescription());
            m.setTimestamp(XmlManager.getXmlManager().getTimestamp(null));
            m.setFile(null);
            m.setFileType(null);
            if (u != null) {
                m.setIdUser(u.getId());
                if (u.getIsPatient())
                    m.setIdPatient(u.getId());
            }
            m.setMeasures(tmpVal);
            m.setFailed(false);
            m.setBtAddress(iBtDevAddr);

            deviceListener.showMeasurementResults(m);
        }
        stop();
    }


    // Oximetry Measure metohds and data

    private static final int NUMBER_OF_MEASUREMENTS = 300; // = 5 min, 1 measure every sec
    private static final int MIN_SAMPLES = 6;
    private static final int BASE_OXY_STREAM_LENGTH = 189; //

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
    private byte[] oxyStream;
    private boolean firstRead = true;

    private void initOxyData () {
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

        oxyQueue = new Vector<>(NUMBER_OF_MEASUREMENTS);
        ByteBuffer tmpStream = ByteBuffer.allocate(BASE_OXY_STREAM_LENGTH);
        tmpStream.put(66, (byte)0x00); //STEP_OXY MSB tempo di campionamento in 1/10 secondo
        tmpStream.put(67, (byte)0x0A); //STEP_OXY LSB
        tmpStream.put(68, (byte)0x00); //FL_TEST
        tmpStream.put(69, (byte)0x20);
        oxyStream = tmpStream.array();
    }

    private void oxySample(Bundle b) {
        int aSpO2 = b.getInt("nSpO2");
        int aHR = b.getInt("nPR");
        boolean fingerIn = b.getBoolean("bProbe");

        // aSpO2 or aHR equals to 0 means invalid values
        if ((aSpO2 != 0) && (aHR != 0)) {
            if (aSpO2 < iSpO2Min)
                iSpO2Min = aSpO2;
            if (aSpO2 > iSpO2Max)
                iSpO2Max = aSpO2;
            if (aHR < iHRMin)
                iHRMin = aHR;
            if (aHR > iHRMax)
                iHRMax = aHR;
            if (aSpO2 < 89) {
                iEventSpO289Count++;
                if (iEventSpO289Count == 20)
                    iEventSpO289++;
            } else {
                iEventSpO289Count = 0;
            }
            if (aHR < 40)
                iEventBradi++;
            if (aHR > 120)
                iEventTachi++;
            if (aSpO2 < 90)
                iT90++;
            if (aSpO2 < 89)
                iT89++;
            if (aSpO2 < 88)
                iT88++;
            if (aSpO2 < 87)
                iT87++;
            if (aHR < 40)
                iT40++;
            if (aHR > 120)
                iT120++;
            oxyQueue.add(new OxyElem(aSpO2, aHR));
        }

        if (!fingerIn) {
            makeOxyResultData();
        }
    }

    private void makeOxyResultData() {
        int hrTot=0, spO2Tot=0, sampleCount;
        OxyElem elem;

        sampleCount = oxyQueue.size();
        if (sampleCount < MIN_SAMPLES) {
            deviceListener.notifyError(DeviceListener.MEASURE_PROCEDURE_ERROR,ResourceManager.getResource().getString("KMinMeasuresMsg"));
            stop();
            return;
        }

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
        int iAnalysisTime = sampleCount; // tempo in secondi
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
        tmpVal.put(GWConst.EGwCode_BATTERY, Integer.toString(iBattery)); // livello batteria


        Measure m = new Measure();
        User u = UserManager.getUserManager().getCurrentUser();
        m.setMeasureType(iUserDevice.getMeasure());
        m.setDeviceDesc(iUserDevice.getDevice().getDescription());
        m.setTimestamp(XmlManager.getXmlManager().getTimestamp(null));
        m.setFile(oxyStream);
        m.setFileType(XmlManager.MIR_OXY_FILE_TYPE);
        if (u != null) {
            m.setIdUser(u.getId());
            if (u.getIsPatient())
                m.setIdPatient(u.getId());
        }
        m.setMeasures(tmpVal);
        m.setFailed(false);
        m.setBtAddress(iBtDevAddr);

        deviceListener.showMeasurementResults(m);
        stop();
    }

    private Time calcTime(int aSec) {
        int hh = aSec/3600;
        int mm = (aSec%3600)/60;
        int ss = (aSec%3600)%60;
        return new Time(hh, mm, ss);
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
}

