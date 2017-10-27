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
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.util.Util;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Vector;


public class AgamatrixJazz extends Handler implements
        DeviceHandler,
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

    private enum TState {
        EWaitingToGetDevice,    // default e notifica disconnessione
        EGettingDevice,         // chiamata di start(...)
        EGettingConnection,     // chiamata a connectDevice()
        EDisconnecting,         // chiamata a disconnectDevice
        EConnected,             // callabck connessione avvenuta OK o fine Misura
        EGettingMeasures       // chiamata startMeasures
    }

    private DeviceListener deviceListener;
    private Vector<BluetoothDevice> deviceList;
    private BTSearcher iServiceSearcher;
    private BTSearcherEventListener scanActivityListener;
    private TState iState;
    private String iBtDevAddr;
    private boolean iPairingMode;
    private TCmd iCmdCode;
    private boolean prePrandial = true;
    private BluetoothDevice device;
    private AgaMatrixClient mClient;
    private int batteryLevel = 0;
    private Measure m;
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

    public AgamatrixJazz(DeviceListener aScheduler) {
        iState = TState.EWaitingToGetDevice;
        scanActivityListener = null;
        iServiceSearcher = new BTSearcher();
        deviceListener = aScheduler;
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
    public void start(OperationType ot, UserDevice ud, BTSearcherEventListener btSearchListener) {
        if (iState == TState.EWaitingToGetDevice && ud != null) {
            iPairingMode = (ot == OperationType.Pair);
            iState = TState.EGettingMeasures;
            measurements = null;
            iBtDevAddr = ud.getBtAddress();
            scanActivityListener = btSearchListener;

            m = new Measure();
            User u = UserManager.getUserManager().getCurrentUser();
            m.setMeasureType(ud.getMeasure());
            m.setDeviceDesc(ud.getDevice().getDescription());
            m.setTimestamp(XmlManager.getXmlManager().getTimestamp(null));
            m.setFile(null);
            m.setFileType(null);
            if (u != null) {
                m.setIdUser(u.getId());
                if (u.getIsPatient())
                    m.setIdPatient(u.getId());
            }
            m.setFailed(false);
            m.setBtAddress(iBtDevAddr);


            iServiceSearcher.clearBTSearcherEventListener();
            iServiceSearcher.addBTSearcherEventListener(this);
            if (iBtDevAddr != null && !iBtDevAddr.isEmpty()) {
                iCmdCode = TCmd.ECmdConnByAddr;
            } else {
                iCmdCode = TCmd.ECmdConnByUser;
            }
            iState = TState.EGettingDevice;
            iServiceSearcher.setSearchType(iCmdCode);
            // it launch the automatic procedures for the manual device search
            iServiceSearcher.startSearchDevices();
        }
    }

    @Override
    public void stopDeviceOperation(int selected) {
        Log.d(TAG, "stopDeviceOperation: selected=" + selected);

        // selected == -1 Normal end of operation
        // selected == -2 Operation interrupted
        // selected >= 0 the user has selected the device at the 'selected' position in the list of discovered devices
        if (selected == -1) {
            // L'utente ha premuto Annulla sulla finestra di dialogo
            stop();
        } else if (selected == -2) {
            // L'utente ha premuto back o ha chiuso la lista dei device trovati sena selezionarne nessuno
            iServiceSearcher.stopSearchDevices(-1);
            iServiceSearcher.removeBTSearcherEventListener(this);
            if (iState != TState.EGettingDevice) {
                // we advise the scheduler of the end of the activity on the device
                deviceListener.operationCompleted();
            }
        } else {
            device = deviceList.get(selected);
            iServiceSearcher.stopSearchDevices(selected);
        }
    }


    // methods of BTSearcherEventListener interface

    @Override
    public void deviceDiscovered(BTSearcherEvent evt, Vector<BluetoothDevice> devList) {
        deviceList = devList;
        if (iCmdCode == TCmd.ECmdConnByAddr) {
            if (iBtDevAddr.equalsIgnoreCase(deviceList.get(deviceList.size() - 1).getAddress())) {
                device = deviceList.get(deviceList.size() - 1);
                iServiceSearcher.stopSearchDevices(deviceList.size() - 1);
            }
        } else if (scanActivityListener != null)
            scanActivityListener.deviceDiscovered(evt, deviceList);
    }

    @Override
    public void deviceSearchCompleted(BTSearcherEvent evt) {
        if (iCmdCode == TCmd.ECmdConnByUser && scanActivityListener != null)
            scanActivityListener.deviceSearchCompleted(evt);
    }

    @Override
    public void deviceSelected(BTSearcherEvent evt) {
        Log.i(TAG, "deviceSelected");
        iState = TState.EGettingConnection;
        iBtDevAddr = device.getAddress();
        mClient = AgaMatrixClient.getInstance(MyApp.getContext(), device);
        mClient.setAPIKey(AUTHORIZATION_KEY);
        mClient.registerConnectionStateListener(this);
        mClient.getBatteryStatus(this);
        deviceListener.notifyToUi(ResourceManager.getResource().getString("KConnectingDev"));
    }


    // Device Library SDK callbacks

    @Override
    public void onConnectionStateChange(int state, int status, String addr) {
        /*
        state - the current connection state.
        status - the status code.
        addr - the mac address of the device.
        */
        Log.d(TAG,"onConnectionStateChange: state="+state+" stauts="+status+"addr="+addr);
        if (state == Constants.AM_CONNECTION_STATE_CONNECTED)
            obtainMessage(HANDLER_DEVICE_CONNECTED).sendToTarget();
        else if (state == Constants.AM_CONNECTION_STATE_DISCONNECTED)
            obtainMessage(HANDLER_DEVICE_DISCONNECTED).sendToTarget();
    }

    @Override
    public void onBatteryStatusAvailable(final int percent) {
        Log.d(TAG,"onBatteryStatusAvailable: percent="+percent);
        obtainMessage(HANDLER_BATTERY, percent, 0 ).sendToTarget();
    }

    @Override
    public void onBatteryStatusError(final int status) {
        Log.d(TAG,"onBatteryStatusError: status="+status);
        obtainMessage(HANDLER_ERROR, status, 0).sendToTarget();
    }
/*
    @Override
    public void onDeviceInformationAvailable(final DeviceInformation deviceInformation) {
    }

    @Override
    public void onDeviceInformationError(final int status) {
        obtainMessage(HANDLER_ERROR, status, 0).sendToTarget();
    }
*/
    @Override
    public void onSettingsReadComplete(final MeterSettings meterSettings) {
        obtainMessage(HANDLER_SETTINGS,meterSettings).sendToTarget();
    }

    @Override
    public void onSettingsRead(int i, byte b) {
    }

    @Override
    public void onSettingsUpdateComplete() {
    }

    @Override
    public void onSettingsError(final int status) {
        obtainMessage(HANDLER_ERROR, status, 0).sendToTarget();
    }

    @Override
    public void onGlucoseMeasurementsCountAvailable(final int count) {
        obtainMessage(HANDLER_MEASURES_COUNT,count,0).sendToTarget();
    }

    @Override
    public void onGlucoseMeasurementsAvailable(final ArrayList<GlucoseMeasurement> measurements) {
        obtainMessage(HANDLER_MEASURES,measurements).sendToTarget();
    }

    @Override
    public void onGlucoseMeasurementsError(final int status) {
        obtainMessage(HANDLER_ERROR, status, 0).sendToTarget();
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case HANDLER_DEVICE_CONNECTED:
                deviceListener.setBtMAC(iBtDevAddr);
                if (iPairingMode) {
                    iState = TState.EDisconnecting;
                    Util.setRegistryValue(REGKEY+iBtDevAddr, 0);
                    mClient.disconnect();
                } else {
                    iState = TState.EConnected;
                    lastSequenceNr = Util.getRegistryIntValue(REGKEY+iBtDevAddr);
                    mClient.getMeterSettings(this);
                    deviceListener.notifyWaitToUi(ResourceManager.getResource().getString("KMeasuring"));
                }
                break;
            case HANDLER_DEVICE_DISCONNECTED:
                if (iPairingMode) {
                    deviceListener.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
                    stop();
                }
                break;
            case HANDLER_SETTINGS:
                if (msg.obj instanceof MeterSettings) {
                    MeterSettings settings = (MeterSettings)msg.obj;
                    if (settings.getUnits() == MeterSettings.UNITS_MM_PER_L)
                        conversionFactor=18.;
                }
                mClient.getGlucoseMeasurementCountStartingAt(lastSequenceNr, this);
                iState = TState.EGettingMeasures;
                break;
            case HANDLER_BATTERY:
                batteryLevel = msg.arg1;
                break;
            case HANDLER_MEASURES_COUNT:
                if (msg.arg1 > 1)
                    mClient.getGlucoseMeasurementsStartingAt(lastSequenceNr, this);
                else {
                    deviceListener.notifyError("",ResourceManager.getResource().getString("KNoNewMeasure"));
                    stop();
                }
                break;
            case HANDLER_MEASURES:
                if (msg.obj != null && msg.obj instanceof ArrayList<?>) {
                    measurements = (ArrayList<?>) msg.obj;
                    measureIndex = 0;
                    if (measurements.size() == 0) {
                        deviceListener.notifyError("",ResourceManager.getResource().getString("KNoNewMeasure"));
                        stop();
                    } else {
                        askMeasure();
                    }
                } else {
                    String message = ResourceManager.getResource().getString("EDataReadError");
                    deviceListener.notifyError(DeviceListener.DEVICE_DATA_ERROR, message);
                    stop();
                }
                break;
            case HANDLER_ERROR:
                String message = ResourceManager.getResource().getString("ECommunicationError");
                deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR,message);
                stop();
                break;
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
        m.setTimestamp(XmlManager.getXmlManager().getTimestamp(cal));

        int val = (int)(glm.getGlucoseConcentration()*conversionFactor);
        HashMap<String,String> tmpVal = new HashMap<>();
        if (prePrandial) {
            tmpVal.put(GWConst.EGwCode_0E, Integer.toString(val));  // glicemia Pre-prandiale
        } else {
            tmpVal.put(GWConst.EGwCode_0T, Integer.toString(val));  // glicemia Post-prandiale
        }
        tmpVal.put(GWConst.EGwCode_BATTERY, Integer.toString(batteryLevel)); // livello batteria
        m.setMeasures(tmpVal);
        m.setFailed(false);
        m.setBtAddress(iBtDevAddr);

        Util.setRegistryValue(REGKEY+iBtDevAddr, glm.getSequenceNumber());

        deviceListener.showMeasurementResults(m);

        stop();
    }

    public void stop() {
        Log.d(TAG, "stop");
        iServiceSearcher.stopSearchDevices(-1);
        iServiceSearcher.clearBTSearcherEventListener();
        iServiceSearcher.close();
        if (mClient != null) {
            mClient.shutdown();
            iState = TState.EDisconnecting;
        }
        // we advise the scheduler of the end of the activity on the device
        deviceListener.operationCompleted();
        reset();
    }

    private void reset() {
        iState = TState.EWaitingToGetDevice;
        iBtDevAddr = null;
        deviceList.clear();
        scanActivityListener = null;
        mClient = null;
        measurements = null;
        measureIndex = 0;
        lastSequenceNr = 0;
    }
}
