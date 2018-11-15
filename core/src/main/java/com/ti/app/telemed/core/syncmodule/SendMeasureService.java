package com.ti.app.telemed.core.syncmodule;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.PowerManager;
import android.util.Log;

import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.measuremodule.MeasureManager;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.webmodule.WebManager;
import com.ti.app.telemed.core.webmodule.webmanagerevents.WebManagerSendingResultEvent;
import com.ti.app.telemed.core.webmodule.webmanagerevents.WebManagerSendingResultEventListener;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

import java.io.File;
import java.util.ArrayList;


public class SendMeasureService extends IntentService implements WebManagerSendingResultEventListener {
    private static final String TAG = "SendMeasureService";
    private static final String WLTAG = "com.ti.app.telemed.core.syncmodule.SendMeasuresService";
    public static final String USER_TAG = "USERTAG";
    private static final int SEND_TIMEOUT = -1;
    private static final int SEND_SUCCESS = 0;
    private static final int SEND_ERROR = 1;
    private static final int SEND_AUTH_ERROR = 2;


    private volatile int sendResult=SEND_TIMEOUT;
    private int errorCode = 0;
    private final Object lock = new Object();

    public SendMeasureService() {
        super("SendMeasureService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm == null) {
                Log.e(TAG, "onHandleIntent: PowerManager is NULL!!");
                return;
            }
            Log.d(TAG, "onHandleIntent");
            final PowerManager.WakeLock  wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WLTAG);
            try {
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(60000);
                Log.d(TAG,"wakeLock acquired");
                final String userId = intent.getStringExtra(USER_TAG);
                final User user = DbManager.getDbManager().getUser(userId);
                if (user == null) {
                    Log.e(TAG,"User is Null");
                    return;
                }
                if (isNetworkConnected())
                    sendMeasures(user);
                else
                    Log.w(TAG,"Network is Not connected");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (wakeLock.isHeld()) {
                    wakeLock.release();
                    Log.d(TAG,"wakeLock released");
                }
            }
        }
    }

    private void sendMeasures(User user) {
        final ArrayList<Measure> measuresToSend = DbManager.getDbManager().getNotSentMeasures(user.getId());
        Log.d(TAG,"Nr measure to send = " + measuresToSend.size());
        for (Measure m:measuresToSend) {
            String xml;
            if (m.getFailed())
                xml = XmlManager.getXmlManager().getFailedMeasureXml(m);
            else
                xml = XmlManager.getXmlManager().getMeasureXml(m);
            try {
                byte[] data = m.getFile();
                if (XmlManager.DOCUMENT_FILE_TYPE.equals(m.getFileType())) {
                    File f = new File(new String(m.getFile(), "UTF-8"));
                    if (f.exists() && f.isDirectory()) {
                        data = new File(f, MeasureManager.DOCUMENT_SEND_TMPFILE).getAbsolutePath().getBytes("UTF-8");
                    }
                }
                synchronized (lock) {
                    sendResult = SEND_TIMEOUT;
                    WebManager.getWebManager().sendMeasureData(user.getLogin(),
                            user.getPassword(),
                            xml,
                            data,
                            m.getFileType(),
                            this);
                    lock.wait(GWConst.HTTP_CONNECTION_TIMEOUT+GWConst.HTTP_READ_TIMEOUT+1000);
                    switch (sendResult) {
                        case SEND_SUCCESS:
                            Log.d(TAG,"Measure send success");
                            m.setSent(true);
                            if (XmlManager.DOCUMENT_FILE_TYPE.equals(m.getFileType())) {
                                try {
                                    // rimuovo il file zip temporaneo utilizzato per l'invio di tutti i files nella directory
                                    File f = new File(new String(m.getFile(), "UTF-8"));
                                    if (f.exists() && f.isDirectory())
                                        new File(f, MeasureManager.DOCUMENT_SEND_TMPFILE).delete();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
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
                            // Inutile provare a spedire altre misure subito
                            return;
                        case SEND_AUTH_ERROR:
                            Log.e(TAG,"Autenticazione fallita");
                            // Inutile provare a spedire altre misure
                            return;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "WebManager error sendMeasureData");
                Log.e(TAG, e.toString());
            }
        }
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        return (cm != null) && (cm.getActiveNetworkInfo() != null);
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
}
