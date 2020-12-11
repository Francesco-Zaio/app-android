package com.ti.app.mydoctor.sms;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;
import androidx.work.BackoffPolicy;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.ti.app.mydoctor.AppResourceManager;
import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.common.Appointment;
import com.ti.app.telemed.core.dbmodule.DbManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.ti.app.mydoctor.sms.AppointmentWorker.KEY_WORK_ID;

public class SMSReceiver extends BroadcastReceiver {


    private static final String TAG = "SMSReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "onReceive");

        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            Map<String, String> msg = RetrieveMessages(intent);
            if (msg.isEmpty()) {
                // unable to retrieve SMS
                setResultCode(Activity.RESULT_CANCELED);
                return;
            }
            for (Map.Entry<String, String> mapEntry : msg.entrySet()) {
                String originAddress = mapEntry.getKey();
                String body = mapEntry.getValue();
                Log.i(TAG, "SMS Received: originAddress=" + originAddress + " - message=" + body);
                parseSMS(body);
                setResultCode(Activity.RESULT_OK);
            }
        } else {
            setResultCode(Activity.RESULT_CANCELED);
        }
    }

    private Map<String, String> RetrieveMessages(Intent intent) {
        Map<String, String> msg = new HashMap<>();
        SmsMessage[] smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent);

        // There can be multiple SMS from multiple senders
        // However, send long SMS of same sender in one message
        for (SmsMessage smsMessage : smsMessages) {
            String originatinAddress = smsMessage.getOriginatingAddress();

            // Check if index with number exists
            if (!msg.containsKey(originatinAddress)) {
                // Index with number doesn't exist
                // Save string into associative array with sender number as index
                msg.put(smsMessage.getOriginatingAddress(), smsMessage.getMessageBody());
            } else {
                // Number has been there, add content but consider that
                // msg.get(originatinAddress) already contains sms:sndrNbr:previousparts of SMS,
                // so just add the part of the current PDU
                String previousparts = msg.get(originatinAddress);
                String msgString = previousparts + smsMessage.getMessageBody();
                msg.put(originatinAddress, msgString);
            }
        }
        return msg;
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
        // Verifico che ci siano le due parentesi
        int i1 = str.indexOf("[");
        int i2 = str.indexOf("]");
        if ((i1 == -1) || (i2 == -1)) {
            Log.i(TAG, "parseSMS: message not valid");
            return;
        }
        String[] s = str.substring(i1 + 1, i2).split("#");
        // verifico che ci siano esattamente 7 campi separati da '#'
        if (s.length != 7) {
            Log.i(TAG, "parseSMS: message not valid");
            return;
        }

        int id;
        String idUser, url = null, title = null, body = null;
        Calendar cal = null;
        boolean isDelete;
        // recupero l'ID della visita
        try {
            id = Integer.parseInt(s[0]);
        } catch (NumberFormatException e) {
            Log.e(TAG, "parseSMS: id format error");
            return;
        }

        // Non verifico se l'utente esiste
        // Vengono gestite tutte le visite anche se il paziente non è ancora registrato nel DB
        idUser = s[1];

        // Nel caso di delete ignoro gli altri dati e cancello la visita
        if (s[4].equalsIgnoreCase("D")) {
            deleteAppointment(String.valueOf(id));
            return;
        }

        // Timestamp
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmm", Locale.getDefault());
        cal = Calendar.getInstance();
        try {
            Date d = format.parse(s[2]);
            if (d != null)
                cal.setTime(d);
        } catch (ParseException e) {
            Log.e(TAG, "parseSMS Date format error: message=" + str);
            return;
        }

        // Titolo
        if (s[3] == null || s[3].isEmpty() || s[3].equalsIgnoreCase("-1"))
            title = AppResourceManager.getResource().getString("AppointmentType.0");
        else
            title = s[3];

        // Body e URL
        i1 = str.indexOf("[");
        i2 = str.indexOf("https");
        if ((i1 == -1) || (i2 == -1)) {
            Log.i(TAG, "parseSMS: message not valid");
            return;
        }
        body = str.substring(0, i1);
        url = str.substring(i2);
        url = url.split("\\r?\\n|\\[")[0];

        // Creazione appuntamento e gestione eventule ripetizione
        int i;
        int num;
        switch (s[5].toUpperCase()) {
            case "G":
                num = Integer.parseInt(s[6]);
                manageAppointment(String.valueOf(id), idUser, url, title, body, cal);
                if (s[4].equalsIgnoreCase("C"))
                    for (i=1;i<num;i++) {
                        cal.add(Calendar.DATE, 1);
                        manageAppointment(String.valueOf(id+i), idUser, url, title, body, cal);
                    }
                break;
            case "S":
                num = Integer.parseInt(s[6]);
                manageAppointment(String.valueOf(id), idUser, url, title, body, cal);
                if (s[4].equalsIgnoreCase("C"))
                    for (i=1;i<num;i++) {
                        cal.add(Calendar.DATE, 7);
                        manageAppointment(String.valueOf(id+i), idUser, url, title, body, cal);
                    }
                break;
            case "M":
                num = Integer.parseInt(s[6]);
                manageAppointment(String.valueOf(id), idUser, url, title, body, cal);
                if (s[4].equalsIgnoreCase("C"))
                    for (i=1;i<num;i++) {
                        cal.add(Calendar.MONTH, 1);
                        manageAppointment(String.valueOf(id+i), idUser, url, title, body, cal);
                    }
                break;
            case "-1":
            default:
                manageAppointment(String.valueOf(id), idUser, url, title, body, cal);
                break;
        }
    }

    private void deleteAppointment(String appointmentId) {
        Log.d(TAG, "deleteAppointment - started!");
        DbManager dbManager = DbManager.getDbManager();
        Appointment app = dbManager.getAppointment(appointmentId);
        removeWorkRequest(appointmentId);
        if (app != null) {
            // Rimuovo l'eventuale notifica se è già visualizzata
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(MyApp.getContext());
            notificationManager.cancel(app.getId());
            dbManager.deleteAppointment(app.getAppointmentId(), 0);
            Log.d(TAG, "Appointment " + app.getAppointmentId() + " deleted");
        }
    }

    private void manageAppointment(String appointmentId, String idUser, String url, String title, String body, Calendar cal) {
        Log.d(TAG, "manageAppointment - started!");
        DbManager dbManager = DbManager.getDbManager();
        Appointment app = dbManager.getAppointment(appointmentId);
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
            app.setTitle(title);
            app.setData(body);
            app.setTimestamp(cal.getTimeInMillis());
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
