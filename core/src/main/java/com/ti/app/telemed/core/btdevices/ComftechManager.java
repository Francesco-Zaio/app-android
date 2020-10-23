package com.ti.app.telemed.core.btdevices;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.common.UserMeasure;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.services.ComftechService;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.util.Util;

import static com.ti.app.telemed.core.util.GWConst.EGwCode_X0;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_X1;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_X2;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_X3;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_X4;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_X5;
import static com.ti.app.telemed.core.util.GWConst.KMsr_Comftech;

public class ComftechManager implements Runnable{
    private static final String TAG = "ComftechManager";

    private static final String COMFTECH_PACKAGE = "com.comftech.howdy";
    private static final String COMFTECH_SERVICE = "com.comftech.howdy.timremoteservices";

    private static final String KEY_COMFTECH_PATIENT = "COMFTECH_PATIENT";
    private static final String KEY_COMFTECH_TH_EGwCode_X0 = "COMFTECH_TH_EGwCode_X0";
    private static final String KEY_COMFTECH_TH_EGwCode_X1 = "COMFTECH_TH_EGwCode_X1";
    private static final String KEY_COMFTECH_TH_EGwCode_X2 = "COMFTECH_TH_EGwCode_X2";
    private static final String KEY_COMFTECH_TH_EGwCode_X3 = "COMFTECH_TH_EGwCode_X3";
    private static final String KEY_COMFTECH_TH_EGwCode_X4 = "COMFTECH_TH_EGwCode_X4";
    private static final String KEY_COMFTECH_TH_EGwCode_X5 = "COMFTECH_TH_EGwCode_X5";
    private static final String KEY_COMFTECH_INTERVAL_NORMAL = "COMFTECH_INTERVAL_NORMAL";
    private static final String KEY_COMFTECH_INTERVAL_ALARM = "COMFTECH_INTERVAL_ALARM";

    /* Message type codes */
    public static final int MSG_START_MONITORING = 1;
    public static final int MSG_STOP_MONITORING = 2;
    public static final int MSG_DATA_NORMAL = 4;
    public static final int MSG_DATA_ALARM = 5;
    public static final int MSG_RESULT = 6;

    /* Message key values */
    public static final String KEY_PATIENT ="PATIENT";
    public static final String KEY_FC_TH_MIN ="FC_TH_MIN";
    public static final String KEY_FC_TH_MAX ="FC_TH_MAX";
    public static final String KEY_FC_TH_TIME ="FC_TH_TIME";
    public static final String KEY_FR_TH_MIN ="FR_TH_MIN";
    public static final String KEY_FR_TH_MAX ="FR_TH_MAX";
    public static final String KEY_FR_TH_TIME ="FR_TH_TIME";
    public static final String KEY_TE_TH_MIN ="TE_TH_MIN";
    public static final String KEY_TE_TH_MAX ="TE_TH_MAX";
    public static final String KEY_TE_TH_TIME ="TE_TH_TIME";
    public static final String KEY_TIME_NORMAL ="TIME_NORMAL";
    public static final String KEY_TIME_OVER ="TIME_OVER";

    public static final String KEY_FC_AVG ="FC_AVG";
    public static final String KEY_FC_SIGMA ="FC_SIGMA";
    public static final String KEY_FC_MAX ="FC_MAX";
    public static final String KEY_FC_MIN ="FC_MIN";
    public static final String KEY_FC_OVER ="FC_OVER";
    public static final String KEY_FC_UNDER ="FC_UNDER";
    public static final String KEY_FC_TIME ="FC_TIME";

    public static final String KEY_FR_AVG ="FR_AVG";
    public static final String KEY_FR_SIGMA ="FR_SIGMA";
    public static final String KEY_FR_MAX ="FR_MAX";
    public static final String KEY_FR_MIN ="FR_MIN";
    public static final String KEY_FR_OVER ="FR_OVER";
    public static final String KEY_FR_UNDER ="FR_UNDER";
    public static final String KEY_FR_TIME ="FR_TIME";

    public static final String KEY_TE_AVG ="TE_AVG";
    public static final String KEY_TE_SIGMA ="TE_SIGMA";
    public static final String KEY_TE_MAX ="TE_MAX";
    public static final String KEY_TE_MIN ="TE_MIN";
    public static final String KEY_TE_OVER ="TE_OVER";
    public static final String KEY_TE_UNDER ="TE_UNDER";
    public static final String KEY_TE_TIME ="TE_TIME";

    public static final String KEY_BATTERY ="BATTERY";
    public static final String KEY_SUPINO ="SUPINO";
    public static final String KEY_PRONO ="PRONO";
    public static final String KEY_FIANCO_SX ="FIANCO_SX";
    public static final String KEY_FIANCO_DX ="FIANCO_DX";
    public static final String KEY_IN_PIEDI ="IN_PIEDI";
    public static final String KEY_BATTERY_TE ="BATTERY_TE";

    private static final String KEY_RESULT = "RESULT";

    /* Result codes */
    private static final int MY_BASE_CODE = 10000;
    public static final int CODE_OK = 0;
    public static final int CODE_RESPONSE_ERROR = MY_BASE_CODE + 1;
    public static final int CODE_SENDDATA_ERROR = MY_BASE_CODE + 2;
    public static final int CODE_TIMEOUT_ERROR = MY_BASE_CODE + 3;
    public static final int CODE_BIND_ERROR = MY_BASE_CODE + 4;


    private final static ComftechManager mInstance = new ComftechManager();

    public interface ResultListener {
        void result(int resultCode);
    }

    private enum OpType {
        StartMonitoring,
        StopMonitoring
    }

    static class RequestData {
        OpType operation;
        String userId;
        ResultListener listener;
    }

    public static ComftechManager getInstance() {
        return mInstance;
    }

    private final Thread currT;
    private List<RequestData> list = Collections.synchronizedList(new LinkedList<RequestData>());
    private RequestData currServed = null;
    private boolean responseReceived = false;
    private int responseCode = CODE_OK;
    private boolean bindingInProgress = false;

    private Messenger mService = null;
    private final Messenger replyMessenger = new Messenger(new HandlerReplyMsg(Looper.getMainLooper()));
    private UserMeasure userMeasure;


    private ComftechManager () {
        currT = new Thread(this);
        currT.setName("ComftechManager thread");
        currT.start();
    }

    // handler for message from service
    class HandlerReplyMsg extends Handler {
        HandlerReplyMsg(Looper l) {
            super(l);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            synchronized (currT) {
                Log.d(TAG, "Message received: id=" + msg.what + " data=\n" + bundle2string(msg.getData()));
                if ((msg.what == MSG_RESULT) && (msg.getData().containsKey(KEY_RESULT))) {
                    responseCode = msg.getData().getInt(KEY_RESULT);
                } else {
                    Log.d(TAG, "Message is Wrong");
                    responseCode = CODE_RESPONSE_ERROR;
                }
                responseReceived = true;
                currT.notifyAll();
            }
        }
    }

    // All'arrivo di ogni misura viene chiamato questo metodo per verificare se il monitoraggio è
    // ancora valido
    public boolean isMonitoringValid(String userId) {
        User u = UserManager.getUserManager().getActiveUser();
        if (u == null || u.isDefaultUser() || u.isBlocked() || !u.isPatient() || !u.getId().equals(userId)) {
            Log.w(TAG, "Current User is Null, or is Blocked, or not patient or different ");
            return false;
        }

        String monitoredId = ComftechManager.getInstance().getMonitoringUserId();
        if (monitoredId.isEmpty() || !monitoredId.equals(userId)) {
            Log.w(TAG, "Monitoring not active or monitored User is different ");
            return false;
        }

        UserMeasure um  = DbManager.getDbManager().getUserMeasure(userId, KMsr_Comftech);
        if (um == null) {
            Log.w(TAG, "Measure not Enabled");
            return false;
        }

        return true;
    }

    // All'arrivo di ogni misura viene chiamato questo metodo per verificare se il monitoraggio è
    // ancora valido
    public boolean updateMonitoring(String userId) {
        UserMeasure um  = DbManager.getDbManager().getUserMeasure(userId, KMsr_Comftech);
        Map<String,String> thresholds = um.getThresholds();
        String th_X0,th_X1,th_X2,th_X3,th_X4,th_X5;
        th_X0 = (thresholds.get(EGwCode_X0) == null)?"":thresholds.get(EGwCode_X0);
        th_X1 = (thresholds.get(EGwCode_X0) == null)?"":thresholds.get(EGwCode_X1);
        th_X2 = (thresholds.get(EGwCode_X0) == null)?"":thresholds.get(EGwCode_X2);
        th_X3 = (thresholds.get(EGwCode_X0) == null)?"":thresholds.get(EGwCode_X3);
        th_X4 = (thresholds.get(EGwCode_X0) == null)?"":thresholds.get(EGwCode_X4);
        th_X5 = (thresholds.get(EGwCode_X0) == null)?"":thresholds.get(EGwCode_X5);
        return !(Util.getRegistryValue(KEY_COMFTECH_TH_EGwCode_X0).equals(th_X0) &&
                Util.getRegistryValue(KEY_COMFTECH_TH_EGwCode_X1).equals(th_X1) &&
                Util.getRegistryValue(KEY_COMFTECH_TH_EGwCode_X2).equals(th_X2) &&
                Util.getRegistryValue(KEY_COMFTECH_TH_EGwCode_X3).equals(th_X3) &&
                Util.getRegistryValue(KEY_COMFTECH_TH_EGwCode_X4).equals(th_X4) &&
                Util.getRegistryValue(KEY_COMFTECH_TH_EGwCode_X5).equals(th_X5) &&
                (Util.getRegistryIntValue(KEY_COMFTECH_INTERVAL_NORMAL) == um.getSendFrequencyNormal()) &&
                (Util.getRegistryIntValue(KEY_COMFTECH_INTERVAL_ALARM) == um.getSendFrequencyAlarm()));
    }

    // check if the monitoring for the userId
    public void checkMonitoring(String userId, boolean stop) {
        Log.d(TAG, "checkMonitoring");
        String monitoredId = getMonitoringUserId();
        if (monitoredId.isEmpty())
            return;

        UserMeasure um  = DbManager.getDbManager().getUserMeasure(userId, KMsr_Comftech);
        if (!monitoredId.equals(userId) || um == null || stop) {
            stopMonitoring(null);
            return;
        }

        if (updateMonitoring(userId))
            startMonitoring(userId, null);
    }

    public void stopMonitoring(ResultListener listener) {
        RequestData data = new RequestData();
        data.userId = null;
        data.listener = listener;
        data.operation = OpType.StopMonitoring;
        synchronized (currT) {
            list.add(data);
            currT.notifyAll();
        }
    }

    public void startMonitoring(String userId, ResultListener listener) {
        if ((userId == null) || userId.isEmpty())
            return;
        RequestData data = new RequestData();
        data.userId = userId;
        data.listener = listener;
        data.operation = OpType.StartMonitoring;
        synchronized (currT) {
            list.add(data);
            currT.notifyAll();
        }
    }

    public String getMonitoringUserId() {
        return Util.getRegistryValue(ComftechManager.KEY_COMFTECH_PATIENT);
    }

    /**
     * Bind to the Comftech Service
     * @return true in case of success
     */
    private boolean bindToComftechService() {
        boolean flag = false;
        Log.d(TAG, "bindToComftechService");
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(COMFTECH_PACKAGE, COMFTECH_SERVICE));
            flag = MyApp.getContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        } catch (SecurityException e) {
            Log.e(TAG, "can't bind to ModemWatcherService, check permission in Manifest");
        }
        return flag;
    }

    /**
     * Send the request contained in @currServed
     * @return true in case of success
     */
    private boolean sendRequest() {
        Message msg =  Message.obtain();
        msg.replyTo = replyMessenger;

        if (currServed.operation == OpType.StopMonitoring)
            msg.what = MSG_STOP_MONITORING;
        else {
            userMeasure = DbManager.getDbManager().getUserMeasure(currServed.userId, KMsr_Comftech);
            if (userMeasure == null) {
                Log.e(TAG, "sendRequest: Comftech Measure not available");
                return false;
            }
            msg.what = MSG_START_MONITORING;
            Bundle bundle = new Bundle();
            bundle.putString(KEY_PATIENT, currServed.userId);
            // Soglia con min/max : “R:40 G:120 R:1000” (min=40 max=120)
            // soglia con solo min : “G:60 R:1000” (min=60)
            Float fval;
            int val;
            fval = userMeasure.getThresholdValue(UserMeasure.ThresholdLevel.RED, EGwCode_X0);
            val = (fval == null)? -1: fval.intValue();
            bundle.putInt(KEY_FC_TH_MIN, val);
            fval = userMeasure.getThresholdValue(UserMeasure.ThresholdLevel.GREEN, EGwCode_X0);
            val = (fval == null)? -1: fval.intValue();
            bundle.putInt(KEY_FC_TH_MAX, val);
            fval = userMeasure.getThresholdValue(UserMeasure.ThresholdLevel.GREEN, EGwCode_X1);
            val = (fval == null)? -1: fval.intValue();
            bundle.putInt(KEY_FC_TH_TIME, val);
            fval = userMeasure.getThresholdValue(UserMeasure.ThresholdLevel.RED, EGwCode_X2);
            val = (fval == null)? -1: fval.intValue();
            bundle.putInt(KEY_FR_TH_MIN, val);
            fval = userMeasure.getThresholdValue(UserMeasure.ThresholdLevel.GREEN, EGwCode_X2);
            val = (fval == null)? -1: fval.intValue();
            bundle.putInt(KEY_FR_TH_MAX, val);
            fval = userMeasure.getThresholdValue(UserMeasure.ThresholdLevel.GREEN, EGwCode_X3);
            val = (fval == null)? -1: fval.intValue();
            bundle.putInt(KEY_FR_TH_TIME, val);
            fval = userMeasure.getThresholdValue(UserMeasure.ThresholdLevel.RED, EGwCode_X4);
            fval = (fval == null)? -1f: fval;
            bundle.putFloat(KEY_TE_TH_MIN, fval);
            fval = userMeasure.getThresholdValue(UserMeasure.ThresholdLevel.GREEN, EGwCode_X4);
            fval = (fval == null)? -1f: fval;
            bundle.putFloat(KEY_TE_TH_MAX, fval);
            fval = userMeasure.getThresholdValue(UserMeasure.ThresholdLevel.GREEN, EGwCode_X5);
            val = (fval == null)? -1: fval.intValue();
            bundle.putInt(KEY_TE_TH_TIME, val);
            bundle.putInt(KEY_TIME_NORMAL, userMeasure.getSendFrequencyNormal());
            bundle.putInt(KEY_TIME_OVER, userMeasure.getSendFrequencyAlarm());
            msg.setData(bundle);
        }

        Log.d(TAG, "Sending message: id=" + msg.what + " data=\n" + bundle2string(msg.getData()));

        try {
            mService.send(msg);
        } catch (RemoteException e) {
            Log.e(TAG, "sendRequest exception: " + e);
            return false;
        }
        return true;
    }

    public static String bundle2string(Bundle bundle) {
        if (bundle == null) {
            return null;
        }
        String string = "Bundle{";
        for (String key : bundle.keySet()) {
            string += " " + key + " => " + bundle.get(key) + ";";
        }
        string += " }Bundle";
        return string;
    }

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d (TAG, "onServiceConnected");
            synchronized (currT) {
                resetTimer();
                mService = new Messenger(service);
                bindingInProgress = false;
                currT.notifyAll();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d (TAG, "onServiceDisconnected");
            synchronized (currT) {
                mService = null;
                bindingInProgress = true;
            }
        }

        @Override
        public void onBindingDied(ComponentName name) {
            Log.d (TAG, "onBindingDied");
            synchronized (currT) {
                mService = null;
                bindingInProgress = false;
            }
        }
    };

    @Override
    public void run() {
        synchronized (currT) {
            while (true) {
                try {
                    if ((currServed != null) || list.isEmpty() || bindingInProgress) {
                        Log.d(TAG, "Wait ....");
                        currT.wait();
                    }
                    if ((responseReceived) && (currServed != null)) {
                        Log.d(TAG, "Awake, response arrived");
                        manageResponse();
                    }
                    if ((currServed == null) && (!list.isEmpty())) {
                        // Send the next web request
                        Log.d(TAG, "Sending the next request.");
                        sendData();
                    }
                } catch (InterruptedException ie) {
                    Log.e(TAG, "Thread interrotto");
                }
            }
        }
    }

    private void sendData() {
        if (mService == null) {
            if (!bindToComftechService()) {
                while (!list.isEmpty()) {
                    currServed = list.remove(0);
                    if (currServed.listener != null)
                        currServed.listener.result(CODE_BIND_ERROR);
                }
                currServed = null;
            } else {
                scheduleTimer(3*1000);
                bindingInProgress = true;
            }
            return;
        }
        if (!list.isEmpty()) {
            currServed = list.get(0);
            list.remove(0);
            responseCode = CODE_OK;
            responseReceived = false;
            if (!sendRequest()) {
                if (currServed.listener != null)
                    currServed.listener.result(CODE_SENDDATA_ERROR);
                currServed = null;
            } else
                scheduleTimer(6*1000);
        }
    }

    private void manageResponse() {
        resetTimer();
        if (responseCode == CODE_OK) {
            switch (currServed.operation) {
                case StartMonitoring:
                    Util.setRegistryValue(KEY_COMFTECH_PATIENT, currServed.userId);
                    Map<String,String> thresholds = userMeasure.getThresholds();
                    Util.setRegistryValue(KEY_COMFTECH_TH_EGwCode_X0, thresholds.get(EGwCode_X0));
                    Util.setRegistryValue(KEY_COMFTECH_TH_EGwCode_X1, thresholds.get(EGwCode_X1));
                    Util.setRegistryValue(KEY_COMFTECH_TH_EGwCode_X2, thresholds.get(EGwCode_X2));
                    Util.setRegistryValue(KEY_COMFTECH_TH_EGwCode_X3, thresholds.get(EGwCode_X3));
                    Util.setRegistryValue(KEY_COMFTECH_TH_EGwCode_X4, thresholds.get(EGwCode_X4));
                    Util.setRegistryValue(KEY_COMFTECH_TH_EGwCode_X5, thresholds.get(EGwCode_X5));
                    Util.setRegistryValue(KEY_COMFTECH_INTERVAL_NORMAL, userMeasure.getSendFrequencyNormal());
                    Util.setRegistryValue(KEY_COMFTECH_INTERVAL_ALARM, userMeasure.getSendFrequencyAlarm());
                    break;
                case StopMonitoring:
                    Util.removeRegistryKey(KEY_COMFTECH_PATIENT);
                    for (int i=0; i<list.size();i++) {
                        RequestData rd = list.get(i);
                        if (rd.operation == OpType.StopMonitoring) {
                            list.remove(i);
                            break;
                        }
                    }
                    break;
            }
        }
        if (currServed.listener != null)
            currServed.listener.result(responseCode);
        MyApp.getContext().unbindService(mConnection);
        mService = null;
        currServed = null;
        bindingInProgress = false;
    }

    private Timer timer;
    private class TimerExpired extends TimerTask {
        @Override
        public void run(){
            Log.w(TAG, "Timeout!");
            synchronized (currT) {
                if (bindingInProgress) {
                    currServed = list.get(0);
                    list.remove(0);
                    bindingInProgress = false;
                    responseCode = CODE_BIND_ERROR;
                } else {
                    responseCode = CODE_TIMEOUT_ERROR;
                }
                responseReceived = true;
                currT.notifyAll();
            }
        }
    }

    private void scheduleTimer(int millisec) {
        resetTimer();
        TimerExpired timerExpired = new TimerExpired();
        timer = new Timer();
        timer.schedule(timerExpired, millisec);
    }

    private void resetTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
}
