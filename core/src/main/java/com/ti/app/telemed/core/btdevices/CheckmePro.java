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
import com.viatom.checkmelib.measurement.SPO2Item;
import com.viatom.checkmelib.measurement.TempItem;
import com.viatom.checkmelib.measurement.User;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Vector;



public class CheckmePro extends DeviceHandler implements
        BTSearcherEventListener,
        BTConnectListener,
        GetInfoThreadListener,
        ReadFileListener {

    private static final String TAG = "CheckmePro";

    private BTSearcher iServiceSearcher;
    private BluetoothDevice selectedDevice;

    private BTUtils.BTBinder btBinder = null;
    private CheckmeDevice device = null;
    private byte[] deviceData = null;
    private ArrayList<ECGItem> ecgItems = new ArrayList<>();
    private ArrayList<ECGInnerItem> ecgData = new ArrayList<>();
    private ArrayList<BPItem> bpItems = new ArrayList<>();
    private ArrayList<com.viatom.checkmelib.measurement.User> userList = new ArrayList<>();
    private int ecgItemPos = 0;


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
    public void cancelDialog(){
        // Not used for this device
    }

    @Override
    public boolean startOperation(OperationType ot, BTSearcherEventListener btSearchListener) {
        if (!startInit(ot, btSearchListener))
            return false;

        Log.d(TAG,"startOperation: iBtDevAddr="+iBtDevAddr + " iCmdCode="+iCmdCode.toString());
        iServiceSearcher.clearBTSearcherEventListener();
        //if (iCmdCode == TCmd.ECmdConnByUser && iBTSearchListener != null)
        //    iServiceSearcher.addBTSearcherEventListener(iBTSearchListener);
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
    public void selectDevice(BluetoothDevice bd){
        Log.d(TAG, "selectDevice: iBtDevAddr="+bd.getAddress());
        iServiceSearcher.stopSearchDevices();
        selectedDevice = bd;
        iBtDevAddr = selectedDevice.getAddress();
        iState = TState.EGettingService;

        iState = TState.EConnected;
        Log.d(TAG, "Connecting...");

        Intent mIntent = new Intent(MyApp.getContext(), com.viatom.checkmelib.bluetooth.BTUtils.class );
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
                for (int i=0; i<devList.size(); i++)
                    if (iBtDevAddr.equalsIgnoreCase(devList.get(i).getAddress())) {
                        selectDevice(devList.get(i));
                    }
                break;
            case ECmdConnByUser:
                if (iBTSearchListener != null)
                    iBTSearchListener.deviceDiscovered(devList);
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
        Log.d(TAG, "onGetInfoFailed: " +" " + errCode);
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
            default:
                break;
        }
    }

    @Override
    public void onReadFailed(String fileName, byte fileType, byte errCode) {
        Log.d(TAG, "onReadFailed: " + fileName + " " + errCode);
        devOpHandler.sendEmptyMessage(HANDLER_ERROR);
    }


    private static final int HANDLER_CONNECTED = 101;
    private static final int HANDLER_USER_LIST_RECEIVED = 102;
    private static final int HANDLER_INFO_RECEIVED = 103;
    private static final int HANDLER_ECG_LIST_RECEIVED = 104;
    private static final int HANDLER_ECG_DATA_RECEIVED = 105;
    private static final int HANDLER_TEMP_DATA_RECEIVED = 106;
    private static final int HANDLER_BP_DATA_RECEIVED = 107;
    private static final int HANDLER_OXY_LIST_RECEIVED = 108;
    private static final int HANDLER_OXY_DATA_RECEIVED = 109;
    private static final int HANDLER_ERROR = 110;

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
            item  = i.next();
            ArrayList<Measure> m = MeasureManager.getMeasureManager().getMeasureData(
                    UserManager.getUserManager().getCurrentUser().getId(),
                    df.format(item.getDate()),
                    df.format(item.getDate()),
                    GWConst.KMsrEcg,
                    null,null,null);
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
        ecgData.add(ecgItemPos,item);
        ecgItemPos++;
        if (ecgItemPos < ecgItems.size()) {
            processEcgItem();
        } else {
            makeECGResultData();
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
            btBinder.interfaceReadFile(u.getUserInfo().getID()+"nibp.dat", MeasurementConstant.CMD_TYPE_BP, 5000, this);
        } else {
            makeBPResultData();
        }
    }

    private void processBPData(){
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
        for (int i=0;i<signal.length;i++) {
            // leadData in uV
            // signal[i] * 1000 * 4033 / (32767*12*8)
            leadData[i] = (int) ((double)signal[i] * 1000f * 4033f / 3145728f);
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
    private void makeECGResultData()
    {
        Log.d(TAG, "makeECGResultData");
        ArrayList<Measure> measureList = new ArrayList<>();
        for (int i=0; i<ecgItems.size();i++) {
            ECGItem item = ecgItems.get(i);
            ECGInnerItem data = ecgData.get(i);
            if ((item == null) || (data == null)) {
                // TODO
                Log.d(TAG, "item or data is Null");
                continue;
            }
            String timestamp = new SimpleDateFormat(MeasureManager.MEASURE_DATE_FORMAT, Locale.getDefault()).format(item.getDate());
            String ecgFileName = createAECG(item,data,timestamp);
            if (ecgFileName == null) {
                // TODO
                Log.d(TAG, "ecgFileName is Null");
                continue;
            }
            String [] tokens  = ecgFileName.split(File.separator);

            Log.d(TAG, "getST = " + data.getST());
            Log.d(TAG, "getFilterMode = " + data.getFilterMode());
            Log.d(TAG, "getStrResultIndex = " + data.getStrResultIndex());

            HashMap<String,String> tmpVal = new HashMap<>();
            tmpVal.put(GWConst.EGwCode_0S, tokens[tokens.length-1]);  // filename
            tmpVal.put(GWConst.EGwCode_2A, Integer.toString(item.getMeasuringMode()));
            tmpVal.put(GWConst.EGwCode_2D, Integer.toString(item.getImgResult()));
            tmpVal.put(GWConst.EGwCode_2E, Integer.toString(data.getHR()));
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
        deviceListener.showMeasurementResultsUrgent(measureList);
        stop();
    }

    private void makeTemperatureResultData() {
        if (!GWConst.KMsrTemp.equals(iUserDevice.getMeasure())) {
            deviceListener.notifyError(DeviceListener.MEASURE_PROCEDURE_ERROR,ResourceManager.getResource().getString("KWrongMeasure"));
        } else {
            Log.d(TAG, "makeTemperatureResultData");
            ArrayList<Measure> measureList = new ArrayList<>();
            ArrayList<TempItem> tempItems = TempItem.getTempItemList(deviceData);
            SimpleDateFormat df = new SimpleDateFormat(MeasureManager.MEASURE_DATE_FORMAT, Locale.getDefault());
            for (TempItem item:tempItems) {
                ArrayList<Measure> mList = MeasureManager.getMeasureManager().getMeasureData(
                        UserManager.getUserManager().getCurrentUser().getId(),
                        df.format(item.getDate()),
                        df.format(item.getDate()),
                        GWConst.KMsrTemp,
                        null,null,null);
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
            deviceListener.notifyError(DeviceListener.MEASURE_PROCEDURE_ERROR,ResourceManager.getResource().getString("KWrongMeasure"));
        } else {
            ArrayList<Measure> measureList = new ArrayList<>();
            SimpleDateFormat df = new SimpleDateFormat(MeasureManager.MEASURE_DATE_FORMAT, Locale.getDefault());
            for (BPItem item:bpItems) {
                ArrayList<Measure> mList = MeasureManager.getMeasureManager().getMeasureData(
                        UserManager.getUserManager().getCurrentUser().getId(),
                        df.format(item.getDate()),
                        df.format(item.getDate()),
                        GWConst.KMsrPres,
                        null,null,null);
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
            deviceListener.notifyError(DeviceListener.MEASURE_PROCEDURE_ERROR,ResourceManager.getResource().getString("KWrongMeasure"));
        } else {
            ArrayList<Measure> measureList = new ArrayList<>();
            ArrayList<SPO2Item> spo2Items = SPO2Item.getSPO2ItemList(deviceData);
            SimpleDateFormat df = new SimpleDateFormat(MeasureManager.MEASURE_DATE_FORMAT, Locale.getDefault());
            for (SPO2Item item:spo2Items) {
                ArrayList<Measure> mList = MeasureManager.getMeasureManager().getMeasureData(
                        UserManager.getUserManager().getCurrentUser().getId(),
                        df.format(item.getDate()),
                        df.format(item.getDate()),
                        GWConst.KMsrOss,
                        null,null,null);
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
            if (measureList.isEmpty())
                deviceListener.notifyError("", ResourceManager.getResource().getString("KNoNewMeasure"));
            else
                deviceListener.showMeasurementResults(measureList);
        }
        stop();
    }
}