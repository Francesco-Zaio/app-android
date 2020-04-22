package com.ti.app.telemed.core.syncmodule;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.webmodule.WebManager;
import com.ti.app.telemed.core.webmodule.webmanagerevents.WebManagerResultEvent;
import com.ti.app.telemed.core.webmodule.webmanagerevents.WebManagerResultEventListener;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SyncWorker extends Worker implements WebManagerResultEventListener  {
    private static final String TAG = "HDSyncWorker";

    public static final String SYNC_WORK_TAG = "HD_SYNC_WORKER";

    private final Context ctx;
    private String userId, login;
    private final CountDownLatch countDownLatch = new CountDownLatch(1);
    private volatile boolean success = false;
    private volatile boolean stopped = false;

    public SyncWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
        ctx = appContext;
    }

    @Override
    public void onStopped() {
        super.onStopped();
        Log.i(TAG, "OnStopped called");
        stopped = true;
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "SyncWorker started!");
        User u = UserManager.getUserManager().getCurrentUser();
        if (u == null)
            u = DbManager.getDbManager().getActiveUser();
        if (u != null && !u.isDefaultUser() && !u.isBlocked()) {
            try {
                userId = u.getId();
                login = u.getLogin();
                success = false;
                WebManager.getWebManager().askOperatorData(u.getLogin(), u.getPassword(), this, false);
                countDownLatch.await(GWConst.HTTP_CONNECTION_TIMEOUT +GWConst.HTTP_READ_TIMEOUT +2000, TimeUnit.MILLISECONDS);
                if (stopped) {
                    Log.w(TAG, "SyncWorker stopped");
                    return Result.failure();
                }
                if (success) {
                    Log.d(TAG, "askOperatorData success");
                    Intent intent = new Intent(MyApp.getContext(), SendMeasureService.class);
                    intent.putExtra(SendMeasureService.USER_TAG, userId);
                    ctx.startService(intent);
                    Log.d(TAG, "SendMeasureService started");
                } else {
                    Log.w(TAG, "askOperatorData failed");
                    return Result.failure();
                }
            } catch (Exception e) {
                Log.e(TAG, e.toString());
                return Result.failure();
            }
        }
        return Result.success();
    }

    @Override
    public void webAuthenticationSucceeded(WebManagerResultEvent evt){
        SyncStatusManager.getSyncStatusManager().setLoginError(false);

        User u = UserManager.getUserManager().getCurrentUser();
        // verifico se nel frattempo non è cambiato l'utente
        if (u != null && u.getId().equals(userId))
            UserManager.getUserManager().reloadCurrentUser();
        success = true;
        countDownLatch.countDown();
    }

    @Override
    public void webAuthenticationFailed(WebManagerResultEvent evt){
        // Le credenziali dell'utente corrente sono errate (l'utente può essere stato rimosso o
        // è stata cambiata la password lato piattaforma)
        // Occorre resettare l'utente corrente a null per obbligare a riautenticarsi e impostare
        // sul DB il flag ACTIVE a false altrimenti ogni ora verrà comunque ritentata
        // l'autenticazione
        SyncStatusManager.getSyncStatusManager().setLoginError(true);
        DbManager.getDbManager().resetActiveUser(userId);
        User u = UserManager.getUserManager().getCurrentUser();
        if (u != null && u.getId().equals(userId)) {
            UserManager.getUserManager().reset();
        }
        success = false;
        countDownLatch.countDown();
    }

    @Override
    public void webChangePasswordSucceded(WebManagerResultEvent evt) {
        SyncStatusManager.getSyncStatusManager().setLoginError(false);
        success = false;
        countDownLatch.countDown();
    }

    @Override
    public void webOperationFailed(WebManagerResultEvent evt, XmlManager.XmlErrorCode code) {
        SyncStatusManager.getSyncStatusManager().setLoginError(true);
        // l'utente corrente è stato disattivato
        if(code != null && code.equals(XmlManager.XmlErrorCode.USER_BLOCKED)) {
            UserManager.getUserManager().setUserBlocked(login);
        }
        success = false;
        countDownLatch.countDown();
    }
}
