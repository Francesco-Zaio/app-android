package com.ti.app.mydoctor.util;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.MyDoctorApp;
import com.ti.app.telemed.core.btdevices.ComftechManager;
import com.ti.app.telemed.core.common.Device;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.util.GWConst;


import java.io.File;

public class AppUtil {
	
	private static final String KEY_SHARED_PREFS = "TELEMONITORING_SP";
	private static final String TAG = "AppUtil";
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

	public static byte[] hexStringToByteArray(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character
					.digit(s.charAt(i + 1), 16));
		}
		return data;
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
	
	public static boolean isCamera(Device device) {
		return ((device.getModel() != null && device.getModel().equalsIgnoreCase(GWConst.DEVICE_CAMERA)));
	}

	public static int getIconId(String measure) {
		switch (Measure.getFamily(measure)) {
			case BIOMETRICA:
				if (measure.equalsIgnoreCase(GWConst.KMsrEcg))
					return R.drawable.ecg_icon;
				else if (measure.equalsIgnoreCase(GWConst.KMsrPeso))
					return R.drawable.peso_icon;
				else if (measure.equalsIgnoreCase(GWConst.KMsrPres))
					return R.drawable.pressione_icon;
				else if (measure.equalsIgnoreCase(GWConst.KMsrOss))
					return R.drawable.ossimetria_icon;
				else if (measure.equalsIgnoreCase(GWConst.KMsrProtr))
					return R.drawable.inr_icon;
				else if (measure.equalsIgnoreCase(GWConst.KMsrGlic))
					return R.drawable.glicemia_icon;
				else if (measure.equalsIgnoreCase(GWConst.KMsrSpir))
					return R.drawable.spirometria_icon;
				else if (measure.equalsIgnoreCase(GWConst.KMsrTemp))
					return R.drawable.temperatura_icon;
				else if (measure.equalsIgnoreCase(GWConst.KMsrImg))
					return R.drawable.immagini_icon;
				else if (measure.equalsIgnoreCase(GWConst.KMsr_Comftech))
					if (ComftechManager.getInstance().getMonitoringUserId().isEmpty())
						return R.drawable.cardioresp_icon;
					else
						return R.drawable.cardioresp_off_icon;
				else
					return R.drawable.icon;
			case DOCUMENTO:
				return R.drawable.documento_icon;
			default:
				return R.drawable.icon;
		}
	}
	
	public static int getSmallIconId(String measure) {
		switch (Measure.getFamily(measure)) {
			case BIOMETRICA:
				if (measure.equalsIgnoreCase(GWConst.KMsrEcg))
					return R.drawable.small_ecg_icon;
				else if (measure.equalsIgnoreCase(GWConst.KMsrPeso))
					return R.drawable.small_peso_icon;
				else if (measure.equalsIgnoreCase(GWConst.KMsrPres))
					return R.drawable.small_pressione_icon;
				else if (measure.equalsIgnoreCase(GWConst.KMsrOss))
					return R.drawable.small_ossimetria_icon;
				else if (measure.equalsIgnoreCase(GWConst.KMsrProtr))
					return R.drawable.small_inr_icon;
				else if (measure.equalsIgnoreCase(GWConst.KMsrGlic))
					return R.drawable.small_glicemia_icon;
				else if (measure.equalsIgnoreCase(GWConst.KMsrSpir))
					return R.drawable.small_spirometria_icon;
				else if (measure.equalsIgnoreCase(GWConst.KMsrTemp))
					return R.drawable.small_temperatura_icon;
				else if (measure.equalsIgnoreCase(GWConst.KMsrImg))
					return R.drawable.small_immagini_icon;
				else if (measure.equalsIgnoreCase(GWConst.KMsr_Comftech))
					return R.drawable.small_cardioresp_icon;
				else
					return R.drawable.icon;
			case DOCUMENTO:
				return R.drawable.small_immagini_icon;
			default:
				return R.drawable.icon;
		}
	}
	
	public static boolean isManualMeasure(Device device) {
		return (device != null && device.getModel() != null && device.getModel().equalsIgnoreCase(GWConst.DEVICE_MANUAL));
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
	    editor.apply();
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
	    editor.apply();
	}

	public static int getDiffHours(long t1, long t2) {
		
		int result = 0;
		try {
			result = Math.abs( (int) ((t1-t2) / (60 * 60 * 1000)) );
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return result;
	}
}
