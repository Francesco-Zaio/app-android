package com.ti.app.telemed.core.btmodule;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.util.Log;

import com.ti.app.telemed.core.common.Device;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.Patient;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.util.Util;

import java.lang.reflect.Method;

/**
 * <h1>Acquisire una misura da un dispsitivo</h1>
 * La classe DeviceHandler rappresenta un generico dispositivo Bluetooth.
 * <p>I metodi di questa classe permettono di eseguire tutte le operazioni necessarie per acquisire una misura dal dispositivo.
 *
 * @author  Massimo Martini
 * @version 1.0
 * @since   2017-11-09
 */


public abstract class DeviceHandler {

    private static final String TAG = "DeviceHandler";

	protected enum TState {
		EWaitingToGetDevice,    // default e notifica disconnessione
		EGettingDevice,         // chiamata di startOperation(...)
		EGettingConnection,     // chiamata a connectDevice()
        EWaitingStartMessage,
        EWaitingHeader,
        EWaitingBody,
        EWaitingInitPacket,
        EWaitingInfoPacket,
        EWaitingControlPacket,
        EWaitingInfoPatientPacket,
        EWaitingInfoCurvePacket,
        EWaitingCurve,
        EWaitingCurveEnd,
        EWaitingTheorSpiroPacket,
        EWaitingAckHS,
        EWaitPowerUpSeq,
        ENumbMeasReq,
        ENumbMeasRes,
        ELastMeasReq,
        ELastMeasRes,
        EConfDeviceReq,
        EConfDeviceRes,
        ESendingClosure,
        EWaitingClosure,
        ELastSending,
        ESendReadClear,
        EWaitReadClear,
        ESendingRecovery,
        ESendPowerUpSeq,
        ECheckMessage,
        ESendingStartCmd,
        ESendingData,
        ESendingCmdOff,
        ESendingReady,
        ESendingHS,
		EDisconnecting,         // chiamata a disconnectDevice
        EDisconnectingOK,
        EDisconnectingPairing,
        EDisconnectingFromUser,
		EConnected,             // callabck connessione avvenuta OK o fine Misura
		EGettingMeasures,       // chiamata startMeasures
        EGettingService
	}

	protected enum TCmd {
		ECmdConnByUser,
		ECmdConnByAddr;
        public String toString() {
            switch (this) {
                case ECmdConnByUser:
                    return "ECmdConnByUser";
                case ECmdConnByAddr:
                    return "ECmdConnByAddr";
            }
            return "";
        }
	}

    /**
     * Specifica il tipo di operazione che deve essere effettuata sul dispositivo.
     */
	public enum OperationType {
        /**
         * Eseguire una misura.
         */
		Measure,
        /**
         * Eseguire solo il Pairing.
         */
		Pair,
        /**
         * Eseguire la configurazione del dispositivo.
         * Questa operazione è necessaria solo per alcuni dispositivi. (es. spirometro per cui
         * devono venire preventivamente configurati i dati del paziente).
         */
		Config
	}

    protected DeviceListener deviceListener;
    protected TState iState;
    protected UserDevice iUserDevice;

    protected OperationType operationType;
    protected BTSearcherEventListener iBTSearchListener;
    protected String iBtDevAddr;
    protected TCmd iCmdCode;
    protected User user;
    protected Patient patient;

    /**
     * Metodo statico che deve essere utilizzato per ottenere un istanza della classe DeviceHandler.
     *
     * @param listener Istanza di una classe che implementa l'interfaccia {@link DeviceListener} che
     *                 ricevera' le notifiche durante l'esecuzione dell'operazione sul dispositivo.
     * @param ud       UserDevice {@link com.ti.app.telemed.core.common.UserDevice} che deve essere gestito dalla nuova istanza di DeviceHandler.
     * @return         La nuova istanza di DeviceHandler o <code>null</code> in caso di errore.
     */
    public static DeviceHandler getInstance(DeviceListener listener, UserDevice ud) {
        if (listener==null || ud == null || ud.getDevice().getDevType()== Device.DevType.NONE) {
            Log.e(TAG, "getInstance: DeviceListener or UserDevice is null or not valid.");
            return null;
        }
        try {
            Class<?> c = Class.forName("com.ti.app.telemed.core.btdevices." + ud.getDevice().getClassName());
            return (DeviceHandler) c.getDeclaredConstructor(DeviceListener.class, UserDevice.class ).newInstance(listener, ud);
        } catch (Exception e) {
            Log.e(TAG, "getInstance: reflection Error! Cannot instantiate class " + ud.getDevice().getClassName());
            return null;
        }
    }

    protected DeviceHandler(DeviceListener listener, UserDevice ud) {
        iState = TState.EWaitingToGetDevice;
        iUserDevice = ud;
        deviceListener = listener;
        iBTSearchListener = null;
        user = null;
        patient = null;
    }

    // NB !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    // DeviceHandler sub classes MUST IMPLEMENT also the following two static methods

    /**
     * Indica se, prima di eseguire una misura, e' necessario eseguire il pairing con il dispositivo.
     *
     * @param  ud {@see com.ti.app.telemed.core.common.UserDevice} a cui si riferisce la richiesta.
     * @return    <code>true</code> nel caso il pairing sia necessario <code>false</code> in caso contrario.
     */
    public static boolean needPairing(UserDevice ud){
        try {
            Class<?> c = Class.forName("com.ti.app.telemed.core.btdevices." + ud.getDevice().getClassName());
            Method m = c.getMethod("needPairing", UserDevice.class);
            return (boolean) m.invoke(null, ud);
        } catch (ClassNotFoundException e) {
            Log.e(TAG,"Class Not Found!! : " + ud.getDevice().getClassName());
            return false;
        } catch (NoSuchMethodException e) {
            Log.e(TAG,"Method Not Found!! : " + ud.getDevice().getClassName());
            return false;
        } catch (Exception e) {
            Log.e(TAG,"Method Invocation Exception! : " + ud.getDevice().getClassName());
            return false;
        }
    }

    /**
     * Indica se, prima di eseguire una misura, e' necessario eseguire la configurazione del dispositivo.
     *
     * @param  ud {@see com.ti.app.telemed.core.common.UserDevice} a cui si riferisce la richiesta.
     * @return    <code>true</code> nel caso la configurazione sia necessaria <code>false</code> in caso contrario.
     */
    public static boolean needConfig(UserDevice ud){
        try {
            Class<?> c = Class.forName("com.ti.app.telemed.core.btdevices." + ud.getDevice().getClassName());
            Method m = c.getMethod("needConfig", UserDevice.class);
            return (boolean) m.invoke(null, ud);
        } catch (ClassNotFoundException e) {
            Log.e(TAG,"Class Not Found!! : " + ud.getDevice().getClassName());
            return false;
        } catch (NoSuchMethodException e) {
            Log.e(TAG,"Method Not Found!! : " + ud.getDevice().getClassName());
            return false;
        } catch (Exception e) {
            Log.e(TAG,"Method Invocation Exception! : " + ud.getDevice().getClassName());
            return false;
        }
    }

    public void onActivityResult(int resultCode, Intent data) {
    }

    /**
     * Avvia l'operazione richiesta sul dispositivo.
     * <p> Se il parametro btSearchListener e' <code>null</code>, lo UserDevice indicato
     * all'istanziazione della classe deve contenere l'indirizzo bluetooth del dispositivo
     * con cui effettuare l'operazione.
     * Se invece il parametro btSearchListener non e' <code>null</code>, verra' avviato un discovery
     * e all'istanza btSearchListener verrano notificati tutti i dispositivi bluetooth trovati.
     * In questo caso dovra' poi essere invocato il metodo {@link #selectDevice(BluetoothDevice bd) selectDevice}
     * per indicare il dispositivo selezionato per eseguire l'operazione.
     *
     * @param  ot               {@link OperationType} tipo di operazione che deve essere eseguita.
     * @param  btSearchListener {@link BTSearcherEventListener} listener che viene notificato durante il discovery.
     * @return                  <code>true</code> se l'operazione viene avviata o <code>false</code> in caso di errore.
     */
    abstract public boolean startOperation(OperationType ot, BTSearcherEventListener btSearchListener);

    /**
     * Termina l'operazione in corso.
     */
    abstract public void stopOperation();

    /**
     * Permette di indicare il dispositivo bluetooth su cui eseguire l'operazione avviata con
     * il metodo {@link #startOperation(OperationType ot, BTSearcherEventListener btSearchListener) startOperation}
     * @param bd {@link BluetoothDevice} dispositivo selezionato.
     */
    abstract public void selectDevice(BluetoothDevice bd);

    /**
     * Notifica una risposta positiva richiesta precedentemente con il metodo {@link DeviceListener#askSomething(String messageText, String positiveText, String negativeText) DeviceListener.askSomething}
     */
    abstract public void confirmDialog();

    /**
     * Notifica una risposta negativa richiesta precedentemente con il metodo {@link DeviceListener#askSomething(String messageText, String positiveText, String negativeText) DeviceListener.askSomething}
     */
    abstract public void cancelDialog();

    /**
     * Indica se è in corso un operazione.
     * @return      <code>true</code> se c'e' un operazione avviata altrimenti <code>false</code>.
     */
    public boolean operationRunning() {
        return iState!=TState.EWaitingToGetDevice;
    }


    protected Measure getMeasure() {
        Measure m = new Measure();
        m.setMeasureType(iUserDevice.getMeasure());
        m.setDeviceDesc(iUserDevice.getDevice().getDescription());
        m.setBtAddress(iBtDevAddr);
        m.setTimestamp(Util.getTimestamp(null));
        m.setFile(null);
        m.setFileType(null);
        m.setFailed(false);
        m.setIdUser(user.getId());
        m.setIdPatient(patient.getId());
        return m;
    }

	protected boolean startInit(OperationType ot, BTSearcherEventListener btSearchListener) {
        if (iState != TState.EWaitingToGetDevice) {
            Log.e(TAG,"startOperation: Operazone già avviata");
            return false;
        }
        if (iUserDevice == null || deviceListener == null) {
            Log.e(TAG,"startOperation: UserDevice or DeviceListener is null!");
            return false;
        }
        user = UserManager.getUserManager().getCurrentUser();
        patient = UserManager.getUserManager().getCurrentPatient();
        if ((patient == null) && (user != null)) {
            if (user.getIsPatient())
                patient = DbManager.getDbManager().getPatientData(user.getId());
        }
        if ((user == null) || (patient==null)) {
            Log.e(TAG, "startOperation: User or Patient is null!");
            return false;
        }

        operationType = ot;
        iBTSearchListener = btSearchListener;
        iState = TState.EGettingDevice;
        if (operationType != OperationType.Pair)
            iBtDevAddr = iUserDevice.getBtAddress();
        else
            iBtDevAddr = "";
        if (iUserDevice.getDevice().getDevType()== Device.DevType.BT) {
            if (iBTSearchListener != null) {
                iCmdCode = TCmd.ECmdConnByUser;
            } else if (iBtDevAddr != null && !iBtDevAddr.isEmpty()) {
                iCmdCode = TCmd.ECmdConnByAddr;
            } else {
                Log.e(TAG, "startOperation: BTSearcherEventListener is null!");
                return false;
            }
        } else
            iCmdCode = TCmd.ECmdConnByAddr;
        return true;
    }
}
