package com.ti.app.telemed.core.btdevices;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.contec.spo2.code.bean.SdkConstants;
import com.contec.spo2.code.callback.ConnectCallback;
import com.contec.spo2.code.callback.RealtimeCallback;
import com.contec.spo2.code.connect.ContecSdk;
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

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Vector;


public class CMS50DLE extends DeviceHandler implements BTSearcherEventListener {

    private static final String TAG = "CMS50D";

    // Handler messages
    private static final int MSG_DEVICE_CONNECTED = 0;
    private static final int MSG_DEVICE_ERROR = 1;
    private static final int MSG_DISCONN = 2;
    private static final int MSG_REALTIME_DATA = 3;
    private static final int MSG_FINGER_OUT = 4;

	private final BTSearcher iServiceSearcher;
    private final Vector<BluetoothDevice> deviceList = new Vector<>();

    private ContecSdk sdk;

	public CMS50DLE(DeviceListener listener, UserDevice ud) {
        super(listener, ud);
        iServiceSearcher = new BTSearcher();
        initOxyData();
	}

	// methods of DeviceHandler interface

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
        if (!startInit(ot, btSearchListener))
            return false;
        if (sdk != null) {
            sdk.disconnect();
            sdk = null;
        }
        sdk = new ContecSdk(MyApp.getContext());
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
        deviceListener.notifyToUi(ResourceManager.getResource().getString("KConnectingDev"));
        sdk.init(false);
        iState = TState.EGettingConnection;
        sdk.connect(bd, connectCallback);
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

        iState = TState.EDisconnecting;
        if (sdk != null) {
            sdk.disconnect();
        }
        reset();
    }


	// ------------------------------------
	// CMS50D Callbacks objects

    private final RealtimeCallback realtimeCallback = new RealtimeCallback() {
        @Override
        public void onRealtimeWaveData(final int signal, final int prSound, final int waveData, int barData, int fingerOut) {
            if (fingerOut == 1) {
                Log.e(TAG, "onRealtimeWaveData - fingerOut=" + fingerOut);
                devOpHandler.sendEmptyMessage(MSG_FINGER_OUT);
            }
        }

        @Override
        public void onSpo2Data(final int piError, final int spo2, final int pr, final int pi) {
            Message msg = devOpHandler.obtainMessage(MSG_REALTIME_DATA);
            Bundle data = new Bundle();
            data.putInt("nSpO2", spo2);
            data.putInt("nPR", pr);
            data.putInt("fPI", pi);
            data.putInt("piError", piError);
            msg.setData(data);
            devOpHandler.sendMessage(msg);
        }

        @Override
        public void onRealtimeEnd() {
            Log.d(TAG, "RealtimeCallback - onRealtimeEnd");
        }

        @Override
        public void onFail(int errorCode) {
            Log.e(TAG, "RealtimeCallback - onFail: " + errorCode);
            devOpHandler.sendEmptyMessage(MSG_DEVICE_ERROR);
        }
    };

    private final ConnectCallback connectCallback = new ConnectCallback() {
        @Override
        public void onConnectStatus(int status) {
            switch (status) {
                case SdkConstants.CONNECT_CONNECTED:
                    Log.d(TAG, "onConnectStatus - Success: " + status );
                    devOpHandler.sendEmptyMessage(MSG_DEVICE_CONNECTED);
                    break;
                case SdkConstants.CONNECT_DISCONNECTED:
                    Log.d(TAG, "onConnectStatus - Disconnected: " + status);
                    devOpHandler.sendEmptyMessage(MSG_DISCONN);
                    break;
                case SdkConstants.CONNECT_UNSUPPORT_DEVICETYPE:
                case SdkConstants.CONNECT_UNSUPPORT_BLUETOOTHTYPE:
                case SdkConstants.CONNECT_DISCONNECT_SERVICE_UNFOUND:
                case SdkConstants.CONNECT_DISCONNECT_NOTIFY_FAIL:
                case SdkConstants.CONNECT_DISCONNECT_EXCEPTION:
                    Log.d(TAG, "onConnectStatus - error: " + status);
                    devOpHandler.sendEmptyMessage(MSG_DEVICE_ERROR);
                    break;
                case SdkConstants.CONNECT_CONNECTING:
                    Log.d(TAG, "onConnectStatus - Connecting...: " + status);
                    break;
                default:
                    Log.d(TAG, "onConnectStatus - Unknown status: " + status);
                    break;
            }
        }
    };


    private final MyHandler devOpHandler = new MyHandler(this);

    private static class MyHandler extends Handler {
        private final WeakReference<CMS50DLE> mOuter;

        private MyHandler(CMS50DLE outer) {
            mOuter = new WeakReference<>(outer);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            CMS50DLE outer = mOuter.get();
            Bundle d;
            switch (msg.what) {
                case MSG_REALTIME_DATA:
                    if (outer.numSamples==0) {
                        outer.deviceListener.notifyToUi(ResourceManager.getResource().getString("KMeasuring"));
                    }
                    outer.numSamples++;
                    d = msg.getData();
                    outer.oxySample(d);
                    break;
                case MSG_FINGER_OUT:
                    outer.makeOxyResultData();
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
                case MSG_DEVICE_CONNECTED:
                    if (outer.iState != TState.EGettingConnection) {
                        // chiamato il metodo stop() durante la richiesta di connessione
                        break;
                    }
                    outer.deviceListener.setBtMAC(outer.iBtDevAddr);
                    if (outer.operationType == OperationType.Pair) {
                        outer.deviceListener.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
                        outer.stop();
                    } else {
                        outer.deviceListener.notifyToUi(ResourceManager.getResource().getString("DoMeasureOS"));
                        outer.sdk.startRealtime(outer.realtimeCallback);
                    }
                    break;
            }
        }
    }

    // Oximetry Measure metohds and data

    private static final int NUMBER_OF_MEASUREMENTS = 300; // = 5 min, 1 measure every sec
    private static final int MIN_SAMPLES = 6;
    private static final int BASE_OXY_STREAM_LENGTH = 189; //

    private static class OxyElem {
        private final int iSat;
        private final int iFreq;

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

        Log.d(TAG, "oxySample: aSpO2="+aSpO2+ " aHR="+aHR);

        //Log.d(TAG, "aSpO2="+aSpO2+ " aHR="+aHR+" fingerIn="+fingerIn+" mVolt="+mVolt+" pLevel="+pLevel);
        // aSpO2 or aHR equals to 0 means invalid values
        if ((aSpO2 > 0) && (aHR > 0)) {
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

    private static class Time {
        private final int hh;
        private final int mm;
        private final int ss;

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
