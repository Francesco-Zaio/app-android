package com.ti.app.telemed.core.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Environment;
import android.util.Log;

import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.measuremodule.MeasureManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
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


	public static boolean isNetworkConnected() {
		ConnectivityManager cm = (ConnectivityManager) MyApp.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
		return (cm != null) && (cm.getActiveNetworkInfo() != null);
	}

	/**
	 * Genera un timestamp testuale nel formato richiesto dalla piattaforma.
	 * @param calendar data da convertire
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

    /**
     * Restituisce la directory di base dove sono memorizzati i files dell'applicazione
     * @return  directory
     */
	private static File getDir() {
		File result = MyApp.getContext().getExternalFilesDir(null);
		// verifica se non esiste uno storage esterno ed in tal caso utilizza quello interno
		if (result == null)
		    result = MyApp.getContext().getFilesDir();
		//File sdDir = Environment.getExternalStorageDirectory();
		// File result = new File(sdDir, "nithd");
        if(!result.exists())
            if (!result.mkdirs())
                Log.e(TAG, "ERROR: unable to create Directory " + result);
		return result;
	}

	/**
	 * Restitusce la directory dove memorizzare i files delle misure del paziente
	 * @param patientId	Identificativo del paziente
	 * @return	Directory delle misure del paziente (null in caso di errore)
	 */
	public static File getMeasuresDir(String patientId) {
        if (patientId == null || patientId.isEmpty())
            return null;
		File sdDir = getDir();
        File result = new File(sdDir, patientId);
        if(!result.exists())
            if (!result.mkdirs()) {
                Log.e(TAG, "ERROR: unable to create Directory " + result);
                return null;
            }
        result = new File(result, "measures");
		if(!result.exists())
			if (!result.mkdirs()) {
                Log.e(TAG, "ERROR: unable to create Directory " + result);
                return null;
            }
		return result;
	}

	/**
	 * Restitusce la directory dove memorizzare il tipo di documento del paziente
	 * @param docType	Tipo di documento
	 * @param patientId	Identificativo del paziente
	 * @return	Directory (null in caso di errore)
	 */
	public static File getDocumentDir(MeasureManager.DocumentType docType, String patientId) {
		if (patientId == null || patientId.isEmpty())
			return null;
        File sdDir = getDir();
        File result = new File(sdDir, patientId);
        if(!result.exists())
            if (!result.mkdirs()) {
                Log.e(TAG, "ERROR: unable to create Directory " + result);
                return null;
            }
		result = new File(result, docType.toString());
        if(!result.exists())
            if (!result.mkdirs()) {
                Log.e(TAG, "ERROR: unable to create Directory " + result);
                return null;
            }
        return result;
    }

	/**
	 * Esegue il parsing di una Stringa in formato JSON (key:value) in una HashMap di Stringhe.
	 * @param jsonString	Stringa in formato Json da convertire
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
	 * Rimuove una chiave dal registry.
	 * @param key	chiave
	 */
	public static void removeRegistryKey(String key) {
		SharedPreferences sp = MyApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		SharedPreferences.Editor editor = sp.edit();
		editor.remove(key);
		editor.apply();
	}

	/**
	 * Scrive un booleano dal registry.
	 * @param key	chiave
	 * @param value	valore
	 */
	public static void setRegistryValue(String key, boolean value) {
		SharedPreferences sp = MyApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		SharedPreferences.Editor editor = sp.edit();
		editor.putBoolean(key, value);
		editor.apply();
	}

	/**
	 * Scrive un intero dal registry.
	 * @param key	chiave
	 * @param value	valore
	 */
	public static void setRegistryValue(String key, int value) {
		SharedPreferences sp = MyApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		SharedPreferences.Editor editor = sp.edit();
		editor.putInt(key, value);
		editor.apply();
	}

	/**
	 * Scrive una Stringa dal registry.
	 * @param key	chiave
	 * @param value	valore
	 */
	public static void setRegistryValue(String key, String value) {
		SharedPreferences sp = MyApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		SharedPreferences.Editor editor = sp.edit();
		if (value != null)
			editor.putString(key, value);
		else
			editor.remove(key);
	    editor.apply();
	}

	/**
	 * Legge un intero dal registry.
	 * @param key	chiave
	 * @return	valore letto (-1 se non trovato)
	 */
	public static int getRegistryIntValue(String key) {
		SharedPreferences sp = MyApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		return sp.getInt(key,-1);
	}

	/**
	 * Legge una Stringa dal registry.
	 * @param key	chiave
	 * @return valore letto (stringa vuota se non trovata)
	 */
	public static String getRegistryValue(String key) {
		SharedPreferences sp = MyApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		return sp.getString(key, "");
	}

	/**
	 * Legge un boolean dal Registry.
	 * @param key	chiave
	 * @param defaultValue valore di default
	 * @return valore letto (defaultValue se non trovato)
	 */
	public static boolean getRegistryValue(String key, boolean defaultValue) {
		SharedPreferences sp = MyApp.getContext().getSharedPreferences(KEY_SHARED_PREFS, 0);
		return sp.getBoolean(key, defaultValue);
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
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "storeFile Error: " + e);
            return false;
		}
    }

	/**
	 * Esegue un dump di un array di bytes in valore esadecimali in un file.
	 * @param fileName		Path del file in cui memorizzare l'array.
	 * @param buffer		Array di bytes da memorizzare.
	 * @return			true in caso di successo o false in caso di errore.
	 */
	public static void logFile(String fileName, byte[] buffer, String tag, boolean append) {
		try {
			File dir = Environment.getExternalStorageDirectory();
			Log.d(TAG, "Store file " + fileName);
			File file = new File(dir, fileName);
			Log.d(TAG, "Logging buffer to " + file.getAbsolutePath());
			BufferedWriter bw = new BufferedWriter(new FileWriter(file, append));
			bw.write(tag +"\n");
			if (buffer != null)
				for (byte b : buffer) {
					bw.write(Integer.toString(b & 0xff));
					bw.write("\n");
				}
			bw.flush();
			bw.close();
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "storeFile Error: " + e);
		}
	}
    /**
     * Comprime inputFile e lo memorizza in outputFile. Se inputFile e' una directory
     * tutti i files della directory vengono compressi e memorizzati in outputFile.
     * @param inputFile		file o directory da comprimere.
     * @param outputFile	file di destinazione.
     * @return	true in caso di successo o false in caso di errore.
     */
    public static boolean zipFile(File inputFile, File outputFile) {
        final int BUFFER = 4096;
        ZipOutputStream out = null;
        BufferedInputStream origin = null;
        try {
            File[] files;
            if (inputFile.isDirectory())
                files = inputFile.listFiles();
            else
                files = new File[]{inputFile};
            FileOutputStream dest = new FileOutputStream(outputFile);
            out = new ZipOutputStream(new BufferedOutputStream(dest));
            for (File file : files) {
                byte data[] = new byte[BUFFER];
                String unmodifiedFilePath = file.getPath();
                int start = file.getParent().length();
                if (start > 0)
                    start += 1;
                String relativePath = unmodifiedFilePath.substring(start);
                FileInputStream fi = new FileInputStream(unmodifiedFilePath);
                origin = new BufferedInputStream(fi, BUFFER);
                ZipEntry entry = new ZipEntry(relativePath);
                out.putNextEntry(entry);
                int count;
                while ((count = origin.read(data, 0, BUFFER)) != -1) {
                    out.write(data, 0, count);
                }
                origin.close();
                origin = null;
            }
            out.close();
            return true;
        } catch (Exception e) {
            try {
                if (out!=null)
                    out.close();
                if (origin!=null)
                    origin.close();
            } catch (Exception e2){
            }
            e.printStackTrace();
            return false;
        }
    }

	/**
	 *
	 * @param src
	 * @param dst
	 * @throws IOException
	 */
	public static void copy(File src, File dst) throws IOException {
		InputStream in = new FileInputStream(src);
		try {
			OutputStream out = new FileOutputStream(dst);
			try {
				// Transfer bytes from in to out
				byte[] buf = new byte[1024];
				int len;
				while ((len = in.read(buf)) > 0) {
					out.write(buf, 0, len);
				}
			} finally {
				out.close();
			}
		} finally {
			in.close();
		}
	}

    /**
     * Rimuove il files passato, se e' una directory rimuove anche le eventuali sottodirectory
     * (equivale al comando Unix 'rm -rf')
     * @param file  file da rimuovere (se è una directory rimmuove tutto l'albero sottostante)
     */
    public static void deleteTree(File file) {
        if (file.exists())
            if (file.isDirectory()) {
                for (File f2 : file.listFiles())
                    deleteTree(f2);
                file.delete();
            }
            else
                file.delete();
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

	/**
	 * Indica se è abilitata la modalita' demo per il device Coaguchek XS - Roche
	 * per leggere la misura anche se è già stata scaricata dal device.
	 * @return	true se abilitata, false altrimenti
	 */
	public static boolean isDemoRocheMode() {
        return getRegistryValue(Util.KEY_ROCHE_DEMO_MODE, false);
	}

	/**
	 * Imposta la modalita demo per il device Coaguchek XS - Roche che permette
	 * di leggere la misura dal device anche se è già stata scaricata.
	 * @param demoMode true per impostare la modalita' demo, false altrimenti
	 */
	public static void setDemoRocheMode(boolean demoMode) {
		setRegistryValue(Util.KEY_ROCHE_DEMO_MODE, demoMode);
	}

	/**
	 * Resetta tutti gli i MAC address dei devices Blutooth memorizzati.
	 */
	public static void resetBTAddresses() {
		DbManager.getDbManager().resetAllBtAddressDevice();
	}

	/**
	 * Converte l'array di bytes passato in una stringa esadecimale
	 * @param array	array di bytes da convertire
	 * @return	stringa in formato esadecimale
	 */
	public static String toHexString(byte[] array){
		String tmp = "";
		for (int i = 0; i < array.length; i++) {
			tmp += " " + Integer.toHexString(array[i] & 0x000000ff);
		}
		return tmp;
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

	public static boolean isEqual(byte[] a1, byte[] a2, int size) {
		if (size > a1.length || size > a2.length)
			return false;
		for(int i=0;i<size;i++)
			if (a1[i] != a2[i])
				return false;
		return true;
	}
}
