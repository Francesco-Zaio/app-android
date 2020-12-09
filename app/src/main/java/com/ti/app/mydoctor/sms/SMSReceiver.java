package com.ti.app.mydoctor.sms;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import androidx.work.BackoffPolicy;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.common.Appointment;
import com.ti.app.telemed.core.dbmodule.DbManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static com.ti.app.mydoctor.sms.AppointmentWorker.KEY_WORK_ID;

public class SMSReceiver extends BroadcastReceiver {


    private static final String TAG = "SMSReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "onReceive");
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            SmsMessage[] smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            for (SmsMessage smsMessage : smsMessages) {
                String phoneNumber = smsMessage.getDisplayOriginatingAddress();
                String message = smsMessage.getDisplayMessageBody();
                String body = smsMessage.getMessageBody();
                String originAddress = smsMessage.getOriginatingAddress();
                Log.i(TAG, "SMS Received: sender=" + phoneNumber + " - message=" + message + " - body=" + body + " - originAddr=" + originAddress);
                parseSMS(body);
                setResultCode(Activity.RESULT_OK);
            }
        } else {
            setResultCode(Activity.RESULT_CANCELED);
        }
    }

    /*
    I 7 parametri inclusi nelle parentesi quadre sono nell’ordine:
    1.	id VC
    2.	id utente
    3.	timestamp VC (yyyyMMddHHmm)
    4.	titolo VC
    5.	azione riguardante la VC  sempre uno tra: “C” = creazione; “M” = modifica; “D” = cancellazione
    6.	codice ripetizione (solo per action di ”creazione”)   sempre uno tra: “G” = giornaliera; “S” = settimanale; “M” = mensile
    7.	numero totale appuntamenti ripetuti (solo per action di ”creazione”)
    Tutti i parametri vengono sempre valorizzati; qualora il valore sia da ignorare viene valorizzato con la stringa “-1”.
    Qualora il codice ripetizione e il numero totale di appuntamenti siano diversi da “-1”, il primo parametro “id VC” si riferisce all’ID della prima Video Conferenza creata: l’ID delle successive è per costruzione pari ai numeri successivi.
    Il codice ripetizione può essere valorizzato solo per l'azione "C"
    Cancellazione/Modifica vengono invite solo per singolo evento.
    */
    private void parseSMS(String str) {

        Log.d(TAG, "parseSMS - started!");
        int i1 = str.indexOf("[");
        int i2 = str.indexOf("]");
        if ((i1 == -1) || (i2 == -1)) {
            Log.i(TAG, "parseSMS: message not valid");
            return;
        }
        String[] s = str.substring(i1 + 1, i2).split(",");
        if (s.length != 7) {
            Log.i(TAG, "parseSMS: message not valid");
            return;
        }

        int id;
        String idUser, url, title, body;
        boolean isDelete;
        id = Integer.parseInt(s[0]);
        // TODO check if user exist and is not blocked
        idUser = s[1];
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmm", Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        try {
            cal.setTime(format.parse(s[2]));
        } catch (ParseException e) {
            Log.e(TAG, "parseSMS Date format error: message=" + str);
            return;
        }
        title = s[3];
        isDelete = s[4].equalsIgnoreCase("D");
        body = str.substring(0, str.indexOf("["));
        url = str.substring(str.indexOf("https"));
        url = url.split("\\r?\\n")[0];
        int i;
        int num;
        switch (s[5].toUpperCase()) {
            case "G":
                num = Integer.parseInt(s[6]);
                manageAppointment(String.valueOf(id), idUser, url, title, body, cal, isDelete);
                if (s[4].equalsIgnoreCase("C"))
                    for (i=1;i<num;i++) {
                        cal.add(Calendar.DATE, 1);
                        manageAppointment(String.valueOf(id+i), idUser, url, title, body, cal, isDelete);
                    }
                break;
            case "S":
                num = Integer.parseInt(s[6]);
                manageAppointment(String.valueOf(id), idUser, url, title, body, cal, isDelete);
                if (s[4].equalsIgnoreCase("C"))
                    for (i=1;i<num;i++) {
                        cal.add(Calendar.DATE, 7);
                        manageAppointment(String.valueOf(id+i), idUser, url, title, body, cal, isDelete);
                    }
                break;
            case "M":
                num = Integer.parseInt(s[6]);
                manageAppointment(String.valueOf(id), idUser, url, title, body, cal, isDelete);
                if (s[4].equalsIgnoreCase("C"))
                    for (i=1;i<num;i++) {
                        cal.add(Calendar.MONTH, 1);
                        manageAppointment(String.valueOf(id+i), idUser, url, title, body, cal, isDelete);
                    }
                break;
            case "-1":
            default:
                manageAppointment(String.valueOf(id), idUser, url, title, body, cal, isDelete);
                break;
        }
    }

    private void manageAppointment(String appointmentId, String idUser, String url, String title, String body, Calendar cal, boolean delete) {
        Log.d(TAG, "manageAppointment - started!");
        DbManager dbManager = DbManager.getDbManager();
        Appointment app = dbManager.getAppointment(appointmentId);
        if (delete) {
            removeWorkRequest(appointmentId);
            if (app != null) {
                dbManager.deleteAppointment(app.getAppointmentId(), 0);
                Log.d(TAG, "Appointment " + app.getAppointmentId() + " deleted");
            }
            return;
        }
        if (app == null) {
            scheduleWorkRequest(appointmentId, cal.getTimeInMillis());
            app = new Appointment();
            app.setAppointmentId(appointmentId);
            app.setType(0);
            app.setIdUser(idUser);
            app.setUrl(url);
            app.setTitle(title);
            app.setData(body);
            app.setTimestamp(cal.getTimeInMillis());
            dbManager.insertAppointment(app);
        } else {
            app.setUrl(url);
            app.setTimestamp(cal.getTimeInMillis());
            app.setTitle(title);
            app.setData(body);
            dbManager.updateAppointment(app);
            scheduleWorkRequest(appointmentId, cal.getTimeInMillis());
        }
    }

    private void scheduleWorkRequest(String appointmentId, long timestamp) {
        Log.d(TAG, "scheduleNotification - started!");
        WorkManager workManager = WorkManager.getInstance(MyApp.getContext());

        Data myData = new Data.Builder()
                .putString(KEY_WORK_ID, appointmentId)
                .build();

        OneTimeWorkRequest oneTimeWork =
                // TODO check if in the future
                new OneTimeWorkRequest.Builder(AppointmentWorker.class)
                        .setInitialDelay(timestamp - System.currentTimeMillis() - (1000*60*15), TimeUnit.MILLISECONDS)
                        // setting backoff on case the work needs to retry
                        .setBackoffCriteria(BackoffPolicy.LINEAR,5,TimeUnit.MINUTES)
                        .addTag(appointmentId)
                        .setInputData(myData)
                        .build();
        workManager.enqueueUniqueWork(appointmentId, ExistingWorkPolicy.REPLACE,oneTimeWork);
    }

    private void removeWorkRequest(String appointmentId) {
        Log.d(TAG, "removeNotification - started!");
        WorkManager workManager = WorkManager.getInstance(MyApp.getContext());
        workManager.cancelUniqueWork(appointmentId);
    }
}
