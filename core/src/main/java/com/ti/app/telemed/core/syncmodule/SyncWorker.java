package com.ti.app.telemed.core.syncmodule;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.util.GWConst;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SyncWorker extends Worker {
    private static final String TAG = "HDSyncWorker";

    public static final String SYNC_WORK_TAG = "HD_SYNC_WORKER";

    private final Context ctx;
    private final CountDownLatch countDownLatch = new CountDownLatch(1);
    private volatile boolean success = false;
    private volatile boolean stopped = false;

    private UserManagerMessageHandler userManagerHandler = new UserManagerMessageHandler(this, Looper.getMainLooper());

    private static class UserManagerMessageHandler extends Handler {
        final SyncWorker worker;

        UserManagerMessageHandler(SyncWorker worker, Looper looper) {
            super(looper);
            this.worker = worker;
        }

        @Override
        public void handleMessage(Message msg) {
            worker.success = (msg.what == UserManager.USER_CHANGED);
            worker.countDownLatch.countDown();
        }
    }

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
        User user = UserManager.getUserManager().getActiveUser();
        if (user != null && !user.isDefaultUser() && !user.isBlocked()) {
            try {
                success = false;
                UserManager.getUserManager().syncActiveUser(userManagerHandler);
                countDownLatch.await(GWConst.HTTP_CONNECTION_TIMEOUT +GWConst.HTTP_READ_TIMEOUT +2000, TimeUnit.MILLISECONDS);
                if (stopped) {
                    Log.w(TAG, "SyncWorker stopped");
                    return Result.failure();
                }
                if (success) {
                    Log.d(TAG, "askOperatorData success");
                    Intent intent = new Intent(MyApp.getContext(), SendMeasureService.class);
                    intent.putExtra(SendMeasureService.USER_TAG, user.getId());
                    ctx.startService(intent);
                    Log.d(TAG, "SendMeasureService started");
                } else {
                    Log.w(TAG, "askOperatorData failed");
                    return Result.success();
                }
            } catch (Exception e) {
                Log.e(TAG, e.toString());
                return Result.success();
            }
        }
        return Result.success();
    }
}
