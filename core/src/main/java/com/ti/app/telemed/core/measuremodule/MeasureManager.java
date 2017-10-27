package com.ti.app.telemed.core.measuremodule;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.syncmodule.SendMeasuresService;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;

import static com.ti.app.telemed.core.util.GWConst.EGwCode_D0;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_D1;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_Q0;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_Q1;
import static com.ti.app.telemed.core.util.GWConst.EGwCode_Q2;

public class MeasureManager {

	public enum DocumentType{
		DischargeDocument,
		AcceptanceDocument
	}

	public enum BooleanFilter{
		yes (1),
		not (0),
		ignore (-1);

        private final int val;

        BooleanFilter(int val) {
            this.val = val;
        }

        public int value() {
            return val;
        }
	}

	public enum NotBioMeasureType {
		HealthStatus,
		PainLevel,
		SleepQuality
	}
	
	private static final String TAG = "MEASUREMANAGER";
    
	// variable for singleton
	private static MeasureManager measureManager;
	
	private Logger logger = Logger.getLogger(MeasureManager.class.getName());
	
	public static MeasureManager getMeasureManager() {
		if (measureManager == null) {
			measureManager = new MeasureManager();
		}
		return measureManager;
	}
	
	public boolean saveMeasureData(@NonNull Measure m) {
		synchronized (this) {
			Log.i(TAG, "saveMeasureData: ");
            try {
                boolean result = DbManager.getDbManager().insertMeasure(m);
                MyApp.getContext().startService(new Intent(MyApp.getContext(), SendMeasuresService.class));
                return result;
            } catch (Exception sqle) {
                logger.log(Level.SEVERE,"ERROR SAVE MEASURE DB " + sqle);
                return false;
            }
		}	
	}

	public boolean saveDocument(String outputFile, DocumentType docType) {
		synchronized (this) {
			if (outputFile == null || outputFile.isEmpty()) {
				Log.e(TAG, "saveDocument - outputFile is null or empty");
				return false;
			}
			User currentUser = UserManager.getUserManager().getCurrentUser();
			if (currentUser == null) {
				Log.e(TAG, "saveDocument - currentUser is Null");
				return false;
			}

			String code;
			switch (docType) {
				case DischargeDocument:
					code = EGwCode_D0;  //nome file
					break;
				case AcceptanceDocument:
					code = EGwCode_D1;  //nome file
					break;
				default:
					Log.e(TAG, "saveDocument - DocumentType not recognized");
					return false;
			}

			Measure m = new Measure();
			HashMap<String, String> map = new HashMap<>();
			String [] tokens  = outputFile.split(File.separator);
			map.put(code, tokens[tokens.length-1]);  //nome file
			try {
				m.setDeviceDesc("Document-" + code);
				m.setTimestamp(XmlManager.getXmlManager().getTimestamp(null));
				m.setMeasureType(code);
				m.setMeasures(map);
				m.setFile(outputFile.getBytes("UTF-8"));
				m.setFileType(XmlManager.DOCUMENT_FILE_TYPE);
				m.setIdUser(currentUser.getId());
				m.setIdPatient(currentUser.getId());
				m.setFailed(false);

				return saveMeasureData(m);
			} catch (Exception e) {
				e.printStackTrace();
				logger.log(Level.SEVERE, e.getMessage());
                return false;
			}
		}
	}

	public boolean saveNotBioMeasure(NotBioMeasureType type, int level, boolean standardProtocol) {
		User currentUser = UserManager.getUserManager().getCurrentUser();
		if (currentUser == null) {
			Log.e(TAG, "saveDocument - currentUser is Null");
			return false;
		}
		synchronized (this) {
			String code;
			switch (type) {
				case HealthStatus:
					code = EGwCode_Q0;
					break;
				case SleepQuality:
					code = EGwCode_Q1;
					break;
				case PainLevel:
					code = EGwCode_Q2;
					break;
				default:
					Log.e(TAG, "saveNotBioMeasure - NotBioMeasureType not recognized");
					return false;
			}

			Measure m = new Measure();
			HashMap<String, String> map = new HashMap<>();
			map.put(code, Integer.toString(level));
			try {
				m.setDeviceDesc(code);
				m.setTimestamp(XmlManager.getXmlManager().getTimestamp(null));
				m.setMeasureType(code);
				m.setMeasures(map);
				m.setStandardProtocol(standardProtocol);
				m.setIdUser(currentUser.getId());
				m.setIdPatient(currentUser.getId());
				m.setFailed(false);
				return saveMeasureData(m);
			} catch (Exception e) {
				e.printStackTrace();
				logger.log(Level.SEVERE, e.getMessage());
				return false;
			}
		}
	}

	/**
	 * Metodo che consente di eliminare una misura dal db
	 * @param measure oggetto di tipo {@code Measure} che rappresenta la misura da eliminare dal db
	 */
	public boolean deleteMeasure(Measure measure) {
		try {
			DbManager.getDbManager().deleteMeasure(measure.getIdUser(), measure.getTimestamp(), measure.getMeasureType());
		} catch (Exception sqle) {
			sqle.printStackTrace();
			logger.log(Level.SEVERE, sqle.getMessage());
			return false;
		}
        return true;
	}
	
	/**
	 * Metodo che consente di eliminare tutte le misure dal db
	 * @param idUser variabile di tipo {@code String} che contiene l'identificatore dell'utente
	 * @param idPatient variabile di tipo {@code String} che contiene l'identificatore del paziente. null o "" per non indicarlo
	 * @param measureType variabile di tipo {@code String} che contiene le operazioni da eliminare: null o "" per non indicarlo
	 */
	public boolean deleteMeasures(String idUser, String idPatient, String measureType) {
		try {
			DbManager.getDbManager().deleteMeasures(idUser, idPatient, measureType);
		} catch (Exception sqle) {
			sqle.printStackTrace();
			logger.log(Level.SEVERE, sqle.getMessage());
			return false;
		}
        return true;
	}
	
	/**
	 * Metodo che restituisce tutte le misure associate ad un utente
	 * @param measureType variabile di tipo {@code String} che specifica quale tipo di misure devono essere restituite: se ALL vengono restituite tutte le misure
	 * @param idUser variabile di tipo {@code String} che identifica l'utente
	 * @param idPatient variabile di tipo {@code String} che identifica il paziente
	 * @return array di oggetti {@code Measure} che contiene l'elenco di misure associate all'utente
	 */
	public ArrayList<Measure> getMeasureData(String idUser, String dateFrom, String dateTo, String measureType, String idPatient, BooleanFilter failed) {
		ArrayList<Measure> listaMisure = null;

		try {
			listaMisure = (ArrayList<Measure>) DbManager.getDbManager().getMeasureData(idUser, dateFrom, dateTo, measureType, idPatient, failed.value());
		} catch (Exception sqle) {
			sqle.printStackTrace();
			logger.log(Level.SEVERE, sqle.getMessage());
		}
		return listaMisure;
	}
}
