package com.ti.app.telemed.core.btmodule;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.util.Log;

import com.ti.app.telemed.core.btdevices.AgamatrixMyStar;
import com.ti.app.telemed.core.btdevices.CMS50DLE;
import com.ti.app.telemed.core.btdevices.CardGuardEasy2CheckClient;
import com.ti.app.telemed.core.btdevices.CheckmePro;
import com.ti.app.telemed.core.btdevices.ComftechDevice;
import com.ti.app.telemed.core.btdevices.DHearth;
import com.ti.app.telemed.core.btdevices.EcgProtocol;
import com.ti.app.telemed.core.btdevices.ForaThermometerClient;
import com.ti.app.telemed.core.btdevices.GIMAPC300SpotCheck;
import com.ti.app.telemed.core.btdevices.IHealth;
import com.ti.app.telemed.core.btdevices.MIRSpirodoc;
import com.ti.app.telemed.core.btdevices.MIRSpirodoc46;
import com.ti.app.telemed.core.btdevices.NoninOximeter;
import com.ti.app.telemed.core.btdevices.OnCallSureSync;
import com.ti.app.telemed.core.btdevices.POD1W_OXY10_LE;
import com.ti.app.telemed.core.btdevices.RocheProthrombineTimeClient;
import com.ti.app.telemed.core.btdevices.TouchECG;
import com.ti.app.telemed.core.common.Device;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.Patient;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.util.Util;

import static com.ti.app.telemed.core.util.GWConst.KCOMFTECH;

/**
 * <h1>Acquisire una misura da un dispsitivo</h1>
 * <p>La classe DeviceHandler rappresenta un generico dispositivo di misura.</p>
 * <p>Questa classe invoca a sua volta i metodi dell'interfaccia {@link DeviceListener} che deve essere implementata dall'utilizzatore della libreria.</p>
 * <p>I metodi di questa classe permettono di eseguire tutte le operazioni necessarie per acquisire una misura dal dispositivo.</p>
 * <p>Tramite il metodo getInstance è possibile ottenere l'istanza della classe per un determintato dispositivo e tipo di misura.</p>
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
        EConnected,             // callabck connessione avvenuta OK o fine Misura
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
        ECheckMessage,
        ESendingStartCmd,
        ESendingData,
        ESendingCmdOff,
        ESendingReady,
		EDisconnecting,         // chiamata a disconnectDevice
        EDisconnectingOK,
        EDisconnectingPairing,
        EDisconnectingFromUser,
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
        switch(ud.getDevice().getModel()) {
            case GWConst.KPO3IHealth:
            case GWConst.KBP5IHealth:
            case GWConst.KHS4SIHealth:
            case GWConst.KBP550BTIHealth:
            case GWConst.KBG5SIHealth:
                return new IHealth(listener, ud);
            case GWConst.KEcgMicro:
                return new EcgProtocol(listener, ud);
            case GWConst.KCcxsRoche:
                return new RocheProthrombineTimeClient(listener, ud);
            case GWConst.KFORATherm:
                return new ForaThermometerClient(listener, ud);
            case GWConst.KSpirodoc:
                return new MIRSpirodoc(listener, ud);
            case GWConst.KSpirodocNew:
                return new MIRSpirodoc46(listener, ud);
            case GWConst.KOximeterNon:
                return new NoninOximeter(listener, ud);
            case GWConst.KPC300SpotCheck:
                return new GIMAPC300SpotCheck(listener, ud);
            case GWConst.KAgamtrixJazz:
                return new AgamatrixMyStar(listener, ud);
            case GWConst.KTouchECG:
                return new TouchECG(listener, ud);
            case GWConst.KTDCC:
                return new CardGuardEasy2CheckClient(listener, ud);
            case GWConst.KCheckmePro:
                return new CheckmePro(listener, ud);
            case GWConst.KOnCall:
                return new OnCallSureSync(listener, ud);
            case GWConst.KDHearth:
                return new DHearth(listener, ud);
            case GWConst.KOXY10:
            case GWConst.KPOD1W:
                return new POD1W_OXY10_LE(listener, ud);
            case GWConst.KOXY9:
                return new CMS50DLE(listener, ud);
            case KCOMFTECH:
                return new ComftechDevice(listener, ud);
            default:
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


    /**
     * Indica se, prima di eseguire una misura, e' necessario eseguire il pairing con il dispositivo.
     *
     * @param  ud {@see com.ti.app.telemed.core.common.UserDevice} a cui si riferisce la richiesta.
     * @return    <code>true</code> nel caso il pairing sia necessario <code>false</code> in caso contrario.
     */
    public static boolean needPairing(UserDevice ud){
        switch(ud.getDevice().getModel()) {
            case GWConst.KSpirodoc:
            case GWConst.KSpirodocNew:
            case GWConst.KPC300SpotCheck:
            case GWConst.KTDCC:
            case GWConst.KCheckmePro:
            case GWConst.KOnCall:
                return true;
            default:
                return false;
        }
    }

    /**
     * Indica se va abilitata la voce pairing per questo device
     * @param  ud {@see com.ti.app.telemed.core.common.UserDevice} a cui si riferisce la richiesta.
     * @return    <code>true</code> nel caso il pairing sia abilitato <code>false</code> in caso contrario.
     */
    public static boolean pairingEnabled(UserDevice ud){
        switch(ud.getDevice().getModel()) {
            default:
                return true;
        }
    }

    /**
     * Indica se il device si comporta come un client e quindi e' il device che si connette al Gateway.
     * In tal caso non si deve effettuare il discovery ma il GW si mette in attesa di ricevere
     * una connessione dal device.
     * @param  ud {@see com.ti.app.telemed.core.common.UserDevice} a cui si riferisce la richiesta.
     * @return    <code>true</code> nel caso il device si comporta come un lient <code>false</code> in caso contrario.
     */
    public static boolean isServer(UserDevice ud){
        switch(ud.getDevice().getModel()) {
            default:
                return false;
        }
    }

    /**
     * Indica se, prima di eseguire una misura, e' necessario eseguire la configurazione del dispositivo.
     * @param  ud {@see com.ti.app.telemed.core.common.UserDevice} a cui si riferisce la richiesta.
     * @return    <code>true</code> nel caso la configurazione sia necessaria <code>false</code> in caso contrario.
     */
    public static boolean needConfig(UserDevice ud){
        switch(ud.getDevice().getModel()) {
            case GWConst.KSpirodoc:
                return MIRSpirodoc.needConfig(ud);
            default:
                return false;
        }
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

    public void activityResult(int requestCode, int resultCode, Intent data) {

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
        m.setStandardProtocol(false);
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
            if (user.isPatient())
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
            } else if (!isServer(iUserDevice)){
                Log.e(TAG, "startOperation: BTSearcherEventListener is null!");
                return false;
            }
        } else
            iCmdCode = TCmd.ECmdConnByAddr;
        return true;
    }
}
