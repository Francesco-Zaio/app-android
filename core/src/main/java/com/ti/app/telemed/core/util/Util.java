package com.ti.app.telemed.core.util;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.common.Patient;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.common.UserMeasure;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.exceptions.DbException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


public class Util {
	
	private static final String KEY_SHARED_PREFS = "TELEMONITORING_SP";
	private static final String TAG = "Util";
    private static final String KEY_ROCHE_DEMO_MODE = "ROCHE_DEMO_MODE";

	@SuppressLint("NewApi")
	public static File getDir() {
		File sdDir = Environment.getExternalStorageDirectory();
		File result = new File(sdDir, "nithd");
        if(!result.exists())
            if (!result.mkdirs())
                Log.e(TAG, "ERROR: unable to create Directory " + result);
		return result;
	}

	public static File getMeasuresDir() {
		File sdDir = getDir();
		File result = new File(sdDir, "measures");
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


	public static void setRegistryValue(String keyValue, boolean stringValue) {
		SharedPreferences sp = MyApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		SharedPreferences.Editor editor = sp.edit();
		editor.putBoolean(keyValue, stringValue);
		editor.apply();
	}

	public static void setRegistryValue(String keyValue, int value) {
		SharedPreferences sp = MyApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		SharedPreferences.Editor editor = sp.edit();
		editor.putInt(keyValue, value);
		editor.apply();
	}

	public static void setRegistryValue(String keyValue, String stringValue) {
		SharedPreferences sp = MyApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		SharedPreferences.Editor editor = sp.edit();
	    editor.putString(keyValue, stringValue);
	    editor.apply();
	}

	public static int getRegistryIntValue(String keyValue) {
		SharedPreferences sp = MyApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		return sp.getInt(keyValue,0);
	}

	public static String getRegistryValue(String keyValue) {
		SharedPreferences sp = MyApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		return sp.getString(keyValue, "");
	}

	public static boolean getRegistryValue(String keyValue, boolean defaultValue) {
		SharedPreferences sp = MyApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		return sp.getBoolean(keyValue, defaultValue);
	}


	public static boolean storeFile(String fileName, byte[] buffer) {
		try {
			Log.d(TAG, "Store file " + fileName);
			File file = new File(fileName);
			file.createNewFile();
            FileOutputStream fos = new FileOutputStream(file);
			fos.write(buffer);
			fos.close();
            return true;
		}
		catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "storeFile Error: " + e);
            return false;
		}
    }

	public static boolean zipFile(String inputFile, String outputFile) {
		final int BUFFER = 2048;

		try {
			BufferedInputStream origin;
			FileOutputStream dest = new FileOutputStream(outputFile);
			ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));

			byte data[] = new byte[BUFFER];
			String[] l = inputFile.split(File.separator);
			String relativePath = l[l.length-1];
			FileInputStream fi = new FileInputStream(inputFile);
			origin = new BufferedInputStream(fi, BUFFER);
			ZipEntry entry = new ZipEntry(relativePath);
			out.putNextEntry(entry);
			int count;
			while ((count = origin.read(data, 0, BUFFER)) != -1) {
				out.write(data, 0, count);
			}
			origin.close();
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
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

	// Modalità demo che permettono di leggere le misure dal device Coaguchek XS - Roche
    // anche se la misura è già stata scaricata dal device
	public static boolean isDemoRocheMode() {
        return getRegistryValue(Util.KEY_ROCHE_DEMO_MODE, false);
	}
	public static void setDemoRocheMode(boolean demoMode) {
		setRegistryValue(Util.KEY_ROCHE_DEMO_MODE, demoMode);
	}

	public static void resetBTAddresses() {
		DbManager.getDbManager().resetAllBtAddressDevice();
	}
}
