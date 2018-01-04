package com.ti.app.telemed.core.syncmodule;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.measuremodule.MeasureManager;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.webmodule.WebManager;
import com.ti.app.telemed.core.webmodule.webmanagerevents.WebManagerSendingResultEvent;
import com.ti.app.telemed.core.webmodule.webmanagerevents.WebManagerSendingResultEventListener;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

import java.io.File;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;


public class SendMeasuresService extends Service implements Runnable, WebManagerSendingResultEventListener {

    private static final String TAG = "SendMeasuresService";
    private static final String WLTAG = "com.ti.app.telemed.core.syncmodule.SendMeasuresService";
    private static volatile PowerManager.WakeLock wakeLock = null;

    private boolean loop = true;
    private final Thread currT;
    private Logger logger = Logger.getLogger(SendMeasuresService.class.getName());

    private User currentUser = null;

    private ArrayList<Measure> measuresToSend = new ArrayList<>();
    private Measure currentMeasure = null;

    // variables to manage the state of the responses from the server
    private boolean receiveError = false;
    private boolean responseReceived = false;
    int errorCode;

    public SendMeasuresService() {
        currT = new Thread(this);
        currT.setName("SendMeasuresService thread");
        currT.start();
    }

    synchronized private static PowerManager.WakeLock getLock(Context context) {
        if (wakeLock == null) {
            PowerManager pmgr = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
            wakeLock = pmgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WLTAG);
            wakeLock.setReferenceCounted(true);
        }
        return(wakeLock);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // per garantire che l'invio delle misure venga completato anche quando viene avviato
        // dall'allarme schedulato, viene utilizzato un wakelock che viene poi rilasciato quando
        // non ci sono risposte da attendere e tutte le misure sono state inviate.
        PowerManager.WakeLock lock=getLock(this.getApplicationContext());
        if (!lock.isHeld() || (flags & START_FLAG_REDELIVERY) != 0) {
            lock.acquire();
        }
        synchronized (currT) {
            currT.notifyAll();
            return START_REDELIVER_INTENT;
        }
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        PowerManager.WakeLock lock=getLock(this.getApplicationContext());
        if (lock.isHeld()) {
            try {
                lock.release();
            } catch (Exception e) {
                Log.e(TAG, "Exception when releasing wakelock", e);
            }
        }
        loop = false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    // methods of WebManagerSendingResultEventListener interface
    @Override
    public void sendingMeasureSucceeded(WebManagerSendingResultEvent evt) {
        synchronized (currT) {
            Log.i(TAG, "sendingMeasureSucceded: Invio misure avvenuto con successo");
            responseReceived = true;
            receiveError = false;
            currT.notifyAll();
        }
    }

    @Override
    public void webAuthenticationFailed(WebManagerSendingResultEvent evt) {
        synchronized (currT) {
            Log.i(TAG, "webAuthenticationFailed: Invio misura fallito");
            responseReceived = true;
            receiveError = true;
            errorCode = 401;
            currT.notifyAll();
        }
    }

    @Override
    public void webOperationFailed(WebManagerSendingResultEvent evt, XmlManager.XmlErrorCode code) {
        synchronized (currT) {
            Log.i(TAG, "webOperationFailed: Invio misura fallito");
            responseReceived = true;
            receiveError = true;
            errorCode = XmlManager.XmlErrorCode.convertFrom(code);
            currT.notifyAll();
        }
    }

    public void run() {
        synchronized (currT) {
            while (loop) {
                try {
                    if ((currentMeasure == null) && (measuresToSend.isEmpty())) {
                        currentUser = UserManager.getUserManager().getCurrentUser();
                        if (currentUser == null) {
                            Log.d(TAG, "UserManager:currentUser is null");
                            currentUser = DbManager.getDbManager().getActiveUser();
                        }
                        if (currentUser != null) {
                            measuresToSend = DbManager.getDbManager().getNotSentMeasures(currentUser.getId());
                            Log.d(TAG, "currentUser is " + currentUser.getLogin());
                            Log.d(TAG, "Found " + measuresToSend.size() + " measures to send!");
                        }
                    }
                    if (!isNetworkConnected() || (currentMeasure != null) || measuresToSend.isEmpty()) {
                        Log.d(TAG, "Wait ....");
                        // Se manca la connettività, o se non ci sono più misure da inviare e risposte da attendere
                        // prima di mettere la thread in sleep deve essere rilasciato l'eventuale wakelock acquisito
                        // nella onStartCommand
                        PowerManager.WakeLock lock=getLock(this.getApplicationContext());
                        if (currentMeasure == null && lock.isHeld()) {
                            try {
                                lock.release();
                            } catch (Exception e) {
                                Log.e(TAG, "Exception when releasing wakelock", e);
                            }
                        }
                        currT.wait();
                    }
                    if (currentMeasure != null && responseReceived) {
                        Log.d(TAG, "Response arrived");
                        manageResponse();
                    } else if (currentMeasure == null) {
                        Log.d(TAG, "send next measure");
                        sendData();
                    }
                } catch (InterruptedException ie) {
                    logger.log(Level.SEVERE, "Thread interrotto");
                }
            }
        }
    }

    private void sendData() {
        if (!measuresToSend.isEmpty() && isNetworkConnected()) {
            // get the first element
            currentMeasure = measuresToSend.get(0);
            measuresToSend.remove(0);
            receiveError = false;
            responseReceived = false;
            String xml;
            if (!currentMeasure.getFailed())
                xml = XmlManager.getXmlManager().getMeasureXml(currentMeasure);
            else
                xml = XmlManager.getXmlManager().getFailedMeasureXml(currentMeasure);
            try {
                byte[] data = currentMeasure.getFile();
                if (XmlManager.DOCUMENT_FILE_TYPE.equals(currentMeasure.getFileType())) {
                    File f = new File(new String(currentMeasure.getFile(), "UTF-8"));
                    if (f.exists() && f.isDirectory()) {
                        data = new File(f, MeasureManager.DOCUMENT_SEND_TMPFILE).getAbsolutePath().getBytes("UTF-8");
                    }
                }
                WebManager.getWebManager().sendMeasureData(currentUser.getLogin(),
                        currentUser.getPassword(),
                        xml,
                        data,
                        currentMeasure.getFileType(),
                        this);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "WebManager error sendMeasureData");
                logger.log(Level.SEVERE, e.toString());
            }
        }
    }

    private void manageResponse() {
        try {
            if (!receiveError) {
                currentMeasure.setSent(true);
                if (XmlManager.DOCUMENT_FILE_TYPE.equals(currentMeasure.getFileType())) {
                    try {
                        // rimuovo il file zip temporaneo utilizzato per l'invio di tutti i files nella directory
                        File f = new File(new String(currentMeasure.getFile(), "UTF-8"));
                        if (f.exists() && f.isDirectory())
                            new File(f, MeasureManager.DOCUMENT_SEND_TMPFILE).delete();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else {
                currentMeasure.setSendFailReason(Integer.toString(errorCode));
                currentMeasure.setSendFailCount(currentMeasure.getSendFailCount()+1);
            }
            DbManager.getDbManager().updateSentMeasure(currentMeasure);
        } finally {
            currentMeasure = null;
        }
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null;
    }
}
