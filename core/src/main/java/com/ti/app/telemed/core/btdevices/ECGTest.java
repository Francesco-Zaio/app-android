package com.ti.app.telemed.core.btdevices;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.util.Log;

import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.btmodule.events.BTSearcherEventListener;
import com.ti.app.telemed.core.common.ECGDrawData;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.UserDevice;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import static com.ti.app.telemed.core.btmodule.DeviceListener.MEASUREMENT_ERROR;


public class ECGTest extends DeviceHandler {

    private Vector<BluetoothDevice> deviceList;

    private static final String TAG = "ECGTest";

    private static final int BASELINE = 2048;
    private static final int MAXVAL = 4095;
    private static final int NLEAD = 3;
    private static final String[] LABELS = new String[]{"I", "II", "III", "aVR", "aVL", "aVF", "V1", "V2", "V3", "V3", "V4", "V5", "V6"};
    private static final int SAMPLING_RATE = 150; // Hz
    private static final int BULK_SAMPLES = 25;
    private static final int NUM_SAMPLES = SAMPLING_RATE*10; // num samples for 10 sec measure
    private static final int KTimeOut = BULK_SAMPLES * 1000 / SAMPLING_RATE; // samples send timeout msec
    private int x = 0;
    private Timer timer;

    public static boolean needPairing(UserDevice userDevice) {
        return false;
    }

    public static boolean needConfig(UserDevice userDevice) {
        return false;
    }

    public ECGTest(DeviceListener listener, UserDevice ud) {
        super(listener, ud);
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
    public boolean startOperation(OperationType ot, BTSearcherEventListener btSearchListener) {
        if (!startInit(ot, btSearchListener))
            return false;

        Log.d(TAG,"startOperation: iBtDevAddr="+iBtDevAddr + " iCmdCode="+iCmdCode.toString());
        iState = TState.EGettingMeasures;

        if (iBtDevAddr == null || iBtDevAddr.isEmpty()) {
            deviceList.add(BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:00:00:00:00:00"));
            iBTSearchListener.deviceDiscovered(deviceList);
            iBTSearchListener.deviceSearchCompleted();
        }
        thread.start();

        return true;
    }


    @Override
    public void abortOperation() {
        Log.d(TAG, "abortOperation");
        thread.interrupt();
        resetTimer();
        stop();
    }

    @Override
    public void selectDevice(BluetoothDevice bd){
        Log.d(TAG, "selectDevice: addr=" + bd.getAddress());
        synchronized (thread) {
            thread.notify();
        }
    }

    private final Thread thread = new Thread() {
        @Override
        public void run() {
            try {
                synchronized (thread) {
                    if (iBtDevAddr == null || iBtDevAddr.isEmpty()) {
                        thread.wait();
                    }

                    deviceListener.notifyWaitToUi(ResourceManager.getResource().getString("KConnectingDev"));
                    sleep(1000);
                    deviceListener.notifyToUi(ResourceManager.getResource().getString("DoMeasureECG"));
                    ECGDrawData.baseline = BASELINE;
                    ECGDrawData.maxVal = MAXVAL;
                    ECGDrawData.gain = 1;
                    ECGDrawData.nLead = NLEAD;
                    ECGDrawData.samplingRate = SAMPLING_RATE;
                    ECGDrawData.lables = LABELS;
                    ECGDrawData.setProgress(0);
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
                // nothing to do
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
            int p = 100*x/NUM_SAMPLES;
            ECGDrawData.progress=p;
            if (p > 20 && p < 80)
                ECGDrawData.message = "Prova";
            else
                ECGDrawData.message = "";
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
