package com.ti.app.telemed.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

public class ResourceManager {

	private static ResourceManager rm;
	private Properties properties;

	private static final String PROPS_it_IT = "resource_it_IT.properties";
	private static final String PROPS_en_GB = "resource_en_GB.properties";
	
	private static final String TAG = "ResourceManager";
	
	private ResourceManager(String language, String country) {
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

	public static ResourceManager getResource() {
		if (rm == null) {
			Locale locale = Locale.getDefault();			
			rm = new ResourceManager(locale.getLanguage(), locale.getCountry());
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
				value = "";
				Log.w(TAG, "ResourceManager Value not found: " + key);
			}
			 
			return value; 
		} catch (Exception e) {
			// Per evitare di piantare l'applicazione per la mancanza di
			// una label nel properties
			return "";
		}
	}

}
