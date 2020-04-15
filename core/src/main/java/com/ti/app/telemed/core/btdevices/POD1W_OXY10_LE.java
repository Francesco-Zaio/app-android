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
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
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
import com.ti.app.telemed.core.xmlmodule.XmlManager;

import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;


public class POD1W_OXY10_LE extends DeviceHandler implements BTSearcherEventListener {

	private static final String TAG = "POD1W_OXY10_LE";

    // Handler messages
    private static final int MSG_DEVICE_ERROR = 0x13;
    private static final int MSG_DISCONN = 0x22;
    private static final int MSG_OXY = 0x23;

    private static final int[] battValues = {10,40,70,100};


    private static final UUID UUID_SERVICE_DATA_OXY10            = UUID.fromString("0000FFF0-0000-1000-8000-00805F9B34FB");
    private static final UUID UUID_CHARACTER_WRITE_OXY10         = UUID.fromString("0000FFF2-0000-1000-8000-00805F9B34FB");
    private static final UUID UUID_CHARACTER_READ_OXY10		  = UUID.fromString("0000FFF1-0000-1000-8000-00805F9B34FB");
    private static final UUID UUID_SERVICE_DATA_POD            = UUID.fromString("0000FFB0-0000-1000-8000-00805F9B34FB");
    private static final UUID UUID_CHARACTER_WRITE_POD         = UUID.fromString("0000FFB2-0000-1000-8000-00805F9B34FB");
    private static final UUID UUID_CHARACTER_READ_POD		  = UUID.fromString("0000FFB1-0000-1000-8000-00805F9B34FB");
    // Client Characteristic Configuration
    private static final String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

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


    public POD1W_OXY10_LE(DeviceListener listener, UserDevice ud) {
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
        // wait some time (end of BT scan) before to connect
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!connect(iBtDevAddr)) {
                    deviceListener.notifyError(DeviceListener.CONNECTION_ERROR,ResourceManager.getResource().getString("EBtDeviceConnError"));
                    stop();
                }
            }
        }, 200);
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
                //if (d.getType() == DEVICE_TYPE_LE) {
                    int i;
                    for (i = 0; i < deviceList.size(); i++)
                        if (deviceList.elementAt(i).getAddress().equals(d.getAddress()))
                            break;
                    if (i >= deviceList.size()) {
                        deviceList.addElement(d);
                        if (iBTSearchListener != null)
                            iBTSearchListener.deviceDiscovered(deviceList);
                    }
                //}
                break;
        }
    }

    @Override
    public void deviceSearchCompleted() {
        if (mBluetoothDeviceAddress == null) {
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
        // parametro TRANSPORT_LE necessario per OXY10 altrimenti non si connette
        Log.d(TAG, "Trying to create a new connection LE.");
        mBluetoothGatt = device.connectGatt(MyApp.getContext(), true, mGattCallback,TRANSPORT_LE);

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
                    if (UUID_SERVICE_DATA_OXY10.equals(s.getUuid()) ||
                            UUID_SERVICE_DATA_POD.equals(s.getUuid())) {
                        Log.d(TAG, "UUID_SERVICE_DATA FOUND");
                        List<BluetoothGattCharacteristic> lc = s.getCharacteristics();
                        for (BluetoothGattCharacteristic c:lc) {
                            Log.d(TAG, "Char UUID:" + s.getUuid());
                            if (c.getUuid().equals(UUID_CHARACTER_WRITE_OXY10) ||
                                    c.getUuid().equals(UUID_CHARACTER_WRITE_POD)) {
                                Log.d(TAG, "UUID_CHARACTER_WRITE FOUND");
                                writeCharacteristic = c;
                            } else if (c.getUuid().equals(UUID_CHARACTER_READ_OXY10) ||
                                    c.getUuid().equals(UUID_CHARACTER_READ_POD)) {
                                Log.d(TAG, "UUID_CHARACTER_READ FOUND");
                                readCharacteristic = c;
                            }
                        }
                    }
                }
                if (writeCharacteristic != null && readCharacteristic != null ) {
                    // N.B.!: The BT requests are asyncronous, we must wait the end of each operation
                    // (onDescriptorWrite callback) before to start a new request
                    enableCharacteristicNotification(readCharacteristic);
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
                if (descriptor.getCharacteristic().getUuid().equals(UUID_CHARACTER_READ_OXY10))
                    doMeasure();
                else if (descriptor.getCharacteristic().getUuid().equals(UUID_CHARACTER_READ_POD))
                    enableCharacteristicNotification(writeCharacteristic);
                else if (descriptor.getCharacteristic().getUuid().equals(UUID_CHARACTER_WRITE_POD))
                    doMeasure();
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
            return POD1W_OXY10_LE.this.read(buffer);
        }
        @Override
        public void close() {
        }
        @Override
        public void clean() {
            POD1W_OXY10_LE.this.clean();
        }
        @Override
        public int available() {
            return POD1W_OXY10_LE.this.available();
        }
    }
    public class SenderBLE implements Isender{
        @Override
        public void send(byte[] d) {
            POD1W_OXY10_LE.this.write(d);
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
        private final WeakReference<POD1W_OXY10_LE> mOuter;

        private MyHandler(POD1W_OXY10_LE outer) {
            mOuter = new WeakReference<>(outer);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            POD1W_OXY10_LE outer = mOuter.get();
            Bundle d;
            switch (msg.what) {
                case MSG_OXY:
                    outer.deviceListener.notifyToUi(ResourceManager.getResource().getString("KMeasuring"));
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
            initOxyData();
            deviceListener.notifyToUi(ResourceManager.getResource().getString("DoMeasureOS"));
            mFingerOximeter = new FingerOximeter(new ReaderBLE(), new SenderBLE(), new FingerOximeterCallBack());
            mFingerOximeter.Start();
            mFingerOximeter.SetWaveAction(false);
        }
    }

    private void enableCharacteristicNotification(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBluetoothGatt.writeDescriptor(descriptor);
        mBluetoothGatt.setCharacteristicNotification(characteristic, true);
    }


    private static final int MAX_SAMPLES = 300; // = 5 min, 1 measure every sec
    private static final int MIN_SAMPLES = 6;
    private static final int BASE_OXY_STREAM_LENGTH = 189; //

    private static class OxyElem {
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

        oxyQueue = new Vector<>(MAX_SAMPLES);
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

        // aSpO2 or aHR equals to 0 means invalid values
        if ((aSpO2 != 0) && (aHR != 0)) {
            numSamples++;
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

        if (!fingerIn || (numSamples >= MAX_SAMPLES)) {
            makeOxyResultData();
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

    private static class Time {
        private int hh;
        private int mm;
        private int ss;

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

    /*
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
    */
}