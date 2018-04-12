package com.ti.app.telemed.core.measuremodule;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.MeasureProtocolCfg;
import com.ti.app.telemed.core.common.Patient;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.common.UserMeasure;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.syncmodule.SendMeasuresService;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.util.Util;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

import android.content.Intent;
import android.os.Handler;
import android.util.Log;

/**
 * <h1>Gestione della misure sul DB</h1>.
 * Questa classe implementa il design pattern del singleton.
 * <p>Tramite questa classe viene gestita la memorizzazione e la lettura delle misure
 * sul DB locale.
 */
public class MeasureManager {
	private static final String TAG = "MEASUREMANAGER";
	private static MeasureManager measureManager;

    private Handler handler = null;
    private Op currentOpOn;
    private final Thread currT;
    private String idUser, idPatient, measureType;
    private Measure.MeasureFamily measureFamily;
    private File docFile;

    private enum Op {
        IDLE,
        SAVE_DOCUMENT,
        DELETE_MANY_MEASURES
    }

    /**
     * Nome file temporaneo utilizzato per l'invio di piu documenti
     */
    public static final String DOCUMENT_SEND_TMPFILE = "documents.zip";

    /**
     * Messaggio inviato all'handler nel caso di operazione eseguita correttamente.
     */
    public static final int OPERATION_COMPLETED = 0;
    /**
     * Messaggio inviato all'handler nel caso di errore.
     */
    public static final int ERROR_OCCURED = 1;

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

    private MeasureManager() {
        currentOpOn = Op.IDLE;
        final MyRunnable runnable = new MyRunnable();
        currT = new Thread(runnable);
        currT.setName("usermanager thread");
        currT.start();
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

        public static DocumentType fromString(String id) {
            switch (id) {
                case "D0":
                    return DischargeDocument;
                case "D1":
                    return AcceptanceDocument;
                case "D2":
                    return LaboratoryReport;
                case "D3":
                    return RadiologicalImage;
                case "D4":
                    return MedicalReport;
                case "D5":
                    return Diagnosis;
                case "D6":
                    return TherapyPrescription;
                case "D7":
                    return Letter;
                case "D8":
                    return WoundImage;
                default:
                    return null;
            }
        }
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
     * Salva ed invia alla piattaforma una misura.
     * @param m     misura da salvare (non puo' essere null).
     * @return      {@code true} in caso di successo o altrimenti {@code false}.
     */
	public boolean saveMeasureData(Measure m) {
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

    /**
     * Salva ed invia alla piattaforma un documento.
     * L'operazione e' asincrona e l'esito viene notificato all'Handler passato
     * precedentemente con il metodo {@link #setHandler(Handler handler) setHandler}
     * @param outputFile    Path assoluto del file o directory contenente il documento o
     *                      documenti da inviare.
     * @param docType       Tipo di documento {@see DocumentType}.
     * @return              {@code true} in caso di successo o altrimenti {@code false}.
     */
	public boolean saveDocument(String outputFile, DocumentType docType) {
		synchronized (currT) {
            if (currentOpOn == Op.IDLE) {
                if (outputFile == null || docType == null) {
                    Log.e(TAG, "saveDocument - null parameter!");
                    return false;
                }
                docFile = new File(outputFile);
                if (!docFile.exists()) {
                    Log.e(TAG, "saveDocument - File not found!");
                    return false;
                }
                User currentUser = UserManager.getUserManager().getCurrentUser();
                Patient currentPatient = UserManager.getUserManager().getCurrentPatient();
                if (currentUser == null || (currentPatient == null && !currentUser.getIsPatient())) {
                    Log.e(TAG, "saveDocument - currentUser or Patient is Null");
                    return false;
                }

                measureType = docType.toString();
                idUser = currentUser.getId();
                idPatient = currentPatient != null ? currentPatient.getId() : currentUser.getId();
                currentOpOn = Op.SAVE_DOCUMENT;
                currT.notifyAll();
                return true;
            }
            return false;
        }
	}

    /**
     * Restituisce un oggetto di tipo Measure che rappresenta il valore di temperatura passato.
     * @param temperature       Valore della temperatura corporea in gradi centigradi.
     * @param standardProtocol  Indica se la misura e' stata acquisita con il "Protocollo standard".
     * @return                  oggetto di tipo Measure.
     */
    public Measure getManualTemperature(double temperature, boolean standardProtocol) {
        User currentUser = UserManager.getUserManager().getCurrentUser();
        Patient currentPatient = UserManager.getUserManager().getCurrentPatient();
        if (currentUser == null || (currentPatient == null && !currentUser.getIsPatient())) {
            Log.e(TAG, "saveManualTemperature - currentUser or Patient is Null");
            return null;
        }

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
            return m;
        } catch (Exception e) {
            Log.e(TAG,e.getMessage());
            return null;
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

	/**
	 * Elimina una misura dal db.
	 * @param measure       Misura da eliminare dal db.
     * @return              {@code true} in caso di successo o altrimenti {@code false}.
	 */
	public boolean deleteMeasure(Measure measure) {
        if (measure == null)
            return false;

        try {
            if (XmlManager.DOCUMENT_FILE_TYPE.equals(measure.getFileType()))
                if (measure.getFile() != null)
                    Util.deleteTree(new File(new String(measure.getFile(), "UTF-8")));
            DbManager.getDbManager().deleteMeasure(measure.getIdUser(), measure.getTimestamp(), measure.getMeasureType());
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
	}
	
	/**
	 * Elimina dal DB le misure selezionate tramite i parametri passati.
     * L'operazione e' asincrona e l'esito viene notificato all'Handler passato
     * precedentemente con il metodo {@link #setHandler(Handler handler) setHandler}
     * @param idUser        Identifivo dell'utente (non puo' essere null o vuoto).
	 * @param idPatient     Identifivo del paziente (se e' null il filtro non viene considerato).
	 * @param measureType   Tipo di misura (se e' null il filtro non viene considerato).
     * @return              {@code true} in caso di successo o altrimenti {@code false}.
	 */
	public boolean deleteMeasures(String idUser, String idPatient, String measureType) {
        synchronized (currT) {
            if (currentOpOn == Op.IDLE) {
                if (idUser == null || idUser.isEmpty()) {
                    Log.e(TAG, "idUser is NULL or empty");
                    return false;
                }
                this.idUser = idUser;
                this.idPatient = idPatient;
                this.measureType = measureType;
                this.measureFamily = null;
                currentOpOn = Op.DELETE_MANY_MEASURES;
                currT.notifyAll();
                return true;
            }
            return false;
        }
	}

    /**
     * Elimina dal DB le misure selezionate tramite i parametri passati.
     * L'operazione e' asincrona e l'esito viene notificato all'Handler passato
     * precedentemente con il metodo {@link #setHandler(Handler handler) setHandler}
     * @param idUser        Identifivo dell'utente (non puo' essere null o vuoto).
     * @param idPatient     Identifivo del paziente (se e' null il filtro non viene considerato).
     * @param measureFamily        Gruppo misura da eliminare (se e' null il filtro non viene considerato).
     * @return              {@code true} in caso di successo o altrimenti {@code false}.
     */
    public boolean deleteMeasures(String idUser, String idPatient, Measure.MeasureFamily measureFamily) {
        synchronized (currT) {
            if (currentOpOn == Op.IDLE) {
                if (idUser == null || idUser.isEmpty()) {
                    Log.e(TAG, "idUser is NULL or empty");
                    return false;
                }
                this.idUser = idUser;
                this.idPatient = idPatient;
                this.measureFamily = measureFamily;
                this.measureType = null;
                currentOpOn = Op.DELETE_MANY_MEASURES;
                currT.notifyAll();
                return true;
            }
            return false;
        }
    }

    /**
     * Legge dal DB le misure selezionate tramite i parametri passati.
     * @param idUser        Identifivo dell'utente (vedi {@link User#getId() User.getId()}) (non puo' essere null).
     * @param dateFrom      Timestamp nel formato "yyyyMMddHHmmss". La data misura deve essere maggiore o uguale di questo paramtro (se e' null il filtro non viene considerato).
     * @param dateTo        Timestamp nel formato "yyyyMMddHHmmss". La data misura deve essere minore o uguale di questo paramtro (se e' null il filtro non viene considerato).
     * @param measureType   Tipo di misura (se e' null il filtro non viene considerato).
     * @param idPatient     Identifivo del paziente (se e' null il filtro non viene considerato).
     * @param failed        Indica se devono essere selezionate anche le misure fallite (se e' null il filtro non viene considerato).
     * @param family        Indica la famiglia di misura (vedi {@link com.ti.app.telemed.core.common.Measure.MeasureFamily}) (se e' null il filtro non viene considerato).
     * @return              La lista di misure selezionate o {@code null} in caso di errore.
     */
	public ArrayList<Measure> getMeasureData(String idUser,
                                             String dateFrom,
                                             String dateTo,
                                             String measureType,
                                             String idPatient,
                                             Boolean failed,
                                             Measure.MeasureFamily family) {
        if (idUser==null || idUser.isEmpty())
            return null;
        return DbManager.getDbManager().getMeasureData(idUser, dateFrom, dateTo, measureType, idPatient, failed, family);
	}

    /**
     * Restituisce i dati configurati per il Protocollo di misura.
     * @return      Protocollo di misura.
     */
    public MeasureProtocolCfg getMeasureProtocolCfg() {
        return DbManager.getDbManager().getMeasureProtocolCfg();
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
     * Verifica se ci sono misure da spedire dell'utente userId passato com parametro o di qualsiasi utente
     * se viene passato null o una stringa vuota e ne ritorna il numero.
     * @param userId        Identificativo dell'utente o null per qualsiasi utente.
     * @return              Numero di misure che non sono ancora state spedite.
     */
    public int getNumNotSentMeasures(String userId) {
        return DbManager.getDbManager().getNumNotSentMeasures(userId);
    }

    private class MyRunnable implements Runnable {
        @Override
        public void run() {
            synchronized (currT) {
                while (true) {
                    switch (currentOpOn) {
                        case IDLE:
                            try {
                                currT.wait();
                            } catch (InterruptedException e) {
                                Log.e(TAG, "Thread interrotto");
                            }
                            break;
                        case DELETE_MANY_MEASURES:
                            deleteMeasures();
                            currentOpOn = Op.IDLE;
                            break;
                        case SAVE_DOCUMENT:
                            saveDocument();
                            currentOpOn = Op.IDLE;
                            break;
                    }
                }
            }
        }
    }

    private void saveDocument() {
        try {
            String path = docFile.getAbsolutePath();
            if (docFile.isDirectory()) {
                if (!Util.zipFile(docFile,new File(docFile, DOCUMENT_SEND_TMPFILE))) {
                    Log.e(TAG, "Errore nella creazione del file zip");
                    if (handler != null)
                        handler.sendEmptyMessage(ERROR_OCCURED);
                    return;
                }
            }

            Measure m = new Measure();
            HashMap<String, String> map = new HashMap<>();
            String [] tokens  = path.split(File.separator);
            map.put(measureType, tokens[tokens.length-1]);  //nome file
            m.setDeviceDesc("Document-" + measureType);
            m.setTimestamp(Util.getTimestamp(null));
            m.setMeasureType(measureType);
            m.setMeasures(map);
            m.setFile(path.getBytes("UTF-8"));
            m.setFileType(XmlManager.DOCUMENT_FILE_TYPE);
            m.setIdUser(idUser);
            m.setIdPatient(idPatient);
            m.setFailed(false);
            saveMeasureData(m);
            if (handler!=null)
                handler.sendEmptyMessage(OPERATION_COMPLETED);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            if (handler!=null)
                handler.sendEmptyMessage(ERROR_OCCURED);
        }
    }

    private void deleteMeasures() {
        try {
            ArrayList<Measure> measures =
                    DbManager.getDbManager().getMeasureData(idUser,null,null,measureType,idPatient,null,measureFamily);
            for (Measure m:measures) {
                if (XmlManager.DOCUMENT_FILE_TYPE.equals(m.getFileType()))
                    if (m.getFile() != null)
                        Util.deleteTree(new File(new String(m.getFile(), "UTF-8")));
                DbManager.getDbManager().deleteMeasure(m.getIdUser(),m.getTimestamp(),m.getMeasureType());
            }
            if (handler!=null)
                handler.sendEmptyMessage(OPERATION_COMPLETED);
        } catch (Exception e) {
            e.printStackTrace();
            if (handler!=null)
                handler.sendEmptyMessage(ERROR_OCCURED);
        }
    }
}
