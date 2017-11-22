package com.ti.app.telemed.core.measuremodule;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.MeasureProtocolCfg;
import com.ti.app.telemed.core.common.Patient;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.common.UserMeasure;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.syncmodule.SendMeasuresService;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.util.Util;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

import android.content.Intent;
import android.util.Log;

import static com.ti.app.telemed.core.util.GWConst.EGwCode_D0;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_D1;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_Q0;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_Q1;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_Q2;
/**
 * <h1>Gestione della misure sul DB</h1>.
 * Questa classe implementa il design pattern del singleton.
 * <p>Tramite questa classe viene gestita la memorizzazione e la lettura delle misure
 * sul DB locale.
 */
public class MeasureManager {
	private static final String TAG = "MEASUREMANAGER";
	private static MeasureManager measureManager;

    /**
     * Restituisce l'istanza di MeasureManager.
     * @return      istanza di MeasureManager.
     */
	public static MeasureManager getMeasureManager() {
		if (measureManager == null) {
			measureManager = new MeasureManager();
		}
		return measureManager;
	}


    /**
     * Tipo di misura non biometrica.
     */
	public enum NotBioMeasureType {
		HealthStatus("Q0"),
        SleepQuality("Q1"),
		PainLevel("Q2");

        private final String id;
        NotBioMeasureType(String s) {
            id = s;
        }
        public boolean equals(String otherId) {
            return id.equals(otherId);
        }
        public String toString() {
            return this.id;
        }
	}


	/**
     * Tipo di documento.
     */
    public enum DocumentType{
        DischargeDocument("D0"),
        AcceptanceDocument("D1"),
        LaboratoryReport("D2"),
        RadiologicalImage("D3"),
        MedicalReport("D4"),
        Diagnosis("D5"),
        TherapyPrescription("D6"),
        Letter("D7"),
        WoundImage("D8");

        private final String id;
        DocumentType(String s) {
            id = s;
        }
        public boolean equals(String otherId) {
            return id.equals(otherId);
        }
        public String toString() {
            return this.id;
        }
    }
    /**
     * Salva ed invia alla piattaforma una misura.
     * @param m     misura da salvare (non puo' essere null).
     * @return      {@code true} in caso di successo o altrimenti {@code false}.
     */
	public boolean saveMeasureData(Measure m) {
		synchronized (this) {
            if (m == null)
                return false;
			Log.i(TAG, "saveMeasureData: ");
            try {
                boolean result = DbManager.getDbManager().insertMeasure(m);
                MyApp.getContext().startService(new Intent(MyApp.getContext(), SendMeasuresService.class));
                return result;
            } catch (Exception sqle) {
                Log.e(TAG, "ERROR SAVE MEASURE DB " + sqle);
                return false;
            }
		}	
	}

    /**
     * Salva ed invia alla piattaforma una documento.
     * @param outputFile        Path assoluto del file contenente il documento da inviare.
     * @param docType           Tipo di documento {@see DocumentType}.
     * @return                  {@code true} in caso di successo o altrimenti {@code false}.
     */
	public boolean saveDocument(String outputFile, DocumentType docType) {
		synchronized (this) {

            if (outputFile == null || outputFile.isEmpty() || docType==null) {
                Log.e(TAG, "saveDocument - null parameter!");
                return false;
            }
            File f = new File(outputFile);
            if(!f.exists() || f.isDirectory()) {
                Log.e(TAG, "saveDocument - File not found!");
                return false;
            }
            User currentUser = UserManager.getUserManager().getCurrentUser();
            Patient currentPatient = UserManager.getUserManager().getCurrentPatient();
            if (currentUser == null || (currentPatient == null && !currentUser.getIsPatient())) {
                Log.e(TAG, "saveDocument - currentUser or Patient is Null");
                return false;
            }

			String code = docType.toString();

			Measure m = new Measure();
			HashMap<String, String> map = new HashMap<>();
			String [] tokens  = outputFile.split(File.separator);
			map.put(code, tokens[tokens.length-1]);  //nome file
			try {
				m.setDeviceDesc("Document-" + code);
				m.setTimestamp(Util.getTimestamp(null));
				m.setMeasureType(code);
				m.setMeasures(map);
				m.setFile(outputFile.getBytes("UTF-8"));
				m.setFileType(XmlManager.DOCUMENT_FILE_TYPE);
				m.setIdUser(currentUser.getId());
				m.setIdPatient(currentUser.getId());
				m.setFailed(false);

				return saveMeasureData(m);
			} catch (Exception e) {
                Log.e(TAG, e.getMessage());
                return false;
			}
		}
	}

    /**
     * Salva ed invia alla piattaforma una misura di temperatura corporea.
     * @param temperature       Valore della temperatura corporea in gradi centigradi.
     * @param standardProtocol  Indica se la misura e' stata acquisita con il "Protocollo standard".
     * @return                  {@code true} in caso di successo o altrimenti {@code false}.
     */
    public boolean saveManualTemperature(double temperature, boolean standardProtocol) {
        User currentUser = UserManager.getUserManager().getCurrentUser();
        Patient currentPatient = UserManager.getUserManager().getCurrentPatient();
        if (currentUser == null || (currentPatient == null && !currentUser.getIsPatient())) {
            Log.e(TAG, "saveManualTemperature - currentUser or Patient is Null");
            return false;
        }
        synchronized (this) {
            Measure m = new Measure();
            HashMap<String, String> map = new HashMap<>();
            String tempVal = String.format(Locale.ITALY, "%.2f", temperature);
            map.put(GWConst.EGwCode_0R, tempVal);
            try {
                m.setDeviceDesc(GWConst.DEVICE_MANUAL);
                m.setTimestamp(Util.getTimestamp(null));
                m.setMeasureType(GWConst.KMsrTemp);
                m.setMeasures(map);
                m.setStandardProtocol(standardProtocol);
                m.setIdUser(currentUser.getId());
                if (currentPatient != null)
                    m.setIdPatient(currentPatient.getId());
                else
                    m.setIdPatient(currentUser.getId());
                m.setFailed(false);
                return saveMeasureData(m);
            } catch (Exception e) {
                Log.e(TAG,e.getMessage());
                return false;
            }
        }
    }

    /**
     * Salva ed invia alla piattaforma una misura non biometrica.
     * @param type              Tipo di misura biometrica {@see NotBioMeasureType}.
     * @param level             Valore della misura biometrica.
     * @param standardProtocol  Indica se la misura e' stata acquisita con il "Protocollo standard".
     * @return                  {@code true} in caso di successo o altrimenti {@code false}.
     */
	public boolean saveNotBioMeasure(NotBioMeasureType type, int level, boolean standardProtocol) {
        User currentUser = UserManager.getUserManager().getCurrentUser();
        Patient currentPatient = UserManager.getUserManager().getCurrentPatient();
        if (currentUser == null || (currentPatient == null && !currentUser.getIsPatient())) {
            Log.e(TAG, "saveNotBioMeasure - currentUser or Patient is Null");
            return false;
        }
		synchronized (this) {
            String code = type.toString();
			Measure m = new Measure();
			HashMap<String, String> map = new HashMap<>();
			map.put(code, Integer.toString(level));
			try {
				m.setDeviceDesc(code);
				m.setTimestamp(Util.getTimestamp(null));
				m.setMeasureType(code);
				m.setMeasures(map);
				m.setStandardProtocol(standardProtocol);
				m.setIdUser(currentUser.getId());
				m.setIdPatient(currentUser.getId());
				m.setFailed(false);
				return saveMeasureData(m);
			} catch (Exception e) {
                Log.e(TAG,e.getMessage());
				return false;
			}
		}
	}

	/**
	 * Elimina una misura dal db.
	 * @param measure       Misura da eliminare dal db.
     * @return              {@code true} in caso di successo o altrimenti {@code false}.
	 */
	public boolean deleteMeasure(Measure measure) {
        if (measure == null)
            return false;
        DbManager.getDbManager().deleteMeasure(measure.getIdUser(), measure.getTimestamp(), measure.getMeasureType());
        return true;
	}
	
	/**
	 * Elimina dal DB le misure selezionate tramite i parametri passati.
     * @param idUser        Identifivo dell'utente (non puo' essere null o vuoto).
	 * @param idPatient     Identifivo del paziente (se e' null il filtro non viene considerato).
	 * @param measureType   Tipo di misura (se e' null il filtro non viene considerato).
     * @return              {@code true} in caso di successo o altrimenti {@code false}.
	 */
	public boolean deleteMeasures(String idUser, String idPatient, String measureType) {
		if (idUser==null || idUser.isEmpty())
		    return false;
        DbManager.getDbManager().deleteMeasures(idUser, idPatient, measureType);
        return true;
	}

    /**
     * Legge dal DB le misure selezionate tramite i parametri passati.
     * @param idUser        Identifivo dell'utente (vedi {@link User#getId() User.getId()}) (non puo' essere null).
     * @param dateFrom      Timestamp nel formato "yyyyMMddHHmmss". La data misura deve essere maggiore o uguale di questo paramtro (se e' null il filtro non viene considerato).
     * @param dateTo        Timestamp nel formato "yyyyMMddHHmmss". La data misura deve essere minore o uguale di questo paramtro (se e' null il filtro non viene considerato).
     * @param measureType   Tipo di misura (se e' null il filtro non viene considerato).
     * @param idPatient     Identifivo del paziente (se e' null il filtro non viene considerato).
     * @param failed        Indica se devono essere selezionate anche le misure fallite (se e' null il filtro non viene considerato).
     * @return              La lista di misure selezionate o {@code null} in caso di errore.
     */
	public ArrayList<Measure> getMeasureData(String idUser,
                                             String dateFrom,
                                             String dateTo,
                                             String measureType,
                                             String idPatient,
                                             Boolean failed) {
        if (idUser==null || idUser.isEmpty())
            return null;
        return DbManager.getDbManager().getMeasureData(idUser, dateFrom, dateTo, measureType, idPatient, failed);
	}

    /**
     * Restituisce i dati configurati per il Protocollo di misura.
     * @return      Protocollo di misura.
     */
    public MeasureProtocolCfg getMeasureProtocolCfg() {
        return DbManager.getDbManager().getMeasureProtocolCfg();
    }

    /**
     * Restituisce la configurazione dei diversi tipi di misura abilitati per l'utente specificato.
     * @param userId        Identificativo dell'utente (vedi {@link User#getId() User.getId()}) (non puo' essere null).
     * @return              Lista di oggetti {@link UserMeasure} o {@code null} in caso di errore.
     */
    public List<UserMeasure> getUserMeasures (String userId) {
        if (userId==null)
            return null;
        return DbManager.getDbManager().getUserMeasures(userId);
    }

    /**
     * Restituisce la lista di tutti i dispositivi utilizzabili dall'utente corrente.
     * @return          Lista di oggetti {@link UserDevice} o {@code null} in caso di errore.
     */
    public List<UserDevice> getCurrentUserDevices() {
        return DbManager.getDbManager().getCurrentUserDevices();
    }

    /**
     * Restituisce la lista dei dispositivi utilizzabili per l'utente e tipo
     * di misura specificati.
     * @param measure   Tipo di misura (non puo' essere null).
     * @param userId    Identificativo dell utente (vedi {@link User#getId() User.getId()}) (non puo' essere null).
     * @return          Lista di oggetti {@link UserDevice} o {@code null} in caso di errore.
     */
    public List<UserDevice> getModelsForMeasure(String measure, String userId) {
        if (measure== null || userId==null)
            return null;
        return DbManager.getDbManager().getModelsForMeasure(measure, userId);
    }

    /**
     * Restituisce la configurazione dei diversi tipi di misura biometriche abilitati per l'utente specificato.
     * @param userId        Identificativo dell'utente (vedi {@link User#getId() User.getId()}) (non puo' essere null).
     * @return              Lista di oggetti {@link UserMeasure}.
     */
    public List<UserMeasure> getBiometricUserMeasures (String userId) {
        if (userId == null)
            return null;
        return DbManager.getDbManager().getBiometricUserMeasures(userId);
    }

    /**
     * Restituisce lo UserDevice corrispondente ai paramtri passati.
     * @param idUser        Identificativo dell utente (vedi {@link User#getId() User.getId()}) (non puo' essere null).
     * @param measureType   Tipo di misura (vedi {@link UserMeasure#getMeasure()}  UserMeasure.getMeasure()}) (non puo' essere null).
     * @param deviceModel   Modello del dispositivo (vedi {@link com.ti.app.telemed.core.common.Device#getModel() Device.getModel()}) (non puo' essere null).
     * @return              Oggetto {@link UserDevice} o {@code null} in caso di errore.
     */
    public UserDevice getUserDevice(String idUser, String measureType, String deviceModel) {
        if (idUser== null || measureType==null || deviceModel==null)
            return null;
        return DbManager.getDbManager().getUserDevice(idUser, measureType, deviceModel);
    }

    /**
     * Resetta sul DB il campo relativo all'indirizzo Bluetooth dello UserDevice passato.
     * @param userDevice    {@link UserDevice}.
     */
    public void cleanBtAddressDevice(UserDevice userDevice) {
        if (userDevice != null)
            DbManager.getDbManager().cleanBtAddressDevice(userDevice);
    }

    /**
     * Aggiorna sul DB il campo relativo all'indirizzo Bluetooth dello UserDevice passato.
     * @param userDevice    {@link UserDevice}.
     */
    public void updateBtAddressDevice(UserDevice userDevice) {
        DbManager.getDbManager().updateBtAddressDevice(userDevice);
    }
}
