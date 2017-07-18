package com.ti.app.telemed.core.btdevices;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.ihealth.communication.control.Po3Control;
import com.ihealth.communication.control.PoProfile;
import com.ihealth.communication.manager.iHealthDevicesCallback;
import com.ihealth.communication.manager.iHealthDevicesManager;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;

import java.util.HashMap;
import java.util.Locale;
import java.util.Vector;


class IHealthPO3 extends Handler implements IHealtDevice{

    private static final String TAG = "IHealthPO3";

    private static final int NUMBER_OF_MEASUREMENTS = 2000; // = 5 min, 1 measure every 150ms
    private static final int MIN_SAMPLES = 45;
    private static final int BASE_OXY_STREAM_LENGTH = 189; //

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
    private int	iAnalysisTime;
    private int batteryLevel;
    private Vector<OxyElem> oxyQueue;
    private byte[] oxyStream;

    private IHealth iHealth = null;
    private Measure measure;

    private static final int HANDLER_LIVE_MEASUREMENT = 100;
    private static final int HANDLER_END_MEASUREMENT = 101;
    private static final int HANDLER_BATTERY = 102;

    private boolean firstRead = true;
    private int callbackId = -1;
    private Po3Control mPo3Control = null;


    IHealthPO3(IHealth iHealth, Measure m) {
        this.measure = m;
        this.iHealth = iHealth;
    }

    private iHealthDevicesCallback mIHealthDevicesCallback = new iHealthDevicesCallback() {
        @Override
        public void onDeviceNotify(String mac, String deviceType, String action, String message) {
            Log.d(TAG, "iHealthDevicesCallback:onDeviceNotify ++ " + "MAC=" + mac + " - DevType=" + deviceType
                    + " - Action=" + action + " - Message=" + message);
            Bundle bundle = new Bundle();
            Message msg = new Message();
            bundle.putString("message", message);
            msg.setData(bundle);
            switch (action) {
                case PoProfile.ACTION_LIVEDA_PO:
                    // Message={"bloodoxygen":96,"heartrate":82,"pulsestrength":2,"pi":0.03700000047683716,"pulseWave":[73,336,524]}
                    msg.what = HANDLER_LIVE_MEASUREMENT;
                    sendMessage(msg);
                    break;
                case PoProfile.ACTION_RESULTDATA_PO:
                    // Message={"bloodoxygen":96,"heartrate":82,"pulsestrength":0,"pi":0.024000000208616257,"pulseWave":[0,0,0],"dataID":"6BA8A3A978A7C3E50C010F56A46CDE67"}
                    msg.what = HANDLER_END_MEASUREMENT;
                    sendMessage(msg);
                    break;
                case PoProfile.ACTION_BATTERY_PO:
                    msg.what = HANDLER_BATTERY;
                    sendMessage(msg);
                    break;
            }
        }
    };

    @Override
    public void startMeasure(String mac){
        if (iHealth == null) {
            Log.e(TAG, "startMeasure: iHealth is NULL!");
            return;
        }

        measure.setBtAddress(mac);

        firstRead = true;

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
        tmpStream.put(67, (byte)0x0A); //STEP_OXY
        tmpStream.put(68, (byte)0x04);
        tmpStream.put(69, (byte)0x20); //FL_TEST
        oxyStream = tmpStream.array();

        callbackId = iHealthDevicesManager.getInstance().registerClientCallback(mIHealthDevicesCallback);
        /* Limited wants to receive notification specified device */
        iHealthDevicesManager.getInstance().addCallbackFilterForDeviceType(callbackId,
                iHealthDevicesManager.TYPE_PO3);
        /* Get po3 controller */
        mPo3Control = iHealthDevicesManager.getInstance().getPo3Control(mac.replace(":", ""));
        Log.d(TAG, ".handleMessage: deviceMac=" + mac + " - mPo3Control=" + mPo3Control);
        mPo3Control.getBattery();
    }

    @Override
    public void stop() {
        if (callbackId != -1)
            iHealthDevicesManager.getInstance().unRegisterClientCallback(callbackId);
        callbackId = -1;

        if (mPo3Control != null)
            mPo3Control.disconnect();
        mPo3Control = null;
    }

    @Override
    public String getStartMeasureMessage() {
        return ResourceManager.getResource().getString("KMeasStartMsg");
    }


    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);
        switch (msg.what) {
            case HANDLER_BATTERY:
                Bundle bundle_battery = msg.getData();
                String messageBattery = bundle_battery.getString("message");
                try {
                    JSONObject reader = new JSONObject(messageBattery);
                    batteryLevel = reader.getInt(PoProfile.BATTERY_PO);
                } catch (JSONException e) {
                    iHealth.notifyError(DeviceListener.DEVICE_DATA_ERROR,
                            ResourceManager.getResource().getString("EGWDeviceDataError"));
                    Log.e(TAG, "HANDLER_BATTERY", e);
                    break;
                }
                iHealth.scheduleTimer();
                mPo3Control.startMeasure();
                break;
            case HANDLER_LIVE_MEASUREMENT:
                // check if disconnect was called (some mesures could be in the queue and should be ignored)
                if (mPo3Control != null) {
                    iHealth.scheduleTimer();
                    if (firstRead) {
                        iHealth.notifyIncomingMeasures(ResourceManager.getResource().getString("KMeasuring"));
                        firstRead = false;
                    }
                    Bundle bundle_live_data = msg.getData();
                    String messageLive = bundle_live_data.getString("message");
                    addNewSample(messageLive);
                }
                break;
            case HANDLER_END_MEASUREMENT:
                iHealth.resetTimer();
                if (oxyQueue.size() < MIN_SAMPLES) {
                    iHealth.notifyError(DeviceListener.MEASURE_PROCEDURE_ERROR,
                            ResourceManager.getResource().getString("KMinMeasuresMsg"));
                    break;
                }
                // stop();
                calcFinalData();
                notifyResultData();
                break;
        }
    }

    private void addNewSample(String message) {
        int aSpO2 = 0;
        int aHR = 0;

        try {
            JSONTokener jsonTokener = new JSONTokener(message);
            JSONObject jsonObject = (JSONObject) jsonTokener.nextValue();
            aSpO2 = jsonObject.getInt(PoProfile.BLOOD_OXYGEN_PO);
            aHR = jsonObject.getInt(PoProfile.PULSE_RATE_PO);
        } catch (JSONException e) {
            Log.e(TAG, "addNewSample(): ", e);
        }

        //At the beginning of the measurement the device send a set of invalid values where
        // the HR equals 30 and the SpO2 equals 70
        if ((aSpO2 == 70) && (aHR == 30))
            return;

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


    private void calcFinalData() {
        int hrTot=0, spO2Tot=0, sampleCount;
        OxyElem elem;

        sampleCount = oxyQueue.size();

        //iOxyStream = iOxyStream->ReAlloc(BASE_OXY_STREAM_LENGTH + SampleCount*2);
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

        iAnalysisTime = sampleCount*150/1000; // tempo in secondi

        iSpO2Med = ((double)spO2Tot/(double)sampleCount);
        iHRMed = ((double)hrTot/(double)sampleCount);
    }


    private void notifyResultData() {
        //MISURE
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
//        String durata = String.format(Locale.ENGLISH, "%02d:%02d:%02d", tDurata.getHh(), tDurata.getMm(), tDurata.getSs());

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
        tmpVal.put(GWConst.EGwCode_BATTERY, Integer.toString(batteryLevel)); // livello batteria

        measure.setMeasures(tmpVal);
        measure.setFile(oxyStream);
        measure.setFileType(XmlManager.MIR_OXY_FILE_TYPE);
        measure.setFailed(false);

        iHealth.notifyEndMeasurement(measure);
    }

    private class Time {
        private int hh;
        private int mm;
        private int ss;

        public Time(int hh, int mm, int ss) {
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

    private Time calcTime(int aSec) {
        int hh = aSec/3600;
        int mm = (aSec%3600)/60;
        int ss = (aSec%3600)%60;
        return new Time(hh, mm, ss);
    }

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
}
