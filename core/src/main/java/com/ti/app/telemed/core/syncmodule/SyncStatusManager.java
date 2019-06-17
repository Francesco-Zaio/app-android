package com.ti.app.telemed.core.syncmodule;

import android.os.Handler;

import java.util.ArrayList;
import java.util.List;

public class SyncStatusManager {
    private static final String TAG = "SyncStatusManager";

    public static final int LOGIN_STATUS_CHANGE = 1;
    public static final int MEASURE_STATUS_CHANGE = 2;

    private static SyncStatusManager syncStatusManager;

    // se vale true c'è stato un problema nel login/cambio pwd verso la piattaforma
    private boolean loginError;
    // se vale true c'è stato un problema nell'invio misure/documenti alla piattaforma
    private boolean measureError;

    private List<Handler> statusListeners = new ArrayList<>();

    public static SyncStatusManager getSyncStatusManager() {
        if (syncStatusManager == null) {
            syncStatusManager = new SyncStatusManager();
        }
        return syncStatusManager;
    }

    private SyncStatusManager() {
        loginError = false;
        measureError = false;
    }

    public void addListener(Handler handler) {
        synchronized (this) {
            if (!statusListeners.contains(handler))
                statusListeners.add(handler);
        }
    }

    public void removeListener(Handler handler) {
        synchronized (this) {
            statusListeners.remove(handler);
        }
    }

    public void setLoginError(boolean error) {
        synchronized (this) {
            if (loginError != error) {
                loginError = error;
                notifyListeners(LOGIN_STATUS_CHANGE);
            }
        }
    }

    public void setMeasureError(boolean error) {
        synchronized (this) {
            if (measureError != error) {
                measureError = error;
                notifyListeners(MEASURE_STATUS_CHANGE);
            }
        }
    }

    public boolean getLoginError() {
        synchronized (this) {
            return loginError;
        }
    }

    public boolean getMeasureError() {
        synchronized (this) {
            return measureError;
        }
    }

    private void notifyListeners(int what) {
        for (Handler h:statusListeners) {
            h.sendEmptyMessage(what);
        }
    }
}
