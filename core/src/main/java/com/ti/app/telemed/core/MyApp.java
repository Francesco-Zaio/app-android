package com.ti.app.telemed.core;

import android.app.Application;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.ti.app.telemed.core.configuration.ConfigurationManager;
import com.ti.app.telemed.core.syncmodule.SyncWorker;

import java.util.concurrent.TimeUnit;

import static com.ti.app.telemed.core.syncmodule.SyncWorker.SYNC_WORK_TAG;

/**
 * <h1>Inizializzazione Libreria</h1>
 * <p>Questa classe estende la classe Android android.app.Application ed esegue tutte le inizializzzioni necessarie alla libreria.</p>
 * @author  Massimo Martini
 * @version 1.0
 * @since   2017-11-09
 */

public class MyApp extends Application {
    private static final String TAG = "MyAppCore";

	private static MyApp instance;
    private static String appVerson;
    private static ConfigurationManager configurationManager;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    configurationManager = new ConfigurationManager();
                    configurationManager.init();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Restituisce il Context dell'applicazione
     * @return      {@link Context}
     */
    public static Context getContext() {
        return instance.getApplicationContext();
    }

    /**
     * Restituisce l'istanza di Configuration Manager
     * @return      {@link ConfigurationManager}
     */
    public static ConfigurationManager getConfigurationManager(){
        return configurationManager;
    }

    public static void testSyncWorker() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        WorkRequest myWorkRequest =
                new OneTimeWorkRequest.Builder(SyncWorker.class)
                        .addTag("TEST_SYNC_WORKER")
                        .setConstraints(constraints)
                        .setInitialDelay(2, TimeUnit.MINUTES)
                        .build();
        WorkManager workManager = WorkManager.getInstance(instance);
        workManager.enqueue(myWorkRequest);
    }

    public static void scheduleSyncWorker(boolean boot) {
        Log.d(TAG,"scheduleSyncWorker");

        // Run SynkWorker only if Network is connected
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest periodicSyncDataWork =
                // Schedule sync worker every 60 minutes
                new PeriodicWorkRequest.Builder(SyncWorker.class, 60, TimeUnit.MINUTES)
                        .addTag(SYNC_WORK_TAG)
                        .setConstraints(constraints)
                        // setting backoff on case the work needs to retry
                        .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
                        .build();

        WorkManager workManager = WorkManager.getInstance(instance);
        if (boot) {
            workManager.enqueueUniquePeriodicWork(
                    SYNC_WORK_TAG,
                    ExistingPeriodicWorkPolicy.REPLACE, //Existing Periodic Work policy
                    periodicSyncDataWork //work request
            );
        } else {
            workManager.enqueueUniquePeriodicWork(
                    SYNC_WORK_TAG,
                    ExistingPeriodicWorkPolicy.KEEP, //Existing Periodic Work policy
                    periodicSyncDataWork //work request
            );
        }
    }

    public static void setAppVersion(String version) {
       appVerson = version;
    }

    public static String getAppVersion() {
        return appVerson;
    }
}
