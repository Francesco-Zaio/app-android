package com.ti.app.telemed.core.usermodule;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btdevices.ComftechManager;
import com.ti.app.telemed.core.common.Patient;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.syncmodule.SyncStatusManager;
import com.ti.app.telemed.core.webmodule.WebManager;
import com.ti.app.telemed.core.webmodule.webmanagerevents.WebManagerResultEvent;
import com.ti.app.telemed.core.webmodule.webmanagerevents.WebManagerResultEventListener;
import com.ti.app.telemed.core.xmlmodule.XmlManager.XmlErrorCode;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * <h1>Gestione della sessione utente</h1>
 * Questa classe implementa il design pattern del singleton.
 * <p>Tramite questa classe vengono effettuate le diverse operazioni di autenticazione (login) e
 * memorizzazone dell'utente corrente loggato e del paziente selezionato.
 * Le operazioni di login, in caso di successo, recuperano anche dalla piattaforma i dati
 * di configurazione dell'utente/paziente e li aggiornano nel DB locale.
 * <p> Alcune operazioni sono asincrone e viene utilizzato un handler per notificare al
 * chiamante l'esito delle operazioni.
 */
public class UserManager {

	private enum Op {
        WEB_LOGIN,
        USER_SYNC,
        LOCAL_LOGIN,
        CHANGE_PWD
    }

    static class RequestData {
        Op operation;
        String login;
        String password;
        String oldPassword;
    }

    private List<RequestData> list = Collections.synchronizedList(new LinkedList<RequestData>());
    private RequestData currServed = null;
    private boolean responseReceived = false;
    private int responseCode = 0;
    private String errorString;

	private static final String TAG = "UserManager";

    private Handler guiHandler;
    private Handler syncHandler;
	private User currentUser = null;
	private Patient currentPatient;

    private final Thread currT;
    private final MyListener listener;

    // variable for singleton
	private static UserManager userManager;

    /**
     * Messaggio inviato all'handler nel caso di autenticazione o cambio password andate a buon fine.
     */
	public static final int USER_CHANGED = 0;
    /**
     * Messaggio inviato all'handler nel caso di autenticazione su piattaforma web fallita.
     */
    public static final int LOGIN_FAILED = 2;
    /**
     * Messaggio inviato all'handler nel caso di autenticazione su db locale fallita.
     */
    public static final int LOCAL_LOGIN_FAILED = 6;
    /**
     * Messaggio inviato all'handler nel caso di errore generico (es. piattaforma non raggiungibile).
     */
	public static final int ERROR_OCCURED = 1;
    /**
     * Messaggio inviato all'handler nel caso di utente disattivato.
     */
	public static final int USER_BLOCKED = 3;
    /**
     * Messaggio inviato all'handler nel caso di utente bloccato per troppi errori di autenticazione.
     */
	public static final int USER_LOCKED = 4;
    /**
     * Messaggio inviato all'handler nel caso di cambio password e la nuova password non rispetta i requisiti minimi di sicurezza.
     */
    public static final int BAD_PASSWORD = 5;

	private UserManager() {
        final MyRunnable runnable = new MyRunnable();
        listener = new MyListener();
        currT = new Thread(runnable);
    	currT.setName("usermanager thread");
        currT.start();
	}

    /**
     * Restituisce l'istanza di UserManager
     * @return               istanza di UserManager
     */
	public static UserManager getUserManager() {
		if (userManager == null) {
			userManager = new UserManager();
		}
		return userManager;
	}

    /**
     * Permette di specificare l'handler che ricevera' le notifiche al termine delle operazioni
     * asincrone
     * @param guiHandler        istanza di Handler
     */
    public void setGuiHandler(Handler guiHandler){
        this.guiHandler = guiHandler;
    }

    /**
     * Salva il Paziente corrente selezionato.
     * @param patient     Paziente
     */
    public void setCurrentPatient(Patient patient) {
        this.currentPatient = patient;
    }

    /**
     * Restituisce il Paziente corrente
     * @return               Paziente corrente
     */
	public Patient getCurrentPatient() {
		return currentPatient;
	}

    /**
     * Legge dal DB locale il Paziente con l'id specificato.
     * @param patientId identificativo del paziente.
     * @return  il paziente selezioneato o null se non esiste un paziente con l'id specificato.
     */
    public Patient getPatientData(String patientId) {
        return DbManager.getDbManager().getPatientData(patientId);
    }

    public String getUserId(String login) {
        User u = DbManager.getDbManager().getUserByLogin(login);
        return (u == null)? null:u.getId();
    }

    /**
     * Restituisce l'utente corrente loggato
     * @return               Utente corrente
     */
    public User getCurrentUser(){
        synchronized (currT) {
            return currentUser;
        }
    }

    /**
     * Imposta l'utente di amministrazione utilizzato solo per il setup dei devices
     * @return Utente di amministrazione devices
     */
    public User setDefaultUser(){
        synchronized (currT) {
            currentUser = DbManager.getDbManager().getUser(DbManager.DEFAULT_USER_ID);
            if (currentUser == null)
                currentUser = DbManager.getDbManager().createDefaultUser();
            currentPatient = currentUser.getPatients().get(0);
            DbManager.getDbManager().alignUserToCurrentDevices(DbManager.DEFAULT_USER_ID);
            return currentUser;
        }
    }

    /**
     * Restituisce la lista di tutti gli utenti configurati nel DB locale.
     * @return Lista utenti.
     */
    public List<User> getAllUsers() {
        return DbManager.getDbManager().getAllUsers();
    }

    /**
     * Cancella l'utente con l'id specificato dal DB locale.
     * @param idUser id utente.
     * @return true in caso di successo o false in caso di errore.
     */
    public boolean deleteUser(String idUser) {
        return DbManager.getDbManager().deleteUser(idUser) > 0;
    }

    /**
     * Restituisce l'ultimo utente che ha effettuato un login.
     * @return {@link User} o null se non è mai stato effettuato un login.
     */
    public User getActiveUser() {
        return DbManager.getDbManager().getActiveUser();
    }

    /**
     * Imposta il flag di auto-login per l'utente passato.
     * @param userId identificatvo utente.
     * @param autoLogin valore da impostare.
     */
    public void saveAutoLoginStatus(String userId, boolean autoLogin) {
        synchronized (currT) {
            if (currentUser != null && currentUser.getId().equals(userId))
                currentUser.setHasAutoLogin(autoLogin);
            DbManager.getDbManager().saveAutoLoginStatus(userId, autoLogin);
        }
    }

    /**
     * Resetta tutti i dati relativi all'utente e paziente corrente
     */
    public void reset() {
        synchronized (currT) {
            this.currentPatient = null;
            this.currentUser = null;
        }
    }

    public void syncActiveUser(Handler handler) {
        User u = getActiveUser();
        if (u == null || handler == null)
            return;
        synchronized (currT) {
            syncHandler = handler;
            RequestData data = new RequestData();
            data.login = u.getLogin();
            data.password = u.getPassword();
            data.operation = Op.USER_SYNC;
            synchronized (currT) {
                list.add(data);
                currT.notifyAll();
            }
        }
    }

    /**
     * Effettua l'autenticazione verso la piattaforma utilizzando le credenziali passate.
     * L'operazione e' asincrona e l'esito viene notificato all'Handler passato.
     * In caso di esito positivo, viene anche recuperata dalla piattaforma e salvata nel DB locale
     * la configurazione dell'utente.
     * Le successive chiamate al metodo {@link #getCurrentUser() getCurrentUser} restituiranno l'istanza
     * di User che ha effettuato la login.
     * @param login          username
     * @param password       password
     */
	public void logInUser(String login, String password) {
        RequestData data = new RequestData();
        data.login = login;
        data.password = password;
        data.operation = Op.WEB_LOGIN;
        synchronized (currT) {
            list.add(data);
            currT.notifyAll();
        }
	}

    /**
     * Rieffettua l'autenticazione dell'utente corrente e l'aggiornamento della sua
     * configurazione dalla piattaforma.
     * L'operazione e' asincrona e l'esito viene notificato all'Handler passato.
     */
    public void logInUser() {
        synchronized (currT) {
            if (currentUser != null) {
                RequestData data = new RequestData();
                data.login = currentUser.getLogin();
                data.password = currentUser.getPassword();
                data.operation = Op.WEB_LOGIN;
                list.add(data);
                currT.notifyAll();
            } else {
                Log.e(TAG, "CurrentUser is NULL");
            }
        }
	}

    /**
     * Modifica la password dell'utente corrente.
     * L'operazione e' asincrona e l'esito viene notificato all'Handler passato.
     *
     * @param oldPassword    vecchia password da modificare
     * @param newPassword    nuova password
     */
    public void changePassword(String oldPassword, String newPassword) {
        synchronized (currT) {
            if (currentUser != null) {
                RequestData data = new RequestData();
                data.login = currentUser.getLogin();
                data.password = newPassword;
                data.oldPassword = oldPassword;
                data.operation = Op.CHANGE_PWD;
                list.add(data);
                currT.notifyAll();
            }
        }
    }

    /**
     * Effettua l'autenticazione verso il DB locale utilizzando le credenziali passate.
     * L'operazione e' asincrona e l'esito viene notificato all'Handler passato.
     * In caso di esito positivo, viene anche recuperata dalla piattaforma e salvata nel DB locale
     * la configurazione dell'utente.
     * Le successive chiamate al metodo {@link #getCurrentUser() getCurrentUser} restituiranno l'istanza
     * di User che ha effettuato la login.
     * @param login      username
     * @param password   password
     */
    public void logInUserFromDb(String login, String password) {
        RequestData data = new RequestData();
        data.login = login;
        data.password = password;
        data.operation = Op.LOCAL_LOGIN;
        synchronized (currT) {
            list.add(data);
            currT.notifyAll();
        }
	}

    /**
     * Blocca l'accesso all'utente indicato.
     * <p>
     * Questo metodo viene chiamato dal servizio che in background aggiorna periodicamente
     * dalla piattaforma i dati dell'utente corrente nel caso in cui l'utente sia stato
     * bloccato.
     * Normalmente non deve essere invocato da GUI.
     *
     * @param login          userId dell'utente da bloccare
     */
    private void setUserBlocked(String login) {
        synchronized (currT) {
            if (login != null) {
                User u = DbManager.getDbManager().updateUserBlocked(login, true);
                if (u != null)
                    ComftechManager.getInstance().checkMonitoring(u.getId(), true);
                reset();
            }
        }
    }

	private void selectUser(User user) {
        synchronized (currT) {
            currentUser = user;
            SyncStatusManager.getSyncStatusManager().userChanged(currentUser);
            DbManager.getDbManager().updateActiveUser(currentUser.getId());
            if (currentUser != null) {
                Log.i(TAG, "selectUser --> utente corrente: " + user.getName() + " " + user.getSurname());
                try {
                    List<Patient> patientList = currentUser.getPatients();
                    if(patientList != null && patientList.size() == 1)
                        currentPatient = patientList.get(0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                // Check if monitoring active and and eventually update the monitoring parametres
                ComftechManager.getInstance().checkMonitoring(currentUser.getId(), false);
            }
        }
	}

    private class MyListener implements WebManagerResultEventListener {
        // methods of WebManagerResultEventListener interface
        @Override
        public void webAuthenticationSucceeded(WebManagerResultEvent evt) {
            Log.i(TAG, "webAuthenticationSucceeded: login="+currServed.login);
            // the web operations had success, so we must only extract the new operator
            // from database
            synchronized (currT) {
                responseCode = USER_CHANGED;
                responseReceived = true;
                currT.notifyAll();
            }
        }

        @Override
        public void webChangePasswordSucceded(WebManagerResultEvent evt) {
            Log.i(TAG, "webChangePasswordSucceded: login="+currServed.login);
            // the change password had success, so we must update the current password
            synchronized (currT) {
                responseCode = USER_CHANGED;
                responseReceived = true;
                currT.notifyAll();
            }
        }

        @Override
        public void webAuthenticationFailed(WebManagerResultEvent evt) {
            Log.i(TAG, "webAuthenticationFailed: login="+currServed.login);
            // log in failed, we must require to the user to repeat the log in operation
            synchronized (currT) {
                responseCode = LOGIN_FAILED;
                responseReceived = true;
                currT.notifyAll();
            }
        }

        @Override
        public void webOperationFailed(WebManagerResultEvent evt, XmlErrorCode code) {
            Log.i(TAG, "webOperationFailed: login=" + currServed.login + " code="+code.toString());
            synchronized (currT) {
                responseReceived = true;
                switch (code) {
                    case PLATFORM_ERROR:
                        errorString = ResourceManager.getResource().getString("errorPlatform");
                        responseCode = ERROR_OCCURED;
                        break;
                    case CONNECTION_ERROR:
                        errorString = ResourceManager.getResource().getString("errorHttp");
                        responseCode = ERROR_OCCURED;
                        break;
                    case PASSWORD_WRONG_TOO_MANY_TIMES:
                        errorString = ResourceManager.getResource().getString("passwordWrongLock");
                        responseCode = USER_LOCKED;
                        break;
                    case USER_BLOCKED:
                        responseCode = USER_BLOCKED;
                        errorString = ResourceManager.getResource().getString("userBlocked");
                        break;
                    case BAD_PASSWORD:
                        responseCode = BAD_PASSWORD;
                        errorString = ResourceManager.getResource().getString("badPassword");
                        break;
                    default:
                        responseCode = ERROR_OCCURED;
                        errorString = "";
                        break;
                }
                currT.notifyAll();
            }
        }
    }

	private class MyRunnable implements Runnable {
        @Override
        public void run() {
            synchronized (currT) {
                while (true) {
                    try {
                        if ((currServed != null) || list.isEmpty()) {
                            Log.d(TAG, "Wait ....");
                            currT.wait();
                        }
                        if ((responseReceived) && (currServed != null)) {
                            Log.d(TAG, "Awake, response arrived");
                            manageResponse();
                        }
                        if ((currServed == null) && (!list.isEmpty())) {
                            // Send the next web request
                            Log.d(TAG, "Sending the next request.");
                            sendRequest();
                        }
                    } catch (InterruptedException ie) {
                        Log.e(TAG, "Thread interrotto");
                    }
                }
            }
        }
    }

    private void sendRequest() {
        currServed = list.remove(0);
        responseCode = 0;
        responseReceived = false;
        errorString = "";

        switch (currServed.operation) {
            case WEB_LOGIN:
            case USER_SYNC:
                try {
                    // now we must listen to the events of web manager for the results
                    WebManager.getWebManager().askOperatorData(currServed.login, currServed.password, UserManager.this.listener, true);
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                    currServed = null;
                }
                break;
            case CHANGE_PWD:
                try {
                    // now we must listen to the events of web manager for the results
                    WebManager.getWebManager().changePassword(currServed.login, currServed.oldPassword, currServed.password, UserManager.this.listener);
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                    currServed = null;
                }
                break;
            case LOCAL_LOGIN:
                loginFromDB();
                currServed = null;
                break;
        }
    }

    private void loginFromDB() {
        try {
            User user = (DbManager.getDbManager()).getUser(currServed.login, currServed.password);
            if (user == null) {
                Log.i(TAG, "Local authentication failed: currentUser is null");
                sendResult(LOCAL_LOGIN_FAILED, ResourceManager.getResource().getString("LOCAL_LOGIN_ERROR"));
            } else {
                selectUser(user);
                sendResult(USER_CHANGED, null);
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            sendResult(ERROR_OCCURED, e.getMessage());
        }
    }

    private void manageResponse() {
        switch (responseCode) {
            case USER_CHANGED:
                SyncStatusManager.getSyncStatusManager().setLoginError(false);
                loginFromDB();
                break;
            case LOGIN_FAILED:
                Log.i(TAG, "webAuthenticationFailed()");
                SyncStatusManager.getSyncStatusManager().setLoginError(true);
                User u = DbManager.getDbManager().getUser(currServed.login, currServed.password);
                if (u != null) {
                    DbManager.getDbManager().resetActiveUser(u.getId());
                    saveAutoLoginStatus(u.getId(),false);
                    ComftechManager.getInstance().checkMonitoring(u.getId(), true);
                }
                sendResult(LOGIN_FAILED, ResourceManager.getResource().getString("LoginDialog.badCredentials"));
                break;
            case USER_BLOCKED:
                setUserBlocked(currServed.login);
            case USER_LOCKED:
            case BAD_PASSWORD:
            case ERROR_OCCURED:
                sendResult(responseCode, errorString);
                break;
        }
        currServed = null;
    }

	private void sendResult(int msgType, String msgText){
        if (currServed.operation == Op.USER_SYNC) {
            // operazione aggiornamento utente da SyncWorker
            Message message = syncHandler.obtainMessage(msgType);
            syncHandler.sendMessage(message);
            // se c'è la GUI attiva aggiorno anche quella (escluso il caso di errori)
            if (guiHandler != null &&
                    (msgType == USER_CHANGED || msgType == LOGIN_FAILED || msgType == USER_BLOCKED)) {
                message = guiHandler.obtainMessage(msgType);
                message.obj = msgText;
                guiHandler.sendMessage(message);
            }
        } else if (guiHandler != null) {
            // generica operazione da GUI
            Message message = guiHandler.obtainMessage(msgType);
            message.obj=msgText;
            guiHandler.sendMessage(message);
        }
	}
}
