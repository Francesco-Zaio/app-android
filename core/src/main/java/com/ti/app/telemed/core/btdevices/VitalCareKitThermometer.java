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

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.Vector;

import static android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE;


public class VitalCareKitThermometer extends DeviceHandler implements BTSearcherEventListener {

	private static final String TAG = "VitalCareKitThermometer";

    // Custom Bluetooth Service
    private static final String MAIN_SERVICE = "0000ffe0-0000-1000-8000-00805f9b34fb";
    private final static UUID UUID_MAIN_SERVICE =  UUID.fromString(MAIN_SERVICE);
    private static final String NOTIFY_CHARACTERISTIC = "0000ffe1-0000-1000-8000-00805f9b34fb";
    private final static UUID UUID_NOTIFY_CHARACTERISTICS = UUID.fromString(NOTIFY_CHARACTERISTIC);
    private static final String WRITE_CHARACTERISTIC = "0000ffe2-0000-1000-8000-00805f9b34fb";
    private final static UUID UUID_WRITE_CHARACTERISTICS = UUID.fromString(WRITE_CHARACTERISTIC);

    // Client Characteristic Configuration
    private static final String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    private static final byte[] EQIPMENT_STATUS_QUERY_COMMAND = {(byte)0xaa, (byte)0x01, (byte)0xd5, (byte)0x00, (byte)0xd4};

    private static final int MAX_CONNCTION_RETRY = 5;

    private final BTSearcher iServiceSearcher;
    private final Vector<BluetoothDevice> deviceList = new Vector<>();

    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothGatt mBluetoothGatt = null;
    private String mBluetoothDeviceAddress = null;
    private boolean stopped = false;
    private int connectionRetry = 0;

    private BluetoothGattCharacteristic notifyCharacteristic = null;
    private BluetoothGattCharacteristic writeCharacteristic = null;

    private int batteryLevel = -1;

    public VitalCareKitThermometer(DeviceListener listener, UserDevice ud) {
        super(listener, ud);
        iServiceSearcher = new BTSearcher();
    }

    // methods of DeviceHandler interface

    @Override
    public void confirmDialog() {
    }

    @Override
    public void cancelDialog() {
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
        if (ot == OperationType.Measure)
            deviceListener.notifyToUi(ResourceManager.getResource().getString("DoMeasureGL"));
        else
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

    private String formatTemp(int temperature) {
        temperature += 5; // La temperatura è in 1/100 di grado: arrotondo alla prima cifra decimale
        String temp = String.valueOf(temperature);
        return temp.substring(0,2)+","+temp.substring(2,3);
    }

    private void makeResultData(int temperature) {

        Measure m = getMeasure();
        HashMap<String,String> tmpVal = new HashMap<>();
        tmpVal.put(GWConst.EGwCode_0R, formatTemp(temperature));
        if (batteryLevel != -1)
            tmpVal.put(GWConst.EGwCode_BATTERY, Integer.toString(batteryLevel)); // livello batteria
        m.setTimestamp(Util.getTimestamp(null));
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
            return mBluetoothGatt.connect();
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
        return true;
    }

    private void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            return;
        }
        //if (mConnectionState != ConnectionState.DISCONNECTED)
        //    mBluetoothGatt.disconnect();
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG,"onConnectionStateChange: status="+status+" newState="+newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectionRetry = 0;
                Log.i(TAG, "Connected to GATT server.");
                // Discover services after successful connection.
                mBluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
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
                Log.d(TAG, "found: " + services.size() + " services");
                for (BluetoothGattService s:services) {
                    Log.d(TAG, "Service: " + s.getUuid().toString());
                    if (s.getUuid().equals(UUID_MAIN_SERVICE)) {
                        Log.d(TAG, "UUID_MAIN_SERVICE FOUND");
                        List<BluetoothGattCharacteristic> lc = s.getCharacteristics();
                        for (BluetoothGattCharacteristic c:lc) {
                            if (c.getUuid().equals(UUID_NOTIFY_CHARACTERISTICS)) {
                                Log.d(TAG, "UUID_NOTIFY_CHARACTERISTICS FOUND");
                                notifyCharacteristic = c;
                            } else if (c.getUuid().equals(UUID_WRITE_CHARACTERISTICS)) {
                                Log.d(TAG, "UUID_WRITE_CHARACTERISTICS FOUND");
                                writeCharacteristic = c;
                            }
                        }
                    }
                }
                if (notifyCharacteristic != null && writeCharacteristic != null) {
                    // N.B.!: The BT requests are asyncronous, we must wait the end of each operation
                    // (onXxxxxxxx callback) before to start a new request
                    if (operationType == OperationType.Pair) {
                        deviceListener.setBtMAC(iBtDevAddr);
                        deviceListener.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
                        stop();
                    } else {
                        if (iCmdCode == TCmd.ECmdConnByUser)
                            deviceListener.setBtMAC(iBtDevAddr);
                        setCharacteristicNotification(notifyCharacteristic, true);
                    }
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
                    " status= " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (descriptor.getUuid().equals(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG))) {
                    if (descriptor.getCharacteristic().getUuid().equals(UUID_NOTIFY_CHARACTERISTICS)) {
                        deviceListener.notifyToUi(ResourceManager.getResource().getString("DoMeasureTC"));

                        Log.d(TAG, "onDescriptorWrite: Query device status");
                        writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                        writeCharacteristic.setValue(EQIPMENT_STATUS_QUERY_COMMAND);
                        if (!mBluetoothGatt.writeCharacteristic(writeCharacteristic)) {
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
                Log.d(TAG, "Characteristic read OK: " + status);
            } else {
                Log.e(TAG, "Characteristic read Error: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (UUID_NOTIFY_CHARACTERISTICS.equals(characteristic.getUuid())) {
                Log.d(TAG, "onCharacteristicChanged: data = " + toHexString(characteristic.getValue()));
                parseMessage(characteristic.getValue());
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "onCharacteristicWrite: status = " + status + " char = " + characteristic.toString());
        }
    };


    /*
    Order LSB -> MSB
    */
    private void parseMessage(byte[] data) {
        if (getXor(data) != data[data.length-1]) {
            Log.e(TAG, "parseMessage: CRC error: ");
            deviceListener.notifyError(DeviceListener.DEVICE_DATA_ERROR, ResourceManager.getResource().getString("EDataReadError"));
            stop();
        } else if (data[2] == (byte)0xc5) {
            Log.e(TAG, "parseMessage: Device status: " + " 0x" + Integer.toHexString(data[2]  & 0x000000ff) );
            /* TODO
                Al momento ignoro il valore della batteria. Da specifiche dovrebbe essere un valore da 1 a 10.
                In realtà il valore letto è 0xA0 con pile nuove e 0x80 con pile parzialmente usate.
            int tmp = data[5] & 0xff;
            Log.d(TAG,"tmp = "+ tmp);
            if ((tmp > 0) && (tmp <= 10)) {
                batteryLevel = tmp * 10;
                Log.d(TAG,"batteryLevel = "+ batteryLevel);
            }
            */
        } else if (data[2] == (byte)0xc1) {
            int temperature = ((data[4] & 0xff) << 8) + (data[5] & 0xff);
            Log.d(TAG, "parseMessage: Temperatura: " + temperature);
            makeResultData(temperature);
        } else {
            Log.e(TAG, "parseMessage: Measure error: " + " 0x" + Integer.toHexString(data[2]  & 0x000000ff) );
            deviceListener.notifyError(DeviceListener.MEASURE_PROCEDURE_ERROR, ResourceManager.getResource().getString("KWrongMeasure"));
            stop();
        }
    }

    public static byte getXor(byte[] data) {
        byte temp = data[1];
        for (int i = 2; i < data.length-1; i++) {
            temp ^= data[i];
        }
        return temp;
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