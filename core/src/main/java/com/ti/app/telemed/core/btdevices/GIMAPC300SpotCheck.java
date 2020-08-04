package com.ti.app.telemed.core.btdevices;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Message;
import android.os.Handler;
import android.util.Log;

import com.creative.SpotCheck.StatusMsg;
import com.creative.base.InputStreamReader;
import com.creative.base.OutputStreamSender;
import com.creative.SpotCheck.SpotSendCMDThread;
import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.BTSearcher;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.btmodule.BTSearcherEventListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.util.GWConst;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
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
        extends DeviceHandler
        implements BTSearcherEventListener,
        IBluetoothCallBack,
        ISpotCheckCallBack {

	private static final String TAG = "GIMAPC300SpotCheck";

    // Handler messages
    private static final int MSG_DEVICE_DISCOVERED = 0x10;
    private static final int MSG_DEVICE_SELECTED = 0x11;
    private static final int MSG_DEVICE_CONNECTED = 0x12;
    private static final int MSG_DEVICE_ERROR = 0x13;
    private static final int MSG_DISCOVERY_FINISH = 0x14;
    private static final int MSG_BATTERY = 0x20;
    private static final int MSG_TEMPERATURE = 0x21;
    private static final int MSG_DISCONN = 0x22;
    private static final int MSG_OXY = 0x23;
    private static final int MSG_BP = 0x24;
    private static final int MSG_MEASURING = 0x25;
    private static final int MSG_MEASURE_CANCELED = 0x26;
    private static final int MSG_GLU = 0x27;

    private static final int[] battValues = {10,40,70,100};

    private BTSearcher iServiceSearcher;
    private BluetoothDevice selectedDevice;
    private BluetoothOpertion  bluetoothOper = null;
    private BluetoothSocket devSocket = null;
    private SpotCheck spotCheck = null;

    // list of dicovered BT devices
    private Vector<BluetoothDevice> deviceList;

	// Dati relativi alle misure
	private String iBodyTemperature;
    private int iBattery = 0;


    public GIMAPC300SpotCheck(DeviceListener listener, UserDevice ud) {
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
        prePrandial = false;
        makeGLUResultData();
    }

    @Override
    public void cancelDialog(){
        prePrandial = true;
        makeGLUResultData();
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
        if (iCmdCode == TCmd.ECmdConnByUser && iBTSearchListener != null)
            iBTSearchListener.deviceSearchCompleted();
    }

    // IBluetoothCallBack methods

    @Override
    public void onFindDevice(BluetoothDevice device) {
        Log.d(TAG, "onFindDevice");
        switch (iCmdCode) {
            case ECmdConnByUser:
                if (iBTSearchListener != null) {
                    if (device != null) {
                        deviceList.add(device);
                    }
                    devOpHandler.sendEmptyMessage(MSG_DEVICE_DISCOVERED);
                }
                break;
            case ECmdConnByAddr:
                if (device.getAddress().equals(iBtDevAddr))  {
                    selectedDevice = device;
                    devOpHandler.sendEmptyMessage(MSG_DEVICE_SELECTED);
                }
                break;
        }
    }

    @Override
    public void onDiscoveryCompleted(List<BluetoothDevice> var1) {
        Log.d(TAG, "onDiscoveryCompleted");
        devOpHandler.sendEmptyMessage(MSG_DISCOVERY_FINISH);
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


    // ISpotCheckCallBack methods

    @Override
    public void OnConnectLose() {
        Log.d(TAG, "OnConnectLose");
        devOpHandler.sendEmptyMessage(MSG_DISCONN);
    }

    @Override
    public void OnGetDeviceID(final String sDeviceID) {
        Log.d(TAG, "OnGetDeviceID: sDeviceID="+sDeviceID);
    }

    @Override
    public void OnGetDeviceVer(int nHWMajor, int nHWMinor, int nSWMajor, int nSWMinor, int nPower, int nBattery) {
        //Log.d(TAG, "OnGetDeviceVer: nHWMajor="+nHWMajor+" nHWMinor="+nHWMinor+" nSWMajor="+nSWMajor+" nSWMinor="+nSWMinor+" nPower="+nPower+" nBattery="+nBattery);
        devOpHandler.obtainMessage(MSG_BATTERY, nPower, nBattery).sendToTarget();
    }

    @Override
    public void OnGetECGAction(int status) {
        Log.d(TAG, "OnGetECGAction->"+status);
        if(status==1) {
            devOpHandler.sendEmptyMessage(MSG_MEASURING);
        } else if (status == 2) {
            devOpHandler.sendEmptyMessage(MSG_MEASURE_CANCELED);
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
        devOpHandler.sendEmptyMessage(MSG_DISCONN);
    }

    @Override
    public void OnGetECGVer(int nHWMajor, int nHWMinor, int nSWMajor, int nSWMinor) {
        Log.d(TAG, "OnGetECGVer");
    }

    @Override
    public void OnGetGlu(float nGlu, int nGluStatus, int unit) {
        Log.d(TAG, "OnGetGlu: nGlu="+nGlu+" nGluStatus="+nGluStatus+" unit="+unit);
        Message msg = devOpHandler.obtainMessage(MSG_GLU);
        Bundle data = new Bundle();
        data.putFloat("nGlu", nGlu);
        data.putInt("nGluStatus", nGluStatus);
        data.putInt("unit", unit);
        msg.setData(data);
        devOpHandler.sendMessage(msg);
    }

    @Override
    public void OnGetGluStatus(int nStatus, int nHWMajor, int nHWMinor, int nSWMajor, int nSWMinor) {
        Log.d(TAG, "OnGetGluStatus: "+" nStatus="+nStatus+" nHWMajor="+nHWMajor+" nHWMinor="+nHWMinor+" nSWMajor="+nSWMajor+" nSWMinor="+nSWMinor);
    }

    @Override
    public void OnGetNIBPAction(int bStart) {
        Log.d(TAG, "OnGetNIBPAction: bStart="+bStart);
        if (bStart == 1)
            devOpHandler.sendEmptyMessage(MSG_MEASURING);
        else if (bStart == 2)
            devOpHandler.sendEmptyMessage(MSG_MEASURE_CANCELED);
    }

    @Override
    public void OnGetNIBPRealTime(boolean bHeartbeat, int nBldPrs) {
        Log.d(TAG, "OnGetNIBPRealTime: bHeartbeat="+(bHeartbeat?"true":"false")+" nBldPrs="+nBldPrs);
    }

    @Override
    public void OnGetNIBPResult(boolean bHR, int nPulse, int nMAP, int nSYS, int nDIA, int nGrade, int nBPErr) {
        Log.d(TAG, "OnGetNIBPResult: bHR="+(bHR?"true":"false")+" nPulse="+nPulse+" nMAP="+nMAP+" nSYS="+nSYS+" nDIA="+nDIA+" nGrade="+nGrade+" nBPErr="+nBPErr);
        Message msg = devOpHandler.obtainMessage(MSG_BP);
        Bundle data = new Bundle();
        data.putBoolean("bHR", bHR);
        data.putInt("nPulse", nPulse);
        data.putInt("nMAP", nMAP);
        data.putInt("nSYS", nSYS);
        data.putInt("nDIA", nDIA);
        data.putInt("nGrade", nGrade);
        data.putInt("nBPErr", nBPErr);
        msg.setData(data);
        devOpHandler.sendMessage(msg);
    }

    @Override
    public void OnGetNIBPStatus(int nStatus, int nHWMajor, int nHWMinor, int nSWMajor, int nSWMinor) {
        Log.d(TAG, "OnGetNIBPStatus: "+" nStatus="+nStatus+" nHWMajor="+nHWMajor+" nHWMinor="+nHWMinor+" nSWMajor="+nSWMajor+" nSWMinor="+nSWMinor);
    }

    @Override
    public void OnGetNIBPMode(int arg0) {
        Log.d(TAG, "OnGetNIBPMode: arg0="+arg0);
    }

    @Override
    public void OnGetPowerOff() { //bluetooth disconnect , call it first
        Log.d(TAG, "OnGetPowerOff");
        devOpHandler.sendEmptyMessage(MSG_DISCONN);
    }

    @Override
    public void OnGetSpO2Param(int nSpO2, int nPR, float nPI, boolean bProbe, int nMode) {
        Log.d(TAG, "OnGetSpO2Param");
        Message msg = devOpHandler.obtainMessage(MSG_OXY);
        Bundle data = new Bundle();
        data.putInt("nSpO2", nSpO2);
        data.putInt("nPR", nPR);
        data.putBoolean("bProbe", bProbe);
        data.putInt("nMode", nMode);
        msg.setData(data);
        devOpHandler.sendMessage(msg);
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
        Message msg = devOpHandler.obtainMessage(MSG_TEMPERATURE);
        Bundle data = new Bundle();
        data.putBoolean("bManualStart", bManualStart);
        data.putBoolean("bProbeOff", bProbeOff);
        data.putFloat("nTmp", nTmp);
        data.putInt("nTmpStatus", nTmpStatus);
        data.putInt("nResultStatus", nResultStatus);
        msg.setData(data);
        devOpHandler.sendMessage(msg);
    }

    @Override
    public void OnGetTmpStatus(int nStatus, int nHWMajor, int nHWMinor, int nSWMajor, int nSWMinor) {
        Log.d(TAG, "OnGetTmpStatus");
    }


    @Override
    public void OnGetSpO2Action(int action) {
        Log.d(TAG, "OnGetSpO2Action: action="+action);
        if (action == 1)
            devOpHandler.sendEmptyMessage(MSG_MEASURING);
        else if (action >= 2)
            devOpHandler.sendEmptyMessage(MSG_MEASURE_CANCELED);
    }

    @Override
    public void NIBP_StartStaticAdjusting() {
        Log.d(TAG, "NIBP_StartStaticAdjusting");
    }

    @Override
    public void OnGetGLUAction(int status) {
        Log.d(TAG, "OnGetGLUAction: status="+status);
        if (status == 1)
            devOpHandler.sendEmptyMessage(MSG_MEASURING);
        else if (status == 2)
            devOpHandler.sendEmptyMessage(MSG_MEASURE_CANCELED);
    }

    @Override
    public void OnGetTMPAction(int status) {
        Log.d(TAG, "OnGetTMPAction");
    }


    public void reset() {
        iState = TState.EWaitingToGetDevice;

        deviceList.clear();
        selectedDevice = null;
        devSocket = null;
        iBtDevAddr = null;
        spotCheck = null;
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
        if (spotCheck != null)
            spotCheck.Stop();

        //if (bluetoothOper!=null)
        //    bluetoothOper.stopDiscovery();

        if (devSocket!=null)
            bluetoothOper.disConnect(devSocket);

        reset();
    }


    private final MyHandler devOpHandler = new MyHandler(this);

    private static class MyHandler extends Handler {
        private final WeakReference<GIMAPC300SpotCheck> mOuter;

        private MyHandler(GIMAPC300SpotCheck outer) {
            mOuter = new WeakReference<>(outer);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            GIMAPC300SpotCheck outer = mOuter.get();
            Bundle d;
            switch (msg.what) {
                case MSG_BATTERY:
                    if (msg.arg1 >= 0 && msg.arg1 < 4)
                        outer.iBattery = battValues[msg.arg1];
                    break;
                case MSG_MEASURING:
                    outer.deviceListener.notifyWaitToUi(ResourceManager.getResource().getString("KMeasuring"));
                    break;
                case MSG_MEASURE_CANCELED:
                    outer.deviceListener.notifyError(DeviceListener.MEASUREMENT_ERROR, ResourceManager.getResource().getString("KAbortOperation"));
                    outer.stop();
                    break;
                case MSG_GLU:
                    d = msg.getData();
                    outer.askGLUMeasure(d);
                    break;
                case MSG_BP:
                    d = msg.getData();
                    outer.makeBPResultData(d);
                    break;
                case MSG_TEMPERATURE:
                    d = msg.getData();
                    if (d.getInt("nResultStatus") == 0) {
                        outer.iBodyTemperature = String.format(Locale.ITALY, "%.2f", msg.getData().getFloat("nTmp"));
                        outer.makeTemperatureResultData();
                    } else {
                        // measure out of range
                        outer.deviceListener.notifyError(DeviceListener.MEASUREMENT_ERROR, ResourceManager.getResource().getString("KWrongMeasure"));
                        outer.stop();
                    }
                    break;
                case MSG_OXY:
                    outer.scheduleMeasureAlarm();
                    if (outer.firstRead) {
                        outer.deviceListener.notifyToUi(ResourceManager.getResource().getString("KMeasuring"));
                        outer.firstRead = false;
                    }
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
                case MSG_DEVICE_DISCOVERED:
                    if (outer.iBTSearchListener != null)
                        outer.iBTSearchListener.deviceDiscovered(outer.deviceList);
                    break;
                case MSG_DISCOVERY_FINISH:
                    if (outer.iState == TState.EGettingDevice && outer.iCmdCode == TCmd.ECmdConnByAddr) {
                        // Bluetooth discovery finished without finding the device
                        outer.deviceListener.notifyError(DeviceListener.DEVICE_NOT_FOUND_ERROR, ResourceManager.getResource().getString("EDeviceNotFound"));
                        outer.stop();
                    } else if (outer.iCmdCode == TCmd.ECmdConnByUser && outer.iBTSearchListener != null)
                        outer.iBTSearchListener.deviceSearchCompleted();
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
                        switch (outer.iUserDevice.getMeasure()) {
                            case GWConst.KMsrGlic:
                                outer.deviceListener.notifyToUi(ResourceManager.getResource().getString("DoMeasureGL"));
                                break;
                            case GWConst.KMsrTemp:
                                outer.deviceListener.notifyToUi(ResourceManager.getResource().getString("DoMeasureTC"));
                                break;
                            case GWConst.KMsrPres:
                                outer.deviceListener.notifyToUi(ResourceManager.getResource().getString("DoMeasurePR"));
                                break;
                            case GWConst.KMsrOss:
                                outer.deviceListener.notifyToUi(ResourceManager.getResource().getString("DoMeasureOS"));
                                break;
                        }
                        outer.startDevice();
                    }
                    break;
            }
        }
    }

    private void startDevice() {
        Log.d(TAG,"startDevice");
        if (devSocket == null)
            return;
        iState = TState.EConnected;
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
            case GWConst.KMsrPres:
                deviceListener.notifyToUi(ResourceManager.getResource().getString("DoMeasurePR"));
                break;
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


    // GLU Measure methods

    private int gluValue;
    private boolean prePrandial;

    private void askGLUMeasure(Bundle b) {
        if (!GWConst.KMsrGlic.equals(iUserDevice.getMeasure())) {
            deviceListener.notifyError(DeviceListener.MEASURE_PROCEDURE_ERROR,ResourceManager.getResource().getString("KWrongMeasure"));
            stop();
            return;
        }

        prePrandial = true;
        int gluResutlStatus = b.getInt("nGluStatus");
        if (gluResutlStatus != 0) {
            deviceListener.notifyError(DeviceListener.MEASUREMENT_ERROR,ResourceManager.getResource().getString("KWrongMeasure"));
            stop();
            return;
        }

        int gluUnit = b.getInt("unit");
        if (gluUnit != 1)
            gluValue = (int)(b.getFloat("nGlu")*18./10.);
        else
            gluValue = (int)(b.getFloat("nGlu"));

        String message;
        message = ResourceManager.getResource().getString("KPrePostMsg").concat("\n\n");
        message = message.concat(ResourceManager.getResource().getString("Glycemia")).concat(": ");
        message = message.concat(Integer.toString(gluValue)).concat(" ");
        message = message.concat(ResourceManager.getResource().getString("GlycemiaUnit"));

        deviceListener.askSomething(message,
                ResourceManager.getResource().getString("MeasureGlyPOSTBtn"),
                ResourceManager.getResource().getString("MeasureGlyPREBtn"));
    }

    private void makeGLUResultData() {
        HashMap<String, String> tmpVal = new HashMap<>();
        if (prePrandial) {
            tmpVal.put(GWConst.EGwCode_0E, Integer.toString(gluValue));  // glicemia Pre-prandiale
        } else {
            tmpVal.put(GWConst.EGwCode_0T, Integer.toString(gluValue));  // glicemia Post-prandiale
        }
        tmpVal.put(GWConst.EGwCode_BATTERY, Integer.toString(iBattery)); // livello batteria
        Measure m = getMeasure();
        m.setMeasures(tmpVal);
        deviceListener.showMeasurementResults(m);
        stop();
    }


    // BP Measure metohds

    private void makeBPResultData(Bundle b) {
        if (!GWConst.KMsrPres.equals(iUserDevice.getMeasure())) {
            deviceListener.notifyError(DeviceListener.MEASURE_PROCEDURE_ERROR,ResourceManager.getResource().getString("KWrongMeasure"));
            stop();
            return;
        }
        int result = b.getInt("nBPErr");
        if (result != StatusMsg.NIBP_ERROR_NO_ERROR) {
            deviceListener.notifyError(DeviceListener.MEASUREMENT_ERROR,ResourceManager.getResource().getString("KWrongMeasure"));
            stop();
            return;
        }

        HashMap<String, String> tmpVal = new HashMap<>();
        tmpVal.put(GWConst.EGwCode_03, Integer.toString(b.getInt("nDIA"))); // pressione minima
        tmpVal.put(GWConst.EGwCode_04, Integer.toString(b.getInt("nSYS"))); // pressione massima
        tmpVal.put(GWConst.EGwCode_06, Integer.toString(b.getInt("nPulse"))); // freq cardiaca
        tmpVal.put(GWConst.EGwCode_BATTERY, Integer.toString(iBattery)); // livello batteria
        Measure m = getMeasure();
        m.setMeasures(tmpVal);
        deviceListener.showMeasurementResults(m);
        stop();
    }



    // Temperature Measure metohds

    private void makeTemperatureResultData() {
        if (!GWConst.KMsrTemp.equals(iUserDevice.getMeasure())) {
            deviceListener.notifyError(DeviceListener.MEASURE_PROCEDURE_ERROR,ResourceManager.getResource().getString("KWrongMeasure"));
        } else {
            HashMap<String, String> tmpVal = new HashMap<>();
            tmpVal.put(GWConst.EGwCode_0R, iBodyTemperature);
            tmpVal.put(GWConst.EGwCode_BATTERY, Integer.toString(iBattery)); // livello batteria

            Measure m = getMeasure();
            m.setMeasures(tmpVal);
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
        if (!GWConst.KMsrOss.equals(iUserDevice.getMeasure())) {
            deviceListener.notifyError(DeviceListener.MEASURE_PROCEDURE_ERROR, ResourceManager.getResource().getString("KWrongMeasure"));
            stop();
            return;
        }

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
            Log.d(TAG, "oxySample : Finger Out!");
            makeOxyResultData();
        }
    }

    private void makeOxyResultData() {

        cancelMeasureAlarm();

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

    private Timer measureTimer;
    private static final int MEASURE_TIMEOUT = 2000; /*msec*/;

    private class MeasureTimeoutTask extends TimerTask {
        public void run() {
           Log.d(TAG, "MeasureTimeoutTask fired!");
            makeOxyResultData();
        }
    }

    private void scheduleMeasureAlarm() {
        Log.d(TAG, "scheduleMeasureAlarm");
        if (measureTimer != null) {
            measureTimer.cancel();
        }
        measureTimer = new Timer();
        measureTimer.schedule(new MeasureTimeoutTask(),MEASURE_TIMEOUT);
    }

    private void cancelMeasureAlarm() {
        if (measureTimer!=null) {
            measureTimer.cancel();
        }
    }


}

