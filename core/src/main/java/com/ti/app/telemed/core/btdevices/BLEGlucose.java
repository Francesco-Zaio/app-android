package com.ti.app.telemed.core.btdevices;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.BTSearcher;
import com.ti.app.telemed.core.btmodule.BTSearcherEventListener;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.util.Util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Vector;

import static android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE;


public class BLEGlucose extends DeviceHandler implements BTSearcherEventListener {

	private static final String TAG = "OnCallSureSync";

	// Standard Glucose Services
    private static String GLUCOSE_SERVICE = "00001808-0000-1000-8000-00805f9b34fb";
    private final static UUID UUID_GLUCOSE_SERVICE =  UUID.fromString(GLUCOSE_SERVICE);
    private static String GLUCOSE_MEASURE_CHARACTERISTIC = "00002a18-0000-1000-8000-00805f9b34fb";
    private final static UUID UUID_GLUCOSE_MEASURE_CHARACTERISTICS = UUID.fromString(GLUCOSE_MEASURE_CHARACTERISTIC);
    private static String GLUCOSE_MEASURE_CONTEXT_CHARACTERISTIC = "00002a34-0000-1000-8000-00805f9b34fb";
    private final static UUID UUID_GLUCOSE_MEASURE_CONTEXT_CHARACTERISTICS = UUID.fromString(GLUCOSE_MEASURE_CONTEXT_CHARACTERISTIC);

    private static String GLUCOSE_FEATURE_CHARACTERISTIC = "00002a51-0000-1000-8000-00805f9b34fb";
    private final static UUID UUID_GLUCOSE_FEATURE_CHARACTERISTICS = UUID.fromString(GLUCOSE_FEATURE_CHARACTERISTIC);

    private static String RECORD_ACCESS_CONTROL_POINT = "00002a52-0000-1000-8000-00805f9b34fb";
    private final static UUID UUID_RECORD_ACCESS_CONTROL_POINT = UUID.fromString(RECORD_ACCESS_CONTROL_POINT);

    // Standard Battery Services
    private static String BATTERY_SERVICE = "0000180f-0000-1000-8000-00805f9b34fb";
    private final static UUID UUID_BATTERY_SERVICE =  UUID.fromString(BATTERY_SERVICE);
    private static String BATTERY_LEVEL_CHARACTERISTIC = "00002a19-0000-1000-8000-00805f9b34fb";
    private final static UUID UUID_BATTERY_LEVEL_CHARACTERISTICS = UUID.fromString(BATTERY_LEVEL_CHARACTERISTIC);

    // Standard Device Information Services
    // private static String DEVINFO_SERVICE = "0000180a-0000-1000-8000-00805f9b34fb";
    // private final static UUID UUID_DEVINFO_SERVICE =  UUID.fromString(DEVINFO_SERVICE);

    // Custom Bluetooth Service
    // private static String MAIN_SERVICE = "11223344-5566-7788-99aa-bbccddeeff00";
    // private final static UUID UUID_MAIN_SERVICE =  UUID.fromString(MAIN_SERVICE);
    // private static String MAIN_CHARACTERISTIC = "00004a5b-0000-1000-8000-00805f9b34fb";
    // private final static UUID UUID_MAIN_CHARACTERISTICS = UUID.fromString(MAIN_CHARACTERISTIC);

    // Client Characteristic Configuration
    private static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";


    private static final int MAX_CONNCTION_RETRY = 5;

    // iServiceSearcher searches for service this client can
    // connect to (in symbian version the type was CBTUtil) and
    // substitutes RSocketServ and RSocket of symbian version too
    private BTSearcher iServiceSearcher;
    private Vector<BluetoothDevice> deviceList = new Vector<>();

    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothGatt mBluetoothGatt = null;
    private String mBluetoothDeviceAddress = null;
    private ConnectionState mConnectionState = ConnectionState.DISCONNECTED;
    private boolean stopped = false;
    private int connectionRetry = 0;

    private BluetoothGattCharacteristic glucoseMeasureCaracteristic = null;
    private BluetoothGattCharacteristic glucoseMeasureContextCaracteristic = null;
    private BluetoothGattCharacteristic glucoseFeatureCaracteristic = null;
    private BluetoothGattCharacteristic batteryLevelCaracteristic = null;
    private BluetoothGattCharacteristic recordAccessControlPointCaracteristic = null;

    // Glucose Feature charcteristic values
    private boolean lowBatteryDetection = false;
    private boolean sensorMalfunctionDetection = false;
    private boolean sensorSampleSizeSupported = false;
    private boolean stripInsertionErrorDetection = false;
    private boolean stripTypeErrorDetection = false;
    private boolean resultHighLowDetection = false;
    private boolean temperatureHighLowDetection = false;
    private boolean readInterruptDetection = false;
    private boolean generalDeviceFault = false;
    private boolean timeFault = false;

    private boolean lowBattery = false;
    private boolean sensorMalfunction = false;
    private boolean sampleSizeError = false;
    private boolean stripInsertionError = false;
    private boolean stripTypeError = false;
    private boolean resultHigherError = false;
    private boolean resultLowerError = false;
    private boolean temperatureHighError = false;
    private boolean temperatureLowError = false;
    private boolean readInterruptError = false;
    private boolean generalDeviceFaultError = false;
    private boolean timeFaultError = false;

    private int batteryLevel = -1;

    private Calendar measureDate = null;
    private double conversionFactor = 100000.; // from Kg/L to mg/dl
    private int measureVal = -1;
    private Boolean prePrandial = null;


    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    public BLEGlucose(DeviceListener listener, UserDevice ud) {
        super(listener, ud);
        iServiceSearcher = new BTSearcher();
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
        Log.d(TAG,"startOperation: iBtDevAddr="+iBtDevAddr + " iCmdCode="+iCmdCode.toString() +
                " OperationType=" + ot);
        deviceList.clear();
        stopped = false;
        iServiceSearcher.clearBTSearcherEventListener();
        iServiceSearcher.addBTSearcherEventListener(this);
        iServiceSearcher.startSearchDevices();
        deviceListener.notifyToUi(ResourceManager.getResource().getString("KSearchingDev"));
        return true;
    }

    @Override
    public void stopOperation() {
        Log.d(TAG, "stopOperation");
        stopped = true;
        stop();
    }

    @Override
    public void selectDevice(BluetoothDevice bd){
        Log.d(TAG, "selectDevice: addr=" + bd.getAddress());
        iServiceSearcher.stopSearchDevices();
        iBtDevAddr = bd.getAddress();
        deviceListener.notifyToUi(ResourceManager.getResource().getString("KConnectingDev"));
        if (!connect(iBtDevAddr)) {
            deviceListener.notifyError(DeviceListener.CONNECTION_ERROR,ResourceManager.getResource().getString("EBtDeviceConnError"));
            stop();
        }
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
                if (d.getType() == DEVICE_TYPE_LE) {
                    int i;
                    for (i = 0; i < deviceList.size(); i++)
                        if (deviceList.elementAt(i).getAddress().equals(d.getAddress()))
                            break;
                    if (i >= deviceList.size()) {
                        deviceList.addElement(d);
                        if (iBTSearchListener != null)
                            iBTSearchListener.deviceDiscovered(deviceList);
                    }
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

    public void reset() {
        iBtDevAddr = null;
        stopped = false;
    }

    public void stop() {
        iServiceSearcher.stopSearchDevices();
        iServiceSearcher.clearBTSearcherEventListener();
        iServiceSearcher.close();

        disconnect();

        reset();
    }

    private static String toHexString(byte[] array){
    	String tmp = "";
        for (byte b : array)
            tmp = tmp.concat(" 0x" + Integer.toHexString(b & 0x000000ff));
    	return tmp;
    }

    private void askMeasure() {
        boolean error = false;
        String message = "";
        /*
        if ( lowBattery) {
            error = true;
            message = ResourceManager.getResource().getString("lowBatteryError");
        } else
        */

        if (sensorMalfunction) {
            error = true;
            message = ResourceManager.getResource().getString("sensorMalfunctionError");
        } else if (sampleSizeError) {
            error = true;
            message = ResourceManager.getResource().getString("sampleSizeError");
        } else if (stripInsertionError) {
            error = true;
            message = ResourceManager.getResource().getString("stripInsertionError");
        } else if (stripTypeError) {
            error = true;
            message = ResourceManager.getResource().getString("stripTypeError");
        } else if (resultHigherError) {
            error = true;
            message = ResourceManager.getResource().getString("resultHigherError");
        } else if (resultLowerError) {
            error = true;
            message = ResourceManager.getResource().getString("resultLowerError");
        } else if (temperatureHighError) {
            error = true;
            message = ResourceManager.getResource().getString("temperatureHighError");
        } else if (temperatureLowError) {
            error = true;
            message = ResourceManager.getResource().getString("temperatureLowError");
        } else if (readInterruptError) {
            error = true;
            message = ResourceManager.getResource().getString("readInterruptError");
        } else if (generalDeviceFaultError) {
            error = true;
            message = ResourceManager.getResource().getString("generalDeviceFaultError");
        } else if (timeFaultError) {
            error = true;
            message = ResourceManager.getResource().getString("timeFaultError");
        }
        if (error) {
            deviceListener.notifyError(DeviceListener.MEASUREMENT_ERROR,message);
            stop();
            return;
        }

        String date = new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(measureDate.getTime());
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(measureDate.getTime());

        message = ResourceManager.getResource().getString("KPrePostMsg").concat("\n\n");

        message = message.concat(ResourceManager.getResource().getString("KDate")).concat(": ");
        message = message.concat(date).concat("\n");
        message = message.concat(ResourceManager.getResource().getString("KTime")).concat(": ");
        message = message.concat(time).concat("\n");

        message = message.concat(ResourceManager.getResource().getString("Glycemia")).concat(": ");
        message = message.concat(Integer.toString(measureVal)).concat(" ");
        message = message.concat(ResourceManager.getResource().getString("GlycemiaUnit"));

        deviceListener.askSomething(message,
                ResourceManager.getResource().getString("MeasureGlyPOSTBtn"),
                ResourceManager.getResource().getString("MeasureGlyPREBtn"));
    }
    
    private void makeResultData() {
        HashMap<String,String> tmpVal = new HashMap<>();
        if (prePrandial) {
            tmpVal.put(GWConst.EGwCode_0E, Integer.toString(measureVal));  // glicemia Pre-prandiale
        } else {
            tmpVal.put(GWConst.EGwCode_0T, Integer.toString(measureVal));  // glicemia Post-prandiale
        }
        if (batteryLevel != -1)
            tmpVal.put(GWConst.EGwCode_BATTERY, Integer.toString(batteryLevel)); // livello batteria
        Measure m = getMeasure();
        m.setTimestamp(Util.getTimestamp(measureDate));
        m.setMeasures(tmpVal);
        m.setFailed(false);
        deviceListener.showMeasurementResults(m);
        stop();
    }

    private boolean connect(String address) {
        Log.d(TAG, "connect");
        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) MyApp.getContext().getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Previously connected device.  Try to reconnect.
        if (address.equals(mBluetoothDeviceAddress)  && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = ConnectionState.CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(MyApp.getContext(), false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = ConnectionState.CONNECTING;
        return true;
    }

    private void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            return;
        }
        if (mConnectionState != ConnectionState.DISCONNECTED)
            mBluetoothGatt.disconnect();
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mConnectionState = ConnectionState.CONNECTED;
                connectionRetry = 0;
                Log.i(TAG, "Connected to GATT server.");
                // Discover services after successful connection.
                mBluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mConnectionState = ConnectionState.DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                if (!stopped)
                    if (connectionRetry++ > MAX_CONNCTION_RETRY) {
                        disconnect();
                        deviceListener.notifyError(DeviceListener.CONNECTION_ERROR,ResourceManager.getResource().getString("ECommunicationError"));
                        stop();
                    } else {
                        connect(mBluetoothDeviceAddress);
                    }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                List<BluetoothGattService> services = gatt.getServices();
                Log.i(TAG, "found: " + services.size() + " services");
                for (BluetoothGattService s:services) {
                    Log.i(TAG, "Service: " + s.getUuid().toString());
                    if (s.getUuid().equals(UUID_GLUCOSE_SERVICE)) {
                        Log.i(TAG, "UUID_GLUCOSE_SERVICE FOUND");
                        List<BluetoothGattCharacteristic> lc = s.getCharacteristics();
                        for (BluetoothGattCharacteristic c:lc) {
                            if (c.getUuid().equals(UUID_GLUCOSE_MEASURE_CHARACTERISTICS)) {
                                Log.i(TAG, "UUID_GLUCOSE_MEASURE_CHARACTERISTICS FOUND");
                                glucoseMeasureCaracteristic = c;
                            } else if(c.getUuid().equals(UUID_GLUCOSE_MEASURE_CONTEXT_CHARACTERISTICS)) {
                                Log.i(TAG, "UUID_GLUCOSE_MEASURE_CONTEXT_CHARACTERISTICS FOUND");
                                glucoseMeasureContextCaracteristic = c;
                            } else if(c.getUuid().equals(UUID_GLUCOSE_FEATURE_CHARACTERISTICS)) {
                                Log.i(TAG, "UUID_GLUCOSE_FEATURE_CHARACTERISTICS FOUND");
                                glucoseFeatureCaracteristic = c;
                            } else if (UUID_RECORD_ACCESS_CONTROL_POINT.equals(c.getUuid())) {
                                Log.i(TAG, "UUID_RECORD_ACCESS_CONTROL_POINT FOUND");
                                recordAccessControlPointCaracteristic = c;
                            }
                        }
                    } else if (s.getUuid().equals(UUID_BATTERY_SERVICE)) {
                        Log.i(TAG, "UUID_BATTERY_SERVICE FOUND");
                        List<BluetoothGattCharacteristic> lc = s.getCharacteristics();
                        for (BluetoothGattCharacteristic c:lc) {
                            if (c.getUuid().equals(UUID_BATTERY_LEVEL_CHARACTERISTICS)) {
                                Log.i(TAG, "UUID_BATTERY_LEVEL_CHARACTERISTICS FOUND");
                                batteryLevelCaracteristic = c;
                            }
                        }
                    }
                }
                if (glucoseMeasureCaracteristic != null &&
                        glucoseFeatureCaracteristic != null &&
                        recordAccessControlPointCaracteristic != null) {
                    // N.B.!: The BT requests are asyncronous, we must wait the end of each operation
                    // (onXxxxxxxx callback) before to start a new request
                    setCharacteristicNotification(glucoseMeasureCaracteristic, true);
                } else {
                    deviceListener.notifyError(DeviceListener.DEVICE_DATA_ERROR, ResourceManager.getResource().getString("EDataReadError"));
                    stop();
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                      int status) {
            Log.d(TAG, "onDescriptorWrite:" + descriptor.getCharacteristic().getUuid().toString() +
                    " - " + descriptor.getUuid().toString());
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (descriptor.getUuid().equals(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG))) {
                    Log.d(TAG, "onDescriptorWrite: Notification  = " + ((descriptor.getValue()[0] & 0x01)!=0));
                    Log.d(TAG, "onDescriptorWrite: Indication  = " + ((descriptor.getValue()[0] & 0x02)!=0));
                    if (descriptor.getCharacteristic().getUuid().equals(UUID_GLUCOSE_MEASURE_CHARACTERISTICS))
                        if (glucoseMeasureContextCaracteristic != null)
                            // if glucoseMeasureContextCaracteristic is supported enable also this notification
                            // the value of glucoseFeatureCaracteristic is read after the
                            setCharacteristicNotification(glucoseMeasureContextCaracteristic, true);
                        else
                            // else read the value of glucoseFeatureCaracteristic
                            mBluetoothGatt.readCharacteristic(glucoseFeatureCaracteristic);
                    else if (descriptor.getCharacteristic().getUuid().equals(UUID_GLUCOSE_MEASURE_CONTEXT_CHARACTERISTICS))
                        mBluetoothGatt.readCharacteristic(glucoseFeatureCaracteristic);
                    else if (descriptor.getCharacteristic().getUuid().equals(UUID_RECORD_ACCESS_CONTROL_POINT)) {
                        // TODO
                        Log.d(TAG, "onDescriptorWrite: request last record");
                        recordAccessControlPointCaracteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                        byte[] data = new byte[2];
                        data[0] = 0x04; // Report Nr of Stored records
                        data[1] = 0x01; // All records
                        recordAccessControlPointCaracteristic.setValue(data);
                        if (!mBluetoothGatt.writeCharacteristic(recordAccessControlPointCaracteristic)) {
                            Log.d("ERROR", "write characteristic recordAccessControlPointCaracteristic failed");
                        }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (UUID_GLUCOSE_FEATURE_CHARACTERISTICS.equals(characteristic.getUuid())) {
                    Log.d(TAG, "onCharacteristicRead: feature=" + toHexString(characteristic.getValue()));
                    if (parseGlucoseFeatureCharacteristic(characteristic.getValue()))
                        if (batteryLevelCaracteristic != null)
                            mBluetoothGatt.readCharacteristic(batteryLevelCaracteristic);
                        else {
                            doMeasure();
                        }
                } else if (UUID_BATTERY_LEVEL_CHARACTERISTICS.equals(characteristic.getUuid())) {
                    Log.d(TAG, "onCharacteristicRead: battery=" + toHexString(characteristic.getValue()));
                    if (parseBatteryLevelCharacteristic(characteristic.getValue()))
                        doMeasure();
                }
            } else {
                Log.e(TAG, "Characteristic read Error: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (UUID_GLUCOSE_MEASURE_CHARACTERISTICS.equals(characteristic.getUuid())) {
                Log.d(TAG, "onCharacteristicChanged: glucose=" + toHexString(characteristic.getValue()));
                parseGlucoseMeasurementCharacteristic(characteristic);
            } else if (UUID_GLUCOSE_MEASURE_CONTEXT_CHARACTERISTICS.equals(characteristic.getUuid())) {
                Log.d(TAG, "onCharacteristicChanged: context=" + toHexString(characteristic.getValue()));
                parseGlucoseContextCharacteristic(characteristic.getValue());
            } else if (UUID_RECORD_ACCESS_CONTROL_POINT.equals(characteristic.getUuid())) {
                Log.d(TAG, "onCharacteristicChanged: RACP =" + toHexString(characteristic.getValue()));
                byte[] value =  characteristic.getValue();
                if (value.length==4 && value[0]==0x05 && value[1]==0x00 && value[2]>0) {
                    byte[] data = new byte[2];
                    data[0] = 0x01; // Report Stored records
                    data[1] = 0x06; // Last record
                    recordAccessControlPointCaracteristic.setValue(data);
                    if (!mBluetoothGatt.writeCharacteristic(recordAccessControlPointCaracteristic)) {
                        Log.d("ERROR", "write characteristic recordAccessControlPointCaracteristic failed");
                    }
                } else {
                    String message = ResourceManager.getResource().getString("KNoNewMeasure");
                    deviceListener.notifyError(DeviceListener.MEASUREMENT_ERROR,message);
                    stop();
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "onCharacteristicWrite: status = " + status + " char = " + characteristic.toString());
        }

    };

    private void doMeasure() {
        if (iCmdCode == TCmd.ECmdConnByUser)
            deviceListener.setBtMAC(iBtDevAddr);
        if (operationType == OperationType.Pair) {
            deviceListener.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
            stop();
        } else {
            deviceListener.notifyToUi(ResourceManager.getResource().getString("DoMeasureGL"));
            setCharacteristicIndication(recordAccessControlPointCaracteristic, true);
        }
    }

    /*
        Glucose Feature characteristic bit mapping (16 bit)
        0  Low Battery Detection During Measurement Supported
        1  Sensor Malfunction Detection Supported
        2  Sensor Sample Size Supported
        3  Sensor Strip Insertion Error Detection Supported
        4  Sensor Strip Type Error Detection Supported
        5  Sensor Result High-Low Detection Supported
        6  Sensor Temperature High-Low Detection Supported
        7  Sensor Read Interrupt Detection Supported
        8  General Device Fault Supported
        9  Time Fault Supported
        10 Multiple Bond Supported
        11-15 Reserved for futureuse
     */
    private boolean parseGlucoseFeatureCharacteristic(byte[] value) {
        if (value.length != 2) {
            Log.e(TAG, "parseGlucoseFeatureCharacteristic: wrong characteristic size: " + value.length);
            deviceListener.notifyError(DeviceListener.DEVICE_DATA_ERROR, ResourceManager.getResource().getString("EDataReadError"));
            stop();
            return false;
        }

        lowBatteryDetection = (value[0] & 0x01) != 0;
        Log.d(TAG,"lowBatteryDetection = " + lowBatteryDetection);
        sensorMalfunctionDetection = (value[0] & 0x02) != 0;
        Log.d(TAG,"sensorMalfunctionDetection = " + sensorMalfunctionDetection);
        sensorSampleSizeSupported = (value[0] & 0x04) != 0;
        Log.d(TAG,"sensorSampleSizeSupported = " + sensorSampleSizeSupported);
        stripInsertionErrorDetection = (value[0] & 0x08) != 0;
        Log.d(TAG,"stripInsertionErrorDetection = " + stripInsertionErrorDetection);
        stripTypeErrorDetection = (value[0] & 0x10) != 0;
        Log.d(TAG,"stripTypeErrorDetection = " + stripTypeErrorDetection);
        resultHighLowDetection = (value[0] & 0x20) != 0;
        Log.d(TAG,"resultHighLowDetection = " + resultHighLowDetection);
        temperatureHighLowDetection = (value[0] & 0x40) != 0;
        Log.d(TAG,"temperatureHighLowDetection = " + temperatureHighLowDetection);
        readInterruptDetection = (value[0] & 0x80) != 0;
        Log.d(TAG,"readInterruptDetection = " + readInterruptDetection);
        generalDeviceFault = (value[1] & 0x01) != 0;
        Log.d(TAG,"generalDeviceFault = " + generalDeviceFault);
        timeFault = (value[1] & 0x02) != 0;
        Log.d(TAG,"timeFault = " + timeFault);
        boolean multipleBond = (value[1] & 0x04) != 0;
        Log.d(TAG,"multipleBond = " + multipleBond);
        return true;
    }

    /*
     one unsigned byte min 0, max 100
     */
    private boolean parseBatteryLevelCharacteristic(byte[] value) {
        if (value.length != 1) {
            Log.e(TAG, "parseBatteryLevelCharacteristic: wrong characteristic size: " + value.length);
            deviceListener.notifyError(DeviceListener.DEVICE_DATA_ERROR, ResourceManager.getResource().getString("EDataReadError"));
            stop();
            return false;
        }
        batteryLevel = value[0];
        Log.d(TAG,"batteryLevel = " + batteryLevel);
        return true;
    }

    /*
    Order LSB -> MSB

    +Flags (1 byte) (Mandatory) (1=true, 0=false)
        0 Time Offset Present
        1 Glucose Concentration, Type and Sample Location Present
        2 Glucose Concentration Units (0=kg/L 1=mol/L)
        3 Sensor Status Annunciation Present
        4 Context Information Follows
        5-7 ReservedForFutureUse

    +Sequence Number (16 bit unsigned integer) (Mandatory)

    +Base Time (7 bytes) (Mandatory)
        year (2 bytes)   (0 anno sconosciuto, min 1582 max 9999)
        month (1 byte)   (0 mese sconosciuto, 1-12 Gen-Dic)
        day (1 byte)     (0 giorno sconosciuto, min 1 max 31)
        hour (1 byte)    (min 0, max 23)
        minutes (1 byte) (min 0, max 59)
        seconds (1 byte) (min 0, max 59)

    +Time Offset (signed 16-bit integer)  (mandatory if Time Offset Present = 1)
                <!--<Enumerations>
                    <Enumeration key="32767" value="Overrun" />
                    <Enumeration key="32768" value="Underrun" />
                    <Enumeration_Range start="0" end="32766" name="Time offset in minutes" />
                    <Enumeration_Range start="32769" end="65535" name="Time offset in minutes" />
                </Enumerations>-->

    +Glucose Concentration - units of kg/L (IEEE-11073 16-bit SFLOAT) (Glucose Concentration, Type and Sample Location Present = 1, Glucose Concentration Units = 0)

    +Glucose Concentration - units of mol/L (IEEE-11073 16-bit SFLOAT) (Glucose Concentration, Type and Sample Location Present = 1, Glucose Concentration Units = 1)

    +Type and Sample location (1 byte) (Glucose Concentration, Type and Sample Location Present = 1)
        bits 0-3 type
            0="Reserved for future use"
            1="Capillary Whole blood"
            2="Capillary Plasma"
            3="Venous Whole blood"
            4="Venous Plasma"
            5="Arterial Whole blood"
            6="Arterial Plasma"
            7="Undetermined Whole blood"
            8="Undetermined Plasma"
            9="Interstitial Fluid (ISF)"
            10="Control Solution"
            11-15 = "ReservedForFutureUse"
        bits 4-7 sample location
            0 = "Reserved for future use"
            1 = "Finger"
            2 = "Alternate Site Test (AST)"
            3 = "Earlobe"
            4 = "Control solution"
            15 = "Sample Location value not available"
            5-14 = "ReservedForFutureUse"

    +Sensor Status Annunciation (2 bytes) (Sensor Status Annunciation Present = 1)
        0 Device battery low at time of measurement
        1 Sensor malfunction or faulting at time of measurement
        2 Sample size for blood or control solution insufficient at time of measurement
        3 Strip insertion error
        4 Strip type incorrect for device
        5 Sensor result higher than the device can process
        6 Sensor result lower than the device can process
        7 Sensor temperature too high for valid test/result at time of measurement
        8 Sensor temperature too low for valid test/result at time of measurement
        9 Sensor read interrupted because strip was pulled too soon at time of measurement
        10 General device fault has occurred in the sensor
        11 Time fault has occurred in the sensor and time may be inaccurate
        12-15 Reserved For Future Use
    */
    private void parseGlucoseMeasurementCharacteristic(BluetoothGattCharacteristic c) {
        byte[] value = c.getValue();
        if (value.length < 10) {
            Log.e(TAG, "parseGlucoseMeasurementCharacteristic: wrong characteristic size: " + value.length);
            deviceListener.notifyError(DeviceListener.DEVICE_DATA_ERROR, ResourceManager.getResource().getString("EDataReadError"));
            stop();
            return;
        }

        boolean timeOffsetPresent = (value[0] & 0x01) != 0;
        Log.d(TAG, "timeOffsetPresent = " + timeOffsetPresent);
        boolean GlucoseConcentrationPresent = (value[0] & 0x02) != 0;
        Log.d(TAG, "GlucoseConcentrationPresent = " + GlucoseConcentrationPresent);
        if ((value[0] & 0x04) != 0) {
            conversionFactor = 18000.; // from mol/L to mg/dL
            Log.d(TAG, "conversionFactor mol/L = " + conversionFactor);
        } else {
            Log.d(TAG, "conversionFactor Kg/L = " + conversionFactor);
        }
        boolean sensorStatusPresent = (value[0] & 0x08) != 0;
        Log.d(TAG, "sensorStatusPresent = " + sensorStatusPresent);
        boolean contextInformationFollows = (value[0] & 0x10) != 0;
        Log.d(TAG, "contextInformationFollows = " + contextInformationFollows);

        //int seqNr = (value[1] & 0xff) + ((value[2] & 0xff) << 8);

        int year = (value[3] & 0xff) + ((value[4] & 0xff) << 8);
        int month = (value[5] & 0xff);
        int day = (value[6] & 0xff);
        int hour = (value[7] & 0xff);
        int minutes = (value[8] & 0xff);
        int seconds = (value[9] & 0xff);
        Log.d(TAG, "Time = " + day + "/" + month + "/" + year + "-" + hour + ":" + minutes + ":" + seconds);

        int timeoffset = 0;

        int index = 10;
        if (timeOffsetPresent) {
            short l = value[index++];
            short h = value[index++];
            timeoffset = (short) ((h << 8) | l);
            Log.d(TAG, "timeoffset = " + timeoffset);
        }

        if (GlucoseConcentrationPresent) {
            Float val = c.getFloatValue(BluetoothGattCharacteristic.FORMAT_SFLOAT, index);
            Log.d(TAG, "Glucose val = " + val + " - " + index);
            index+=2;
            /*
            int tmp = (value[index++] & 0xff) + (short) ((value[index++] & 0xff) << 8);
            double val = Util.parseSFLOATtoDouble((short) tmp);
            */
            Log.d(TAG, "Glucose val = " + val);
            measureVal = (int) (val * conversionFactor);
            Log.d(TAG, "Converted val mg/dL= " + measureVal);
        }

        if (GlucoseConcentrationPresent) {
            int sampleType = value[index] & 0x0f;
            Log.d(TAG, "GsampleType = " + sampleType);
            int sampleLocation = (value[index++] & 0xf0) >> 4;
            Log.d(TAG, "sampleLocation = " + sampleLocation);
        }

        if (sensorStatusPresent) {
            if (lowBatteryDetection) {
                lowBattery = (value[index] & 0x01) != 0;
                Log.d(TAG, "lowBattery = " + lowBattery);
            }
            if (sensorMalfunctionDetection) {
                sensorMalfunction = (value[index] & 0x02) != 0;
                Log.d(TAG, "sensorMalfunction = " + sensorMalfunction);
            }
            if (sensorSampleSizeSupported) {
                sampleSizeError = (value[index] & 0x04) != 0;
                Log.d(TAG, "sampleSizeError = " + sampleSizeError);
            }
            if (stripInsertionErrorDetection) {
                stripInsertionError = (value[index] & 0x08) != 0;
                Log.d(TAG, "stripInsertionError = " + stripInsertionError);
            }
            if (stripTypeErrorDetection) {
                stripTypeError = (value[index] & 0x10) != 0;
                Log.d(TAG, "stripTypeError = " + stripTypeError);
            }
            if (resultHighLowDetection) {
                resultHigherError = (value[index] & 0x20) != 0;
                resultLowerError = (value[index] & 0x40) != 0;
                Log.d(TAG, "resultHigherError = " + resultHigherError);
                Log.d(TAG, "resultLowerError = " + resultHigherError);
            }
            if (temperatureHighLowDetection) {
                temperatureHighError = (value[index] & 0x80) != 0;
                temperatureLowError = (value[index + 1] & 0x01) != 0;
                Log.d(TAG, "temperatureHighError = " + temperatureHighError);
                Log.d(TAG, "temperatureLowError = " + temperatureLowError);
            }
            if (readInterruptDetection) {
                readInterruptError = (value[index + 1] & 0x02) != 0;
                Log.d(TAG, "readInterruptError = " + readInterruptError);
            }
            if (generalDeviceFault) {
                generalDeviceFaultError = (value[index + 1] & 0x04) != 0;
                Log.d(TAG, "generalDeviceFaultError = " + generalDeviceFaultError);
            }
            if (timeFault) {
                timeFaultError = (value[index + 1] & 0x08) != 0;
                Log.d(TAG, "timeFaultError = " + timeFaultError);
            }
        }

        if (year == 0 || month == 0 || day == 0 ) {
            timeFaultError = true;
            Log.d(TAG, "Date is Unknown: timeFaultError = true");
        } else {
            measureDate = Calendar.getInstance();
            measureDate.set(year, month, day, hour, minutes, seconds);
            if (timeoffset != 0)
                measureDate.add(Calendar.MINUTE, timeoffset);
            Log.d(TAG, "Measure timestamp = " + measureDate.toString());
        }

        if (!contextInformationFollows)
            askMeasure();
    }

    // use only meal information for PrePrandial/PostPrandial flag
    // All the other information are not managed
    private void parseGlucoseContextCharacteristic(byte[] value) {
        Log.d(TAG, "parseGlucoseContextCharacteristic");
        boolean CarbohydrateIDPresent = (value[0] & 0x01) != 0;
        boolean mealPresent = (value[0] & 0x02) != 0;
        boolean extendedFlags = (value[0] & 0x80) != 0;

        int index = 3;
        if (extendedFlags)
            index++;
        if (CarbohydrateIDPresent)
            index += 3;
        if (mealPresent) {
            int meal = value[index] & 0xff;
            switch (meal) {
                case 1:
                    prePrandial = true;
                case 2:
                    prePrandial = false;
                default:
                    prePrandial = null;
            }
        }
        askMeasure();
    }

    private void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enable) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBluetoothGatt.writeDescriptor(descriptor);
        mBluetoothGatt.setCharacteristicNotification(characteristic, enable);
    }

    private void setCharacteristicIndication(BluetoothGattCharacteristic characteristic, boolean enable) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
        mBluetoothGatt.writeDescriptor(descriptor);
        mBluetoothGatt.setCharacteristicNotification(characteristic, enable);
    }
}