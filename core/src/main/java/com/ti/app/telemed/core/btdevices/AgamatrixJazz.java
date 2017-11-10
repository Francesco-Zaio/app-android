package com.ti.app.telemed.core.btdevices;

import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.agamatrix.ambtsdk.lib.AgaMatrixClient;
import com.agamatrix.ambtsdk.lib.Constants;
import com.agamatrix.ambtsdk.lib.interfaces.BatteryStatusListener;
import com.agamatrix.ambtsdk.lib.interfaces.ConnectionStateListener;
import com.agamatrix.ambtsdk.lib.interfaces.GlucoseMeasurementsListener;
import com.agamatrix.ambtsdk.lib.interfaces.MeterSettingsListener;
import com.agamatrix.ambtsdk.lib.model.GlucoseMeasurement;
import com.agamatrix.ambtsdk.lib.model.MeterSettings;
import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.BTSearcher;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.btmodule.events.BTSearcherEvent;
import com.ti.app.telemed.core.btmodule.events.BTSearcherEventListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.Patient;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.util.Util;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Vector;


public class AgamatrixJazz extends DeviceHandler implements
        BTSearcherEventListener,
        ConnectionStateListener,
        MeterSettingsListener,
        BatteryStatusListener,
// DeviceInformationListener,
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
    private int measureIndex = 0;
    private int lastSequenceNr = 0;

    private static final String TAG = "AgamatrixJazz";

    public static boolean needPairing(UserDevice userDevice) {
        return false;
    }

    public static boolean needConfig(UserDevice userDevice) {
        return false;
    }

    public AgamatrixJazz(DeviceListener listener, UserDevice ud) {
            super(listener, ud);

        iServiceSearcher = new BTSearcher();
        deviceList = new Vector<>();
        mClient = null;
        measurements = null;
        measureIndex = 0;
        lastSequenceNr = 0;
    }

    // methods of DeviceHandler interface

    @Override
    public void confirmDialog() {
        prePrandial = true;
        makeResultData();
    }

    @Override
    public void cancelDialog() {
        prePrandial = false;
        makeResultData();
    }

    @Override
    public boolean startOperation(OperationType ot, BTSearcherEventListener btSearchListener) {
        if (!startInit(ot, btSearchListener))
            return false;

        Log.d(TAG,"startOperation: iBtDevAddr="+iBtDevAddr + " iCmdCode="+iCmdCode.toString());
        iServiceSearcher.clearBTSearcherEventListener();
        iServiceSearcher.addBTSearcherEventListener(this);
        iServiceSearcher.setSearchType(iCmdCode);
        iServiceSearcher.startSearchDevices();

        return true;
    }

    @Override
    public void abortOperation() {
        Log.d(TAG, "abortOperation");
        stop();
    }

    @Override
    public void selectDevice(int selected){
        Log.d(TAG, "selectDevice: selected=" + selected);
        iServiceSearcher.stopSearchDevices(selected);
    }


    // methods of BTSearcherEventListener interface

    @Override
    public void deviceDiscovered(BTSearcherEvent evt, Vector<BluetoothDevice> devList) {
        Log.d(TAG,"deviceDiscovered: size="+devList.size());
        deviceList = devList;
        if (iCmdCode == TCmd.ECmdConnByAddr) {
            for (int i=0; i<deviceList.size(); i++)
                if (iBtDevAddr.equalsIgnoreCase(deviceList.get(i).getAddress())) {
                    iServiceSearcher.stopSearchDevices(i);
                }
        } else if (iBTSearchListener != null) {
            iBTSearchListener.deviceDiscovered(evt, deviceList);
        }
    }

    @Override
    public void deviceSearchCompleted(BTSearcherEvent evt) {
        if (iCmdCode == TCmd.ECmdConnByUser && iBTSearchListener != null)
            iBTSearchListener.deviceSearchCompleted(evt);
    }

    @Override
    public void deviceSelected(BTSearcherEvent evt) {
        iState = TState.EGettingConnection;
        BluetoothDevice d = iServiceSearcher.getCurrBTDevice();
        Log.i(TAG, "selectDevice: iBtDevAddr="+d.getAddress());
        iBtDevAddr =  d.getAddress();
        mClient = AgaMatrixClient.getInstance(MyApp.getContext(), d);
        mClient.setAPIKey(AUTHORIZATION_KEY);
        mClient.registerConnectionStateListener(this);
        mClient.getBatteryStatus(this);
        deviceListener.notifyToUi(ResourceManager.getResource().getString("KConnectingDev"));
    }


    // Device Library SDK callbacks

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
                    if (outer.lastSequenceNr > 0)
                        outer.mClient.getGlucoseMeasurementCountStartingAt(outer.lastSequenceNr, outer);
                    else
                        outer.mClient.getGlucoseMeasurementCount(outer);
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
                        outer.mClient.getMeterSettings(outer);
                        outer.deviceListener.notifyWaitToUi(ResourceManager.getResource().getString("KMeasuring"));
                    }
                    break;
                case HANDLER_MEASURES_COUNT:
                    if (outer.lastSequenceNr > 0 && msg.arg1 > 1)
                        outer.mClient.getGlucoseMeasurementsStartingAt(outer.lastSequenceNr, outer);
                    else if (outer.lastSequenceNr == 0 && msg.arg1 > 0)
                        outer.mClient.getAllGlucoseMeasurements(outer);
                    else {
                        outer.deviceListener.notifyError("", ResourceManager.getResource().getString("KNoNewMeasure"));
                        outer.stop();
                    }
                    break;
                case HANDLER_MEASURES:
                    if (msg.obj != null && msg.obj instanceof ArrayList<?>) {
                        outer.measurements = (ArrayList<?>) msg.obj;
                        outer.measureIndex = 0;
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
        if (measurements.get(measureIndex) instanceof GlucoseMeasurement) {
            GlucoseMeasurement glm = (GlucoseMeasurement)measurements.get(measureIndex);
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
                    ResourceManager.getResource().getString("MeasureGlyPREBtn"),
                    ResourceManager.getResource().getString("MeasureGlyPOSTBtn"));
        } else {
                String message = ResourceManager.getResource().getString("EDataReadError");
                deviceListener.notifyError(DeviceListener.DEVICE_DATA_ERROR, message);
                stop();

        }
    }

    private void makeResultData() {
        if (!(measurements.get(measureIndex) instanceof GlucoseMeasurement)) {
            String message = ResourceManager.getResource().getString("EDataReadError");
            deviceListener.notifyError(DeviceListener.DEVICE_DATA_ERROR, message);
            stop();
            return;
        }

        GlucoseMeasurement glm = (GlucoseMeasurement) measurements.get(measureIndex);
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
        m.setTimestamp(XmlManager.getXmlManager().getTimestamp(cal));
        m.setMeasures(tmpVal);
        m.setFailed(false);

        Util.setRegistryValue(REGKEY+iBtDevAddr, glm.getSequenceNumber());

        deviceListener.showMeasurementResults(m);
        stop();
    }

    private void stop() {
        Log.d(TAG, "stop");
        if (iState == TState.EGettingDevice) {
            iServiceSearcher.stopSearchDevices(-1);
        }
        iServiceSearcher.stopSearchDevices(-1);
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
        measureIndex = 0;
        lastSequenceNr = 0;
    }
}
