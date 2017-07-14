package com.ti.app.mydoctor;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

import com.ti.app.telemed.core.MyApp;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

public class AppResourceManager {

	private static AppResourceManager rm;
	private Properties properties;

	private static final String PROPS_it_IT = "appresource_it_IT.properties";
	private static final String PROPS_en_GB = "appresource_en_GB.properties";

	private static final String TAG = "AppResourceManager";

	private AppResourceManager(String language, String country) {
		// Read from the /assets directory
		try {
			Resources resources = MyApp.getContext().getResources();
			AssetManager assetManager = resources.getAssets();

		    InputStream inputStream = assetManager.open(getFilename(language, country));
		    properties = new Properties();
		    properties.load(inputStream);
		    Log.i(TAG, "The properties are now loaded");
		} catch (IOException e) {
			Log.e(TAG,"Failed to open microlog property file");
		    e.printStackTrace();
		}
	}

	private String getFilename(String language, String country) {
		String filename = PROPS_en_GB;
		if("it".equals(language)){ //&& "IT".equals(country)){
			filename = PROPS_it_IT;
		} else if("en".equals(language)){
			filename = PROPS_en_GB;
		}
		return filename;
	}

	public static AppResourceManager getResource() {
		if (rm == null) {
			Locale locale = Locale.getDefault();			
			rm = new AppResourceManager(locale.getLanguage(), locale.getCountry());
		}
		return rm;
	}
	
	public void closeResource(){
		rm = null;
	}

	public String getString(String key) {
		try {
			String value = properties.getProperty(key);
			
			if (value == null) {
				value = "??" + key + "??";
				Log.w(TAG, "AppResourceManager Value not found: " + key);
			}
			 
			return value; 
		} catch (Exception e) {
			// Per evitare di piantare l'applicazione per la mancanza di
			// una label nel properties
			return "??" + key + "??";
		}
	}

}
