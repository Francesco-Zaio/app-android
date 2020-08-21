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
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static com.ti.app.mydoctor.sms.AppointmentWorker.KEY_WORK_ID;

public class SMSReceiver extends BroadcastReceiver {


    private static final String TAG = "SMSReceiver";
    // TODO definire il mittente
    private static final String SENDER="TODO";

    @Override
    public void onReceive(Context context, Intent intent) {

    /*
        // Retrieve the sms message chunks from the intent
        SmsMessage[] rawSmsChunks;
        try {
            rawSmsChunks = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        } catch (NullPointerException ignored) { return; }

        // Gather all sms chunks for each sender separately
        Map<String, StringBuilder> sendersMap = new HashMap<>();
        for (SmsMessage rawSmsChunk : rawSmsChunks) {
            if (rawSmsChunk != null) {
                String sender = rawSmsChunk.getDisplayOriginatingAddress();
                String smsChunk = rawSmsChunk.getDisplayMessageBody();
                StringBuilder smsBuilder;
                if ( ! sendersMap.containsKey(sender) ) {
                    // For each new sender create a separate StringBuilder
                    smsBuilder = new StringBuilder();
                    sendersMap.put(sender, smsBuilder);
                } else {
                    // Sender already in map. Retrieve the StringBuilder
                    smsBuilder = sendersMap.get(sender);
                }
                // Add the sms chunk to the string builder
                smsBuilder.append(smsChunk);
            }
        }

        // Loop over every sms thread and concatenate the sms chunks to one piece
        for ( Map.Entry<String, StringBuilder> smsThread : sendersMap.entrySet() ) {
            String sender  = smsThread.getKey();
            StringBuilder smsBuilder = smsThread.getValue();
            String message = smsBuilder.toString();
            handler.handleSms(sender, message);
        }
     */

        Log.i(TAG, "onReceive");
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            SmsMessage[] smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            for (SmsMessage smsMessage : smsMessages) {
                String phoneNumber = smsMessage.getDisplayOriginatingAddress();
                String message = smsMessage.getDisplayMessageBody();
                String body = smsMessage.getMessageBody();
                String originAddress = smsMessage.getOriginatingAddress();
                // TODO filter the sender
                Log.i(TAG, "SMS Received: sender=" + phoneNumber + " - message=" + message + " - body=" + body + " - originAddr=" + originAddress);
                parseSMS(body);
                /*
                if (SENDER.equals(phoneNumber)) {
                    parseSMS(body);
                    Log.i(TAG, "Valid SMS Received: sender=" + phoneNumber + " - message=" + message + " - body=" + body + " - originAddr=" + originAddress);
                } else
                    Log.i(TAG, "Not Valid SMS Received: sender="+phoneNumber+" - message="+message+" - body="+body+" - originAddr="+originAddress);
                */
                setResultCode(Activity.RESULT_OK);
            }
        } else {
            setResultCode(Activity.RESULT_CANCELED);
        }
    }

    private void parseSMS(String str) {
        String id, idUser, url, body;
        long timestamp;
        boolean isDelete;

        Log.d(TAG, "parseSMS - started!");
        body = str.substring(0, str.indexOf("["));
        url = str.substring(str.indexOf("https"));
        url = url.split("\\r?\\n")[0];
        String[] s = str.substring(str.indexOf("[") + 1, str.indexOf("]")).split(",");
        if (s.length != 4) {
            Log.e(TAG, "parseSMS Error: message=" + str);
            return;
        }
        id = s[0];
        // TODO check if user exist and is not blocked
        idUser = s[1];
        isDelete = s[3].equalsIgnoreCase("D");
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmm", Locale.getDefault());
        try {
            timestamp = format.parse(s[2]).getTime();
        } catch (ParseException e) {
            Log.e(TAG, "parseSMS Date format error: message=" + str);
            return;
        }
        manageAppointment(id, idUser, url, body, timestamp, isDelete);
    }

    private void manageAppointment(String appointmentId, String idUser, String url, String body, long timestamp, boolean delete) {
        Log.d(TAG, "manageAppointment - started!");
        DbManager dbManager = DbManager.getDbManager();
        Appointment app = dbManager.getAppointment(appointmentId);
        if (app == null) {
            scheduleNotification(appointmentId, timestamp);
            app = new Appointment();
            app.setAppointmentId(appointmentId);
            app.setType(0);
            app.setIdUser(idUser);
            app.setUrl(url);
            app.setData(body);
            app.setTimestamp(timestamp);
            dbManager.insertAppointment(app);
        } else {
            if (delete) {
                removeNotification(appointmentId);
                dbManager.deleteAppointment(app.getAppointmentId());
            } else {
                long currTimestamp = app.getTimestamp();
                String currData = app.getData();
                if (timestamp != currTimestamp || !body.equals(currData)) {
                    app.setTimestamp(timestamp);
                    app.setData(body);
                    dbManager.updateAppointment(app);
                    scheduleNotification(appointmentId, timestamp);
                }
            }
        }
    }

    private void scheduleNotification(String appointmentId, long timestamp) {
        Log.d(TAG, "scheduleNotification - started!");
        WorkManager workManager = WorkManager.getInstance(MyApp.getContext());

        Data myData = new Data.Builder()
                .putString(KEY_WORK_ID, appointmentId)
                .build();

        OneTimeWorkRequest oneTimeWork =
                // TODO schedule 10 minutes before and check if in the future
                new OneTimeWorkRequest.Builder(AppointmentWorker.class)
                        .setInitialDelay(timestamp - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                        // setting backoff on case the work needs to retry
                        .setBackoffCriteria(BackoffPolicy.LINEAR,5,TimeUnit.MINUTES)
                        .addTag(appointmentId)
                        .setInputData(myData)
                        .build();
        workManager.enqueueUniqueWork(appointmentId, ExistingWorkPolicy.REPLACE,oneTimeWork);
    }

    private void removeNotification(String appointmentId) {
        Log.d(TAG, "removeNotification - started!");
        WorkManager workManager = WorkManager.getInstance(MyApp.getContext());
        workManager.cancelUniqueWork(appointmentId);
    }
}
