package com.ti.app.telemed.core.common;

import android.util.Log;

import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.common.UserMeasure.ThresholdLevel;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;


public class Measure implements Serializable{
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private static final String TAG = "Measure";

    private String deviceDesc = "";
    private String btAddress = "N.A.";
    private String timestamp;
    private String measureType;
    private MeasureFamily family;
    private boolean standardProtocol = false;
    private Map<String,String> measures = null;
    private Map<String,String> thresholds = null;
    private boolean sent = false;
    private byte[] file = null;
    private String fileType = null;
    private String idUser;
    private String idPatient;
    private boolean failed = false;
    private String failureMessage = "";
    private String failureCode = "";
    private Integer sendFailCount = 0;
    private String sendFailReason = "";
    private boolean urgent = false;
    private String result = RESULT_NONE;

    public static final String RESULT_NONE = "N";
    public static final String RESULT_GREEN = "G";
    public static final String RESULT_YELLOW = "Y";
    public static final String RESULT_ORANGE = "O";
    public static final String RESULT_RED = "R";


    public static MeasureFamily getFamily(String measureType) {
        if (measureType.matches("D\\d+"))
            return MeasureFamily.DOCUMENTO;
        else if (measureType.matches("Q\\d+"))
            return MeasureFamily.NONBIOMETRICA;
        else
            return MeasureFamily.BIOMETRICA;
    }

    public String getDeviceDesc() {
        return deviceDesc;
    }

    public void setDeviceDesc(String deviceDesc) {
        this.deviceDesc = deviceDesc;
    }

    public String getBtAddress() {
        return btAddress;
    }

    public void setBtAddress(String btAddress) {
        this.btAddress = btAddress;
    }

    public Map<String,String> getMeasures() {
        return measures;
    }

    public void setMeasures(Map<String,String> measures) {
        this.measures = measures;
    }

    public Map<String,String> getThresholds() {
        return thresholds;
    }

    public void setThresholds(Map<String,String> thresholds) {
        this.thresholds = thresholds;
    }

    public String getMeasureType() {
        return measureType;
    }

    public void setMeasureType(String measureType) {
        this.measureType = measureType;
        family = getFamily(measureType);
    }

    public MeasureFamily getFamily() {
        return family;
    }

    public void setFamily(MeasureFamily family) {
        this.family = family;
    }

    public boolean getStandardProtocol() {
        return standardProtocol;
    }

    public void setStandardProtocol(boolean standardProtocol) {
        this.standardProtocol = standardProtocol;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public boolean getSent() {
        return sent;
    }

    public void setSent(boolean sent) {
        this.sent = sent;
    }

    public byte[] getFile() {
        return file;
    }

    public void setFile(byte[] file) {
        this.file = file;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getIdUser() {
        return this.idUser;
    }

    public void setIdUser(String idUser) {
        this.idUser = idUser;
    }

    public String getIdPatient() {
        return this.idPatient;
    }

    public void setIdPatient(String idPatient) {
        this.idPatient = idPatient;
    }

    public boolean getFailed() {
        return this.failed;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
    }

    public String getFailureCode() {
        return this.failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getFailureMessage() {
        return this.failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    public String getSendFailReason() {
        return this.sendFailReason;
    }

    public void setSendFailReason(String sendFailReason) {
        this.sendFailReason = sendFailReason;
    }

    public Integer getSendFailCount() {
        return sendFailCount;
    }

    public void setSendFailCount(Integer sendFailCount) {
        this.sendFailCount = sendFailCount;
    }

    public boolean getUrgent() {
        return urgent;
    }

    public void setUrgent(boolean urgent) {
        this.urgent = urgent;
    }

    public String getResult() {
        return this.result;
    }

    public void setResult(String result) {
        this.result = result;
    }


    /**
     * Metodo che restituisce i valori delle soglie calcolati per ogni valore della misura
     * @return hashmap di oggetti {@code ThresholdLevel} che contiene le soglie calcolate per ogni chiave della misura
     */
    public Map<String,ThresholdLevel> checkTresholds() {
        HashMap<String,ThresholdLevel> ret = new HashMap<>();
        if ((measures == null))
            return ret;

        if (thresholds == null) {
            thresholds = new HashMap<>();
            UserMeasure um = DbManager.getDbManager().getUserMeasure(idUser, measureType);
            if (um != null) {
                for (Map.Entry<String, String> entry : measures.entrySet())
                    if (um.getThresholds().containsKey(entry.getKey()))
                        thresholds.put(entry.getKey(), um.getThresholds().get(entry.getKey()));
            }
        }

        for (Map.Entry<String, String> entry : measures.entrySet())
        {
            if (thresholds.containsKey(entry.getKey()))
                ret.put(entry.getKey(), checkThresholdValue(thresholds.get(entry.getKey()), entry.getValue()));
            else
                ret.put(entry.getKey(), ThresholdLevel.NONE);
        }
        return ret;
    }

    private ThresholdLevel checkThresholdValue(String thStringValue, String measureStringValue) {
        ThresholdLevel ret = ThresholdLevel.NONE;
        try {
            thStringValue = thStringValue.replace (',', '.');
            measureStringValue = measureStringValue.replace (',', '.');
            float measureValue = Float.parseFloat(measureStringValue);
            String[] thl1 = thStringValue.split(" ");
            for (String th : thl1) {
                ThresholdLevel oldVal = ret;
                String[] thl2 = th.split(":");
                float thValue = Float.parseFloat(thl2[1]);
                switch (thl2[0]) {
                    case "R":
                        ret = ThresholdLevel.RED;
                        if (oldVal.ordinal() < ret.ordinal()) {
                            if (measureValue < thValue)
                                return ret;
                        } else if (measureValue <= thValue)
                            return ret;
                        break;
                    case "O":
                        ret = ThresholdLevel.ORANGE;
                        if (oldVal.ordinal() < ret.ordinal()) {
                            if (measureValue < thValue)
                                return ret;
                        } else if (measureValue <= thValue)
                            return ret;
                        break;
                    case "Y":
                        ret = ThresholdLevel.YELLOW;
                        if (oldVal.ordinal() < ret.ordinal()) {
                            if (measureValue < thValue)
                                return ret;
                        } else if (measureValue <= thValue)
                            return ret;
                        break;
                    case "G":
                        ret = ThresholdLevel.GREEN;
                        if (measureValue <= thValue)
                            return ret;
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "checkThresholdValue BAD FORMAT ERROR threshold=" + thStringValue + " measure=" + measureStringValue);
            return ThresholdLevel.NONE;
        }
        return ret;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof Measure){
            Measure m = (Measure)obj;
            return this.timestamp.equals(m.getTimestamp())
                    && this.idUser.equals(m.getIdUser())
                    && this.measureType.equals(m.getMeasureType());
        } else {
            return false;
        }
    }

    public enum MeasureFamily {
        MEDICAZIONE(0),
        BIOMETRICA(1),
        NONBIOMETRICA(2),
        DOCUMENTO(3);

        private final int value;
            MeasureFamily(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static MeasureFamily get(int value) {
            switch (value) {
                case 0:
                    return MEDICAZIONE;
                case 1:
                    return BIOMETRICA;
                case 2:
                    return NONBIOMETRICA;
                case 3:
                    return DOCUMENTO;
                default:
                    return BIOMETRICA;
            }
        }
    }
}
