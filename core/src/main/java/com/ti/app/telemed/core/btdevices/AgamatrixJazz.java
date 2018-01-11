package com.ti.app.telemed.core.btdevices;

import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.agamatrix.ambtsdk.lib.AgaMatrixClient;
import com.agamatrix.ambtsdk.lib.Constants;
import com.agamatrix.ambtsdk.lib.interfaces.AgaMatrixTimeListener;
import com.agamatrix.ambtsdk.lib.interfaces.BatteryStatusListener;
import com.agamatrix.ambtsdk.lib.interfaces.ConnectionStateListener;
import com.agamatrix.ambtsdk.lib.interfaces.DeviceInformationListener;
import com.agamatrix.ambtsdk.lib.interfaces.GlucoseMeasurementsListener;
import com.agamatrix.ambtsdk.lib.interfaces.MeterSettingsListener;
import com.agamatrix.ambtsdk.lib.model.AgaMatrixTime;
import com.agamatrix.ambtsdk.lib.model.DeviceInformation;
import com.agamatrix.ambtsdk.lib.model.GlucoseMeasurement;
import com.agamatrix.ambtsdk.lib.model.MeterSettings;
import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.BTSearcher;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.btmodule.BTSearcherEventListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.util.Util;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Vector;


public class AgamatrixJazz extends DeviceHandler implements
        BTSearcherEventListener,
        AgaMatrixTimeListener,
        ConnectionStateListener,
        MeterSettingsListener,
        BatteryStatusListener,
        DeviceInformationListener,
        GlucoseMeasurementsListener {

    private static final String AUTHORIZATION_KEY = "0300000080ff006d5819113d12ac54cc8485c64f";
    private static final String REGKEY = "AgamatrixJazz";


    private static final int HANDLER_DEVICE_CONNECTED = 100;
    private static final int HANDLER_DEVICE_DISCONNECTED = 101;
    private static final int HANDLER_BATTERY =103;
    private static final int HANDLER_MEASURES = 104;
    private static final int HANDLER_ERROR = 105;
    private static final int HANDLER_SETTINGS = 106;
    private static final int HANDLER_MEASURES_COUNT = 107;

    private Vector<BluetoothDevice> deviceList;
    private BTSearcher iServiceSearcher;
    private boolean prePrandial = true;
    private AgaMatrixClient mClient;
    private int batteryLevel = 0;
    private double conversionFactor = 1.;
    private ArrayList<?> measurements;
    private int lastSequenceNr = 0;

    private static final String TAG = "AgamatrixJazz";


    public AgamatrixJazz(DeviceListener listener, UserDevice ud) {
        super(listener, ud);

        iServiceSearcher = new BTSearcher();
        deviceList = new Vector<>();
        mClient = null;
        measurements = null;
        lastSequenceNr = 0;
    }

    // methods of DeviceHandler interface

    @Override
    public void confirmDialog() {
        prePrandial = false;
        makeResultData();
    }

    @Override
    public void cancelDialog() {
        prePrandial = true;
        makeResultData();
    }

    @Override
    public boolean startOperation(OperationType ot, BTSearcherEventListener btSearchListener) {
        if (!startInit(ot, btSearchListener))
            return false;
        Log.d(TAG,"startOperation: iBtDevAddr="+iBtDevAddr + " iCmdCode="+iCmdCode.toString());
        iServiceSearcher.clearBTSearcherEventListener();
        iServiceSearcher.addBTSearcherEventListener(this);
        iServiceSearcher.startSearchDevices();
        return true;
    }

    @Override
    public void stopOperation() {
        Log.d(TAG, "stopOperation");
        stop();
    }

    @Override
    public void selectDevice(BluetoothDevice bd){
        Log.d(TAG, "selectDevice: iBtDevAddr="+bd.getAddress());
        iServiceSearcher.stopSearchDevices();
        iState = TState.EGettingConnection;
        iBtDevAddr =  bd.getAddress();
        mClient = AgaMatrixClient.getInstance(MyApp.getContext(), bd);
        mClient.setAPIKey(AUTHORIZATION_KEY);
        mClient.registerConnectionStateListener(this);
        mClient.getDeviceInformation(this);
        deviceListener.notifyToUi(ResourceManager.getResource().getString("KConnectingDev"));
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


    // Device Library SDK callbacks
    @Override
    public void onAgaMatrixTimeUpdated(){
        Log.d(TAG,"onAgaMatrixTimeUpdated");
        mClient.getBatteryStatus(this);
    }

    @Override
    public void onAgaMatrixTimeUpdatedError(int errorCode){
        Log.d(TAG,"onDeviceInformationError: status="+errorCode);
        devOpHandler.obtainMessage(HANDLER_ERROR, errorCode, 0).sendToTarget();
    }

    @Override
    public void onAgaMatrixTime(AgaMatrixTime var1){
    }

    @Override
    public void onAgaMatrixTimeError(int var1){
    }

    @Override
    public void onDeviceInformationAvailable(DeviceInformation var1) {
        ByteBuffer bb = ByteBuffer.allocate(10);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        Calendar current = Calendar.getInstance();
        com.agamatrix.ambtsdk.lib.Util.writeBTDateToBuffer(current.getTime(),null,bb);
        mClient.updateAgaMatrixTime(bb.array(),this);
    }

    @Override
    public void onDeviceInformationError(int errorCode){
        Log.d(TAG,"onDeviceInformationError: status="+errorCode);
        devOpHandler.obtainMessage(HANDLER_ERROR, errorCode, 0).sendToTarget();
    }

    @Override
    public void onConnectionStateChange(int state, int status, String macAddr) {
        Log.d(TAG,"onConnectionStateChange: state="+state+" stauts="+status+"macAddr="+macAddr);
        if (state == Constants.AM_CONNECTION_STATE_CONNECTED)
            devOpHandler.obtainMessage(HANDLER_DEVICE_CONNECTED).sendToTarget();
        else if (state == Constants.AM_CONNECTION_STATE_DISCONNECTED)
            devOpHandler.obtainMessage(HANDLER_DEVICE_DISCONNECTED).sendToTarget();
    }

    @Override
    public void onBatteryStatusAvailable(final int percent) {
        Log.d(TAG,"onBatteryStatusAvailable: percent="+percent);
        devOpHandler.obtainMessage(HANDLER_BATTERY, percent, 0 ).sendToTarget();
    }

    @Override
    public void onBatteryStatusError(final int status) {
        Log.d(TAG,"onBatteryStatusError: status="+status);
        devOpHandler.obtainMessage(HANDLER_ERROR, status, 0).sendToTarget();
    }

    @Override
    public void onSettingsReadComplete(final MeterSettings meterSettings) {
        Log.d(TAG,"onSettingsReadComplete:");
        devOpHandler.obtainMessage(HANDLER_SETTINGS,meterSettings).sendToTarget();
    }

    @Override
    public void onSettingsRead(int i, byte b) {
        Log.d(TAG,"onSettingsRead: i="+i+" b="+b);
    }

    @Override
    public void onSettingsUpdateComplete() {
        Log.d(TAG,"onSettingsUpdateComplete:");
    }

    @Override
    public void onSettingsError(final int status) {
        Log.d(TAG,"onSettingsError: status"+status);
        devOpHandler.obtainMessage(HANDLER_ERROR, status, 0).sendToTarget();
    }

    @Override
    public void onGlucoseMeasurementsCountAvailable(final int count) {
        Log.d(TAG,"onGlucoseMeasurementsCountAvailable: count="+count);
        devOpHandler.obtainMessage(HANDLER_MEASURES_COUNT,count,0).sendToTarget();
    }

    @Override
    public void onGlucoseMeasurementsAvailable(final ArrayList<GlucoseMeasurement> measurements) {
        Log.d(TAG,"onGlucoseMeasurementsAvailable: nr="+measurements.size());
        devOpHandler.obtainMessage(HANDLER_MEASURES,measurements).sendToTarget();
    }

    @Override
    public void onGlucoseMeasurementsError(final int status) {
        Log.d(TAG,"onGlucoseMeasurementsError: status="+status);
        devOpHandler.obtainMessage(HANDLER_ERROR, status, 0).sendToTarget();
    }

    private final MyHandler devOpHandler = new MyHandler(this);

    private static class MyHandler extends Handler {
        private final WeakReference<AgamatrixJazz> mOuter;

        private MyHandler(AgamatrixJazz outer) {
            mOuter = new WeakReference<>(outer);
        }

        @Override
        public void handleMessage(Message msg) {
            AgamatrixJazz outer = mOuter.get();
            switch (msg.what) {
                case HANDLER_DEVICE_CONNECTED:
                    break;
                case HANDLER_DEVICE_DISCONNECTED:
                    if (outer.iState == TState.EConnected || outer.iState == TState.EGettingMeasures) {
                        String message = ResourceManager.getResource().getString("ECommunicationError");
                        outer.deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, message);
                        outer.stop();
                    }
                    break;
                case HANDLER_SETTINGS:
                    if (msg.obj instanceof MeterSettings) {
                        MeterSettings settings = (MeterSettings) msg.obj;
                        if (settings.getUnits() == MeterSettings.UNITS_MM_PER_L)
                            outer.conversionFactor = 18.;
                    }
                    if (outer.lastSequenceNr > 0) {
                        outer.mClient.getGlucoseMeasurementCountStartingAt(outer.lastSequenceNr, outer);
                        Log.d(TAG,"getGlucoseMeasurementCountStartingAt " + outer.lastSequenceNr);
                    } else {
                        outer.mClient.getGlucoseMeasurementCount(outer);
                        Log.d(TAG,"getGlucoseMeasurementCount");
                    }
                    outer.iState = TState.EGettingMeasures;
                    break;
                case HANDLER_BATTERY:
                    outer.deviceListener.setBtMAC(outer.iBtDevAddr);
                    if (outer.operationType == OperationType.Pair) {
                        outer.deviceListener.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
                        outer.stop();
                    } else {
                        outer.batteryLevel = msg.arg1;
                        outer.iState = TState.EConnected;
                        outer.lastSequenceNr = Util.getRegistryIntValue(REGKEY + outer.iBtDevAddr);
                        Log.d(TAG,"read lastSequenceNr="+outer.lastSequenceNr);
                        outer.mClient.getMeterSettings(outer);
                        outer.deviceListener.notifyWaitToUi(ResourceManager.getResource().getString("KMeasuring"));
                    }
                    break;
                case HANDLER_MEASURES_COUNT:
                    if (outer.lastSequenceNr > 0 && msg.arg1 > 1) {
                        outer.mClient.getGlucoseMeasurementsStartingAt(outer.lastSequenceNr, outer);
                        Log.d(TAG,"getGlucoseMeasurementsStartingAt " + outer.lastSequenceNr);
                    } else if (outer.lastSequenceNr <= 0 && msg.arg1 > 0) {
                        outer.mClient.getAllGlucoseMeasurements(outer);
                        Log.d(TAG, "getAllGlucoseMeasurements " + outer.lastSequenceNr);
                    } else {
                        outer.deviceListener.notifyError("", ResourceManager.getResource().getString("KNoNewMeasure"));
                        outer.stop();
                    }
                    break;
                case HANDLER_MEASURES:
                    if (msg.obj != null && msg.obj instanceof ArrayList<?>) {
                        outer.measurements = (ArrayList<?>) msg.obj;
                        if (outer.measurements.size() == 0) {
                            outer.deviceListener.notifyError("", ResourceManager.getResource().getString("KNoNewMeasure"));
                            outer.stop();
                        } else {
                            outer.askMeasure();
                        }
                    } else {
                        String message = ResourceManager.getResource().getString("EDataReadError");
                        outer.deviceListener.notifyError(DeviceListener.DEVICE_DATA_ERROR, message);
                        outer.stop();
                    }
                    break;
                case HANDLER_ERROR:
                    String message = ResourceManager.getResource().getString("ECommunicationError");
                    outer.deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR, message);
                    outer.stop();
                    break;
            }
        }
    }

    private void askMeasure() {
        if (measurements.get(0) instanceof GlucoseMeasurement) {
            GlucoseMeasurement glm = (GlucoseMeasurement)measurements.get(0);
            String message;
            int val = (int)(glm.getGlucoseConcentration()*conversionFactor);
            Date d = glm.getTimestamp();
            String date = new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(d);
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(d);

            message = ResourceManager.getResource().getString("KPrePostMsg").concat("\n\n");

            message = message.concat(ResourceManager.getResource().getString("KDate")).concat(": ");
            message = message.concat(date).concat("\n");
            message = message.concat(ResourceManager.getResource().getString("KTime")).concat(": ");
            message = message.concat(time).concat("\n");

            message = message.concat(ResourceManager.getResource().getString("Glycemia")).concat(": ");
            message = message.concat(Integer.toString(val)).concat(" ");
            message = message.concat(ResourceManager.getResource().getString("GlycemiaUnit"));


            deviceListener.askSomething(message,
                    ResourceManager.getResource().getString("MeasureGlyPOSTBtn"),
                    ResourceManager.getResource().getString("MeasureGlyPREBtn"));
        } else {
                String message = ResourceManager.getResource().getString("EDataReadError");
                deviceListener.notifyError(DeviceListener.DEVICE_DATA_ERROR, message);
                stop();

        }
    }

    private void makeResultData() {
        if (!(measurements.get(0) instanceof GlucoseMeasurement)) {
            String message = ResourceManager.getResource().getString("EDataReadError");
            deviceListener.notifyError(DeviceListener.DEVICE_DATA_ERROR, message);
            stop();
            return;
        }

        GlucoseMeasurement glm = (GlucoseMeasurement) measurements.get(0);
        Calendar cal = Calendar.getInstance();
        cal.setTime(glm.getTimestamp());
        int val = (int)(glm.getGlucoseConcentration()*conversionFactor);
        HashMap<String,String> tmpVal = new HashMap<>();
        if (prePrandial) {
            tmpVal.put(GWConst.EGwCode_0E, Integer.toString(val));  // glicemia Pre-prandiale
        } else {
            tmpVal.put(GWConst.EGwCode_0T, Integer.toString(val));  // glicemia Post-prandiale
        }
        tmpVal.put(GWConst.EGwCode_BATTERY, Integer.toString(batteryLevel)); // livello batteria

        Measure m = getMeasure();
        m.setTimestamp(Util.getTimestamp(cal));
        m.setMeasures(tmpVal);
        m.setFailed(false);

        Util.setRegistryValue(REGKEY+iBtDevAddr, glm.getSequenceNumber());
        Log.d(TAG,"set lastSequenceNr to "+glm.getSequenceNumber());

        deviceListener.showMeasurementResults(m);
        stop();
    }

    private void stop() {
        Log.d(TAG, "stop");
        if (iState == TState.EGettingDevice) {
            iServiceSearcher.stopSearchDevices();
        }
        iServiceSearcher.stopSearchDevices();
        iServiceSearcher.clearBTSearcherEventListener();
        iServiceSearcher.close();
        if (mClient != null) {
            iState = TState.EDisconnecting;
            mClient.shutdown();
        }
        reset();
    }

    private void reset() {
        iState = TState.EWaitingToGetDevice;
        iBtDevAddr = null;
        deviceList.clear();
        iBTSearchListener = null;
        mClient = null;
        measurements = null;
        lastSequenceNr = 0;
    }
}
