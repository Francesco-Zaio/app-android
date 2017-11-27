package com.ti.app.telemed.core.devicemodule;

import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.common.UserMeasure;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.usermodule.UserManager;

import java.util.List;

/**
 * <h1>Gestione dei Devices sul DB</h1>.
 * Questa classe implementa il design pattern del singleton.
 * <p>Tramite questa classe viene gestita la lettura e modifica sul DB locale delle informazioni
 * relative ai Devices gestiti.
 */
public class DeviceManager {
    private static final String TAG = "MEASUREMANAGER";
    private static DeviceManager deviceManager;

    /**
     * Restituisce l'istanza di DeviceManager.
     * @return      istanza di DeviceManager.
     */
    public static DeviceManager getDeviceManager() {
        if (deviceManager == null) {
            deviceManager = new DeviceManager();
        }
        return deviceManager;
    }

    /**
     * Restituisce la lista di tutti i dispositivi utilizzabili dall'utente corrente.
     * @return          Lista di oggetti {@link UserDevice} o {@code null} in caso di errore.
     */
    public List<UserDevice> getCurrentUserDevices() {
        return DbManager.getDbManager().getUserDevices(UserManager.getUserManager().getCurrentUser().getId());
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

    public void updateUserDeviceModel(String userId, String measure, Integer idDevice) {
        DbManager.getDbManager().updateUserDeviceModel(userId,measure,idDevice);
    }
}
