package com.ti.app.telemed.core.util;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import com.ti.app.telemed.core.MyApp;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


public class Util {
	
	private static final String KEY_SHARED_PREFS = "TELEMONITORING_SP";
	private static final String TAG = "Util";
    private static final String KEY_DEMO_MODE = "DEMO_MODE";

	public static final String KEY_WARNING_TIMESTAMP = "KEY_WARNING_TIMESTAMP";

	@SuppressLint("NewApi")
	public static File getDir() {
		File sdDir = Environment
				.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
		File result = new File(sdDir, "nithd");
        if(!result.exists())
            if (!result.mkdirs())
                Log.e(TAG, "ERROR: unable to create Directory " + result);
		return result;
	}

	public static File getAcceptanceDocDir() {
		File sdDir = getDir();
		File result = new File(sdDir, "AcceptanceDocument");
        if(!result.exists())
            if (!result.mkdirs())
                Log.e(TAG, "ERROR: unable to create Directory " + result);
		return result;
	}

	public static File getDischargeDocDir() {
		File sdDir = getDir();
		File result = new File(sdDir, "DischargeDocument");
        if(!result.exists())
            if (!result.mkdirs())
                Log.e(TAG, "ERROR: unable to create Directory " + result);
		return result;
	}

	public static Map<String,String> jsonToStringMap(String jsonString) {
		HashMap<String, String> map = new HashMap<>();
		try {
			JSONObject jObject = new JSONObject(jsonString);
			Iterator<?> keys = jObject.keys();

			while( keys.hasNext() ){
				String key = (String)keys.next();
				String value = jObject.getString(key);
				map.put(key, value);
			}
		} catch (JSONException e){
			e.printStackTrace();
			Log.e(TAG, e.getMessage());
		}
		return map;
	}

	public static Long getRegistryLongValue(String keyValue) {		
		SharedPreferences sp = MyApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);		 		
		return Long.parseLong(sp.getString(keyValue, "0"));
	}
	
	public static String getRegistryValue(String keyValue) {		
		SharedPreferences sp = MyApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		return sp.getString(keyValue, "");
	}

	public static void setRegistryValue(String keyValue, String stringValue) {
		
		Log.d(TAG, "setRegistryValue(" + keyValue + "," + stringValue + ")");
		
		SharedPreferences sp = MyApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		SharedPreferences.Editor editor = sp.edit();
	    editor.putString(keyValue, stringValue);
	    editor.apply();
	}
	
	public static boolean getRegistryValue(String keyValue, boolean defaultValue) {
		
		SharedPreferences sp = MyApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);		 		
		boolean value = sp.getBoolean(keyValue, defaultValue);
		
		Log.d(TAG, "getRegistryValue(" + keyValue + "," + value + ")");
		
		return value;
	}
	
	public static String getRegistryValue(String keyValue, String  defaultValue) {
		
		SharedPreferences sp = MyApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);		 		
		return sp.getString(keyValue, defaultValue);
	}

	public static void setRegistryValue(String keyValue, boolean stringValue) {
		
		Log.d(TAG, "setRegistryValue(" + keyValue + "," + stringValue + ")");
		
		SharedPreferences sp = MyApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		SharedPreferences.Editor editor = sp.edit();
	    editor.putBoolean(keyValue, stringValue);
	    editor.apply();
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
        return getRegistryValue(Util.KEY_DEMO_MODE, false);
	}

	public static void setDemoMode(boolean demoMode) {
		setRegistryValue(Util.KEY_DEMO_MODE, demoMode);
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
