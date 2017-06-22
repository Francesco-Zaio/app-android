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

// this is a singleton
public class UserManager implements Runnable, WebManagerResultEventListener {

	private enum Op {
        IDLE,
        TALK_TO_WEB,
        CHANGE_PWD,
        TALK_TO_DB,
        RETURN_LOGIN_FAILED
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

    // variable for singleton
	private static UserManager userManager;

	private Logger logger = Logger.getLogger(UserManager.class.getName());

	private Handler handler;

	//Costanti per l'invio di Message
	public static final int USER_CHANGED = 0; // Autenticazione andata a buon fine
	public static final int LOGIN_FAILED = 2; // Credenziali di autenticazione errate
	public static final int ERROR_OCCURED = 1; // Errore generico (es. piattaforma non raggiungibile)
	public static final int USER_BLOCKED = 3; // Utente disattivato
	public static final int USER_LOCKED = 4; // Utente bloccato per troppi errori di autenticazione
    public static final int BAD_PASSWORD = 5; // La nuova password non rispetta i requisiti minimi di sicurezza

	private UserManager() {
		currentOpOn = Op.IDLE;
        currT = new Thread(this);
    	currT.setName("usermanager thread");
        currT.start();
	}

	public static UserManager getUserManager() {
		if (userManager == null) {
			userManager = new UserManager();
		}
		return userManager;
	}

	public void setHandler(Handler handler){
		this.handler = handler;
	}

	public Patient getCurrentPatient() {
		return currentPatient;
	}
	
	public void setCurrentPatient(Patient patient) {
		this.currentPatient = patient;
	}
	
	public User getCurrentUser(){
        synchronized (currT) {
            return currentUser;
        }
	}			
	
	// this is a trigger to the process of logging in a user
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

	// this is a trigger to the process of logging in a user (when login and password are already set)
	public void logInUser() throws Exception {
        synchronized (currT) {
            if (currentOpOn == Op.IDLE) {
                if (currentUser != null) {
                    login = currentUser.getLogin();
                    password = currentUser.getPassword();
                    currentOpOn = Op.TALK_TO_WEB;
                } else {
                    throw new Exception();
                }
            }
            currT.notifyAll();
        }
	}

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
	
	// this is a trigger to the process of logging in a user from the database when the
	// application is off line (login and password are already set)
	public void logInUserFromDb() throws Exception {
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

    public void selectUser(String userId) {
        try {
            User user = DbManager.getDbManager().getUser(userId);
            if (user == null) {
                reset();
                Log.e(TAG, "User with id " + userId + " not exist!");
            } else {
                selectUser(user, true);
            }
        } catch (Exception e) {
            Log.e(TAG, "database error UserManager run");
        }
    }
	
	public void selectUser(User user) {
		selectUser(user, false);
	}
	
	public void selectUser(User user, boolean silent) {
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
	
	public boolean checkPassword(String user, String password){
        synchronized (currT) {
            return (user != null && this.login != null && user.equals(this.login))
                    && (password != null && this.password != null && password
                    .equals(this.password));
        }
	}

    private void updateUserCredentials() throws DbException {
        synchronized (currT) {
            if (login != null && password != null) {
                DbManager.getDbManager().updateUserCredentials(login, password);
            }
        }
    }

    public void setUserBlocked(String userId) {
        synchronized (currT) {
            if (userId != null) {
                DbManager.getDbManager().updateUserBlocked(userId, true);
                if (currentUser != null && currentUser.getId().equals(userId)) {
                    try {
                        User user = (DbManager.getDbManager()).getUser(userId);
						if (user != null)
							selectUser(user, true);
						else
							reset();
                    } catch (DbException e) {
                        logger.log(Level.SEVERE, "Failed to load current user from login and password");
                    }
                }
            }
        }
    }
	
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
		if(code!=null){
			logger.log(Level.INFO, code.toString());
            if(code.equals(XmlErrorCode.PLATFORM_ERROR))
                sendMessage(ERROR_OCCURED, ResourceManager.getResource().getString("errorPlatform"));
            else if(code.equals(XmlErrorCode.PASSWORD_WRONG_TOO_MANY_TIMES))
                sendMessage(USER_LOCKED, ResourceManager.getResource().getString("passwordWrongLock"));
            else if(code.equals(XmlErrorCode.USER_BLOCKED)) {
				setUserBlocked(login);
				sendMessage(USER_BLOCKED, ResourceManager.getResource().getString("userBlocked"));
			} else if(code.equals(XmlErrorCode.BAD_PASSWORD)) {
                sendMessage(BAD_PASSWORD, ResourceManager.getResource().getString("badPassword"));
            }else
                sendMessage(ERROR_OCCURED, "");
        }
	}

	// methods of Runnable interface
	
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
							WebManager.getWebManager().askOperatorData(login, password, this, true);
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
                            WebManager.getWebManager().changePassword(login, oldPassword, newPassword, this);
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
								selectUser(user);
							}							
						} catch (DbException e) {
							logger.log(Level.SEVERE, "database error UserManager run");
							sendMessage(ERROR_OCCURED, e.getMessage());
						} finally {
							currentOpOn = Op.IDLE;
						}
                        break;
                    case RETURN_LOGIN_FAILED:
            			// the login failed and we have to require to the user to retry to
                    	// log in
                    	sendMessage(LOGIN_FAILED, ResourceManager.getResource().getString("LoginDialog.badCredentials"));
            			currentOpOn = Op.IDLE;
						break;
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

	public void reset() {
        synchronized (currT) {
            this.login = null;
            this.password = null;
            this.currentPatient = null;
            this.currentUser = null;
        }
	}
}
