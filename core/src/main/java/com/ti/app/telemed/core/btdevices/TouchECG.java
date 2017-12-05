package com.ti.app.telemed.core.btdevices;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
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
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.util.Util;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;


public class TouchECG extends DeviceHandler {
    private static final String TAG = "TouchECG";

    private static final String TOUCH_ECG_PACKAGE = "com.cardioline.touchECG";

    // Codifica esito restituito dalla app
    private static final int RESULT_OK = -1;
    private static final int RESULT_ABORT = 0;
    private static final String KEY_RETURN = "RETURN";

    // Codifica chiavi attributo passati alla app
    private static final String KEY_BUNDLE = "CARDIOLINE_BUNDLE";
    private static final String KEY_ID = "ID"; // Codice Fiscale paziente MaxLength = 24
    private static final String KEY_FIRSTNAME = "FIRSTNAME"; // Nome paziente MaxLength = 16
    private static final String KEY_LASTNAME = "LASTNAME"; // Cognome paziente MaxLength = 24
    private static final String KEY_SEX ="SEX"; //int see enum PATIENT_GENDER
    private static final String KEY_BIRTHDATE  = "BIRTHDATE"; // string format yyyymmdd , for example 19000401 (1 april , 1900)
    private static final String KEY_RACE ="RACE"; //int see enum PATIENT_RACE
    private static final String KEY_WEIGHT = "WEIGHT"; //int
    private static final String KEY_WEIGHTUM = "WEIGHTUM "; //int see enum PATIENT_WEIGHTUM for unit measure
    private static final String KEY_HEIGHT = "HEIGHT"; //int
    private static final String KEY_HEIGHTUM = "HEIGHTUM"; //int see enum PATIENT_HEIGHTUM for unit measure
    private static final String KEY_TECHNICIAN = "TECHNICIAN"; //string MaxLength = 30; we can put the string in specific format [USERNAME]|[NAME SURNAME]; the “|” char is the separator,for example = “GRETTI|Gianni Rettini”
    private static final String KEY_PATTERN  = "PATTERN"; //string the output file name produced by touchECG, this can be parsed by touchECG see apposite PATTERN section
    private static final String KEY_PATH_SCP  = "PATH_SCP"; // string, this permit to specify where the touchECG save the scp file when save
    private static final String KEY_APPLICATION = "APPLICATION"; // int , this will be set to 2= SEMI  see enum APPLICATION


    private String filename;

    public TouchECG(DeviceListener listener, UserDevice ud) {
        super(listener, ud);
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

        PackageManager pm = MyApp.getContext().getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(TOUCH_ECG_PACKAGE);
        if (intent == null) {
            deviceListener.notifyError(DeviceListener.PACKAGE_NOT_FOUND_ERROR,ResourceManager.getResource().getString("ENoTouchECG"));
            return false;
        }
        startActivity(intent);
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
        iState = TState.EGettingService;
    }

    @Override
    public void onActivityResult(int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            filename = data.getStringExtra(KEY_RETURN);
            FileInputStream fis = null;
            byte[] fileContent = null;
            try {
                File file = new File(filename);
                fileContent = new byte[(int) file.length()];
                fis = new FileInputStream(file);
                long n = fis.read(fileContent);
                if (n != fileContent.length) {
                    deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR, ResourceManager.getResource().getString("EDataReadError"));
                    return;
                }
            } catch (Exception e) {
                deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR, ResourceManager.getResource().getString("EDataReadError"));
                return;
            } finally {
                try {
                    if (fis != null)
                        fis.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Measure m = getMeasure();
            HashMap<String,String> tmpVal = new HashMap<>();
            String [] tokens  = filename.split(File.separator);
            tmpVal.put(GWConst.EGwCode_0G, tokens[tokens.length-1]);  //nome file
            m.setMeasures(tmpVal);
            m.setFile(fileContent);
            m.setFileType(XmlManager.ECG_FILE_TYPE);
            m.setFailed(false);
            m.setBtAddress("N.A.");
            deviceListener.showMeasurementResults(m);
        } else {
            deviceListener.notifyError(DeviceListener.NO_MEASURES_FOUND, ResourceManager.getResource().getString("ENoMeasurementDone"));
        }
    }

    private void startActivity(Intent intent) {
        intent.setFlags(0);
        Bundle b = new Bundle();
        b.putString(KEY_ID, truncate(patient.getCf(),24));
        b.putString(KEY_FIRSTNAME, truncate(patient.getName(),16));
        b.putString(KEY_LASTNAME, truncate(patient.getSurname(),24));
        b.putInt(KEY_SEX, patient.getSex().equals("M")?1:2);
        b.putString(KEY_BIRTHDATE, patient.getBirthdayDate());
        b.putInt(KEY_RACE, decodeRace(patient.getEthnic()));
        double weight = Double.parseDouble( patient.getWeight().replace(",",".") );
        b.putInt(KEY_WEIGHT, (int)(weight + 0.5));
        b.putInt(KEY_WEIGHTUM, 1); //Kg
        b.putInt(KEY_HEIGHT, Integer.parseInt(patient.getHeight()));
        b.putInt(KEY_HEIGHTUM, 1); // cm
        String technician = user.getId()+"|"+user.getName()+" "+user.getSurname();
        b.putString(KEY_TECHNICIAN, truncate(technician,30));
        b.putString(KEY_PATTERN, patient.getId() + "-"
                + new SimpleDateFormat("yyyyMMddhhmmss",Locale.ITALIAN).format(new Date()));
        b.putString(KEY_PATH_SCP, Util.getMeasuresDir().getAbsolutePath());
        b.putInt(KEY_APPLICATION, 2); // tc get passing parameter and remain in realtime mode

        intent.putExtra(KEY_BUNDLE,b);
        deviceListener.startActivity(intent);
    }

    private void reset() {
        iState = TState.EWaitingToGetDevice;
    }

    private void stop() {
        reset();
    }

    private String truncate(String str, int length) {
        if(str != null && str.length() > length) {
            return str.substring(0, length) + "...";
        } else {
            return str;
        }
    }

    private int decodeRace(String ethnic) {
        switch(ethnic) {
            case "0":  // Europei
                return 1; // RACE_CAUCASIAN
            case "8": // Africano
                return 2; //RACE_BLACK
            case "1": // Orientali
            case "2": // Hong Kong
            case "3": // Giapponese
            case "4": // Polinesiano
            case "5": // Indiano del nord
            case "6": // Indiano del sud
            case "7": // Pachistano
                return 3; // RACE_ORIENTAL
            default:
                return 0; // RACE_UNSPEC
        }
    }

}
