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
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.pod1w.base.BaseDate;
import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.BTSearcher;
import com.ti.app.telemed.core.btmodule.BTSearcherEventListener;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.UserDevice;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingQueue;

import com.pod1w.base.Ireader;
import com.pod1w.base.Isender;
import com.pod1w.FingerOximeter.FingerOximeter;
import com.pod1w.FingerOximeter.IFingerOximeterCallBack;
import com.ti.app.telemed.core.util.GWConst;

import static android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE;


public class POD1W_LE extends DeviceHandler implements BTSearcherEventListener {

	private static final String TAG = "POD1W_LE";

    // Handler messages
    private static final int MSG_DEVICE_ERROR = 0x13;
    private static final int MSG_DISCONN = 0x22;
    private static final int MSG_OXY = 0x23;

    private static final int[] battValues = {10,40,70,100};


    private static final UUID UUID_SERVICE_DATA            = UUID.fromString("0000FFB0-0000-1000-8000-00805F9B34FB");
    private static final UUID UUID_CHARACTER_WRITE         = UUID.fromString("0000FFB2-0000-1000-8000-00805F9B34FB");
    private static final UUID UUID_CHARACTER_READ		  = UUID.fromString("0000FFB1-0000-1000-8000-00805F9B34FB");
    // Client Characteristic Configuration
    private static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    private static final int MAX_CONNCTION_RETRY = 5;

    private BTSearcher iServiceSearcher;
    private Vector<BluetoothDevice> deviceList = new Vector<>();
    private int iBattery = 0;

    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothGatt mBluetoothGatt = null;
    private String mBluetoothDeviceAddress = null;
    private boolean stopped = false;
    private int connectionRetry = 0;

    private BluetoothGattCharacteristic readCharacteristic = null;
    private BluetoothGattCharacteristic writeCharacteristic = null;
    private FingerOximeter mFingerOximeter;
    

    public POD1W_LE(DeviceListener listener, UserDevice ud) {
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
        mFingerOximeter = null;
        mBluetoothGatt = null;
    }

    public void stop() {
        Log.d(TAG, "stop");
        iServiceSearcher.stopSearchDevices();
        iServiceSearcher.clearBTSearcherEventListener();
        iServiceSearcher.close();

        disconnect();
        reset();
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

        if (mFingerOximeter != null)
            mFingerOximeter.Stop();
        mFingerOximeter = null;

        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
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
                Log.i(TAG, "found: " + services.size() + " services");
                for (BluetoothGattService s:services) {
                    Log.d(TAG, "Service: " + s.getUuid().toString());
                    if (s.getUuid().equals(UUID_SERVICE_DATA)) {
                        Log.d(TAG, "UUID_SERVICE_DATA FOUND");
                        List<BluetoothGattCharacteristic> lc = s.getCharacteristics();
                        for (BluetoothGattCharacteristic c:lc) {
                            if (c.getUuid().equals(UUID_CHARACTER_WRITE)) {
                                Log.d(TAG, "UUID_CHARACTER_WRITE FOUND");
                                writeCharacteristic = c;
                            } else if(c.getUuid().equals(UUID_CHARACTER_READ)) {
                                Log.d(TAG, "UUID_CHARACTER_READ FOUND");
                                readCharacteristic = c;
                            }
                        }
                    }
                }
                if (writeCharacteristic != null && readCharacteristic != null ) {
                    // N.B.!: The BT requests are asyncronous, we must wait the end of each operation
                    // (onDescriptorWrite callback) before to start a new request
                    setCharacteristicNotification(readCharacteristic, true);
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
            //Log.d(TAG, "onDescriptorWrite:" + descriptor.getCharacteristic().getUuid().toString() + " - " + descriptor.getUuid().toString());
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (descriptor.getUuid().equals(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG))) {
                    if (descriptor.getCharacteristic().getUuid().equals(UUID_CHARACTER_READ))
                        setCharacteristicNotification(writeCharacteristic, true);
                    else if (descriptor.getCharacteristic().getUuid().equals(UUID_CHARACTER_WRITE))
                        doMeasure();
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic,
                                         int status) {
            //Log.d(TAG, "onCharacteristicRead: status = " + status + " char = " + characteristic.toString());
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            mBuffer.add(characteristic.getValue());
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            //Log.d(TAG, "onCharacteristicWrite: status = " + status + " char = " + characteristic.toString());
        }
    };

    public class ReaderBLE implements Ireader{
        @Override
        public int read(byte[] buffer) {
            return POD1W_LE.this.read(buffer);
        }
        @Override
        public void close() {
        }
        @Override
        public void clean() {
            POD1W_LE.this.clean();
        }
        @Override
        public int available() {
            return POD1W_LE.this.available();
        }
    }
    public class SenderBLE implements Isender{
        @Override
        public void send(byte[] d) {
            POD1W_LE.this.write(d);
        }
        @Override
        public void close() {
        }
    }


    private final static int TRANSFER_PACKAGE_SIZE = 10;
    private LinkedBlockingQueue<byte[]> mBuffer = new LinkedBlockingQueue<>();

    private void write(byte[] bytes)
    {
        int byteOffset = 0;
        while(bytes.length - byteOffset > TRANSFER_PACKAGE_SIZE)
        {
            byte[] b = new byte[TRANSFER_PACKAGE_SIZE];
            System.arraycopy(bytes,byteOffset,b,0, TRANSFER_PACKAGE_SIZE);
            writeCharacteristic.setValue(b);
            mBluetoothGatt.writeCharacteristic(writeCharacteristic);
            byteOffset += TRANSFER_PACKAGE_SIZE;
        }
        if(bytes.length - byteOffset != 0)
        {
            byte[] b = new byte[bytes.length - byteOffset];
            System.arraycopy(bytes,byteOffset,b,0,bytes.length - byteOffset);
            writeCharacteristic.setValue(b);
            mBluetoothGatt.writeCharacteristic(writeCharacteristic);
        }
    }

    private int read(byte[] dataBuffer){
        if(mBuffer.size()>0){
            byte[] temp = mBuffer.poll();
            if(temp!=null && temp.length>0){
                int len = Math.min(temp.length, dataBuffer.length);
                System.arraycopy(temp, 0, dataBuffer, 0, len);
                return len;
            }
        }
        return 0;
    }

    private void clean() {
        if(mBuffer!=null){
            mBuffer.clear();
        }
    }

    private int available() {
        if(mBuffer!=null){
            return mBuffer.size();
        }
        return 0;
    }

    private final MyHandler devOpHandler = new MyHandler(this);

    private static class MyHandler extends Handler {
        private final WeakReference<POD1W_LE> mOuter;

        private MyHandler(POD1W_LE outer) {
            mOuter = new WeakReference<>(outer);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            POD1W_LE outer = mOuter.get();
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
        public void OnGetSpO2Wave(List<BaseDate.Wave> waves) {
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

    private void doMeasure() {
        if (iCmdCode == TCmd.ECmdConnByUser)
            deviceListener.setBtMAC(iBtDevAddr);
        if (operationType == OperationType.Pair) {
            deviceListener.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
            stop();
        } else {
            deviceListener.notifyToUi(ResourceManager.getResource().getString("DoMeasureOS"));
            mFingerOximeter = new FingerOximeter(new ReaderBLE(), new SenderBLE(), new FingerOximeterCallBack());
            mFingerOximeter.Start();
            mFingerOximeter.SetWaveAction(false);
        }
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

    // L'ossimetro OXY10 invia un campione al secondo e solo 10 campioni dopo di che smette di
    // inviare anche se il paziente continua la misurazione.
    // E' stata presa la decisione di considerare solo l'ultimo valore ricevuto dall'Ossimetro
    private void oxySample(Bundle b) {
        int aSpO2 = b.getInt("nSpO2");
        int aHR   = b.getInt("nPR");
        iSpO2Med = aSpO2;
        iHRMed   = aHR;

        boolean fingerIn = b.getBoolean("nStatus");
        float mVolt = b.getFloat("nPower");
        int pLevel = b.getInt("powerLevel");
        if(mVolt != 0){
            // 2.2V=0%  3.0v=100%
            iBattery = (int)(125f*mVolt - 275f);
            if (iBattery < 0)
                iBattery = 0;
            else if (iBattery > 100)
                iBattery = 100;
        } else {
            iBattery = battValues[pLevel];
        }
        Log.d(TAG, "oxySample: aSpO2="+aSpO2+ " aHR="+aHR+" fingerIn="+fingerIn+" mVolt="+mVolt+" pLevel="+pLevel+" iBattery="+iBattery);

//        if (!fingerIn || numSamples >= 10) {
          if (!fingerIn) {
            makeOxyResultData();
        }
    }

    private void makeOxyResultData() {
        HashMap<String,String> tmpVal = new HashMap<>();
        tmpVal.put(GWConst.EGwCode_07, String.valueOf((int)iSpO2Med));  // O2 Med
        tmpVal.put(GWConst.EGwCode_0F, String.valueOf((int)iHRMed));   // HR Med
        tmpVal.put(GWConst.EGwCode_BATTERY, Integer.toString(iBattery)); // livello batteria

        Measure m = getMeasure();
        m.setMeasures(tmpVal);
        deviceListener.showMeasurementResults(m);
        stop();

    }
}