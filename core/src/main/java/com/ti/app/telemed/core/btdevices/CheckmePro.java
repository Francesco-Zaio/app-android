package com.ti.app.telemed.core.btdevices;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.BTSearcher;
import com.ti.app.telemed.core.btmodule.BTSearcherEventListener;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.measuremodule.MeasureManager;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.util.AECG;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.util.Util;
import com.ti.app.telemed.core.xmlmodule.XmlManager;
import com.viatom.checkmelib.bluetooth.BTConnectListener;
import com.viatom.checkmelib.bluetooth.BTUtils;
import com.viatom.checkmelib.bluetooth.GetInfoThreadListener;
import com.viatom.checkmelib.bluetooth.ReadFileListener;
import com.viatom.checkmelib.measurement.BPItem;
import com.viatom.checkmelib.measurement.CheckmeDevice;
import com.viatom.checkmelib.measurement.ECGInnerItem;
import com.viatom.checkmelib.measurement.ECGItem;
import com.viatom.checkmelib.measurement.MeasurementConstant;
import com.viatom.checkmelib.measurement.SLMInnerItem;
import com.viatom.checkmelib.measurement.SLMItem;
import com.viatom.checkmelib.measurement.SPO2Item;
import com.viatom.checkmelib.measurement.TempItem;
import com.viatom.checkmelib.measurement.User;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Vector;

import static android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE;


public class CheckmePro extends DeviceHandler implements
        BTSearcherEventListener,
        BTConnectListener,
        GetInfoThreadListener,
        ReadFileListener {

    private static final String TAG = "CheckmePro";

    private static final int BASE_OXY_STREAM_LENGTH = 189; //

    private BTSearcher iServiceSearcher;
    private BluetoothDevice selectedDevice;

    private BTUtils.BTBinder btBinder = null;
    private CheckmeDevice device = null;
    private byte[] deviceData = null;
    private ArrayList<ECGItem> ecgItems = new ArrayList<>();
    private ArrayList<ECGInnerItem> ecgData = new ArrayList<>();
    private ArrayList<BPItem> bpItems = new ArrayList<>();
    private ArrayList<com.viatom.checkmelib.measurement.User> userList = new ArrayList<>();
    private ArrayList<SLMItem> slmItems = new ArrayList<>();
    private ArrayList<SLMInnerItem> slmData = new ArrayList<>();
    private int slmItemPos = 0;
    private int ecgItemPos = 0;
    private ArrayList<Measure> measureList = new ArrayList<>();

    private Vector<BluetoothDevice> mdevList = new Vector<>();


    public CheckmePro(DeviceListener listener, UserDevice ud) {
        super(listener, ud);
        iServiceSearcher = new BTSearcher();
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected: try to connect");
            btBinder = (BTUtils.BTBinder) service;
            btBinder.interfaceConnect(selectedDevice.getAddress(), CheckmePro.this);
            deviceListener.notifyToUi(ResourceManager.getResource().getString("KConnectingDev"));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected: " + name.toString());
            btBinder = null;
        }
    };

    // methods of DeviceHandler interface

    @Override
    public void confirmDialog() {
        // Not used for this device
    }

    @Override
    public void cancelDialog() {
        // Not used for this device
    }

    @Override
    public boolean startOperation(OperationType ot, BTSearcherEventListener btSearchListener) {
        if (!startInit(ot, btSearchListener))
            return false;

        Log.d(TAG, "startOperation: iBtDevAddr=" + iBtDevAddr + " iCmdCode=" + iCmdCode.toString());
        mdevList.clear();
        iServiceSearcher.clearBTSearcherEventListener();
        iServiceSearcher.addBTSearcherEventListener(this);
        iServiceSearcher.startSearchDevices();
        deviceListener.notifyToUi(ResourceManager.getResource().getString("KSearchingDev"));
        return true;
    }

    @Override
    public void stopOperation() {
        Log.d(TAG, "stopOperation");
        stop();
    }

    @Override
    public void selectDevice(BluetoothDevice bd) {
        Log.d(TAG, "selectDevice: iBtDevAddr=" + bd.getAddress());
        iServiceSearcher.stopSearchDevices();
        selectedDevice = bd;
        iBtDevAddr = selectedDevice.getAddress();
        iState = TState.EGettingService;

        iState = TState.EConnected;
        Log.d(TAG, "Connecting...");

        Intent mIntent = new Intent(MyApp.getContext(), com.viatom.checkmelib.bluetooth.BTUtils.class);
        mIntent.setAction("com.viatom.checkmelib.bluetooth.BTUtils");
        MyApp.getContext().startService(mIntent);
        // mIntent.setPackage(MyApp.getContext().getPackageName());
        // mIntent.setPackage("com.viatom.checkmelib.bluetooth");
        MyApp.getContext().bindService(mIntent, connection, Service.BIND_AUTO_CREATE);
    }


    public void stop() {
        Log.i(TAG, "stop");
        iServiceSearcher.stopSearchDevices();
        iServiceSearcher.clearBTSearcherEventListener();
        iServiceSearcher.close();

        if (btBinder != null) {
            btBinder.interfaceInterruptAllThread();
            btBinder = null;
            MyApp.getContext().unbindService(connection);
        }
        reset();
    }

    public void reset() {
        Log.i(TAG, "reset");
        // this class object must return to the initial state
        iState = TState.EWaitingToGetDevice;
        btBinder = null;
        device = null;
        deviceData = null;
    }


    // methods of BTSearchEventListener interface
    @Override
    public void deviceDiscovered(Vector<BluetoothDevice> devList) {
        switch (iCmdCode) {
            case ECmdConnByAddr:
                for (int i = 0; i < devList.size(); i++)
                    if (iBtDevAddr.equalsIgnoreCase(devList.get(i).getAddress())) {
                        selectDevice(devList.get(i));
                    }
                break;
            case ECmdConnByUser:
                BluetoothDevice d = devList.get(devList.size()-1);
                if (d.getType() != DEVICE_TYPE_LE) {
                    int i;
                    for (i = 0; i < mdevList.size(); i++)
                        if (mdevList.elementAt(i).getAddress().equals(d.getAddress()))
                            break;
                    if (i >= mdevList.size()) {
                        mdevList.addElement(d);
                        if (iBTSearchListener != null)
                            iBTSearchListener.deviceDiscovered(mdevList);
                    }
                }
                break;
        }
    }

    @Override
    public void deviceSearchCompleted() {
        if (iCmdCode == TCmd.ECmdConnByUser && iBTSearchListener != null)
            iBTSearchListener.deviceSearchCompleted();
    }

    // BTConnectListener interface
    @Override
    public void onConnectSuccess() {
        Log.d(TAG, "onConnectSuccess:");
        devOpHandler.sendEmptyMessage(HANDLER_CONNECTED);
    }

    @Override
    public void onConnectFailed(byte errorCode) {
        Log.d(TAG, "onConnectFailed:" + errorCode);
        devOpHandler.sendEmptyMessage(HANDLER_ERROR);
    }


    // GetInfoThreadListener interface
    @Override
    public void onGetInfoSuccess(String info) {
        Log.d(TAG, "onGetInfoSuccess: " + " " + info);
        deviceListener.notifyToUi(ResourceManager.getResource().getString("KMeasuring"));
        device = CheckmeDevice.decodeCheckmeDevice(info);
        devOpHandler.sendEmptyMessage(HANDLER_INFO_RECEIVED);
    }

    @Override
    public void onGetInfoFailed(byte errCode) {
        Log.d(TAG, "onGetInfoFailed: " + " " + errCode);
        devOpHandler.sendEmptyMessage(HANDLER_ERROR);
    }

    // Interface ReadFileListener
    @Override
    public void onReadPartFinished(String fileName, byte fileType, float percentage) {
        //Log.d(TAG, "onReadPartFinished: " + fileName + "part  " + percentage);
    }

    @Override
    public void onReadSuccess(String fileName, byte fileType, byte[] fileBuf) {
        Log.d(TAG, "onReadSuccess: " + fileName);
        switch (fileType) {
            case MeasurementConstant.CMD_TYPE_ECG_LIST:
                deviceData = fileBuf;
                devOpHandler.sendEmptyMessage(HANDLER_ECG_LIST_RECEIVED);
                break;
            case MeasurementConstant.CMD_TYPE_ECG_NUM:
                deviceData = fileBuf;
                devOpHandler.sendEmptyMessage(HANDLER_ECG_DATA_RECEIVED);
                break;
            case MeasurementConstant.CMD_TYPE_TEMP:
                deviceData = fileBuf;
                devOpHandler.sendEmptyMessage(HANDLER_TEMP_DATA_RECEIVED);
                break;
            case MeasurementConstant.CMD_TYPE_BP:
                deviceData = fileBuf;
                devOpHandler.sendEmptyMessage(HANDLER_BP_DATA_RECEIVED);
                break;
            case MeasurementConstant.CMD_TYPE_SPO2:
                deviceData = fileBuf;
                devOpHandler.sendEmptyMessage(HANDLER_OXY_LIST_RECEIVED);
                break;
            case MeasurementConstant.CMD_TYPE_USER_LIST:
                deviceData = fileBuf;
                devOpHandler.sendEmptyMessage(HANDLER_USER_LIST_RECEIVED);
                break;
            case MeasurementConstant.CMD_TYPE_SLM_LIST:
                deviceData = fileBuf;
                devOpHandler.sendEmptyMessage(HANDLER_SLM_LIST_RECEIVED);
                break;
            case MeasurementConstant.CMD_TYPE_SLM_NUM:
                deviceData = fileBuf;
                devOpHandler.sendEmptyMessage(HANDLER_SLM_DATA_RECEIVED);
                break;
            default:
                break;
        }
    }

    @Override
    public void onReadFailed(String fileName, byte fileType, byte errCode) {
        Log.d(TAG, "onReadFailed: " + fileName + " " + errCode);
        switch (fileType) {
            case MeasurementConstant.CMD_TYPE_TEMP:
                makeTemperatureResultData();
                break;
            case MeasurementConstant.CMD_TYPE_SPO2:
                makeOxyResultData();
                break;
            case MeasurementConstant.CMD_TYPE_SLM_LIST:
                makeSlmResultData();
                break;
            case MeasurementConstant.CMD_TYPE_BP:
                processUserList();
                break;
            case MeasurementConstant.CMD_TYPE_ECG_LIST:
                makeECGResultData();
                break;
            default:
                devOpHandler.sendEmptyMessage(HANDLER_ERROR);
                break;
        }
    }


    private static final int HANDLER_CONNECTED = 101;
    private static final int HANDLER_USER_LIST_RECEIVED = 102;
    private static final int HANDLER_INFO_RECEIVED = 103;
    private static final int HANDLER_ECG_LIST_RECEIVED = 104;
    private static final int HANDLER_ECG_DATA_RECEIVED = 105;
    private static final int HANDLER_TEMP_DATA_RECEIVED = 106;
    private static final int HANDLER_BP_DATA_RECEIVED = 107;
    private static final int HANDLER_OXY_LIST_RECEIVED = 108;
    private static final int HANDLER_SLM_LIST_RECEIVED = 109;
    private static final int HANDLER_SLM_DATA_RECEIVED = 110;
    private static final int HANDLER_ERROR = 130;

    private final MyHandler devOpHandler = new MyHandler(this);

    private static class MyHandler extends Handler {
        private final WeakReference<CheckmePro> mOuter;

        private MyHandler(CheckmePro outer) {
            mOuter = new WeakReference<>(outer);
        }

        @Override
        public void handleMessage(Message msg) {
            CheckmePro outer = mOuter.get();
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_CONNECTED:
                    outer.deviceListener.setBtMAC(outer.iBtDevAddr);
                    outer.btBinder.interfaceGetInfo(2000, outer);
                    break;
                case HANDLER_INFO_RECEIVED:
                    if (outer.operationType == OperationType.Pair) {
                        outer.deviceListener.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
                        outer.stop();
                        break;
                    }
                    switch (outer.iUserDevice.getMeasure()) {
                        case GWConst.KMsrEcg:
                            outer.btBinder.interfaceReadFile(MeasurementConstant.FILE_NAME_ECG_LIST, MeasurementConstant.CMD_TYPE_ECG_LIST, 5000, outer);
                            break;
                        case GWConst.KMsrTemp:
                            outer.btBinder.interfaceReadFile(MeasurementConstant.FILE_NAME_TEMP_LIST, MeasurementConstant.CMD_TYPE_TEMP, 5000, outer);
                            break;
                        case GWConst.KMsrPres:
                            outer.btBinder.interfaceReadFile(MeasurementConstant.FILE_NAME_USER_LIST, MeasurementConstant.CMD_TYPE_USER_LIST, 5000, outer);
                            break;
                        case GWConst.KMsrOss:
                            outer.btBinder.interfaceReadFile(MeasurementConstant.FILE_NAME_SPO2_LIST, MeasurementConstant.CMD_TYPE_SPO2, 5000, outer);
                            break;
                    }
                    break;
                case HANDLER_USER_LIST_RECEIVED:
                    outer.userList = User.getUserList(outer.deviceData);
                    outer.processUserList();
                    break;
                case HANDLER_ECG_LIST_RECEIVED:
                    if (outer.checkEcgList())
                        outer.processEcgItem();
                    break;
                case HANDLER_ECG_DATA_RECEIVED:
                    outer.processEcgData();
                    break;
                case HANDLER_TEMP_DATA_RECEIVED:
                    outer.makeTemperatureResultData();
                    break;
                case HANDLER_BP_DATA_RECEIVED:
                    outer.processBPData();
                    break;
                case HANDLER_OXY_LIST_RECEIVED:
                    outer.makeOxyResultData();
                    break;
                case HANDLER_SLM_LIST_RECEIVED:
                    if (outer.checkSlmList())
                        outer.processSlmItem();
                    break;
                case HANDLER_SLM_DATA_RECEIVED:
                    outer.processSlmData();
                    break;
                case HANDLER_ERROR:
                    String message = ResourceManager.getResource().getString("ECommunicationError");
                    outer.deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR, message);
                    outer.stop();
                    break;
            }
        }
    }

    private boolean checkEcgList() {
        ecgItems = ECGItem.getEcgItemList(deviceData);
        Log.d(TAG, "checkEcgList: List size = " + (ecgItems == null ? "0" : ecgItems.size()));
        if (ecgItems == null) {
            deviceListener.notifyError("", ResourceManager.getResource().getString("KNoNewMeasure"));
            stop();
            return false;
        }

        Iterator<ECGItem> i = ecgItems.iterator();
        ECGItem item;
        SimpleDateFormat df = new SimpleDateFormat(MeasureManager.MEASURE_DATE_FORMAT, Locale.getDefault());
        while (i.hasNext()) {
            item = i.next();
            ArrayList<Measure> m = MeasureManager.getMeasureManager().getMeasureData(
                    UserManager.getUserManager().getCurrentUser().getId(),
                    df.format(item.getDate()),
                    df.format(item.getDate()),
                    GWConst.KMsrEcg,
                    null, null, null);
            if (m != null && !m.isEmpty()) {
                // the measure is already into the DB
                i.remove();
            }
        }
        if (ecgItems.isEmpty()) {
            deviceListener.notifyError("", ResourceManager.getResource().getString("KNoNewMeasure"));
            stop();
            return false;
        }
        ecgItemPos = 0;
        return true;
    }

    private void processEcgItem() {
        Log.d(TAG, "processEcgItem");
        ECGItem item = ecgItems.get(ecgItemPos);
        String egcFileName = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(item.getDate());
        btBinder.interfaceReadFile(egcFileName, MeasurementConstant.CMD_TYPE_ECG_NUM, 5000, this);
    }

    private void processEcgData() {
        ECGInnerItem item = new ECGInnerItem(deviceData);
        Log.d(TAG, "processEcgData: signal length = " + item.getECGData().length);
        ecgData.add(ecgItemPos, item);
        ecgItemPos++;
        if (ecgItemPos < ecgItems.size()) {
            processEcgItem();
        } else {
            makeECGResultData();
        }
    }

    private boolean checkSlmList() {
        slmItems = SLMItem.getSlmItemList(deviceData);
        Log.d(TAG, "checkSlmList: List size = " + (slmItems == null ? "0" : slmItems.size()));
        if (slmItems == null) {
            deviceListener.notifyError("", ResourceManager.getResource().getString("KNoNewMeasure"));
            stop();
            return false;
        }

        Iterator<SLMItem> i = slmItems.iterator();
        SLMItem item;
        SimpleDateFormat df = new SimpleDateFormat(MeasureManager.MEASURE_DATE_FORMAT, Locale.getDefault());
        while (i.hasNext()) {
            item = i.next();
            ArrayList<Measure> m = MeasureManager.getMeasureManager().getMeasureData(
                    UserManager.getUserManager().getCurrentUser().getId(),
                    df.format(item.getDate()),
                    df.format(item.getDate()),
                    GWConst.KMsrOss,
                    null, null, null);
            if (m != null && !m.isEmpty()) {
                // the measure is already into the DB
                i.remove();
            }
        }
        if (slmItems.isEmpty()) {
            if (measureList.isEmpty()) {
                deviceListener.notifyError("", ResourceManager.getResource().getString("KNoNewMeasure"));
                stop();
                return false;
            } else {
                deviceListener.showMeasurementResults(measureList);
                stop();
                return false;
            }
        } else {
            slmItemPos = 0;
            return true;
        }
    }

    private void processSlmItem() {
        Log.d(TAG, "processSlmItem");
        SLMItem item = slmItems.get(slmItemPos);
        String slmFileName = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(item.getDate());
        btBinder.interfaceReadFile(slmFileName, MeasurementConstant.CMD_TYPE_SLM_NUM, 5000, this);
    }

    private void processSlmData() {
        SLMInnerItem item = new SLMInnerItem(deviceData);
        Log.d(TAG, "processSlmData: SPO2 samples nr = " + item.getSpo2List().size());
        slmData.add(slmItemPos, item);
        slmItemPos++;
        if (slmItemPos < slmItems.size()) {
            processSlmItem();
        } else {
            makeSlmResultData();
        }
    }

    private void processUserList() {
        if (userList == null) {
            deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR, ResourceManager.getResource().getString("ECommunicationError"));
            stop();
            return;
        }
        if (!userList.isEmpty()) {
            User u = userList.remove(0);
            btBinder.interfaceReadFile(u.getUserInfo().getID() + "nibp.dat", MeasurementConstant.CMD_TYPE_BP, 5000, this);
        } else {
            makeBPResultData();
        }
    }

    private void processBPData() {
        ArrayList<BPItem> tmpList = BPItem.getBpItemList(deviceData);
        if (tmpList != null)
            bpItems.addAll(tmpList);
        processUserList();
    }

    private String createAECG(ECGItem item, ECGInnerItem data, String timestamp) {
        AECG aecg = new AECG(MyApp.getContext(), item.getDate(), 500);
        int[] signal = data.getECGData();
        int[] leadData = new int[signal.length];
        /*
        Descrizione "cino-inglese" del segnale....
        If one of the ECG data is n, and reduced to the actual voltage X(n)(mV) signal formula is
        X(n) = (n*4033)/(32767*12*8)
        */
        for (int i = 0; i < signal.length; i++) {
            // leadData in uV
            // signal[i] * 1000 * 4033 / (32767*12*8)
            leadData[i] = (int) ((double) signal[i] * 1000f * 4033f / 3145728f);
        }

        AECG.LeadType leadId;
        switch (item.getMeasuringMode()) {
            case 2:
            case 4:
                leadId = AECG.LeadType.LEAD_II;
                break;
            case 1:
            case 3:
            default:
                leadId = AECG.LeadType.LEAD_I;
                break;
        }
        aecg.addLead(leadId, leadData, 0, 1);
        return aecg.saveFile(Util.getMeasuresDir(UserManager.getUserManager().getCurrentPatient().getId())
                + File.separator + timestamp);
    }

    /**
     * Aggiorna il risultato della misura e lo notifica alla GUI
     */
    private void makeECGResultData() {
        Log.d(TAG, "makeECGResultData");
        ArrayList<Measure> measureList = new ArrayList<>();
        for (int i = 0; i < ecgItems.size(); i++) {
            ECGItem item = ecgItems.get(i);
            ECGInnerItem data = ecgData.get(i);
            if ((item == null) || (data == null)) {
                // TODO
                Log.d(TAG, "item or data is Null");
                continue;
            }
            String timestamp = new SimpleDateFormat(MeasureManager.MEASURE_DATE_FORMAT, Locale.getDefault()).format(item.getDate());
            String ecgFileName = createAECG(item, data, timestamp);
            if (ecgFileName == null) {
                // TODO
                Log.d(TAG, "ecgFileName is Null");
                continue;
            }
            String[] tokens = ecgFileName.split(File.separator);

            HashMap<String, String> tmpVal = new HashMap<>();
            tmpVal.put(GWConst.EGwCode_0S, tokens[tokens.length - 1]);  // filename
            tmpVal.put(GWConst.EGwCode_2A, Integer.toString(item.getMeasuringMode()));
            tmpVal.put(GWConst.EGwCode_2D, Integer.toString(item.getImgResult()));
            tmpVal.put(GWConst.EGwCode_2E, Integer.toString(data.getHR()));
            if ((item.getMeasuringMode() == 3) || (item.getMeasuringMode() == 4))
                tmpVal.put(GWConst.EGwCode_2F, Integer.toString(data.getST()));
            tmpVal.put(GWConst.EGwCode_2G, Integer.toString(data.getQRS()));
            tmpVal.put(GWConst.EGwCode_2H, Integer.toString(data.getPVCs()));
            tmpVal.put(GWConst.EGwCode_2I, Integer.toString(data.getQTc()));
            tmpVal.put(GWConst.EGwCode_2J, Integer.toString(data.getQT()));
            tmpVal.put(GWConst.EGwCode_2K, Integer.toString(data.getFilterMode()));
            tmpVal.put(GWConst.EGwCode_2L, Integer.toString(data.getStrResultIndex()));

            Measure m = getMeasure();
            m.setTimestamp(timestamp);
            try {
                m.setFile(ecgFileName.getBytes("UTF-8"));
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
            m.setFileType(XmlManager.AECG_FILE_TYPE);
            m.setMeasures(tmpVal);
            measureList.add(m);
        }
        if (measureList.isEmpty())
            deviceListener.notifyError("", ResourceManager.getResource().getString("KNoNewMeasure"));
        else
            deviceListener.showMeasurementResultsUrgent(measureList);
        stop();
    }

    private void makeTemperatureResultData() {
        if (!GWConst.KMsrTemp.equals(iUserDevice.getMeasure())) {
            deviceListener.notifyError(DeviceListener.MEASURE_PROCEDURE_ERROR, ResourceManager.getResource().getString("KWrongMeasure"));
        } else {
            Log.d(TAG, "makeTemperatureResultData");
            ArrayList<Measure> measureList = new ArrayList<>();
            ArrayList<TempItem> tempItems = TempItem.getTempItemList(deviceData);
            SimpleDateFormat df = new SimpleDateFormat(MeasureManager.MEASURE_DATE_FORMAT, Locale.getDefault());
            for (TempItem item : tempItems) {
                ArrayList<Measure> mList = MeasureManager.getMeasureManager().getMeasureData(
                        UserManager.getUserManager().getCurrentUser().getId(),
                        df.format(item.getDate()),
                        df.format(item.getDate()),
                        GWConst.KMsrTemp,
                        null, null, null);
                if (mList == null || mList.isEmpty()) {
                    HashMap<String, String> tmpVal = new HashMap<>();
                    tmpVal.put(GWConst.EGwCode_0R, String.format(Locale.ITALY, "%.2f", item.getResult()));
                    String timestamp = new SimpleDateFormat(MeasureManager.MEASURE_DATE_FORMAT, Locale.getDefault()).format(item.getDate());
                    Measure m = getMeasure();
                    m.setTimestamp(timestamp);
                    m.setMeasures(tmpVal);
                    measureList.add(m);
                }
            }
            if (measureList.isEmpty())
                deviceListener.notifyError("", ResourceManager.getResource().getString("KNoNewMeasure"));
            else
                deviceListener.showMeasurementResults(measureList);
        }
        stop();
    }

    private void makeBPResultData() {
        Log.d(TAG, "makeBPResultData");
        if (!GWConst.KMsrPres.equals(iUserDevice.getMeasure())) {
            deviceListener.notifyError(DeviceListener.MEASURE_PROCEDURE_ERROR, ResourceManager.getResource().getString("KWrongMeasure"));
        } else {
            ArrayList<Measure> measureList = new ArrayList<>();
            SimpleDateFormat df = new SimpleDateFormat(MeasureManager.MEASURE_DATE_FORMAT, Locale.getDefault());
            for (BPItem item : bpItems) {
                ArrayList<Measure> mList = MeasureManager.getMeasureManager().getMeasureData(
                        UserManager.getUserManager().getCurrentUser().getId(),
                        df.format(item.getDate()),
                        df.format(item.getDate()),
                        GWConst.KMsrPres,
                        null, null, null);
                if (mList == null || mList.isEmpty()) {
                    HashMap<String, String> tmpVal = new HashMap<>();
                    tmpVal.put(GWConst.EGwCode_03, Integer.toString(item.getDiastolic())); // pressione minima
                    tmpVal.put(GWConst.EGwCode_04, Integer.toString(item.getSystolic())); // pressione massima
                    tmpVal.put(GWConst.EGwCode_06, Integer.toString(item.getPulseRate())); // freq cardiaca
                    String timestamp = new SimpleDateFormat(MeasureManager.MEASURE_DATE_FORMAT, Locale.getDefault()).format(item.getDate());
                    Measure m = getMeasure();
                    m.setTimestamp(timestamp);
                    m.setMeasures(tmpVal);
                    measureList.add(m);
                }
            }
            if (measureList.isEmpty())
                deviceListener.notifyError("", ResourceManager.getResource().getString("KNoNewMeasure"));
            else
                deviceListener.showMeasurementResults(measureList);
        }
        stop();
    }

    private void makeOxyResultData() {
        Log.d(TAG, "makeOxyResultData");
        if (!GWConst.KMsrOss.equals(iUserDevice.getMeasure())) {
            deviceListener.notifyError(DeviceListener.MEASURE_PROCEDURE_ERROR, ResourceManager.getResource().getString("KWrongMeasure"));
            stop();
        } else {
            ArrayList<SPO2Item> spo2Items = SPO2Item.getSPO2ItemList(deviceData);
            SimpleDateFormat df = new SimpleDateFormat(MeasureManager.MEASURE_DATE_FORMAT, Locale.getDefault());
            for (SPO2Item item : spo2Items) {
                ArrayList<Measure> mList = MeasureManager.getMeasureManager().getMeasureData(
                        UserManager.getUserManager().getCurrentUser().getId(),
                        df.format(item.getDate()),
                        df.format(item.getDate()),
                        GWConst.KMsrOss,
                        null, null, null);
                if (mList == null || mList.isEmpty()) {
                    HashMap<String, String> tmpVal = new HashMap<>();
                    tmpVal.put(GWConst.EGwCode_07, String.valueOf(item.getOxygen())); // O2 Med
                    tmpVal.put(GWConst.EGwCode_0F, String.valueOf(item.getPr()));  // HR Med
                    String timestamp = new SimpleDateFormat(MeasureManager.MEASURE_DATE_FORMAT, Locale.getDefault()).format(item.getDate());
                    Measure m = getMeasure();
                    m.setTimestamp(timestamp);
                    m.setMeasures(tmpVal);
                    measureList.add(m);
                }
            }

            btBinder.interfaceReadFile(MeasurementConstant.FILE_NAME_SLM_LIST, MeasurementConstant.CMD_TYPE_SLM_LIST, 5000, this);
        }
    }

    private class OxyElem {
        private int iSat;
        private int iFreq;

        OxyElem(int sat, int freq) {
            iSat = sat;
            iFreq = freq;
        }

        int getISat() {
            return iSat;
        }

        int getIFreq() {
            return iFreq;
        }
    }

    private class Time {
        private int hh;
        private int mm;
        private int ss;

        Time(int hh, int mm, int ss) {
            this.hh = hh;
            this.mm = mm;
            this.ss = ss;
        }

        int getHh() {
            return hh;
        }

        int getMm() {
            return mm;
        }

        int getSs() {
            return ss;
        }
    }

    private Time calcTime(int aSec) {
        int hh = aSec/3600;
        int mm = (aSec%3600)/60;
        int ss = (aSec%3600)%60;
        return new Time(hh, mm, ss);
    }

    private void makeSlmResultData() {
        for (int i = 0; i < slmItems.size(); i++) {
            SLMItem item = slmItems.get(i);
            SLMInnerItem data = slmData.get(i);
            if ((item == null) || (data == null)) {
                // TODO
                Log.d(TAG, "item or data is Null");
                continue;
            }
            double iSpO2Med,iHRMed;
            int	iSpO2Min,iSpO2Max;
            int	iHRMin,iHRMax;
            int	iEventSpO289; // + 20 sec < 89 %
            int	iEventSpO289Count;
            int	iEventBradi; // HR<40
            int	iEventTachi; // HR>120
            int	iT90; //tempo SpO2<90%
            int	iT89; //tempo SpO2<89%
            int	iT88; //tempo SpO2<88%
            int	iT87; //tempo SpO2<87%
            int	iT40; //tempo HR<40 bpm
            int	iT120; //tempo HR>120 bpm
            int	iAnalysisTime;
            Vector<OxyElem> oxyQueue;
            iSpO2Min = 1024;
            iSpO2Max = 0;
            iHRMin = 1024;
            iHRMax = 0;
            iEventSpO289 = iEventSpO289Count = 0;
            iEventBradi = iEventTachi = 0;
            iT90 = iT89 = iT88 = iT87 = 0;
            iT40 = iT120 = 0;
            oxyQueue = new Vector<>(data.getSpo2Dat().length);
            byte[] oxyStream;
            ByteBuffer tmpStream = ByteBuffer.allocate(BASE_OXY_STREAM_LENGTH);
            tmpStream.put(66, (byte)0x00); //STEP_OXY MSB
            tmpStream.put(67, (byte)0x14); //STEP_OXY LSB (tempo di campionamento in 1/10 sec)
            tmpStream.put(68, (byte)0x04);
            tmpStream.put(69, (byte)0x20); //FL_TEST
            oxyStream = tmpStream.array();

            for (int j=0; j<data.getSpo2Dat().length; j++) {
                int aSpO2, aHR;
                aSpO2 = data.getSpo2List().get(j);
                aHR = data.getPrList().get(j);
                oxyQueue.add(new OxyElem(aSpO2,aHR));

                if (aSpO2 > 100)
                    continue;

                if (aSpO2<iSpO2Min)
                    iSpO2Min = aSpO2;

                if (aSpO2>iSpO2Max)
                    iSpO2Max = aSpO2;

                if (aHR<iHRMin)
                    iHRMin = aHR;

                if (aHR>iHRMax)
                    iHRMax = aHR;

                if (aSpO2<89) {
                    iEventSpO289Count++;
                    if (iEventSpO289Count == 10)
                        iEventSpO289++;
                }
                else {
                    iEventSpO289Count=0;
                }

                if (aHR<40)
                    iEventBradi++;
                if (aHR>120)
                    iEventTachi++;
                if (aSpO2<90)
                    iT90++;
                if (aSpO2<89)
                    iT89++;
                if (aSpO2<88)
                    iT88++;
                if (aSpO2<87)
                    iT87++;
                if (aHR<40)
                    iT40++;
                if (aHR>120)
                    iT120++;

            }

            int hrTot=0, spO2Tot=0, sampleCount;
            OxyElem elem;

            sampleCount = oxyQueue.size();

            tmpStream = ByteBuffer.allocate(BASE_OXY_STREAM_LENGTH + sampleCount*2);
            tmpStream.put(oxyStream);
            oxyStream = tmpStream.array();

            // Sample num
            oxyStream[187] = (byte)((sampleCount>>8) & 0xFF);
            oxyStream[188] = (byte)((sampleCount) & 0xFF);

            int num = 0;
            for (int k=0; k<oxyQueue.size(); k++) {
                elem = oxyQueue.get(k);

                if (elem.getISat() <= 100) {
                    hrTot += elem.getIFreq();
                    spO2Tot += elem.getISat();
                    num++;
                }

                oxyStream[BASE_OXY_STREAM_LENGTH + (k*2)] =	(byte)(elem.getISat() & 0xFF);		//SpO2
                oxyStream[BASE_OXY_STREAM_LENGTH + (k*2) + 1] = (byte)(elem.getIFreq() & 0xFF);	//HR
            }

            iAnalysisTime = sampleCount*2; // tempo in secondi

            iSpO2Med = ((double)spO2Tot/(double)num);
            iHRMed = ((double)hrTot/(double)num);

            //MISURE
            DecimalFormat df = new DecimalFormat("0.0");

            // SpO2 Media
            int appo = (int) iSpO2Med * 10;
            oxyStream[94] = (byte)((appo>>8) & 0xFF);
            oxyStream[95] = (byte)(appo & 0xFF);

            // HR Media
            appo = (int)  iHRMed * 10;
            oxyStream[102] = (byte)((appo>>8) & 0xFF);
            oxyStream[103] = (byte)(appo & 0xFF);

            // SpO2 Min
            oxyStream[92] = (byte)((iSpO2Min>>8) & 0xFF);
            oxyStream[93] = (byte)(iSpO2Min & 0xFF);

            // HR Min
            oxyStream[100] = (byte)((iHRMin>>8) & 0xFF);
            oxyStream[101] = (byte)(iHRMin & 0xFF);

            // SpO2 Max
            oxyStream[96] = (byte)((iSpO2Max>>8) & 0xFF);
            oxyStream[97] = (byte)(iSpO2Max & 0xFF);

            // HR Max
            oxyStream[104] = (byte)((iHRMax>>8) & 0xFF);
            oxyStream[105] = (byte)(iHRMax & 0xFF);

            //Eventi SpO2 <89%
            oxyStream[110] = (byte)((iEventSpO289>>8) & 0xFF);
            oxyStream[111] = (byte)(iEventSpO289 & 0xFF);

            //Eventi di Bradicardia (HR<40)
            oxyStream[114] = (byte)((iEventBradi>>8) & 0xFF);
            oxyStream[115] = (byte)(iEventBradi & 0xFF);

            //Eventi di Tachicardia (HR>120)
            oxyStream[116] = (byte)((iEventTachi>>8) & 0xFF);
            oxyStream[117] = (byte)(iEventTachi & 0xFF);

            //SpO2 < 90%
            oxyStream[124] = (byte)calcTime(iT90).getHh();
            oxyStream[125] = (byte)calcTime(iT90).getMm();
            oxyStream[126] = (byte)calcTime(iT90).getSs();

            //SpO2 < 89%
            oxyStream[127] = (byte)calcTime(iT89).getHh();
            oxyStream[128] = (byte)calcTime(iT89).getMm();
            oxyStream[129] = (byte)calcTime(iT89).getSs();

            //SpO2 < 88%
            oxyStream[130] = (byte)calcTime(iT88).getHh();
            oxyStream[131] = (byte)calcTime(iT88).getMm();
            oxyStream[132] = (byte)calcTime(iT88).getSs();

            //SpO2 < 87%
            oxyStream[133] = (byte)calcTime(iT87).getHh();
            oxyStream[134] = (byte)calcTime(iT87).getMm();
            oxyStream[135] = (byte)calcTime(iT87).getSs();

            //HR < 40 bpm
            oxyStream[136] = (byte)calcTime(iT40).getHh();
            oxyStream[137] = (byte)calcTime(iT40).getMm();
            oxyStream[138] = (byte)calcTime(iT40).getSs();

            //HR > 120 bpm
            oxyStream[139] = (byte)calcTime(iT120).getHh();
            oxyStream[140] = (byte)calcTime(iT120).getMm();
            oxyStream[141] = (byte)calcTime(iT120).getSs();

            // DURATA
            Time tDurata = calcTime(iAnalysisTime);
            //String durata = Integer.toString(iAnalysisTime);
            String durata = String.format(Locale.ENGLISH, "%02d:%02d:%02d", tDurata.getHh(), tDurata.getMm(), tDurata.getSs());

            //Recording Time & Analysis Time
            oxyStream[84] = (byte)tDurata.getHh();
            oxyStream[85] = (byte)tDurata.getMm();
            oxyStream[86] = (byte)tDurata.getSs();
            oxyStream[87] = (byte)tDurata.getHh();
            oxyStream[88] = (byte)tDurata.getMm();
            oxyStream[89] = (byte)tDurata.getSs();

            String timestamp = new SimpleDateFormat(MeasureManager.MEASURE_DATE_FORMAT, Locale.getDefault()).format(item.getDate());
            String oxyFileName = "oxy-"+ timestamp +".oxy";

            // Creo un istanza di Misura del tipo PR
            HashMap<String,String> tmpVal = new HashMap<>();
            tmpVal.put(GWConst.EGwCode_07, df.format(iSpO2Med).replace ('.', ','));  // O2 Med
            tmpVal.put(GWConst.EGwCode_1B, df.format(iSpO2Min).replace ('.', ','));  // O2 Min
            tmpVal.put(GWConst.EGwCode_1D, df.format(iSpO2Max).replace ('.', ','));  // O2 Max
            tmpVal.put(GWConst.EGwCode_0F, df.format(iHRMed).replace ('.', ','));  // HR Med
            tmpVal.put(GWConst.EGwCode_1A, df.format(iHRMin).replace ('.', ','));  // HR Min
            tmpVal.put(GWConst.EGwCode_1C, df.format(iHRMax).replace ('.', ','));  // HR Max
            tmpVal.put(GWConst.EGwCode_1G, durata);
            tmpVal.put(GWConst.EGwCode_1H, oxyFileName);  // filename

            Measure m = getMeasure();
            m.setTimestamp(timestamp);
            m.setMeasures(tmpVal);
            m.setFile(oxyStream);
            m.setFileType(XmlManager.MIR_OXY_FILE_TYPE);
            m.setFailed(false);
            measureList.add(m);
        }

        if (measureList.isEmpty())
            deviceListener.notifyError("", ResourceManager.getResource().getString("KNoNewMeasure"));
        else
            deviceListener.showMeasurementResults(measureList);
        stop();
    }
}