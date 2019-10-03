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
	private static final String PROPS_es_ES = "resource_es_ES.properties";
	private static final String PROPS_pt_BR = "resource_pt_BR.properties";
	private static final String PROPS_fr_FR = "resource_fr_FR.properties";

	private static final String TAG = "ResourceManager";
	
	private ResourceManager(String language) {
		// Read from the /assets directory
		try {
			Resources resources = MyApp.getContext().getResources();
			AssetManager assetManager = resources.getAssets();

		    InputStream inputStream = assetManager.open(getFilename(language));
		    properties = new Properties();
		    properties.load(inputStream);
		    Log.i(TAG, "The properties are now loaded");
		} catch (IOException e) {
			Log.e(TAG,"Failed to open property file");
		    e.printStackTrace();
		}
	}

	private String getFilename(String language) {
		String filename = PROPS_en_GB;
		if("it".equals(language)){ //&& "IT".equals(country)){
			filename = PROPS_it_IT;
		} else if("en".equals(language)){
			filename = PROPS_en_GB;
		} else if("es".equals(language)){
			filename = PROPS_es_ES;
		} else if("pt".equals(language)){
			filename = PROPS_pt_BR;
		} else if("fr".equals(language)){
			filename = PROPS_fr_FR;
		}
		return filename;
	}

	public static ResourceManager getResource() {
		if (rm == null) {
			Locale locale = Locale.getDefault();			
			rm = new ResourceManager(locale.getLanguage());
		}
		return rm;
	}

	public String getString(String key) {
		try {
			String value = properties.getProperty(key);
			
			if (value == null) {
				value = "??" + key + "??";
				Log.w(TAG, "ResourceManager Value not found: " + key);
			}
			 
			return value; 
		} catch (Exception e) {
			// Per evitare di piantare l'applicazione per la mancanza di
			// una label nel properties
			return "??" + key + "??";
		}
	}

}
