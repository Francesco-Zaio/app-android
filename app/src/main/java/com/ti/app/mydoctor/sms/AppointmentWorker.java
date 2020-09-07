package com.ti.app.mydoctor.sms;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.ti.app.mydoctor.AppResourceManager;
import com.ti.app.mydoctor.R;
import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.common.Appointment;
import com.ti.app.telemed.core.dbmodule.DbManager;

public class AppointmentWorker extends Worker {
    private static final String TAG = "AppointmentWorker";

    public static final String KEY_WORK_ID = "ID";
    private static final String CHANNEL_ID = "TELEMONITORING_CHANNEL";
    private static boolean channelRegisterd = false;

    public AppointmentWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    @Override
    public void onStopped() {
        super.onStopped();
        Log.i(TAG, "OnStopped called");
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "SyncWorker started!");
        // TODO check id user exists and is not blocked
        try {
            Data d = getInputData();
            String id = d.getString(KEY_WORK_ID);
            Appointment app = DbManager.getDbManager().getAppointment(id);
            if (app != null)
                createNotification(app);
            else {
                Log.e(TAG,"doWork: Appointment not found into the DB!");
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            return Result.success();
        }
        return Result.success();
    }

    private void createNotification(@NonNull Appointment app) {
        Log.d(TAG, "createNotification - started!");

        if (!channelRegisterd)
            registerNotificationChannel();
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(app.getUrl()));
        PendingIntent pendingIntent = PendingIntent.getActivity(MyApp.getContext(), 0, intent, 0);

        //Bitmap bm = BitmapFactory.decodeResource(MyApp.getContext().getResources(), R.drawable.logo_icon);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(MyApp.getContext(), CHANNEL_ID)
                // TODO creare icona bianco/nero
                .setSmallIcon(R.drawable.ic_notification_televisita)
                .setWhen(app.getTimestamp())
                .setShowWhen(true)
                .setContentTitle(AppResourceManager.getResource().getString("AppointmentType." + app.getType()))
                //.setContentText(app.getData())
                //.setStyle(new NotificationCompat.BigTextStyle().bigText(app.getData()))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(false)
                .addAction(R.drawable.camera, MyApp.getContext().getString(R.string.start), pendingIntent);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(MyApp.getContext());

        // notificationId is a unique int for each notification that you must define
        notificationManager.notify(app.getId(), builder.build());
        Log.d(TAG, "createNotification - notification created!");
    }

    private void registerNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        Log.d(TAG, "registerNotificationChannel - started!");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "HomeDoctor";
            String description = "HomeDoctor channel";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = MyApp.getContext().getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            Log.d(TAG, "registerNotificationChannel channel - registered!");
        }
        channelRegisterd = true;
    }
}
