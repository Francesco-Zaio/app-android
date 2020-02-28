package com.ti.app.telemed.core.btdevices;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.BTSearcherEventListener;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.util.Util;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;


public class DHearth extends DeviceHandler {
    private static final String TAG = "TouchECG";

    private static final String DHEARTH_PACKAGE = "com.dheartcare.dheart";
    private static final String DHEARTH_INTENT = "com.dheartcare.dheart.RECORD_ECG";
    private static final String PDF_PATH = "PDFPATH";

    private boolean stopped = true;

    private Measure m;

    public DHearth(DeviceListener listener, UserDevice ud) {
        super(listener, ud);
    }

    // DeviceHandler interface Methods

    @Override
    public void confirmDialog() {
        m.setUrgent(true);
        deviceListener.showMeasurementResults(m);
    }

    @Override
    public void cancelDialog() {
        m.setUrgent(false);
        deviceListener.showMeasurementResults(m);
    }

    @Override
    public boolean startOperation(OperationType ot, BTSearcherEventListener btSearchListener) {
        if (!startInit(ot, btSearchListener))
            return false;
        stopped = false;
        Log.d(TAG,"startOperation: iBtDevAddr="+iBtDevAddr + " iCmdCode="+iCmdCode.toString());
        return startActivity();
    }

    @Override
    public void stopOperation() {
        Log.d(TAG, "stopOperation");
        stop();
    }

    @Override
    public void selectDevice(BluetoothDevice bd){
        Log.d(TAG, "selectDevice: addr=" + bd.getAddress());
        iState = TState.EGettingService;
    }

    private boolean startActivity() {
        PackageManager pm = MyApp.getContext().getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(DHEARTH_PACKAGE);
        if (intent == null) {
            deviceListener.notifyError(DeviceListener.PACKAGE_NOT_FOUND_ERROR,ResourceManager.getResource().getString("ENoTouchECG"));
            return false;
        }
        intent = new Intent(DHEARTH_INTENT);
        deviceListener.notifyToUi(ResourceManager.getResource().getString("KWaitingECG"));
        deviceListener.startActivity(intent);
        return true;
    }

    @Override
    public void activityResult(int requestCode, int resultCode, Intent data) {
        if (stopped)
            return;
        if (resultCode == Activity.RESULT_OK && data != null) {
            Bundle extras = data.getExtras();
            String filename = extras.getString(PDF_PATH);
            if (filename == null) {
                deviceListener.notifyError(DeviceListener.DEVICE_DATA_ERROR, ResourceManager.getResource().getString("EDataReadError"));
                stop();
                return;
            }
            Log.d(TAG, "file path: " + filename);
            try {
                File fileFrom = new File(filename);
                String name = Util.getMeasuresDir(UserManager.getUserManager().getCurrentPatient().getId())
                        + File.separator + fileFrom.getName();
                File fileTo = new File(name);
                Util.copy(fileFrom, fileTo);
                fileFrom.delete();
                m = getMeasure();
                HashMap<String,String> tmpVal = new HashMap<>();
                String [] tokens  = filename.split(File.separator);
                tmpVal.put(GWConst.EGwCode_0W, tokens[tokens.length-1]);  //nome file
                m.setMeasures(tmpVal);
                m.setFile(fileTo.getAbsolutePath().getBytes("UTF-8"));
                m.setFileType(XmlManager.PDF_FILE_TYPE);
                m.setFailed(false);
                m.setBtAddress("N.A.");
                deviceListener.askSomething(ResourceManager.getResource().getString("KUrgentMsg"),
                        ResourceManager.getResource().getString("KMsgYes"),
                        ResourceManager.getResource().getString("KMsgNo"));
                // m.setUrgent(false);
                // deviceListener.showMeasurementResults(m);
            } catch (Exception e) {
                deviceListener.notifyError(DeviceListener.DEVICE_DATA_ERROR, ResourceManager.getResource().getString("EDataReadError"));
                stop();
            }
        } else {
            deviceListener.notifyError(DeviceListener.NO_MEASURES_FOUND, ResourceManager.getResource().getString("ENoMeasurementDone"));
            stop();
        }
    }

    private void reset() {
        iState = TState.EWaitingToGetDevice;
    }

    private void stop() {
        stopped = true;
        reset();
    }
}
