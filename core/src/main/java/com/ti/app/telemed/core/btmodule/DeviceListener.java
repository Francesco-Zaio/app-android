package com.ti.app.telemed.core.btmodule;

import android.content.Intent;

import com.ti.app.telemed.core.common.Measure;

import java.util.ArrayList;

/**
 * Interfaccia che contiene la definizione di tutti la callback invocate dalla
 * classe {@link DeviceHandler} durante l'esecuzione di un operazione sul dispositivo Bluetooth
 */
public interface DeviceListener {
    String CONNECTION_ERROR = "E01";
    String DEVICE_NOT_FOUND_ERROR = "E02";
    String COMMUNICATION_ERROR = "E03";
    String MEASUREMENT_ERROR = "E04";
    String DEVICE_DATA_ERROR = "E05";
    String MEASURE_PROCEDURE_ERROR = "E06";
    String NO_MEASURES_FOUND = "E07";
    String DEVICE_CFG_ERROR = "E08";
    String DEVICE_MEMORY_EXHAUSTED = "E09";
    String USER_CFG_ERROR = "E10";
    String TIMESTAMP_ERROR = "E11";
    String PACKAGE_NOT_FOUND_ERROR = "E12";

    /**
     * Invocato in caso di errore
     * <p>
     * @param errorCode       Stringa contenete il codice di errore
     * @param errorMessage    Stringa localizzata che contiene la descrizione dell'errore
     */
    void notifyError(String errorCode, String errorMessage);

    /**
     * Invia un messaggio di notifica duranta l'esecuzione dell'operazione
     * @param message         Messaggio
     */
    void notifyToUi(String message);

    /**
     * Invia un messaggio di notifica duranta l'esecuzione dell'operazione.
     * Rispetto al metodo {@link #notifyToUi(String message) notifyToUi} indica che
     * e' in corso un operazione non interrompibilie
     * @param message         Messaggio
     */
    void notifyWaitToUi(String message);

    /**
     * Invia una richiesta all'utente che prevede dua opzioni possibili.
     * <p>La scelta della prima opzione va notificata chiamando il metodo {@link DeviceHandler#confirmDialog()}</p>
     * <p>La scelta della seconda opzione va notificata chiamando il metodo {@link DeviceHandler#cancelDialog()}  </p>
     * <p>Ad esempio questo metodo viene chiamato durante una misura di glicemia per chiedere al paziente se si tratta di una misura PrePrandiale o PostPrandiale</p>
     *
     * @param messageText     Messaggio da visualizzare
     * @param positiveText    Testo del pulsante di scelta dell'opzione 1
     * @param negativeText    Testo del pulsante di scelta dell'opzione 2
     */
    void askSomething(String messageText, String positiveText, String negativeText);

    /**
     * Metodo che viene invocato al termine della connessione con successo con il
     * dispositivo bluetooth selezionato e richede di salvarne il mac address.
     * In questo modo alla successive richieste non sarà più necessario effettuare
     * il discovery e chiedere all'utente di selezionare il dispositivo.
     *
     * @param mac            MAC address da salvare
     */
    void setBtMAC(String mac);

    /**
     * Metodo invocato al termine di una misurazione eseguita con successo.
     *
     * @param m              Misura rilevata
     */
    void showMeasurementResults(Measure m);

    /**
     * Metodo invocato al termine di una misurazione eseguita con successo.
     *
     * @param m              Misura rilevata
     */
    void showMeasurementResults(ArrayList<Measure> measureList);

    /**
     * Metodo invocato al termine di un operazione di pairing o configurazione eseguita
     * con successo
     * @param msg            Messaggio da visualizzare
     */
    void configReady(String msg);

    /**
     *  Richiede l'avvio della GUI che permette la visualizzazione in real-time dei
     *  segnali acquisiti dall ECG. <p>
     *  Metodo utilizzato per quei dispositivi ECG che non hanno un proprio display
     *  (Es. Contec 8000GW)
     *  TBD
     */
    void startEcgDraw();

    /**
     * Richiede di lanciare un attivita esterna alla app con il metodo startActivityForResult
     * e di restituire il risultato poi tramite il metodo DeviceHandler.onActivityResult
     * @param intent intent da lanciare
     */
    void startActivity(Intent intent);
}
