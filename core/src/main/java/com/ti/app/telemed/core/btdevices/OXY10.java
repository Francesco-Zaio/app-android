package com.ti.app.telemed.core.btdevices;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;


import com.oxy10.FingerOximeter.FingerOximeter;
import com.oxy10.FingerOximeter.IFingerOximeterCallBack;
import com.oxy10.base.BaseDate.Wave;
import com.oxy10.base.InputStreamReader;
import com.oxy10.base.Ireader;
import com.oxy10.base.Isender;
import com.oxy10.base.OutputStreamSender;
import com.oxy10.bluetooth.BluetoothOpertion;
import com.oxy10.bluetooth.IBluetoothCallBack;
import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.BTSearcher;
import com.ti.app.telemed.core.btmodule.BTSearcherEventListener;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

public class OXY10
        extends DeviceHandler
        implements BTSearcherEventListener,
        IBluetoothCallBack {

	private static final String TAG = "OXY10";

    // Handler messages
    private static final int MSG_DEVICE_SELECTED = 0x11;
    private static final int MSG_DEVICE_CONNECTED = 0x12;
    private static final int MSG_DEVICE_ERROR = 0x13;
    private static final int MSG_DISCONN = 0x22;
    private static final int MSG_OXY = 0x23;

    private static final int[] battValues = {10,40,70,100};

    private BTSearcher iServiceSearcher;
    private BluetoothDevice selectedDevice;
    private BluetoothOpertion  bluetoothOper = null;
    private BluetoothSocket devSocket = null;
    private FingerOximeter mFingerOximeter;

    // list of dicovered BT devices
    private Vector<BluetoothDevice> deviceList;

    private int iBattery = 0;


    public OXY10(DeviceListener listener, UserDevice ud) {
        super(listener, ud);

        deviceList = new Vector<>();
        iServiceSearcher = new BTSearcher();
    }


    // DeviceHandler Methods

    @Override
    public boolean startOperation(OperationType ot, BTSearcherEventListener btSearchListener) {
        if (!startInit(ot, btSearchListener))
            return false;
        Log.d(TAG,"startOperation: iBtDevAddr="+iBtDevAddr + " iCmdCode="+iCmdCode.toString());
        iServiceSearcher.clearBTSearcherEventListener();
        iServiceSearcher.addBTSearcherEventListener(this);
        iServiceSearcher.startSearchDevices();

        Log.d(TAG,"startOperation: iBtDevAddr="+iBtDevAddr + " iCmdCode="+iCmdCode.toString());
        if (bluetoothOper == null)
            bluetoothOper = new BluetoothOpertion(MyApp.getContext(),this);

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
        selectedDevice = bd;
        iBtDevAddr = selectedDevice.getAddress();
        devOpHandler.sendEmptyMessage(MSG_DEVICE_SELECTED);
    }

    @Override
    public void confirmDialog() {
    }

    @Override
    public void cancelDialog(){
    }


    // methods of BTSearcherEventListener interface

    @Override
    public void deviceDiscovered(Vector<BluetoothDevice> devList) {
        Log.d(TAG,"deviceDiscovered: size="+devList.size());
        deviceList = devList;
        if (iCmdCode == TCmd.ECmdConnByAddr) {
            for (int i=0; i<deviceList.size(); i++)
                if (iBtDevAddr.equalsIgnoreCase(deviceList.get(i).getAddress())) {
                    selectDevice(deviceList.get(i));
                }
        } else if (iBTSearchListener != null) {
            iBTSearchListener.deviceDiscovered(deviceList);
        }
    }

    @Override
    public void deviceSearchCompleted() {
        if (iState == TState.EGettingDevice && iCmdCode == TCmd.ECmdConnByAddr) {
            // Bluetooth discovery finished without finding the device
            deviceListener.notifyError(DeviceListener.DEVICE_NOT_FOUND_ERROR, ResourceManager.getResource().getString("EDeviceNotFound"));
            stop();
        } else if (iCmdCode == TCmd.ECmdConnByUser && iBTSearchListener != null)
            iBTSearchListener.deviceSearchCompleted();
    }

    // IBluetoothCallBack methods

    @Override
    public void onFindDevice(BluetoothDevice device) {
        Log.d(TAG, "onFindDevice");
        // Not used
    }

    @Override
    public void onDiscoveryCompleted(List<BluetoothDevice> var1) {
        Log.d(TAG, "onDiscoveryCompleted");
        // Not used
    }

    @Override
    public void onConnected(BluetoothSocket socket) {
            Log.d(TAG, "onConnected");
            Message msg = devOpHandler.obtainMessage(MSG_DEVICE_CONNECTED);
            msg.obj = socket;
            devOpHandler.sendMessage(msg);
    }

    @Override
    public void onConnectFail(String var1){
        Log.d(TAG, "onConnectFail: " + var1);
        devOpHandler.sendEmptyMessage(MSG_DEVICE_ERROR);
    }

    @Override
    public void onException(int var1){
        Log.d(TAG, "onException: " + var1);
        devOpHandler.sendEmptyMessage(MSG_DEVICE_ERROR);
    }

    @Override
    public void onConnectLocalDevice(BluetoothSocket var1){
        Log.d(TAG, "onConnectLocalDevice");
    }

    public void reset() {
        iState = TState.EWaitingToGetDevice;

        deviceList.clear();
        selectedDevice = null;
        devSocket = null;
        iBtDevAddr = null;
    }

    public void stop() {
        Log.d(TAG, "stop");

        if (iState == TState.EGettingDevice) {
            iServiceSearcher.stopSearchDevices();
        }
        iServiceSearcher.stopSearchDevices();
        iServiceSearcher.clearBTSearcherEventListener();
        iServiceSearcher.close();

        iState = TState.EDisconnecting;

        if (mFingerOximeter != null){
            mFingerOximeter.Stop();
            mFingerOximeter = null;
        }

        if (devSocket!=null)
            bluetoothOper.disConnect(devSocket);

        reset();
    }

    private void startDevice() {
        Log.d(TAG,"startDevice");
        if (devSocket == null)
            return;
        iState = TState.EConnected;
        try {
            Ireader reader = new InputStreamReader(devSocket.getInputStream());
            Isender sender = new OutputStreamSender(devSocket.getOutputStream());
            mFingerOximeter = new FingerOximeter(reader, sender, new FingerOximeterCallBack());
            mFingerOximeter.Start();
            mFingerOximeter.SetWaveAction(false);
        } catch (IOException e) {
            deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR,ResourceManager.getResource().getString("ECommunicationError"));
            stop();
        }

        initOxyData();
    }


    private final MyHandler devOpHandler = new MyHandler(this);

    private static class MyHandler extends Handler {
        private final WeakReference<OXY10> mOuter;

        private MyHandler(OXY10 outer) {
            mOuter = new WeakReference<>(outer);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            OXY10 outer = mOuter.get();
            Bundle d;
            switch (msg.what) {
                case MSG_OXY:
                    if (outer.numSamples==0) {
                        outer.deviceListener.notifyToUi(ResourceManager.getResource().getString("KMeasuring"));
                    }
                    outer.numSamples++;
                    d = msg.getData();
                    outer.oxySample(d);
                    break;
                case MSG_DISCONN:
                    if (outer.iState == TState.EConnected) {
                        outer.deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
                        outer.stop();
                    }
                    break;
                case MSG_DEVICE_ERROR:
                    if (outer.iState==TState.EWaitingToGetDevice)
                        break;
                    outer.deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
                    outer.stop();
                    break;
                case MSG_DEVICE_SELECTED:
                    // for some unknown reasons the discovered could notify the same device more times
                    if (outer.iState == TState.EGettingDevice) {
                        outer.bluetoothOper.connect(outer.selectedDevice);
                        outer.deviceListener.notifyToUi(ResourceManager.getResource().getString("KConnectingDev"));
                        outer.iState = TState.EGettingConnection;
                    }
                    break;
                case MSG_DEVICE_CONNECTED:
                    if (outer.iState != TState.EGettingConnection) {
                        // chiamato il metodo stop() durante la richiesta di connessione
                        // occorre chiudere il socket.
                        BluetoothSocket socket = (BluetoothSocket)msg.obj;
                        if(socket != null)
                            try {
                                socket.close();
                            } catch (Exception e) {
                                // ignora eventuali eccezioni
                            }
                        break;
                    }
                    outer.devSocket = (BluetoothSocket)msg.obj;
                    outer.deviceListener.setBtMAC(outer.iBtDevAddr);
                    if (outer.operationType == OperationType.Pair) {
                        outer.deviceListener.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
                        outer.stop();
                    } else {
                        outer.deviceListener.notifyToUi(ResourceManager.getResource().getString("DoMeasureOS"));
                        outer.startDevice();
                    }
                    break;
            }
        }
    }

    class FingerOximeterCallBack implements IFingerOximeterCallBack {

        @Override
        public void OnGetSpO2Param(int nSpO2, int nPR, float fPI, boolean nStatus, int nMode, float nPower,int powerLevel) {
            Message msg = devOpHandler.obtainMessage(MSG_OXY);
            Bundle data = new Bundle();
            data.putInt("nSpO2", nSpO2);
            data.putInt("nPR", nPR);
            data.putFloat("fPI", fPI);
            data.putFloat("nPower", nPower);
            data.putBoolean("nStatus", nStatus);
            data.putInt("nMode", nMode);
            data.putFloat("nPower", nPower);
            data.putInt("powerLevel", powerLevel);
            msg.setData(data);
            devOpHandler.sendMessage(msg);
        }

        // freq. di campionamento 50Hz.
        // 10 msg/sec, ogni messaggio contiene 5 campioni
        @Override
        public void OnGetSpO2Wave(List<Wave> waves) {
            Log.d(TAG, "OnGetSpO2Wave");
            // Non utilizzato
        }

        @Override
        public void OnGetDeviceVer(String hardVer,String softVer,String deviceName) {
            Log.d(TAG, "OnGetDeviceVer: hardVer:"+hardVer+",softVer:"+softVer+",deviceName:"+deviceName);
        }

        @Override
        public void OnConnectLose() {
            Log.d(TAG, "OnConnectLose");
            devOpHandler.sendEmptyMessage(MSG_DISCONN);
        }
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
    private int numSamples = 0;

    private void initOxyData () {
        numSamples = 0;

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
        boolean fingerIn = b.getBoolean("nStatus");
        float mVolt = b.getFloat("nPower");
        int pLevel = b.getInt("powerLevel");
        if(mVolt != 0){
            // 2.5V=0%  3,0v=100%
            iBattery = (int)(200f*mVolt - 500f);
            if (iBattery < 0)
                iBattery = 0;
            else if (iBattery > 100)
                iBattery = 100;
        }else {
            iBattery = battValues[pLevel];
        }
        Log.d(TAG, "OnGetSpO2Param: aSpO2="+aSpO2+ " aHR="+aHR+" fingerIn="+fingerIn+" mVolt="+mVolt+" pLevel="+pLevel+" iBattery="+iBattery);

        //Log.d(TAG, "aSpO2="+aSpO2+ " aHR="+aHR+" fingerIn="+fingerIn+" mVolt="+mVolt+" pLevel="+pLevel);
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

        if (!fingerIn || numSamples >= 10) {
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

        Measure m = getMeasure();
        m.setFile(oxyStream);
        m.setFileType(XmlManager.MIR_OXY_FILE_TYPE);
        m.setMeasures(tmpVal);
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

        Time(int hh, int mm, int ss) {
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

