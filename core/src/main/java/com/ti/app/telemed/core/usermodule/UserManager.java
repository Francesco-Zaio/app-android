package com.ti.app.telemed.core.usermodule;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.common.Patient;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.common.UserPatient;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.webmodule.WebManager;
import com.ti.app.telemed.core.webmodule.webmanagerevents.WebManagerResultEvent;
import com.ti.app.telemed.core.webmodule.webmanagerevents.WebManagerResultEventListener;
import com.ti.app.telemed.core.xmlmodule.XmlManager.XmlErrorCode;
import com.ti.app.telemed.core.exceptions.DbException;
import com.ti.app.telemed.core.util.GWConst;

import android.os.Bundle;
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
        IDLE,
        TALK_TO_WEB,
        CHANGE_PWD,
        TALK_TO_DB
    }

	private static final String TAG = "UserManager";

	private User currentUser = null;
	private Patient currentPatient;

	// temporary for login configuration
	private String login;
	private String password;
	private String oldPassword;
    private String newPassword;

	// variables for thread management
	private boolean loop = true;
    // switch about current state (which operation is
    // currently on, or idle if there isn't)
    private Op currentOpOn;
    private final Thread currT;
    private final MyListener listener;

    // variable for singleton
	private static UserManager userManager;

	private Logger logger = Logger.getLogger(UserManager.class.getName());
	private Handler handler;

    /**
     * Messaggio inviato all'handler nel caso di autenticazione o cambio password andate a buon fine.
     */
	public static final int USER_CHANGED = 0;
    /**
     * Messaggio inviato all'handler nel caso di credenziali di autenticazione errate.
     */
	public static final int LOGIN_FAILED = 2;
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
		currentOpOn = Op.IDLE;
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
     * @param handler        istanza di Handler
     */
	public void setHandler(Handler handler){
		this.handler = handler;
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
     * Restituisce l'utente corrente loggato
     * @return               Utente corrente
     */
	public User getCurrentUser(){
        synchronized (currT) {
            return currentUser;
        }
	}

    /**
     * Effettua l'autenticazione verso la piattaforma utilizzando le credenziali passate.
     * L'operazione e' asincrona e l'esito viene notificato all'Handler passato
     * precedentemente con il metodo {@link #setHandler(Handler handler) setHandler}.
     * In caso di esito positivo, viene anche recuperata dalla piattaforma e salvata nel DB locale
     * la configurazione dell'utente.
     * Le successive chiamate al metodo {@link #getCurrentUser() getCurrentUser} restituiranno l'istanza
     * di User che ha effettuato la login.
     * @param login          username
     * @param password       password
     */
	public void logInUser(String login, String password) {
		synchronized (currT) {
			if (currentOpOn == Op.IDLE) {
				this.login = login;
				this.password = password;				
				// now the user manager has to listen to the (status) events of web socket
	        	currentOpOn = Op.TALK_TO_WEB;
	        }
	        currT.notifyAll();
		}
	}

    /**
     * Rieffettua l'autenticazione dell'utente corrente e l'aggiornamento della sua
     * configurazione dalla piattaforma.
     * L'operazione e' asincrona e l'esito viene notificato all'Handler passato
     * precedentemente con il metodo {@link #setHandler(Handler handler) setHandler}
     */
    public void logInUser() {
        synchronized (currT) {
            if (currentOpOn == Op.IDLE) {
                if (currentUser != null) {
                    login = currentUser.getLogin();
                    password = currentUser.getPassword();
                    currentOpOn = Op.TALK_TO_WEB;
                } else {
					Log.e(TAG, "CurrentUser is NULL");
                }
            }
            currT.notifyAll();
        }
	}

    /**
     * Modifica la password dell'utente corrente.
     * L'operazione e' asincrona e l'esito viene notificato all'Handler passato
     * precedentemente con il metodo {@link #setHandler(Handler handler) setHandler}
     *
     * @param oldPassword    vecchia password da modificare
     * @param newPassword    nuova password
     */
    public void changePassword(String oldPassword, String newPassword) {
        synchronized (currT) {
            if (currentOpOn == Op.IDLE) {
                if (currentUser != null) {
                    login = currentUser.getLogin();
                    this.oldPassword = oldPassword;
                    this.newPassword = newPassword;
                    currentOpOn = Op.CHANGE_PWD;
                }
            }
            currT.notifyAll();
        }
    }
	
	private void logInUserFromDb() throws Exception {
		synchronized (currT) {
			if (currentOpOn == Op.IDLE) {
				if ((login != null) && (password != null)) {
					currentOpOn = Op.TALK_TO_DB;
				} else {
					throw new Exception();
				}
	        }
	        currT.notifyAll();
		}
	}

    /**
     * Effettua l'autenticazione verso il DB locale utilizzando le credenziali passate.
     * L'operazione e' asincrona e l'esito viene notificato all'Handler passato
     * precedentemente con il metodo {@link #setHandler(Handler handler) setHandler}.
     * In caso di esito positivo, viene anche recuperata dalla piattaforma e salvata nel DB locale
     * la configurazione dell'utente.
     * Le successive chiamate al metodo {@link #getCurrentUser() getCurrentUser} restituiranno l'istanza
     * di User che ha effettuato la login.
     * @param login      username
     * @param password   password
     */
    public void logInUserFromDb(String login, String password) throws Exception {
		synchronized (currT) {
			if (currentOpOn == Op.IDLE) {
				if ((login != null) && (password != null)) {
					this.login = login;
					this.password = password;
					currentOpOn = Op.TALK_TO_DB;
				} else {
					throw new Exception();
				}
	        }
	        currT.notifyAll();
		}
	}

    /**
     * Ricarica dal DB locale i dati dell'utente corrente.
     * <p>
     * Questo metodo viene chiamato dal servizio che in background aggiorna periodicamente
     * dalla piattaforma i dati dell'utente corrente.
     * Normalmente non deve essere invocato da GUI.
     */
    public void reloadCurrentUser() {
        User user = DbManager.getDbManager().getUser(currentUser.getId());
        selectUser(user, true);
    }

    /**
     * Resetta tutti i dati relativi all'utente e paziente corrente
     */
    public void reset() {
        synchronized (currT) {
            this.login = null;
            this.password = null;
            this.currentPatient = null;
            this.currentUser = null;
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
    public void setUserBlocked(String login) {
        synchronized (currT) {
            if (login != null) {
                DbManager.getDbManager().updateUserBlocked(login, true);
                if (currentUser != null && currentUser.getLogin().equals(login)) {
                    try {
                        User user = (DbManager.getDbManager()).getUser(currentUser.getId());
                        if (user != null)
                            selectUser(user, true);
                        else
                            reset();
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Failed to load current user from login and password");
                    }
                }
            }
        }
    }

	private void selectUser(User user, boolean silent) {
        synchronized (currT) {
            currentUser = user;

            if (currentUser != null) {
                Log.i(TAG, "selectUser --> utente corrente: " + user.getName() + " " + user.getSurname());
                try {
                    DbManager.getDbManager().setCurrentUser(currentUser);
                    List<UserPatient> patientList = DbManager.getDbManager().getUserPatients(currentUser.getId());
                    if(patientList != null && patientList.size() == 1)
                        currentPatient = DbManager.getDbManager().getPatientData(patientList.get(0).getIdPatient());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (!silent)
                sendMessage(USER_CHANGED, null);
        }
	}

    private void updateUserCredentials() throws DbException {
        synchronized (currT) {
            if (login != null && password != null) {
                DbManager.getDbManager().updateUserCredentials(login, password);
            }
        }
    }

    private class MyListener implements WebManagerResultEventListener {
        // methods of WebManagerResultEventListener interface
        @Override
        public void webAuthenticationSucceeded(WebManagerResultEvent evt) {
            // the web operations had success, so we must only extract the new operator
            // from database
            try {
                updateUserCredentials();
                logInUserFromDb();
            } catch (DbException e) {
                logger.log(Level.SEVERE, "Failed to update patient login and password");
                sendMessage(ERROR_OCCURED, e.getMessage());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error encoding password");
                sendMessage(ERROR_OCCURED, "Error encoding password");
            }
        }

        @Override
        public void webChangePasswordSucceded(WebManagerResultEvent evt) {
            // the change password had success, so we must update the current user on the DB
            synchronized (currT) {
                password = newPassword;
            }
            webAuthenticationSucceeded(evt);
        }


        @Override
        public void webAuthenticationFailed(WebManagerResultEvent evt) {
            // log in failed, we must require to the user to repeat the log in operation
            Log.i(TAG, "webAuthenticationFailed()");
            sendMessage(LOGIN_FAILED, ResourceManager.getResource().getString("LoginDialog.badCredentials"));
        }

        @Override
        public void webOperationFailed(WebManagerResultEvent evt, XmlErrorCode code) {
            if (code != null) {
                logger.log(Level.INFO, code.toString());
                if (code.equals(XmlErrorCode.PLATFORM_ERROR))
                    sendMessage(ERROR_OCCURED, ResourceManager.getResource().getString("errorPlatform"));
                else if (code.equals(XmlErrorCode.CONNECTION_ERROR))
                    // Troppi tentativi di autenticazione falliti, utente temporaneamente bloccato
                    sendMessage(ERROR_OCCURED, ResourceManager.getResource().getString("errorHttp"));
                else if (code.equals(XmlErrorCode.PASSWORD_WRONG_TOO_MANY_TIMES))
                    // Troppi tentativi di autenticazione falliti, utente temporaneamente bloccato
                    sendMessage(USER_LOCKED, ResourceManager.getResource().getString("passwordWrongLock"));
                else if (code.equals(XmlErrorCode.USER_BLOCKED)) {
                    // Utente disattivato
                    setUserBlocked(login);
                    sendMessage(USER_BLOCKED, ResourceManager.getResource().getString("userBlocked"));
                } else if (code.equals(XmlErrorCode.BAD_PASSWORD)) {
                    // La nuova password inviata non rispetta i requisiti minimi di sicurezza
                    sendMessage(BAD_PASSWORD, ResourceManager.getResource().getString("badPassword"));
                } else
                    // errore generico non identificato
                    sendMessage(ERROR_OCCURED, "");
            }
        }
    }

	private class MyRunnable implements Runnable {
        @Override
        public void run() {
            synchronized (currT) {
                while (loop) {
                    switch (currentOpOn) {
                        case IDLE:
                            try {
                                currT.wait();
                            } catch (InterruptedException e) {
                                logger.log(Level.SEVERE, "Thread interrotto");
                            }
                            break;
                        case TALK_TO_WEB:
                            try {
                                // now we must listen to the events of web manager for the results
                                WebManager.getWebManager().askOperatorData(login, password, UserManager.this.listener, true);
                            } catch (Exception e) {
                                logger.log(Level.SEVERE, "web error");
                                e.printStackTrace();
                            } finally {
                                currentOpOn = Op.IDLE;
                            }
                            break;
                        case CHANGE_PWD:
                            try {
                                // now we must listen to the events of web manager for the results
                                WebManager.getWebManager().changePassword(login, oldPassword, newPassword, UserManager.this.listener);
                            } catch (Exception e) {
                                logger.log(Level.SEVERE, "web error");
                                e.printStackTrace();
                            } finally {
                                currentOpOn = Op.IDLE;
                            }
                            break;
                        case TALK_TO_DB:
                            try {
                                // we have obtained the result (negative when we go on off line,
                                // positive otherwise) so we don't need to go on to listen to the
                                // events of web manager and web socket
                                User user = (DbManager.getDbManager()).getUser(login, password);
                                if (user == null) {
                                    logger.log(Level.SEVERE, "Local authentication failed: currentUser is null");
                                    sendMessage(LOGIN_FAILED, ResourceManager.getResource().getString("LOCAL_LOGIN_ERROR"));
                                } else {
                                    selectUser(user, false);
                                }
                            } catch (Exception e) {
                                logger.log(Level.SEVERE, "database error UserManager run");
                                sendMessage(ERROR_OCCURED, e.getMessage());
                            } finally {
                                currentOpOn = Op.IDLE;
                            }
                            break;
                    }
                }
            }
        }
    }
	
	private void sendMessage(int msgType, String msgText){
		Message message = handler.obtainMessage(msgType);	
		if(msgText != null && msgText.length() > 0){
			Bundle data = new Bundle();
			data.putString(GWConst.MESSAGE, msgText);
			message.setData(data);
		}
		handler.sendMessage(message);
	}
}
