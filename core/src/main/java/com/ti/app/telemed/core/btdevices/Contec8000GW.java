package com.ti.app.telemed.core.btdevices;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.BTSearcher;
import com.ti.app.telemed.core.btmodule.BTSocket;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.btmodule.BTSearcherEventListener;
import com.ti.app.telemed.core.btmodule.BTSocketEventListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.devicesactivities.Contec8000GWActivity;

import java.io.IOException;
import java.util.Vector;


public class Contec8000GW extends DeviceHandler implements  BTSearcherEventListener, BTSocketEventListener {

    public static final String CONTEC8000GW_BROADCAST_EVENT = "com.ti.app.telemed.core.btdevices.Contec8000GW";
    public static final String BT_ADDRESS = "BT_ADDRESS";
    public static final String MEASURE_OBJECT = "MEASURE_OBJECT";

    public static final String RESULT = "RESULT";
    public static final int RESULT_OK = 0;
    public static final int RESULT_ERROR = 1;
    public static final int RESULT_ABORT = 2;

    private static final String TAG = "Contec8000GW";

    // iServiceSearcher searches for service this client can
    // connect to (in symbian version the type was CBTUtil) and
    // substitutes RSocketServ and RSocket of symbian version too
    private BTSearcher iServiceSearcher;
    // iCGBloodPressureSocket is an RSocket in the Symbian version
    private BTSocket deviceSocket;

    private boolean serverOpenFailed;
    private Vector<BluetoothDevice> deviceList;
    private int currentPos;
    private BluetoothDevice selectedDevice;
    private boolean deviceSearchCompleted;


    public static boolean needPairing(UserDevice userDevice) {
        return true;
    }

    public static boolean needConfig(UserDevice userDevice) {
        return false;
    }

    public Contec8000GW (DeviceListener listener, UserDevice ud) {
        super(listener, ud);

        deviceSearchCompleted = false;
        serverOpenFailed = false;
        iServiceSearcher = new BTSearcher();
        deviceSocket = BTSocket.getBTSocket();
    }

    // DeviceHandler interface Methods

    @Override
    public void confirmDialog() {
    }
    @Override
    public void cancelDialog(){
    }

    @Override
    public boolean startOperation(OperationType ot, BTSearcherEventListener btSearchListener) {
        if (!startInit(ot, btSearchListener))
            return false;

        Log.d(TAG,"startOperation: iBtDevAddr="+iBtDevAddr + " iCmdCode="+iCmdCode.toString());
        iServiceSearcher.clearBTSearcherEventListener();
        iServiceSearcher.addBTSearcherEventListener(this);
        if (iCmdCode == TCmd.ECmdConnByUser)
            iServiceSearcher.addBTSearcherEventListener(btSearchListener);
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
        Log.d(TAG, "selectDevice: addr=" + bd.getAddress());
        iServiceSearcher.stopSearchDevices();
        iState = TState.EGettingService;
        selectedDevice = bd;
        iBtDevAddr = selectedDevice.getAddress();
        runBTSearcher();
    }


    // methods of BTSearcherEventListener interface

    @Override
    public void deviceDiscovered(Vector<BluetoothDevice> devList) {
        deviceList = devList;
        // we recall runBTSearcher, because every time we find a device, we have
        // to check if it is the device we want
        runBTSearcher();
    }

    @Override
    public void deviceSearchCompleted() {
        deviceSearchCompleted = true;
        currentPos = 0;
    }


    private void connectToDevice() throws IOException {
        Log.i(TAG, "connectToDevice");
        deviceSocket.addBTSocketEventListener(this);
        deviceSocket.connect(selectedDevice);
    }

    private void runBTSearcher() {
        switch (iState) {
            case EGettingDevice:
                switch (iCmdCode) {
                    case ECmdConnByAddr:
                        String tmpBtDevAddr;
                        tmpBtDevAddr = deviceList.elementAt(currentPos).getAddress();
                        if (tmpBtDevAddr.equalsIgnoreCase(iBtDevAddr)) {
                            selectDevice(deviceList.elementAt(currentPos));
                        } else {
                            if (deviceSearchCompleted) {
                                deviceSearchCompleted = false;
                                if (currentPos <= deviceList.size() - 1) {
                                    currentPos++;
                                } else {
                                    iServiceSearcher.startSearchDevices();
                                }
                            } else {
                                if (currentPos <= deviceList.size() - 1) {
                                    currentPos++;
                                }
                            }
                        }
                        break;
                    case ECmdConnByUser:
                        // the selection done by user is managed in the ui class which
                        // implements BTSearcherEventListener interface, so here arrive
                        // when the selection is already done
                        deviceListener.notifyWaitToUi(ResourceManager.getResource().getString("KConnectingDev"));
                        break;
                }
                break;
            case EGettingService:
                iState = TState.EGettingConnection;
                try {
                    if (operationType == OperationType.Pair)
                        connectToDevice();
                    else {
                        startActivity();
                    }
                } catch (IOException e) {
                    deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
                }
                break;
        }
    }

    private void startActivity() {
        Measure m = getMeasure();
        Intent intent = new Intent( MyApp.getContext(), Contec8000GWActivity.class );
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(MEASURE_OBJECT, m);
        intent.putExtra(BT_ADDRESS, iBtDevAddr);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(CONTEC8000GW_BROADCAST_EVENT);
        MyApp.getContext().registerReceiver(receiver,intentFilter);
        MyApp.getContext().startActivity(intent);
    }

    private void runBTSocket() {
        switch(iState) {
            case EGettingConnection:
                Log.i(TAG, "EGettingConnection in runBTSocket");
                if (operationType == OperationType.Pair) {
                    deviceListener.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
                    deviceListener.setBtMAC(iBtDevAddr);
                }
                stop();
                break;
            case EDisconnecting:
                Log.i(TAG, "EDisconnecting in runBTSocket");
                iState = TState.EWaitingToGetDevice;
                deviceSocket.removeBTSocketEventListener(this);
                deviceSocket.close();
                reset();
                break;
        }
    }

    private void reset() {
        iState = TState.EWaitingToGetDevice;
        deviceSearchCompleted = false;
        serverOpenFailed = false;
        iBtDevAddr = null;
    }

    private void stop() {
        try {
            MyApp.getContext().unregisterReceiver(receiver);
        } catch (IllegalArgumentException e){
            // ignore exception
        }
        iServiceSearcher.stopSearchDevices();
        iServiceSearcher.clearBTSearcherEventListener();
        iServiceSearcher.close();
        if (!serverOpenFailed) {
            deviceSocket.close();
            deviceSocket.removeBTSocketEventListener(this);
        }
        reset();
    }




    // Methods of BTSocketEventListener interface

    @Override
    public void errorThrown(int type, String description) {
        Log.e(TAG, "writeErrorThrown " + type + " " + description);
        switch (type) {
            case 0: //thread interrupted
            case 4: //bluetooth close error
                reset();
                deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR,ResourceManager.getResource().getString("ECommunicationError"));
                break;
            case 1: //bluetooth open error
                serverOpenFailed = true;
                deviceListener.notifyError(DeviceListener.CONNECTION_ERROR,ResourceManager.getResource().getString("EBtDeviceConnError"));
                break;
            case 2: //bluetooth read error
            case 3: //bluetooth write error
                iState = TState.EDisconnecting;
                runBTSocket();
                reset();
                deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR,ResourceManager.getResource().getString("ECommunicationError"));
                break;
        }
    }

    @Override
    public void openDone() {
        runBTSocket();
    }

    @Override
    public void readDone() {
        runBTSocket();
    }

    @Override
    public void writeDone() {
        runBTSocket();
    }


    private final BroadcastReceiver receiver = new BroadcastReceiver () {
        @Override
        public void onReceive(Context context, Intent intent) {
            int result = intent.getExtras().getInt(RESULT);
            switch (result) {
                case RESULT_ABORT:
                    reset();
                    deviceListener.notifyError(DeviceListener.NO_MEASURES_FOUND, ResourceManager.getResource().getString("KNoNewMeasure"));
                    break;
                case RESULT_ERROR:
                    reset();
                    deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
                    break;
                case RESULT_OK:
                    Measure iMeasure = (Measure) intent.getExtras().getSerializable(MEASURE_OBJECT);
                    deviceListener.showMeasurementResults(iMeasure);
                    break;
            }
        }
    };

}
