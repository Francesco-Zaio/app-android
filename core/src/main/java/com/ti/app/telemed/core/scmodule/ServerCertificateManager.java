package com.ti.app.telemed.core.scmodule;

import java.util.ArrayList;

import javax.security.cert.X509Certificate;

import com.ti.app.telemed.core.common.ServerCertificate;
import com.ti.app.telemed.core.dbmodule.DbManager;

import android.os.Handler;
import android.util.Log;

public class ServerCertificateManager {
	
	private static ServerCertificateManager scManager;
	
	private static final String TAG = "ServerCertificateManager";
	
	private Handler handler;
	
	public static final int DELETE_SUCCESS = 0;
	public static final int DELETE_FAILURE = 1;
	public static final int ADD_CERTIFICATE_FAILURE = 2;
	
	private ServerCertificateManager() {
		
    }
	
	public static ServerCertificateManager getScMananger() {
		if(scManager == null){
	    	scManager = new  ServerCertificateManager();
    	}
    	return scManager;
	}
	
	private void sendMessageToHandler(int msgType) {
		switch(msgType) {
		case ADD_CERTIFICATE_FAILURE:
			handler.sendEmptyMessage(ADD_CERTIFICATE_FAILURE);
			break;
		default:
			handler.sendEmptyMessage(msgType);
			break;
		}
	}

	public void setHandler(Handler handler) {
		this.handler = handler;
	}
	
	public Handler getHandler() {
		return this.handler;
	}
	
	/**
	 * Metodo che permette di eliminare tutti i certificati di sicurezza associati ad un utente
	 * @param idUser variabile di tipo {@code String} che contiene l'identificatore dell'utente
	 */
	public void deleteAllServerCerts(String idUser) {
		try {
			Log.i(TAG, "Elimino tutti i certificati di " + idUser);
			DbManager.getDbManager().deleteAllServerCerts(idUser);
			Log.i(TAG, "Certificati eliminati");
			sendMessageToHandler(DELETE_SUCCESS);
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "deleteAllServerCerts: " + e.getMessage());
			sendMessageToHandler(DELETE_FAILURE);
		}
	}
	
	/**
	 * Metodo che permette di aggiungere un certificato all'elenco dei certificati personali dell'utente
	 * @param idUser variabile di tipo {@code String} che contiene l'identificatore dell'utente
	 * @param certificato oggetto di tipo {@code X509Certificate} che contiene il certificato del server
	 */
	public void addServerCertificate(String idUser, X509Certificate certificato) {
		try {
			Log.i(TAG, "addServerCertificate: " + certificato.getPublicKey());
			ServerCertificate sc = new ServerCertificate();
			sc.setPublicKey(certificato.getPublicKey().getEncoded());
			DbManager.getDbManager().insertServerCertificate(idUser, sc);
			Log.i(TAG, "addServerCertificate: certificato inserito");
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "addServerCertificate: " + e.getMessage());
			sendMessageToHandler(ADD_CERTIFICATE_FAILURE);
		}
	}
	
	/**
	 * Metodo che restituisce i certificati accettati dall'utente
	 * @param idUser variabile di tipo {@code String} che contiene l'identificatore dell'utente
	 * @return
	 */
	public static ArrayList<ServerCertificate> getAllowedServerCertificate(String idUser) {
		DbManager dbManager = DbManager.getDbManager();
    	try {
    		ArrayList<ServerCertificate> scList = dbManager.getServerCertificate(idUser);
			return scList;
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "getAllowedServerCertificate: " + e.getMessage());
			return null;
		}
    }
}
