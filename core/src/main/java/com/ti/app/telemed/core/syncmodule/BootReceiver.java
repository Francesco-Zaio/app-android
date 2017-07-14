package com.ti.app.telemed.core.syncmodule;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.exceptions.DbException;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.webmodule.WebManager;
import com.ti.app.telemed.core.webmodule.webmanagerevents.WebManagerResultEvent;
import com.ti.app.telemed.core.webmodule.webmanagerevents.WebManagerResultEventListener;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

public class BootReceiver extends BroadcastReceiver implements  WebManagerResultEventListener {

    private static final String TAG = "BootReceiver";
    private static final String WLTAG = "com.ti.app.telemed.core.syncmodule.BootReceiver";
    private static volatile PowerManager.WakeLock wakeLock = null;
    private static final String ALARM_ACTION = "com.ti.app.telemed.core.syncmodule.ALARM_ACTION";
    private String userId, login;

    synchronized private static PowerManager.WakeLock getLock(Context context) {
        if (wakeLock == null) {
            PowerManager pmgr = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
            wakeLock = pmgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WLTAG);
            wakeLock.setReferenceCounted(true);
        }
        return(wakeLock);
    }

    public BootReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equalsIgnoreCase(Intent.ACTION_BOOT_COMPLETED)) {
            // imposta un alarm ogni ora
            Log.d(TAG, "BOOT COMPLETED");
            registerAlarm(context);
        } else if (intent.getAction().equalsIgnoreCase(ALARM_ACTION)) {
            // ogni ora sveglia il servizio di invo misure e richiede l'aggiornamento della configurazione dell'utente
            Log.d(TAG, "Alarm fired!");
            User u = UserManager.getUserManager().getCurrentUser();
            if (u == null)
                u = DbManager.getDbManager().getActiveUser();
            if (u != null && !u.isBlocked()) {
                try {
                    userId = u.getId();
                    login = u.getLogin();
                    PowerManager.WakeLock lock=getLock(context);
                    if (!lock.isHeld()) {
                        lock.acquire(GWConst.CONNECTION_TIMEOUT+GWConst.READ_TIMEOUT+5000);
                    }
                    WebManager.getWebManager().askOperatorData(u.getLogin(), u.getPassword(), this, false);
                    context.startService(new Intent(context, SendMeasuresService.class));
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
            }
        }
    }

    private static PendingIntent getAlarmIntent(Context context, int flags) {
        Intent i = new Intent(context, BootReceiver.class);
        i.setAction(ALARM_ACTION);
        return PendingIntent.getBroadcast(context, 0, i, flags);
    }

    public static void registerAlarm(Context context) {
        if (getAlarmIntent(context, PendingIntent.FLAG_NO_CREATE) == null) {
            AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            manager.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), AlarmManager.INTERVAL_HOUR, getAlarmIntent(context,0));
        }
    }

    @Override
    public void webAuthenticationSucceeded(WebManagerResultEvent evt){
        User u = UserManager.getUserManager().getCurrentUser();
        if (u != null && u.getId().equals(userId))
            UserManager.getUserManager().selectUser(userId);

        PowerManager.WakeLock lock = getLock(MyApp.getContext());
        if (lock.isHeld()) {
            lock.release();
        }
    }
    @Override
    public void webAuthenticationFailed(WebManagerResultEvent evt){
        // Le credenziali dell'utente corrente sono errate (l'utente può essere stato rimosso o
        // è stata cambiata la password lato piattaforma)
        // Occorre resettare l'utente corrente a null per obbligare a riautenticarsi e impostare
        // sul DB il flag ACTIVE a false altrimenti ogni ora verrà comunque ritentata
        // l'autenticazione
        DbManager.getDbManager().resetActiveUser(userId);
        User u = UserManager.getUserManager().getCurrentUser();
        if (u != null && u.getId().equals(userId)) {
            UserManager.getUserManager().reset();
        }
        PowerManager.WakeLock lock=getLock(MyApp.getContext());
        if (lock.isHeld()) {
            lock.release();
        }
    }
    @Override
    public void webChangePasswordSucceded(WebManagerResultEvent evt) {
        PowerManager.WakeLock lock=getLock(MyApp.getContext());
        if (lock.isHeld()) {
            lock.release();
        }
    }
    @Override
    public void webOperationFailed(WebManagerResultEvent evt, XmlManager.XmlErrorCode code) {
        // l'utente corrente è stato disattivato
        if(code != null && code.equals(XmlManager.XmlErrorCode.USER_BLOCKED)) {
            UserManager.getUserManager().setUserBlocked(login);
        }
        PowerManager.WakeLock lock=getLock(MyApp.getContext());
        if (lock.isHeld()) {
            lock.release();
        }
    }
}
