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
import com.ti.app.telemed.core.btmodule.events.BTSearcherEvent;
import com.ti.app.telemed.core.btmodule.events.BTSearcherEventListener;
import com.ti.app.telemed.core.btmodule.events.BTSocketEvent;
import com.ti.app.telemed.core.btmodule.events.BTSocketEventListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.devicesactivities.Contec8000GWActivity;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.xmlmodule.XmlManager;


import java.io.IOException;
import java.util.Vector;


public class Contec8000GW extends BroadcastReceiver implements DeviceHandler, BTSearcherEventListener, BTSocketEventListener {

    public static final String CONTEC8000GW_BROADCAST_EVENT = "com.ti.app.telemed.core.btdevices.Contec8000GW";
    public static final String RESULT = "RESULT";
    public static final int RESULT_OK = 0;
    public static final int RESULT_ERROR = 1;
    public static final int RESULT_ABORT = 2;

    private static final String TAG = "Contec8000GW";

    private enum TState {
        EWaitingToGetDevice,
        EGettingDevice,
        EGettingService,
        EGettingConnection,
        EActivityRunning,
        EDisconnecting,
        EDisconnectingPairing,
    }

    // State of the active object
    private TState iState;
    // Type of search the scheduler requires
    private TCmd iCmdCode;

    // iServiceSearcher searches for service this client can
    // connect to (in symbian version the type was CBTUtil) and
    // substitutes RSocketServ and RSocket of symbian version too
    private BTSearcher iServiceSearcher;
    // iCGBloodPressureSocket is an RSocket in the Symbian version
    private BTSocket deviceSocket;

    // BT address obtained from scheduler (in symbian version the
    // type was TBTDevAddr)
    private String iBtDevAddr;

    // BT address of the found device (we can have not received bt address from
    // scheduler or it can be changed, so we take this value from the searcher
    // after the device finding)
    private String iBTAddress;
    private boolean iPairingMode;
    private Measure iMeasure;

    private boolean serverOpenFailed;

    private Vector<BluetoothDevice> deviceList;
    private int currentPos;
    private boolean deviceSearchCompleted;

    // a pointer to scheduler
    private DeviceListener iScheduler;

    public static boolean needPairing(UserDevice userDevice) {
        return true;
    }

    public static boolean needConfig(UserDevice userDevice) {
        return false;
    }

    public Contec8000GW (DeviceListener aScheduler) {
        iState = TState.EWaitingToGetDevice;
        iScheduler = aScheduler;
        deviceSearchCompleted = false;
        serverOpenFailed = false;
        iServiceSearcher = new BTSearcher();
        deviceSocket = BTSocket.getBTSocket();
    }

    private void connectToDevice() throws IOException {
        Log.i(TAG, "connectToDevice");
        iBTAddress = iServiceSearcher.getCurrBTDevice().getAddress();
        deviceSocket.addBTSocketEventListener(this);
        deviceSocket.connect(iServiceSearcher.getCurrBTDevice());
    }

    private void runBTSearcher() {
        switch (iState) {
            case EGettingDevice:
                switch (iCmdCode) {
                    case ECmdConnByAddr:
                        String tmpBtDevAddr;
                        tmpBtDevAddr = deviceList.elementAt(currentPos).getAddress();
                        if (tmpBtDevAddr.equals(iBtDevAddr)) {
                            iServiceSearcher.stopSearchDevices(currentPos);
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
                        iScheduler.notifyWaitToUi(ResourceManager.getResource().getString("KConnectingDev"));
                        break;
                }
                break;
            case EGettingService:
                iState = TState.EGettingConnection;
                try {
                    if (iPairingMode)
                        connectToDevice();
                    else {
                        startActivity();
                    }
                } catch (IOException e) {
                    iScheduler.notifyError(ResourceManager.getResource().getString("EBtDeviceConnError"),"");
                }
                break;
        }
    }

    private void startActivity() {
        Intent intent = new Intent( MyApp.getContext(), Contec8000GWActivity.class );
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(MEASURE_OBJECT, iMeasure);
        intent.putExtra(BT_ADDRESS, iBtDevAddr);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(CONTEC8000GW_BROADCAST_EVENT);
        MyApp.getContext().registerReceiver(this,intentFilter);
        MyApp.getContext().startActivity(intent);
    }

    private void runBTSocket() {
        switch(iState) {
            case EGettingConnection:
                Log.i(TAG, "EGettingConnection in runBTSocket");
                iState = TState.EDisconnectingPairing;
                stop();
                break;
            case EDisconnecting:
                Log.i(TAG, "EDisconnecting in runBTSocket");
                iState = TState.EWaitingToGetDevice;
                deviceSocket.removeBTSocketEventListener(this);
                deviceSocket.close();
                reset();
                break;
            case EDisconnectingPairing:
                Log.i(TAG, "EDisconnectingPairing in runBTSocket");
                //Pairing eseguito con successo. Salva il BT MAC
                deviceSocket.removeBTSocketEventListener(this);
                iScheduler.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
                iScheduler.setBtMAC(iBTAddress);
                currentPos = 0;
                break;
        }
    }


    // DeviceHandler interface Methods

    @Override
    public void confirmDialog() {
    }
    @Override
    public void cancelDialog(){
    }

    @Override
    public void start(OperationType ot, UserDevice ud, BTSearcherEventListener btSearchListener) {
        if (iState == TState.EWaitingToGetDevice) {
            iPairingMode = ot == OperationType.Pair;
            iBtDevAddr = ud.getBtAddress();

            iMeasure = new Measure();
            User u = UserManager.getUserManager().getCurrentUser();
            iMeasure.setMeasureType(ud.getMeasure());
            iMeasure.setDeviceDesc(ud.getDevice().getDescription());
            iMeasure.setTimestamp(XmlManager.getXmlManager().getTimestamp(null));
            iMeasure.setFile(null);
            iMeasure.setFileType(null);
            if (u != null) {
                iMeasure.setIdUser(u.getId());
                if (u.getIsPatient())
                    iMeasure.setIdPatient(u.getId());
            }
            iMeasure.setFailed(false);
            iMeasure.setBtAddress(iBTAddress);

            iServiceSearcher.clearBTSearcherEventListener();
            iServiceSearcher.addBTSearcherEventListener(this);
            if (iBtDevAddr != null && !iBtDevAddr.isEmpty()) {
                iCmdCode = TCmd.ECmdConnByAddr;
            } else {
                iCmdCode = TCmd.ECmdConnByUser;
                iServiceSearcher.addBTSearcherEventListener(btSearchListener);
            }
            iState = TState.EGettingDevice;
            iServiceSearcher.setSearchType(iCmdCode);
            // it launch the automatic procedures for the manual device search
            iServiceSearcher.startSearchDevices();
        }
    }

    @Override
    public void stopDeviceOperation(int selected) {
        if (selected == -1) {
            // L'utente ha premuto Annulla sulla finestra di dialogo
            stop();
            reset();
        } else if (selected == -2) {
            // L'utente ha premuto back o ha chiuso la lista dei device trovati sena selezionarne nessuno
            iServiceSearcher.stopSearchDevices(-1);
            iServiceSearcher.removeBTSearcherEventListener(this);
            if (iState != TState.EGettingDevice) {
                // we advise the scheduler of the end of the activity on the device
                iScheduler.operationCompleted();
            }
        } else {
            // >= 0 (device selezionato)
            iServiceSearcher.stopSearchDevices(selected);
        }
    }


    public void reset() {
        iState = TState.EWaitingToGetDevice;
        deviceSearchCompleted = false;
        serverOpenFailed = false;
        iBTAddress = null;
    }

    public void stop() {
        if (iState == TState.EGettingDevice) {
            iServiceSearcher.stopSearchDevices(-1);
            iServiceSearcher.close();
            // we advise the scheduler of the end of the activity on the device
            iScheduler.operationCompleted();
        } else if (iState == TState.EGettingService) {
            iServiceSearcher.close();
            // we advise the scheduler of the end of the activity on the device
            iScheduler.operationCompleted();
        } else if (iState == TState.EDisconnectingPairing) {
            deviceSocket.close();
            deviceSocket.removeBTSocketEventListener(this);
            runBTSocket();
            // we advise the scheduler of the end of the activity on the device
            iScheduler.operationCompleted();
        }  else {
            if (!serverOpenFailed) {
                // cancels all outstanding operations on socket
                deviceSocket.close();
                deviceSocket.removeBTSocketEventListener(this);
            }
            // we advise the scheduler of the end of the activity on the device
            iScheduler.operationCompleted();
        }
    }


    // methods of BTSearcherEventListener interface

    @Override
    public void deviceDiscovered(BTSearcherEvent evt,  Vector<BluetoothDevice> devList) {
        deviceList = devList;
        // we recall runBTSearcher, because every time we find a device, we have
        // to check if it is the device we want
        runBTSearcher();
    }

    @Override
    public void deviceSearchCompleted(BTSearcherEvent evt) {
        deviceSearchCompleted = true;
        currentPos = 0;
    }

    @Override
    public void deviceSelected(BTSearcherEvent evt) {
        Log.i(TAG, "NoninOximeter: deviceSelected");
        // we change status
        iState = TState.EGettingService;

        runBTSearcher();
    }


    // Methods of BTSocketEventListener interface

    @Override
    public void errorThrown(BTSocketEvent evt, int type, String description) {
        Log.e(TAG, "errorThrown " + type + " " + description);
        switch (type) {
            case 0: //thread interrupted
            case 4: //bluetooth close error
                reset();
                iScheduler.notifyError(description,"");
                break;
            case 1: //bluetooth open error
                serverOpenFailed = true;
            case 2: //bluetooth read error
            case 3: //bluetooth write error
                iState = TState.EDisconnecting;
                runBTSocket();
                reset();
                iScheduler.notifyError(ResourceManager.getResource().getString("ECommunicationError"),"");
                break;
        }
    }

    @Override
    public void openDone(BTSocketEvent evt) {
        runBTSocket();
    }

    @Override
    public void readDone(BTSocketEvent evt) {
        runBTSocket();
    }

    @Override
    public void writeDone(BTSocketEvent evt) {
        runBTSocket();
    }


    // BroadcastReceiver Method

    @Override
    public void onReceive(Context context, Intent intent) {
        int result = intent.getExtras().getInt(RESULT);
        switch (result) {
            case RESULT_ABORT:
                reset();
                iScheduler.notifyError(ResourceManager.getResource().getString("KNoNewMeasure"),"");
                break;
            case RESULT_ERROR:
                reset();
                iScheduler.notifyError(ResourceManager.getResource().getString("ECommunicationError"),"");
                break;
            case RESULT_OK:
                iMeasure = (Measure)intent.getExtras().getSerializable(MEASURE_OBJECT);
                iScheduler.showMeasurementResults(iMeasure);
                break;
        }
    }

}
