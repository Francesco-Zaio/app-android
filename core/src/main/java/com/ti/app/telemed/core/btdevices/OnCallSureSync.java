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

import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Vector;

import static android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE;


public class OnCallSureSync extends DeviceHandler implements BTSearcherEventListener {

	private static final String TAG = "OnCallSureSync";

	// Standard Glucose Services


    // Standard Battery Services
    private static String BATTERY_SERVICE = "0000180f-0000-1000-8000-00805f9b34fb";
    private final static UUID UUID_BATTERY_SERVICE =  UUID.fromString(BATTERY_SERVICE);
    private static String BATTERY_LEVEL_CHARACTERISTIC = "00002a19-0000-1000-8000-00805f9b34fb";
    private final static UUID UUID_BATTERY_LEVEL_CHARACTERISTICS = UUID.fromString(BATTERY_LEVEL_CHARACTERISTIC);


    // Custom Bluetooth Service
    private static String MAIN_SERVICE = "11223344-5566-7788-99aa-bbccddeeff00";
    private final static UUID UUID_MAIN_SERVICE =  UUID.fromString(MAIN_SERVICE);
    private static String MAIN_CHARACTERISTIC = "00004a5b-0000-1000-8000-00805f9b34fb";
    private final static UUID UUID_MAIN_CHARACTERISTICS = UUID.fromString(MAIN_CHARACTERISTIC);

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

    private BluetoothGattCharacteristic mainCaracteristic = null;
    private BluetoothGattCharacteristic batteryLevelCaracteristic = null;

    private int batteryLevel = -1;

    private Calendar measureDate = null;
    private int measureVal = -1;
    private Boolean prePrandial = null;


    private static final String MSG_HEADER = "&DZ ";
    private static final char REC_SEP = 0x1e;
    private static final char FIELD_SEP = 0x20;
    private static final char EOM = 0x0d;
    private static final char CRC_DEL = 0x06;

    private byte[] message;


    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    public OnCallSureSync(DeviceListener listener, UserDevice ud) {
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

        if (batteryLevel < 20) {
            error = true;
            message = ResourceManager.getResource().getString("lowBatteryError");
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

    private void askMeasures() {
        // TODO
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
                    if (s.getUuid().equals(UUID_MAIN_SERVICE)) {
                        Log.i(TAG, "UUID_MAIN_SERVICE FOUND");
                        List<BluetoothGattCharacteristic> lc = s.getCharacteristics();
                        for (BluetoothGattCharacteristic c:lc) {
                            if (c.getUuid().equals(UUID_MAIN_CHARACTERISTICS)) {
                                Log.i(TAG, "UUID_MAIN_CHARACTERISTICS FOUND");
                                mainCaracteristic = c;
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
                if (mainCaracteristic != null && batteryLevelCaracteristic !=null) {
                    // N.B.!: The BT requests are asyncronous, we must wait the end of each operation
                    // (onXxxxxxxx callback) before to start a new request
                    mBluetoothGatt.readCharacteristic(batteryLevelCaracteristic);
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
                    if (descriptor.getCharacteristic().getUuid().equals(UUID_MAIN_CHARACTERISTICS))
                        ; // TODO
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (UUID_BATTERY_LEVEL_CHARACTERISTICS.equals(characteristic.getUuid())) {
                    Log.d(TAG, "onCharacteristicRead: battery=" + toHexString(characteristic.getValue()));
                    if (parseBatteryLevelCharacteristic(characteristic.getValue())) {
                        if (iCmdCode == TCmd.ECmdConnByUser)
                            deviceListener.setBtMAC(iBtDevAddr);
                        if (operationType == OperationType.Pair) {
                            deviceListener.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
                            stop();
                        } else {
                            deviceListener.notifyToUi(ResourceManager.getResource().getString("DoMeasureGL"));
                            setCharacteristicNotification(mainCaracteristic, true);
                        }
                    } else {
                        deviceListener.notifyError(DeviceListener.DEVICE_DATA_ERROR, ResourceManager.getResource().getString("EDataReadError"));
                        stop();
                    }
                }
            } else {
                Log.e(TAG, "Characteristic read Error: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (UUID_MAIN_CHARACTERISTICS.equals(characteristic.getUuid())) {
                Log.d(TAG, "onCharacteristicChanged: glucose=" + toHexString(characteristic.getValue()));
                appendToMessage(characteristic.getValue());
                if (message.length > 0 && message[message.length-1] == EOM)
                {
                    Log.d(TAG, "Message received" + toHexString(message));
                    parseMessage(message);
                    message = null;
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "onCharacteristicWrite: status = " + status + " char = " + characteristic.toString());
        }

    };

    private void appendToMessage(byte[] data) {
        if (message == null)
        {
            message = data;
            return;
        }
        byte[] oldByteArray = message;
        byte[] newByteArray = new byte[data.length + oldByteArray.length];
        // Copy original array
        System.arraycopy(oldByteArray, 0, newByteArray, 0, oldByteArray.length);
        // Copy data
        System.arraycopy(data, 0, newByteArray, oldByteArray.length, data.length);
        message = newByteArray;
    }

    /*
     one unsigned byte min 0, max 100
     */
    private boolean parseBatteryLevelCharacteristic(byte[] value) {
        if (value.length != 1) {
            Log.e(TAG, "parseBatteryLevelCharacteristic: wrong characteristic size: " + value.length);
            return false;
        }
        batteryLevel = value[0];
        Log.d(TAG,"batteryLevel = " + batteryLevel);
        return true;
    }

    /*
    Order LSB -> MSB
    */
    private void parseMessage(byte[] data) {
        String str = new String(data, Charset.forName("UTF-8"));

        if (!str.startsWith(MSG_HEADER)) {
            Log.e(TAG, "parseMessage: wrong characteristic size: " + data.length);
            deviceListener.notifyError(DeviceListener.DEVICE_DATA_ERROR, ResourceManager.getResource().getString("EDataReadError"));
            stop();
            return ;
        }

        // remove header
        str = str.substring(MSG_HEADER.length());

        // empty data
        if (str.startsWith("y"))
        {
            Log.e(TAG, "No measures found ");
            deviceListener.notifyError(DeviceListener.DEVICE_DATA_ERROR, ResourceManager.getResource().getString("ENoMeasuresFound"));
            stop();
            return ;
        }

        String dataCount = str.substring(0, str.indexOf(' '));
        int count = Integer.valueOf(dataCount);
        Log.d(TAG, "total count: " + dataCount);

        // remove count
        str = str.substring(dataCount.length() + 1).trim();

        // remove crc
        str = str.substring(0, str.indexOf(CRC_DEL)).trim();

        String[] dataArray = str.split(Character.toString(REC_SEP));
        for (String s:dataArray) {
            // TODO Parse each record
        }

        // askMeasure();
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