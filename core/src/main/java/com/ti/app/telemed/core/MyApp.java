package com.ti.app.telemed.core;


import android.app.Application;
import android.content.Context;

import com.ti.app.telemed.core.configuration.ConfigurationManager;
import com.ti.app.telemed.core.syncmodule.BootReceiver;

public class MyApp extends Application {

	private static MyApp instance;
    private static ConfigurationManager configurationManager;

    @Override
    public void onCreate() {
        super.onCreate();

        // check if the update cfg Alarm is already registered
        BootReceiver.registerAlarm(this);
    }

    public MyApp() {
        instance = this;
    }

    public static Context getContext() {
        return instance;
    }

    public static ConfigurationManager getConfigurationManager(){
        if(configurationManager == null) {
            configurationManager = new ConfigurationManager();
        }
        return configurationManager;
    }
}
