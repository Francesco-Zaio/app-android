package com.ti.app.telemed.core.common;

import android.util.Log;

import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.exceptions.DbException;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

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

    // N.B.: L'ordine di dichiarazione dei valori è importante e viene
    // usato nella valutazione dei valre limite delle soglie
    public enum ThresholdLevel {
        NONE,
        RED,
        ORANGE,
        GREEN
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

    /**
     * Metodo che restituisce i valori delle soglie calcolati per ogni valore della misura
     * @return hashmap di oggetti {@code ThresholdLevel} che contiene le soglie calcolate per ogni valore della misura o (@code null) se i valori sono null
     */
    public Map<String,ThresholdLevel> checkTresholds() {
        if ((measures == null) || (thresholds == null))
            return null;

        HashMap<String,ThresholdLevel> ret = new HashMap<>();
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
                    case "G":
                        ret = ThresholdLevel.GREEN;
                        if (measureValue <= thValue)
                            return ret;
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "checkThresholdValue BAD FROMAT ERROR threshold=" + thStringValue + " measure=" + measureStringValue);
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
}
