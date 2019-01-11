package com.ti.app.telemed.core;


import android.app.Application;
import android.content.Context;
import android.os.AsyncTask;

import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobRequest;
import com.ti.app.telemed.core.configuration.ConfigurationManager;
import com.ti.app.telemed.core.syncmodule.SyncJob;
import com.ti.app.telemed.core.syncmodule.SynchJobCreator;

import java.util.concurrent.TimeUnit;

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
        instance = this;
        JobManager.create(this).addJobCreator(new SynchJobCreator());
        scheduleSyncJob();

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

    public static void scheduleSyncJob() {
        /*
        Set<JobRequest> jobRequests = JobManager.instance().getAllJobRequestsForTag(SyncJob.JOB_TAG);
        if (!jobRequests.isEmpty()) {
            return;
        }
        */
        new JobRequest.Builder(SyncJob.JOB_TAG)
                .setPeriodic(TimeUnit.HOURS.toMillis(1), TimeUnit.MINUTES.toMillis(30))
                .setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
                .setRequirementsEnforced(true)
                .setUpdateCurrent(true)
                .build()
                .schedule();
    }
}
