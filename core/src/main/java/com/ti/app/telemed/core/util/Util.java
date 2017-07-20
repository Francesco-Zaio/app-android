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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


public class Util {
	
	private static final String KEY_SHARED_PREFS = "TELEMONITORING_SP";
	private static final String KEY_BTGT_CALIBRATE_VALUE = "BTGT_CALIBRATE_VALUE";
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
	    editor.commit();
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
	    editor.commit();
	}
	
	public static String getCurrentCalibrationCode(){
		String cal = getRegistryValue(KEY_BTGT_CALIBRATE_VALUE);
		if((cal == null) || cal.isEmpty()){
			cal = "-";
		}
		return "[" + cal + "]";
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


	public static void removeRegistryValue(String keyValue) {
		
		Log.d(TAG, "removeRegistryValue(" + keyValue +  ")");
		
		SharedPreferences sp = MyApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		SharedPreferences.Editor editor = sp.edit();
	    editor.remove(keyValue);
	    editor.commit();
	}

    public static boolean isToday(String date) {
        Log.i(TAG, "isToday");

        boolean retValue = false;

        long currentDateTime = System.currentTimeMillis();
        Date currentDate = new Date(currentDateTime);
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String currentDateString = df.format(currentDate);

        Log.i(TAG, "isToday " + currentDateString);
        Log.i(TAG, "isToday " + date);

        if( date.equalsIgnoreCase(currentDateString) )
            retValue = true;

        return retValue;
    }

    public static boolean isToday(Date date) {
        Log.i(TAG, "isToday");

        boolean retValue = false;

        long currentDateTime = System.currentTimeMillis();
        Date currentDate = new Date(currentDateTime);
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String currentDateString = df.format(currentDate);

        Log.i(TAG, "isToday " + currentDateString);
        Log.i(TAG, "isToday " + date);

        if( df.format(date).equalsIgnoreCase(currentDateString) )
            retValue = true;

        return retValue;
    }

    public static Date getStartOfDay(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DATE);

        calendar.set(year, month, day, 0, 0, 0);

        return calendar.getTime();
    }

    public static Date getEndOfDay(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DATE);
        calendar.set(year, month, day, 23, 59, 59);
        return calendar.getTime();
    }
}
