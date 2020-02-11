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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Vector;

import static android.bluetooth.BluetoothDevice.BOND_BONDED;
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

    private static final String MEASURE_HEADER = "&DZ ";
    private static final char REC_SEP = 0x1e;
    private static final char EOM = 0x0d;
    private static final char CRC_DEL = 0x06;

    private static final String SET_DATE_HEADER = "&FC "; // Questo comando non funzione (return code = 133)
    private static final String METER_ID_HEADER = "&DB ";
    private static final String UPLOAD_LOG_HEADER  = "&DZ 2 ";
    private static final String AUTOSEND_STOP  = "&D1 1 ";

    private byte[] message;

    private ArrayList<GlucoseMeasure> measures = new ArrayList<>();
    private int currentMeasure;

    private boolean bonded = false;


    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    public enum DeviceMeasurementType
    {
        None,
        AfterMeal,
        BeforeMeal,
        Mark,
        MarkControl
    }

    private class GlucoseMeasure {
        String value;
        Date timestamp;
        Boolean pre;
    };

    public OnCallSureSync(DeviceListener listener, UserDevice ud) {
        super(listener, ud);
        iServiceSearcher = new BTSearcher();
    }

    // methods of DeviceHandler interface

    @Override
    public void confirmDialog() {
        if (currentMeasure < measures.size()) {
            measures.get(currentMeasure).pre = false;
            currentMeasure++;
            askMeasure();
        }
    }

    @Override
    public void cancelDialog() {
        measures.get(currentMeasure).pre = true;
        currentMeasure++;
        askMeasure();
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

    private void askMeasure() {
        String message;

        if (batteryLevel < 20) {
            message = ResourceManager.getResource().getString("lowBatteryError");
            deviceListener.notifyError(DeviceListener.MEASUREMENT_ERROR,message);
            stop();
            return;
        }

        while ((currentMeasure < measures.size()) && (measures.get(currentMeasure).pre != null))
            currentMeasure ++;

        if (currentMeasure < measures.size()) {
            String date = new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(measures.get(currentMeasure).timestamp);
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(measures.get(currentMeasure).timestamp);

            message = ResourceManager.getResource().getString("KPrePostMsg").concat("\n\n");

            message = message.concat(ResourceManager.getResource().getString("KDate")).concat(": ");
            message = message.concat(date).concat("\n");
            message = message.concat(ResourceManager.getResource().getString("KTime")).concat(": ");
            message = message.concat(time).concat("\n");

            message = message.concat(ResourceManager.getResource().getString("Glycemia")).concat(": ");
            message = message.concat(measures.get(currentMeasure).value).concat(" ");
            message = message.concat(ResourceManager.getResource().getString("GlycemiaUnit"));

            deviceListener.askSomething(message,
                    ResourceManager.getResource().getString("MeasureGlyPOSTBtn"),
                    ResourceManager.getResource().getString("MeasureGlyPREBtn"));
        } else
            makeResultData();
    }
    
    private void makeResultData() {
        ArrayList<Measure> mList = new ArrayList<>();
        for (GlucoseMeasure gm: measures) {
            Measure m = getMeasure();
            HashMap<String,String> tmpVal = new HashMap<>();
            if (gm.pre) {
                tmpVal.put(GWConst.EGwCode_0E, gm.value);  // glicemia Pre-prandiale
            } else {
                tmpVal.put(GWConst.EGwCode_0T, gm.value);  // glicemia Post-prandiale
            }
            if (batteryLevel != -1)
                tmpVal.put(GWConst.EGwCode_BATTERY, Integer.toString(batteryLevel)); // livello batteria
            Calendar c = Calendar.getInstance();
            c.setTime(gm.timestamp);
            m.setTimestamp(Util.getTimestamp(c));
            m.setMeasures(tmpVal);
            m.setFailed(false);
            mList.add(m);
        }
        deviceListener.showMeasurementResults(mList);
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
        if (device.getBondState() == BOND_BONDED)
            bonded = true;
        else
            bonded = false;
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
                    if (bonded) {
                        if (operationType == OperationType.Pair) {
                            deviceListener.setBtMAC(iBtDevAddr);
                            deviceListener.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
                            stop();
                            return;
                        } else {
                            mBluetoothGatt.readCharacteristic(batteryLevelCaracteristic);
                        }
                    } else {
                        deviceListener.notifyToUi(ResourceManager.getResource().getString("KPairingMsg"));
                        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
                        MyApp.getContext().registerReceiver(mReceiver, filter);
                        mBluetoothGatt.readCharacteristic(batteryLevelCaracteristic);
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
                    if (descriptor.getCharacteristic().getUuid().equals(UUID_MAIN_CHARACTERISTICS)) {
                        /*
                        // TODO
                        String  message = UPLOAD_LOG_HEADER + CRC_DEL + crc(UPLOAD_LOG_HEADER.toCharArray()) + EOM;
                        try {
                            Log.d(TAG, "Writing command Upload Log: " + toHexString(message.getBytes("UTF8")));
                            mainCaracteristic.setValue(message.getBytes("UTF8"));
                            mBluetoothGatt.writeCharacteristic(mainCaracteristic);
                        } catch (UnsupportedEncodingException e) {
                            Log.e(TAG, "onDescriptorWrite: " + e);
                        }
                        */
                    }
                }
            }
        }

        /*
        L'impostazione della data/ora non funziona.
        Calendar c = Calendar.getInstance();
        int year = Calendar.getInstance().get(Calendar.YEAR) % 100;
        int day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
        int month = Calendar.getInstance().get(Calendar.MONTH);
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int minutes = Calendar.getInstance().get(Calendar.MINUTE);
        String data = SET_DATE_HEADER +
                year + " " +
                month + " " +
                day + " " +
                hour + " " +
                minutes + " ";
        String  message = data + CRC_DEL + crc(data.toCharArray()) + EOM;
        */

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (UUID_BATTERY_LEVEL_CHARACTERISTICS.equals(characteristic.getUuid())) {
                    Log.d(TAG, "onCharacteristicRead: battery=" + toHexString(characteristic.getValue()));
                    if (parseBatteryLevelCharacteristic(characteristic.getValue())) {
                        if (operationType != OperationType.Pair && bonded) {
                            setCharacteristicNotification(mainCaracteristic, true);
                            deviceListener.notifyToUi(ResourceManager.getResource().getString("DoMeasureGL"));
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
                Log.d(TAG, "onCharacteristicChanged: data = " + toHexString(characteristic.getValue()));
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

        if (!str.startsWith(MEASURE_HEADER)) {
            Log.e(TAG, "parseMessage: wrong characteristic size: " + data.length);
            deviceListener.notifyError(DeviceListener.DEVICE_DATA_ERROR, ResourceManager.getResource().getString("EDataReadError"));
            stop();
            return ;
        }

        // Invio ack per indicare che le misure sono state ricevute.
        // Se non si invia l'ack:
        // 1) le misure vengano ritrasmesse ogni 2 secondi per 3 volte
        // 2) alla prossima misurazione vengono inviate anche le misure precedentemente già ricevute.
        String  reply = AUTOSEND_STOP + CRC_DEL + crc(AUTOSEND_STOP.toCharArray()) + EOM;
        try {
            Log.d(TAG, "Writing command Autosend stop: " + toHexString(reply.getBytes("UTF8")));
            mainCaracteristic.setValue(reply.getBytes("UTF8"));
            mBluetoothGatt.writeCharacteristic(mainCaracteristic);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "onDescriptorWrite: " + e);
        }

        // remove header
        str = str.substring(MEASURE_HEADER.length());

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

        // Le misure sono in ordine cronologico inverso, dalla più recente alla più vecchia
        // quindi l'array measures contiene alla posizione 0 la misura più recente.
        String[] dataArray = str.split(Character.toString(REC_SEP));
        for (String s:dataArray) {
            GlucoseMeasure m = parseMeasure(s);
            if (m != null)
                measures.add(m);
        }
        currentMeasure = 0;
        askMeasure();
    }

    private GlucoseMeasure parseMeasure(String rawData) {
        try {
            String[] values = rawData.split(" ");
            if (values.length < 3) return null;
            DateFormat df = new SimpleDateFormat("MMddyyHHmm",Locale.US);
            Date d = df.parse(values[0]);
            DeviceMeasurementType mark = ParseMark(values[2]);

            GlucoseMeasure m = new GlucoseMeasure();
            m.timestamp = d;
            if (mark == DeviceMeasurementType.AfterMeal)
                m.pre = false;
            else if (mark == DeviceMeasurementType.BeforeMeal)
                m.pre = true;
            else
                m.pre = null;
            m.value = values[1];
            return m;
        } catch (ParseException pe) {
            // TODO
            Log.e(TAG,"parseMeasure: " + pe);
        }
        return null;
    }

    private DeviceMeasurementType ParseMark(String mark) {
        if (mark.length() <= 0) { return DeviceMeasurementType.None; }
        if (mark.charAt(0) == '0') { return DeviceMeasurementType.None; }
        if (mark.charAt(0) == '1' && mark.length() > 1 && mark.charAt(1) != '2') { return DeviceMeasurementType.AfterMeal; }
        if (mark.charAt(0) == '2') { return DeviceMeasurementType.BeforeMeal; }
        if (mark.charAt(0) == '4') { return DeviceMeasurementType.Mark; }
        if (mark.charAt(0) == '1' && mark.length() > 1 && mark.charAt(1) == '2') { return DeviceMeasurementType.MarkControl; }
        return DeviceMeasurementType.None;
    }

    private static String crc(char[] buf)
    {
        int crc_calc = 0xffff;
        for (int i = 0; i < buf.length; i++) {
            crc_calc ^= (buf[i] & 0xff);
            for (int j = 8; j > 0; j--)
            {
                if ((crc_calc & 0x0001) == 1) { crc_calc = (crc_calc >>> 1) ^ 0xA001; }
                else { crc_calc >>>= 1; }
            }
            crc_calc = crc_calc & 0xffff;
        }
        return String.valueOf(crc_calc);
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

    private final BroadcastReceiver mReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            final String action = intent.getAction();

            if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
            {
                final BluetoothDevice dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                Log.d(TAG, "ACTION_BOND_STATE_CHANGED: address=" + dev.getAddress() + " state=" + state );
                if (!dev.getAddress().equalsIgnoreCase(iBtDevAddr))
                    return;
                Log.d(TAG, "ACTION_BOND_STATE_CHANGED: address=" + dev.getAddress() + " state=" + state );

                switch(state){
                    case BluetoothDevice.BOND_BONDING:
                        // Bonding...
                        Log.d(TAG, "ACTION_BOND_STATE_CHANGED: Bonding...");
                        break;
                    case BluetoothDevice.BOND_BONDED:
                        // Bonded...
                        Log.d(TAG, "ACTION_BOND_STATE_CHANGED: Bonded!");
                        MyApp.getContext().unregisterReceiver(mReceiver);
                        deviceListener.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
                        stop();
                        break;
                    case BluetoothDevice.BOND_NONE:
                        Log.d(TAG, "ACTION_BOND_STATE_CHANGED: NOT Bonded!");
                        MyApp.getContext().unregisterReceiver(mReceiver);
                        deviceListener.setBtMAC(iBtDevAddr);
                        deviceListener.notifyError(DeviceListener.DEVICE_DATA_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
                        stop();
                        break;
                    default:
                        Log.d(TAG, "ACTION_BOND_STATE_CHANGED: STATE UNKNOWN!");
                        MyApp.getContext().unregisterReceiver(mReceiver);
                        deviceListener.notifyError(DeviceListener.DEVICE_DATA_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
                        stop();
                        break;
                }
            }
        }
    };
}