package com.ti.app.telemed.core.syncmodule;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;

import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.measuremodule.MeasureManager;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.util.Util;
import com.ti.app.telemed.core.webmodule.WebManager;
import com.ti.app.telemed.core.webmodule.webmanagerevents.WebManagerSendingResultEvent;
import com.ti.app.telemed.core.webmodule.webmanagerevents.WebManagerSendingResultEventListener;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static com.ti.app.telemed.core.xmlmodule.XmlManager.AECG_FILE_TYPE;
import static com.ti.app.telemed.core.xmlmodule.XmlManager.DOCUMENT_FILE_TYPE;
import static com.ti.app.telemed.core.xmlmodule.XmlManager.IMG_FILE_TYPE;
import static com.ti.app.telemed.core.xmlmodule.XmlManager.PDF_FILE_TYPE;


public class SendMeasureService extends JobIntentService implements WebManagerSendingResultEventListener {

    /**
     * Unique job ID for this service.
     */
    static final int JOB_ID = 1000;

    private static final String TAG = "SendMeasureService";
    private static final String WLTAG = "com.ti.app.telemed.core.syncmodule:SendMeasuresService";
    public static final String USER_TAG = "USERTAG";
    public static final String MEASURE_TAG = "MEASURETAG";

    private static final int SEND_TIMEOUT = -1;
    private static final int SEND_SUCCESS = 0;
    private static final int SEND_ERROR = 1;
    private static final int SEND_AUTH_ERROR = 2;


    private volatile int sendResult=SEND_TIMEOUT;
    private int errorCode = 0;
    private final Object lock = new Object();

    /**
     * Convenience method for enqueuing work in to this service.
     */
    public static void enqueueWork(Context context, Measure measure, String userId) {
        if (measure == null && (userId == null || userId.isEmpty()))
            return;
        Intent intent = new Intent(context, SendMeasureService.class);
        if (measure != null) {
            intent.putExtra(SendMeasureService.MEASURE_TAG, measure);
        }
        if (userId != null && !userId.isEmpty())
            intent.putExtra(SendMeasureService.USER_TAG, userId);
        enqueueWork(context, SendMeasureService.class, JOB_ID, intent);
    }

    public SendMeasureService() {
        super();
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm == null) {
            Log.e(TAG, "onHandleIntent: PowerManager is NULL!!");
            return;
        }
        //Log.d(TAG, "onHandleIntent");
        final PowerManager.WakeLock  wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WLTAG);
        try {
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(60000);
            Log.d(TAG,"wakeLock acquired");
            String userId;
            final Measure measure = (Measure) intent.getSerializableExtra(MEASURE_TAG);
            if (measure != null)
                userId = measure.getIdUser();
            else
                userId = intent.getStringExtra(USER_TAG);
            final User user = DbManager.getDbManager().getUser(userId);
            if (user == null) {
                Log.e(TAG,"User is Null");
                return;
            }
            if (Util.isNetworkConnected()) {
                if (measure != null)
                    sendMeasure(user, measure);
                else
                    sendMeasures(user);
            } else {
                Log.w(TAG, "Network is Not connected");
            }
            // Update the Warning Flag
            if (!DbManager.getDbManager().notSentMeasures(user.getId()))
                SyncStatusManager.getSyncStatusManager().setMeasureError(false);
            else
                SyncStatusManager.getSyncStatusManager().setMeasureError(true);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (wakeLock.isHeld()) {
                wakeLock.release();
                Log.d(TAG,"wakeLock released");
            }
        }
    }

    private void sendMeasures(User user) {
        final ArrayList<Measure> measuresToSend = DbManager.getDbManager().getNotSentMeasures(user.getId());
        Log.d(TAG,"Nr measure to send = " + measuresToSend.size());
        for (Measure m:measuresToSend)
            if (sendMeasure(user, m))
                break;
    }

    // return true if the send process must stop (Authentication error)
    private boolean sendMeasure(User user, Measure m) {
        String xml;
        if (m.getFailed())
            xml = XmlManager.getXmlManager().getFailedMeasureXml(m);
        else
            xml = XmlManager.getXmlManager().getMeasureXml(m);
        try {
            byte[] data = m.getFile();
            long size = 0;
            File tmpFile;
            if (data != null && m.getFileType() != null)
                switch (m.getFileType()) {
                    case DOCUMENT_FILE_TYPE:
                        File f = new File(new String(m.getFile(), StandardCharsets.UTF_8));
                        if (f.exists())
                            if (f.isDirectory()) {
                                tmpFile = new File(f, MeasureManager.DOCUMENT_SEND_TMPFILE);
                                data = tmpFile.getAbsolutePath().getBytes(StandardCharsets.UTF_8);
                                size = tmpFile.length();
                            } else {
                                tmpFile = new File(new String(m.getFile(), StandardCharsets.UTF_8));
                                size = tmpFile.length();
                            }
                        break;
                    case IMG_FILE_TYPE:
                    case AECG_FILE_TYPE:
                    case PDF_FILE_TYPE:
                        tmpFile = new File(new String(m.getFile(), StandardCharsets.UTF_8));
                        if (tmpFile.exists())
                            size = tmpFile.length();
                        break;
                }
            long sendTimeout;
            // Send timeout: minimo fra 10 minuti e timeout calcolato sulla base del file da inviare (min 50Kb/sec)
            sendTimeout = Math.min(10*60*1000,  GWConst.HTTP_CONNECTION_TIMEOUT +
                    GWConst.HTTP_READ_TIMEOUT  +
                    (size/1024/50*1000));
            Log.d(TAG, "Send file size is " + size/1024 + "Kb");
            Log.d(TAG, "Send timeout is " + sendTimeout/1000 + "sec");
            synchronized (lock) {
                sendResult = SEND_TIMEOUT;
                errorCode = 408;
                WebManager.getWebManager().sendMeasureData(user.getLogin(),
                        user.getPassword(),
                        xml,
                        data,
                        m.getFileType(),
                        this);
                lock.wait(sendTimeout);
                switch (sendResult) {
                    case SEND_SUCCESS:
                        Log.d(TAG,"Measure send success");
                        m.setSent(true);
                        deleteMeasureFile(m);
                        DbManager.getDbManager().updateSentMeasure(m);
                        break;
                    case SEND_ERROR:
                        Log.e(TAG,"Measure send error: errorCode="+errorCode);
                        m.setSendFailReason(Integer.toString(errorCode));
                        m.setSendFailCount(m.getSendFailCount() + 1);
                        DbManager.getDbManager().updateSentMeasure(m);
                        break;
                    case SEND_TIMEOUT:
                        Log.e(TAG,"Server down o non raggiungibile");
                        m.setSendFailReason(Integer.toString(errorCode));
                        DbManager.getDbManager().updateSentMeasure(m);
                        // Inutile provare a spedire altre misure subito
                        break;
                    case SEND_AUTH_ERROR:
                        Log.e(TAG,"Autenticazione fallita");
                        m.setSendFailReason(Integer.toString(errorCode));
                        DbManager.getDbManager().updateSentMeasure(m);
                        // Inutile provare a spedire altre misure
                        return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "WebManager error sendMeasureData");
            Log.e(TAG, e.toString());
        }
        return false;
    }

    // methods of WebManagerSendingResultEventListener interface
    @Override
    public void sendingMeasureSucceeded(WebManagerSendingResultEvent evt) {
        synchronized (lock) {
            Log.i(TAG, "sendingMeasureSucceded: Invio misure avvenuto con successo");
            sendResult = SEND_SUCCESS;
            lock.notifyAll();
        }
    }

    @Override
    public void webAuthenticationFailed(WebManagerSendingResultEvent evt) {
        synchronized (lock) {
            Log.i(TAG, "webAuthenticationFailed: Invio misura fallito");
            sendResult = SEND_AUTH_ERROR;
            errorCode = 401;
            lock.notifyAll();
        }
    }

    @Override
    public void webOperationFailed(WebManagerSendingResultEvent evt, XmlManager.XmlErrorCode code) {
        synchronized (lock) {
            Log.i(TAG, "webOperationFailed: Invio misura fallito");
            sendResult = SEND_ERROR;
            errorCode = XmlManager.XmlErrorCode.convertFrom(code);
            lock.notifyAll();
        }
    }

    private void deleteMeasureFile(Measure m) {
        if (m == null || m.getFileType() == null)
            return;
        switch (m.getFileType()) {
            case XmlManager.DOCUMENT_FILE_TYPE:
                try {
                    // rimuovo il file zip temporaneo utilizzato per l'invio di tutti i files nella directory
                    File f = new File(new String(m.getFile(), StandardCharsets.UTF_8));
                    if (f.exists() && f.isDirectory())
                        new File(f, MeasureManager.DOCUMENT_SEND_TMPFILE).delete();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case XmlManager.AECG_FILE_TYPE:
            case XmlManager.PDF_FILE_TYPE:
                try {
                    File f = new File(new String(m.getFile(), StandardCharsets.UTF_8));
                    if (f.exists())
                        f.delete();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
        }
    }
}
