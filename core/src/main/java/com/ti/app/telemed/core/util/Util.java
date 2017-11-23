package com.ti.app.telemed.core.util;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.dbmodule.DbManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Contiene vari metodi di conversione, scrittura/lettura registry, e recupero path filesystem
 * utilizzati.
 */
public class Util {
	
	private static final String KEY_SHARED_PREFS = "TELEMONITORING_SP";
	private static final String TAG = "Util";
    private static final String KEY_ROCHE_DEMO_MODE = "ROCHE_DEMO_MODE";
    private static final String TIMESTAMPFORMAT = "yyyyMMddHHmmss";

	/**
	 * Genera un timestamp testuale nel formato richiesto dalla piattaforma (ad esempio timestamp misure).
	 * @param calendar
	 * @return Stringa contenente il timestamp.
	 */
    public static String getTimestamp(Calendar calendar) {
        if (calendar == null)
            calendar = new GregorianCalendar();
        return new SimpleDateFormat(TIMESTAMPFORMAT, Locale.ENGLISH).format(calendar.getTime());
    }

	/**
	 * Legge un timestamp testuale nel formato della piattaforma e lo converte in un oggetto Date
	 * @param dateString	Timestamp.
	 * @return				data o null in caso di errore.
	 */
	public static Date parseTimestamp(String dateString) {
        try {
            if (dateString != null && ! dateString.isEmpty())
                return new SimpleDateFormat(TIMESTAMPFORMAT, Locale.ENGLISH).parse(dateString);
        } catch (ParseException e) {
            Log.e(TAG, "parseTimestamp: ParseException");
        }
        return null;
    }

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

	/**
	 * Esegue il parsing di una Stringa in formato JSON in una HashMap di Stringhe.
	 * @param jsonString
	 * @return HashMap<String,String> (vuota in caso di errore).
	 */
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

	/**
	 * Scrive un booleano dal registry.
	 * @param keyValue
	 * @param stringValue
	 */
	public static void setRegistryValue(String keyValue, boolean stringValue) {
		SharedPreferences sp = MyApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		SharedPreferences.Editor editor = sp.edit();
		editor.putBoolean(keyValue, stringValue);
		editor.apply();
	}

	/**
	 * Scrive un intero dal registry.
	 * @param keyValue
	 * @param value
	 */
	public static void setRegistryValue(String keyValue, int value) {
		SharedPreferences sp = MyApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		SharedPreferences.Editor editor = sp.edit();
		editor.putInt(keyValue, value);
		editor.apply();
	}

	/**
	 * Scrive una Stringa dal registry.
	 * @param keyValue
	 * @param stringValue
	 */
	public static void setRegistryValue(String keyValue, String stringValue) {
		SharedPreferences sp = MyApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		SharedPreferences.Editor editor = sp.edit();
	    editor.putString(keyValue, stringValue);
	    editor.apply();
	}
	/**
	 * Legge un intero dal registry.
	 * @param keyValue
	 * @return
	 */
	public static int getRegistryIntValue(String keyValue) {
		SharedPreferences sp = MyApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		return sp.getInt(keyValue,0);
	}

	/**
	 * Legge una Stringa dal registry.
	 * @param keyValue
	 * @return
	 */
	public static String getRegistryValue(String keyValue) {
		SharedPreferences sp = MyApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		return sp.getString(keyValue, "");
	}

	/**
	 * Legge un boolean dal Registry.
	 * @param keyValue
	 * @param defaultValue
	 * @return
	 */
	public static boolean getRegistryValue(String keyValue, boolean defaultValue) {
		SharedPreferences sp = MyApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		return sp.getBoolean(keyValue, defaultValue);
	}

	/**
	 * Momorizza un array di bytes in un file.
	 * @param fileName		Path del file in cui memorizzare l'array.
	 * @param buffer		Array di bytes da memorizzare.
	 * @return			true in caso di successo o false in caso di errore.
	 */
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

	/**
	 * Comprime inputFile e lo memorizza in outputFile.
	 * @param inputFile		path del file da comprimere.
	 * @param outputFile	pathe del file di destinazione.
	 * @return	true in caso di successo o false in caso di errore.
	 */
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

	/**
	 * restutuisce la differenza in ore fra due timestamp in formato Time Unix (millisecondi).
	 * @param t1	primo timestamp.
	 * @param t2	secondo timestamp.
	 * @return		differenza in ore.
	 */
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

	/**
	 * Resetta tutti gli i MAC address dei devices Blutooth memorizzati.
	 */
	public static void resetBTAddresses() {
		DbManager.getDbManager().resetAllBtAddressDevice();
	}
}
