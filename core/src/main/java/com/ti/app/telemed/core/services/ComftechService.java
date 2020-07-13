package com.ti.app.telemed.core.services;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.util.Log;

import com.ti.app.telemed.core.btdevices.ComftechManager;
import com.ti.app.telemed.core.common.Device;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.measuremodule.MeasureManager;
import com.ti.app.telemed.core.util.Util;

import java.util.HashMap;

import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_BATTERY;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_BATTERY_TE;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_FC_TH_MAX;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_FC_TH_MIN;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_FC_TH_TIME;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_FIANCO_DX;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_FIANCO_SX;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_FR_TH_MAX;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_FR_TH_MIN;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_FR_TH_TIME;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_IN_PIEDI;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_PATIENT;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_FC_AVG;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_FC_MAX;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_FC_MIN;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_FC_OVER;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_FC_SIGMA;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_FC_TIME;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_FC_UNDER;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_FR_AVG;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_FR_MAX;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_FR_MIN;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_FR_OVER;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_FR_SIGMA;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_FR_TIME;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_FR_UNDER;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_PRONO;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_SUPINO;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_TE_AVG;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_TE_MAX;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_TE_MIN;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_TE_OVER;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_TE_SIGMA;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_TE_TH_MAX;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_TE_TH_MIN;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_TE_TH_TIME;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_TE_TIME;
import static com.ti.app.telemed.core.btdevices.ComftechManager.KEY_TE_UNDER;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_AF;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_AG;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_X0;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_X1;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_X2;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_X3;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_X4;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_X5;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_X6;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_X7;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_X8;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_X9;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_XA;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_XB;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_XC;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_XD;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_XE;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_XF;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_XG;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_XH;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_XJ;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_XK;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_XL;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_XM;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_XN;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_XP;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_XQ;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_XR;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_XS;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_XT;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_XU;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_XV;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_XW;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_XX;
import static com.ti.app.telemed.core.util.GWConst.KCOMFTECH;
import static com.ti.app.telemed.core.util.GWConst.KMsr_Comftech;

public class ComftechService extends Service {

    private static final String TAG = "ComftechService";
    private static final String WLTAG = "com.ti.app.telemed.core.services:ComftechService";

    public ComftechService() {
    }

    private static String getSoglia(float min, float max) {
        String ret = null;
        if (min != -1f && max != -1f)
            ret = "R:".concat(String.valueOf(min)).concat(" G:").concat(String.valueOf(max)).concat(" R:1000");
        else if (min != -1)
            ret = "R:".concat(String.valueOf(min)).concat(" G:1000");
        else if (max != -1)
            ret = "G:".concat(String.valueOf(max)).concat(" R:1000");
        return ret;
    }
    private static String getSoglia(int min, int max) {
        String ret = null;
        if (min != -1 && max != -1)
            ret = "R:".concat(String.valueOf(min)).concat(" G:").concat(String.valueOf(max)).concat(" R:1000");
        else if (min != -1)
            ret = "R:".concat(String.valueOf(min)).concat(" G:1000");
        else if (max != -1)
            ret = "G:".concat(String.valueOf(max)).concat(" R:1000");
        return ret;
    }

    private static class IncomingHandler extends Handler {
        private final ComftechService mInstance;

        IncomingHandler(ComftechService c) {
            mInstance = c;
        }

        @Override
        public void handleMessage(Message msg) {
            ComftechManager cm = ComftechManager.getInstance();
            PowerManager pm = (PowerManager) mInstance.getSystemService(POWER_SERVICE);
            if (pm == null) {
                Log.e(TAG, "handleMessage: PowerManager is NULL!!");
                return;
            }
            final PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WLTAG);
            try {
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(15000);
                Log.d(TAG,"wakeLock acquired");

                Bundle data = msg.getData();
                String userId = data.getString(KEY_PATIENT);
                if (!cm.isMonitoringValid(userId)) {
                    Log.w(TAG, "Stopping monitoring");
                    cm.stopMonitoring(null);
                    return;
                }

                if (cm.updateMonitoring(userId)) {
                    Log.i(TAG, "Update monitoring parameters");
                    cm.startMonitoring(userId, null);
                }

                /*
                soglia minima e massima pari ad esempio a 40 e 120  “R:40 G:120 R:1000”
                tempo minimo fuori soglia pari ad esempio a 60 secondi  “G:60 R:1000”
                 */
                int min,max;
                float fmin,fmax;
                String soglia;
                HashMap<String,String> thMap = new HashMap<>();
                int val;
                float fval;
                HashMap<String,String> measureMap = new HashMap<>();

                min = data.getInt(KEY_FC_TH_MIN);
                max = data.getInt(KEY_FC_TH_MAX);
                soglia = getSoglia(min, max);
                if (soglia != null) {
                    thMap.put(EGwCode_X0, soglia);
                    measureMap.put(EGwCode_X0,"0");
                }

                min = data.getInt(KEY_FC_TH_TIME);
                soglia = getSoglia(min, -1);
                if (soglia != null) {
                    thMap.put(EGwCode_X1, soglia);
                    measureMap.put(EGwCode_X1,"0");
                }

                min = data.getInt(KEY_FR_TH_MIN);
                max = data.getInt(KEY_FR_TH_MAX);
                soglia = getSoglia(min, max);
                if (soglia != null) {
                    thMap.put(EGwCode_X2, soglia);
                    measureMap.put(EGwCode_X2,"0");
                }

                min = data.getInt(KEY_FR_TH_TIME);
                soglia = getSoglia(min, -1);
                if (soglia != null) {
                    thMap.put(EGwCode_X3, soglia);
                    measureMap.put(EGwCode_X3,"0");
                }

                fmin = data.getFloat(KEY_TE_TH_MIN);
                fmax = data.getFloat(KEY_TE_TH_MAX);
                soglia = getSoglia(fmin, fmax);
                if (soglia != null) {
                    thMap.put(EGwCode_X4, soglia);
                    measureMap.put(EGwCode_X4,"0");
                }

                min = data.getInt(KEY_TE_TH_TIME);
                soglia = getSoglia(min, -1);
                if (soglia != null) {
                    thMap.put(EGwCode_X5, soglia);
                    measureMap.put(EGwCode_X5,"0");
                }

                val = data.getInt(KEY_FC_AVG);
                if (val != -1)
                    measureMap.put(EGwCode_X6,String.valueOf(val));
                val = data.getInt(KEY_FC_SIGMA);
                if (val != -1)
                    measureMap.put(EGwCode_X7,String.valueOf(val));
                val = data.getInt(KEY_FC_MAX);
                if (val != -1)
                    measureMap.put(EGwCode_X8,String.valueOf(val));
                val = data.getInt(KEY_FC_MIN);
                if (val != -1)
                    measureMap.put(EGwCode_X9,String.valueOf(val));
                val = data.getInt(KEY_FC_OVER);
                if (val != -1)
                    measureMap.put(EGwCode_XA,String.valueOf(val));
                val = data.getInt(KEY_FC_UNDER);
                if (val != -1)
                    measureMap.put(EGwCode_XB,String.valueOf(val));
                val = data.getInt(KEY_FC_TIME);
                if (val != -1)
                    measureMap.put(EGwCode_XC,String.valueOf(val));

                val = data.getInt(KEY_FR_AVG);
                if (val != -1)
                    measureMap.put(EGwCode_XD,String.valueOf(val));
                val = data.getInt(KEY_FR_SIGMA);
                if (val != -1)
                    measureMap.put(EGwCode_XE,String.valueOf(val));
                val = data.getInt(KEY_FR_MAX);
                if (val != -1)
                    measureMap.put(EGwCode_XF,String.valueOf(val));
                val = data.getInt(KEY_FR_MIN);
                if (val != -1)
                    measureMap.put(EGwCode_XG,String.valueOf(val));
                val = data.getInt(KEY_FR_OVER);
                if (val != -1)
                    measureMap.put(EGwCode_XH,String.valueOf(val));
                val = data.getInt(KEY_FR_UNDER);
                if (val != -1)
                    measureMap.put(EGwCode_XJ,String.valueOf(val));
                val = data.getInt(KEY_FR_TIME);
                if (val != -1)
                    measureMap.put(EGwCode_XK,String.valueOf(val));

                fval = data.getFloat(KEY_TE_AVG);
                if (fval != -1f)
                    measureMap.put(EGwCode_XL,String.valueOf(fval));
                fval = data.getFloat(KEY_TE_SIGMA);
                if (fval != -1f)
                    measureMap.put(EGwCode_XM,String.valueOf(fval));
                fval = data.getFloat(KEY_TE_MAX);
                if (fval != -1f)
                    measureMap.put(EGwCode_XN,String.valueOf(fval));
                fval = data.getFloat(KEY_TE_MIN);
                if (fval != -1f)
                    measureMap.put(EGwCode_XP,String.valueOf(fval));
                val = data.getInt(KEY_TE_OVER);
                if (val != -1)
                    measureMap.put(EGwCode_XQ,String.valueOf(val));
                val = data.getInt(KEY_TE_UNDER);
                if (val != -1)
                    measureMap.put(EGwCode_XR,String.valueOf(val));
                val = data.getInt(KEY_TE_TIME);
                if (val != -1)
                    measureMap.put(EGwCode_XS,String.valueOf(val));

                val = data.getInt(KEY_SUPINO);
                if (val != -1)
                    measureMap.put(EGwCode_XT,String.valueOf(val));
                val = data.getInt(KEY_PRONO);
                if (val != -1)
                    measureMap.put(EGwCode_XU,String.valueOf(val));
                val = data.getInt(KEY_FIANCO_SX);
                if (val != -1)
                    measureMap.put(EGwCode_XV,String.valueOf(val));
                val = data.getInt(KEY_FIANCO_DX);
                if (val != -1)
                    measureMap.put(EGwCode_XW,String.valueOf(val));
                val = data.getInt(KEY_IN_PIEDI);
                if (val != -1)
                    measureMap.put(EGwCode_XX,String.valueOf(val));

                val = data.getInt(KEY_BATTERY);
                if (val != -1)
                    measureMap.put(EGwCode_AF,String.valueOf(val));
                val = data.getInt(KEY_BATTERY_TE);
                if (val != -1)
                    measureMap.put(EGwCode_AG,String.valueOf(val));

                Device d = DbManager.getDbManager().getDeviceWhereMeasureModel(KMsr_Comftech, KCOMFTECH);
                Measure m = new Measure();
                if (msg.what == ComftechManager.MSG_DATA_NORMAL)
                    m.setResult(Measure.RESULT_GREEN);
                else
                    m.setResult(Measure.RESULT_RED);
                m.setMeasureType(KMsr_Comftech);
                m.setDeviceDesc(d.getDescription());
                m.setTimestamp(Util.getTimestamp(null));
                m.setFile(null);
                m.setFileType(null);
                m.setFailed(false);
                m.setIdUser(userId);
                m.setIdPatient(userId);
                m.setStandardProtocol(false);
                m.setThresholds(thMap);
                m.setMeasures(measureMap);
                MeasureManager.getMeasureManager().saveMeasureData(m);
            } catch (Exception e) {
                Log.e(TAG,"Exception: " + e);
            } finally {
                if (wakeLock.isHeld()) {
                    wakeLock.release();
                    Log.d(TAG,"wakeLock released");
                }
            }
        }
    }

    final Messenger myMessenger = new Messenger(new IncomingHandler(this));

    @Override
    public IBinder onBind(Intent intent) {
        return myMessenger.getBinder();
    }
}