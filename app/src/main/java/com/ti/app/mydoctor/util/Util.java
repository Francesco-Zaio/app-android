package com.ti.app.mydoctor.util;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.MyDoctorApp;
import com.ti.app.telemed.core.common.Device;
import com.ti.app.telemed.core.util.GWConst;


import java.io.File;
import java.io.FileOutputStream;

public class Util {
	
	public static final String KEY_SHARED_PREFS = "TELEMONITORING_SP";
	public static final String KEY_BTGT_CALIBRATE_VALUE = "BTGT_CALIBRATE_VALUE";
	private static final String TAG = "Util";
	public static final String KEY_AUTO_UPDATE = "KEY_AUTO_UPDATE";
	public static final String KEY_AUTO_UPDATE_DATE = "KEY_AUTO_UPDATE_DATE";
	public static final String KEY_WARNING_TIMESTAMP = "KEY_WARNING_TIMESTAMP";
	public static final String KEY_FORCE_LOGOUT = "KEY_FORCE_LOGOUT";
	public static final String KEY_LAST_USER = "KEY_LAST_USER";
	public static final String KEY_URL_QUIZ = "KEY_URL_QUIZ";
	public static final String URL_QUIZ_DEFAULT = "group/pazienti/lista-attestati";
	public static final String KEY_MEASURE_TYPE = "measureType.";

	public static final String KEY_GRID_LAYOUT = "KEY_GRID_LAYOUT";

	
	@SuppressLint("NewApi")
	public static File getDir() {
		File sdDir = Environment
				.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
		File result = new File(sdDir, "nithd");
		result.mkdirs();

		return result;
	}
	
	public static String toHexString(byte[] array, int max){
    	String tmp = "";
    	for (int i = 0; i < array.length; i++) {
			tmp += " " + Integer.toHexString(array[i] & 0x000000ff);
			
			if (i == max)
				return tmp;
		}
    	return tmp;
    }
	
	
	public static String toHexString(byte[] array){
    	String tmp = "";
    	for (int i = 0; i < array.length; i++) {
			tmp += " " + Integer.toHexString(array[i] & 0x000000ff);
		}
    	return tmp;
    }
	
	public static String toHexString(char[] array){
    	String tmp = "";
    	for (int i = 0; i < array.length; i++) {
			tmp += " " + Integer.toHexString(array[i] & 0x000000ff);
		}
    	return tmp;
    }
	
	public static String getString(int id){
		return MyDoctorApp.getContext().getResources().getString(id);
	}

	public static boolean isGlucoTelDevice(Device device) {
		return (device.getModel() != null && device.getModel().equalsIgnoreCase(GWConst.KBTGT));
	}
	
	public static boolean isC40(Device device) {
		return  (
				(device.getModel() != null && device.getModel().equalsIgnoreCase(GWConst.KANDPR))
				||
				(device.getModel() != null && device.getModel().equalsIgnoreCase(GWConst.KANDPS))
				);
	}
	
	public static boolean isCamera(Device device) {
		return  (
				(device.getModel() != null && device.getModel().equalsIgnoreCase(GWConst.KCAMERA))
				);
	}
	
	public static boolean isStmDevice(Device device) {
		return (device.getModel() != null && device.getModel().equalsIgnoreCase(GWConst.KSTM));
	}
	
	public static boolean glucoTelNotCalibrated(){
		//return isEmptyString(getRegistryValue(Util.KEY_BTGT_CALIBRATE_VALUE + "_" + DbManager.getDbManager().getActiveUser().getId()));
		return isEmptyString(getRegistryValue(Util.KEY_BTGT_CALIBRATE_VALUE/* + "_" + DbManager.getDbManager().getActiveUser().getId()*/));
	}

	public static boolean isNoPairingDevice(Device device){
		return device.getModel().equalsIgnoreCase(GWConst.KEcgMicro)
				|| device.getModel().equalsIgnoreCase(GWConst.KCcxsRoche)
				|| device.getModel().equalsIgnoreCase(GWConst.KOximeterNon)
				|| device.getModel().equalsIgnoreCase(GWConst.KFORATherm)
				|| device.getModel().equalsIgnoreCase(GWConst.KPO3IHealth)
				|| device.getModel().equalsIgnoreCase(GWConst.KBP5IHealth)
				|| device.getModel().equalsIgnoreCase(GWConst.KHS4SIHealth)
				|| device.getModel().equalsIgnoreCase(GWConst.KBP550BTHealth)
				|| isManualMeasure(device);
	}

	public static int getIconId(String measure) {
		if(measure.equalsIgnoreCase(GWConst.KMsrEcg))
			return R.drawable.ecg_icon;
		else if(measure.equalsIgnoreCase(GWConst.KMsrPeso))
			return R.drawable.peso_icon;
		else if(measure.equalsIgnoreCase(GWConst.KMsrPres))
			return R.drawable.pressione_icon;
		else if(measure.equalsIgnoreCase(GWConst.KMsrOss))
			return R.drawable.ossimetria_icon;
		else if(measure.equalsIgnoreCase(GWConst.KMsrProtr))
			return R.drawable.inr_icon;
		else if(measure.equalsIgnoreCase(GWConst.KMsrGlic))
			return R.drawable.glicemia_icon;
		else if(measure.equalsIgnoreCase(GWConst.KMsrSpir))
			return R.drawable.spirometria_icon;
		else if(measure.equalsIgnoreCase(GWConst.KMsrTemp))
			return R.drawable.temperatura_icon;
		else if(measure.equalsIgnoreCase(GWConst.KMsrAritm))
			return R.drawable.aritmia_icon;
		else if(measure.equalsIgnoreCase(GWConst.KMsrImg))
			return R.drawable.immagini_icon;
		else
			return R.drawable.icon;
	}	
	
	public static int getSmallIconId(String measure) {
		if(measure.equalsIgnoreCase(GWConst.KMsrEcg))
			return R.drawable.small_ecg_icon;
		else if(measure.equalsIgnoreCase(GWConst.KMsrPeso))
			return R.drawable.small_peso_icon;
		else if(measure.equalsIgnoreCase(GWConst.KMsrPres))
			return R.drawable.small_pressione_icon;
		else if(measure.equalsIgnoreCase(GWConst.KMsrOss))
			return R.drawable.small_ossimetria_icon;
		else if(measure.equalsIgnoreCase(GWConst.KMsrProtr))
			return R.drawable.small_inr_icon;
		else if(measure.equalsIgnoreCase(GWConst.KMsrGlic))
			return R.drawable.small_glicemia_icon;
		else if(measure.equalsIgnoreCase(GWConst.KMsrSpir))
			return R.drawable.small_spirometria_icon;
		else if(measure.equalsIgnoreCase(GWConst.KMsrTemp))
			return R.drawable.small_temperatura_icon;
		else if(measure.equalsIgnoreCase(GWConst.KMsrAritm))
			return R.drawable.small_aritmia_icon;
		else if(measure.equalsIgnoreCase(GWConst.KMsrImg))
			return R.drawable.small_immagini_icon;
		else
			return R.drawable.icon;
	}	
	
	public static int getIconRunningId(String measure) {
		if(measure.equalsIgnoreCase(GWConst.KMsrAritm))
			return R.drawable.aritmia_icon_run;
		else
			return getIconId(measure);
	}
	
	public static boolean isManualMeasure(Device device) {
		return (device != null && device.getModel() != null && device.getModel().equalsIgnoreCase(GWConst.KManualMeasure));
	}
	
	public static Long getRegistryLongValue(String keyValue) {		
		SharedPreferences sp = MyDoctorApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		return Long.parseLong(sp.getString(keyValue, "0"));
	}
	
	public static String getRegistryValue(String keyValue) {		
		SharedPreferences sp = MyDoctorApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		return sp.getString(keyValue, "");
	}

	public static void setRegistryValue(String keyValue, String stringValue) {
		
		Log.d(TAG, "setRegistryValue(" + keyValue + "," + stringValue + ")");
		
		SharedPreferences sp = MyDoctorApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		SharedPreferences.Editor editor = sp.edit();
	    editor.putString(keyValue, stringValue);
	    editor.commit();
	}
	
	public static boolean getRegistryValue(String keyValue, boolean defaultValue) {
		
		SharedPreferences sp = MyDoctorApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		boolean value = sp.getBoolean(keyValue, defaultValue);
		
		Log.d(TAG, "getRegistryValue(" + keyValue + "," + value + ")");
		
		return value;
	}
	
	public static String getRegistryValue(String keyValue, String  defaultValue) {
		
		SharedPreferences sp = MyDoctorApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		return sp.getString(keyValue, defaultValue);
	}

	public static void setRegistryValue(String keyValue, boolean stringValue) {
		
		Log.d(TAG, "setRegistryValue(" + keyValue + "," + stringValue + ")");
		
		SharedPreferences sp = MyDoctorApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		SharedPreferences.Editor editor = sp.edit();
	    editor.putBoolean(keyValue, stringValue);
	    editor.commit();
	}
	
	public static String getCurrentCalibrationCode(){
		//String cal = getRegistryValue(KEY_BTGT_CALIBRATE_VALUE + "_" + DbManager.getDbManager().getActiveUser().getId());
		String cal = getRegistryValue(KEY_BTGT_CALIBRATE_VALUE/* + "_" + DbManager.getDbManager().getActiveUser().getId()*/);
		if(isEmptyString(cal)){
			cal = "-";
		}
		return "[" + cal + "]";
	}
	
	public static boolean isEmptyString(String s) {
		return s == null || s.equals("");
	}

	public static byte[] hexStringToByteArray(String s) {

		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character
					.digit(s.charAt(i + 1), 16));
		}
		return data;
	}

	public static void storeFile(String fileName, byte[] buffer) {
		
		String folderName = Environment.getExternalStorageDirectory().toString() + "/bgw-log/";
		File folderFile = new File(folderName);
		
		try {
			folderFile.mkdirs();
		} catch (Exception e) {
			e.printStackTrace();
			Log.d(TAG, "storeFile mkdirs: " + e);
		}
		
		try {
			Log.d(TAG, "Store file " + fileName + " in " + folderName);
			File file = new File(folderFile, fileName);
			file.createNewFile();
			FileOutputStream fos = new FileOutputStream(file);
			fos.write(buffer);
			fos.close();			
		}
		catch (Exception e) {
			e.printStackTrace();
			Log.d(TAG, "storeFile Error: " + e);
		}
	}
	
	public static boolean isDemoMode() {
		boolean demoMode = false;
		
		try {		
			String baseDir = Environment.getExternalStorageDirectory().getAbsolutePath();
			String fileName = "tlm.demo";
			
			File f = new File(baseDir + File.separator + fileName);
			Log.d(TAG, "exists? " + f.toString());
			
			demoMode = f.exists();
			f = null;			
		}
		catch (Exception e) {
			demoMode = false;
		}		
		Log.i(TAG, "isDemoMode=" + demoMode);
		
		return demoMode;
	}

	public static int getDiffHours(long t1, long t2) {
		
		int result = 0;
		try {
			result = Math.abs( (int) ((t1-t2) / (60 * 60 * 1000)) );
		}
		catch(Exception e) {}
		return result;
	}
}
