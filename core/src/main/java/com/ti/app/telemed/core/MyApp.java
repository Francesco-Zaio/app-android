package com.ti.app.telemed.core;


import android.app.Application;
import android.content.Context;
import android.os.AsyncTask;

import com.ti.app.telemed.core.configuration.ConfigurationManager;
import com.ti.app.telemed.core.syncmodule.BootReceiver;

/**
 * <h1>Inizializzazione Libreria</h1>
 * <p>Questa classe estende la classe Android android.app.Application ed esegue tutte le inizializzzioni necessarie alla libreria.</p>
 * @author  Massimo Martini
 * @version 1.0
 * @since   2017-11-09
 */

public class MyApp extends Application {

	private static MyApp instance;
    private static ConfigurationManager configurationManager;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate() {
        super.onCreate();

        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                // check if the update cfg Alarm is already registered
                try {
                    BootReceiver.registerAlarm(MyApp.this);
                    configurationManager = new ConfigurationManager();
                    configurationManager.init();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public MyApp() {
        super();
        instance = this;
    }

    /**
     * Restituisce il Context dell'applicazione
     * @return      {@link Context}
     */
    public static Context getContext() {
        return instance;
    }

    /**
     * Restituisce l'istanza di Configuration Manager
     * @return      {@link ConfigurationManager}
     */
    public static ConfigurationManager getConfigurationManager(){
        return configurationManager;
    }
}
