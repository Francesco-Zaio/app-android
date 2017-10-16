package com.ti.app.telemed.core.btdevices;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.util.Log;

import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.btmodule.events.BTSearcherEvent;
import com.ti.app.telemed.core.btmodule.events.BTSearcherEventListener;
import com.ti.app.telemed.core.common.ECGDrawData;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import static com.ti.app.telemed.core.btmodule.DeviceListener.MEASUREMENT_ERROR;


public class ECGTest extends Handler implements DeviceHandler {

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
    private BTSearcherEventListener scanActivityListener;
    private TState iState;
    String iBTAddress;

    private static final String TAG = "ECGTest";

    private static final int BASELINE = 2048;
    private static final int MAXVAL = 4095;
    private static final int NLEAD = 12;
    private static final String[] LABELS = new String[]{"I", "II", "III", "aVR", "aVL", "aVF", "V1", "V2", "V3", "V3", "V4", "V5", "V6"};
    private static final int SAMPLING_RATE = 300; // Hz
    private static final int BULK_SAMPLES = 25;
    private static final int NUM_SAMPLES = SAMPLING_RATE*10; // num samples for 10 sec measure
    private static final int KTimeOut = BULK_SAMPLES * 1000 / SAMPLING_RATE; // samples send timeout msec
    private double x = 0;
    private Timer timer;

    public static boolean needPairing(UserDevice userDevice) {
        return false;
    }

    public static boolean needConfig(UserDevice userDevice) {
        return false;
    }

    public ECGTest(DeviceListener aScheduler) {
        Log.d(TAG, "IHealth");
        iState = TState.EWaitingToGetDevice;
        deviceListener = aScheduler;
        deviceList = new Vector<>();
    }

    // methods of DeviceHandler interface

    @Override
    public void confirmDialog() {
        // Not used for this device
    }

    @Override
    public void cancelDialog(){
        // Not used for this device
    }

    @Override
    public void start(OperationType ot, UserDevice ud, BTSearcherEventListener btSearchListener) {
        if (iState == TState.EWaitingToGetDevice && ud != null) {
            iState = TState.EGettingMeasures;
            scanActivityListener = btSearchListener;
            iBTAddress = ud.getBtAddress();

            Measure m = new Measure();
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
            m.setBtAddress(iBTAddress);

            if (iBTAddress == null || iBTAddress.isEmpty()) {
                deviceList.add(BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:00:00:00:00:00"));
                scanActivityListener.deviceDiscovered(new BTSearcherEvent(this), deviceList);
                scanActivityListener.deviceSearchCompleted(new BTSearcherEvent(this));
            }
            thread.start();
        }
    }

    @Override
    public void stopDeviceOperation(int selected) {
        Log.d(TAG, "stopDeviceOperation: selected=" + selected);

        // selected == -1 Normal end of operation
        // selected == -2 Operation interrupted
        // selected >= 0 the user has selected the device at the 'selected' position in the list of discovered devices
        if (selected < 0) {
            thread.interrupt();
            resetTimer();
            stop();
        }  else {
            synchronized (thread) {
                thread.notify();
            }
        }
    }


    private final Thread thread = new Thread() {
        @Override
        public void run() {
            try {
                synchronized (thread) {
                    if (iBTAddress == null || iBTAddress.isEmpty()) {
                        thread.wait();
                    }

                    deviceListener.notifyWaitToUi(ResourceManager.getResource().getString("KConnectingDev"));
                    sleep(1000);
                    deviceListener.notifyToUi(ResourceManager.getResource().getString("DoMeasureECG"));
                    ECGDrawData.baseline = BASELINE;
                    ECGDrawData.maxVal = MAXVAL;
                    ECGDrawData.nLead = NLEAD;
                    ECGDrawData.samplingRate = SAMPLING_RATE;
                    ECGDrawData.lables = LABELS;
                    ECGDrawData.progress = 0;
                    sleep(1000);
                    deviceListener.startEcgDraw();
                    x = 0;
                    sleep(1000);
                    scheduleTimer();
                    thread.wait();
                    sleep(1000);
                    notifyError(MEASUREMENT_ERROR, "Errore di Misurazione");
                }
            } catch (InterruptedException e) {
                return;
            }
        }
    };

    private class TimerExpired extends TimerTask {
        @Override
        public void run(){
            List <int[]> l = new ArrayList<>();
            double rad;
            for (int i=0; i<BULK_SAMPLES; i++) {
                int[] lead = new int[ECGDrawData.nLead];
                rad = x * Math.PI / 160d;
                x++;
                int y = (int)((Math.sin(rad)+1) * (double)(ECGDrawData.maxVal) / 2d);
                for (int j=0; j<ECGDrawData.nLead; j++)
                    lead[j] = y;
                l.add(lead);
            }
            ECGDrawData.addData(l);
            if (x >= NUM_SAMPLES) {
                resetTimer();
                synchronized (thread) {
                    thread.notify();
                }
            }
            //else
            //    scheduleTimer();
        }
    }

    private void scheduleTimer() {
        resetTimer();
        TimerExpired timerExpired = new TimerExpired();
        timer = new Timer();
        //timer.schedule(timerExpired, KTimeOut);
        timer.scheduleAtFixedRate(timerExpired, KTimeOut, KTimeOut);
    }

    private void resetTimer() {
        if (timer!=null) {
            timer.cancel();
        }
    }


    public void stop() {
        Log.d(TAG, "stop");
        // we advise the scheduler of the end of the activity on the device
        deviceListener.operationCompleted();
        deviceList.clear();
    }

    void notifyError(String errorCode, String errorMessage) {
        deviceListener.notifyError(errorCode, errorMessage);
        stop();
    }

    void notifyEndMeasurement(Measure m) {
        stop();
        deviceListener.showMeasurementResults(m);
    }
}
