package com.ti.app.telemed.core.syncmodule;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.ti.app.telemed.core.MyApp;


public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equalsIgnoreCase(intent.getAction())) {
            Log.d(TAG, "BOOT COMPLETED");
            MyApp.scheduleSyncJob();
        }
    }

}
