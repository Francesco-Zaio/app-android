package com.ti.app.telemed.core.btdevices;

import android.bluetooth.BluetoothDevice;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.BTSearcher;
import com.ti.app.telemed.core.btmodule.BTSocket;
import com.ti.app.telemed.core.btmodule.events.BTSearcherEventListener;
import com.ti.app.telemed.core.btmodule.events.BTSocketEventListener;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

public class EcgProtocol extends DeviceHandler implements
        BTSocketEventListener,
        BTSearcherEventListener  {
    /**
     * TState
     * The state of the active object, determines behaviour within
     * the RunL method.
     * EWaitingToGetDevice waiting for the user to select a device
     * EGettingDevice searching for a device
     * EGettingService searching for a service
     * EConnected connected to a service on a remote machine
     * ESendingData sending data to the remote machine
     * EWaitingStartMessage connected to a client and waiting for first byte of message
     * EWaitingHeader connected to a client and waiting for read header of message
     * EWaitingBody connected to a client and waiting for read body of message
     * ECheckMessage connected to a client and waiting for read CRC of message
     * EDisconnecting disconnecting to a service on a remote machine
     * EDisconnected client not connected
     */


    private enum TTypeMessage {
        kT_COPY ((byte)0x43),
        kT_DATA_ACK ((byte)0xDA),
        kT_DATA_BLK ((byte)0xDB),
        kT_DATA_END ((byte)0xDE),
        kT_DATA_END_TX ((byte)0xEE),
        START_MESSAGE ((byte)0xAC);

        private final byte val;

        TTypeMessage(byte val) {
            this.val = val;
        }

        static byte getVal(TTypeMessage tm) {
            return tm.val;
        }
    }


    private final int KMaximumMessageLengthECG = 512; //2048;  // The maximum length of any message
    // that can be read
    private final int KFirstMessageLengthECG = 8;  // The maximum length of any message
    // that can be read

    private final int KMaxRetry = 10; // The maximum number of retransmissions

    // iServiceSearcher searches for service this client can
    // connect to (in symbian version the type was CBTUtil) and
    // substitutes RSocketServ and RSocket of symbian version too
    private BTSearcher iServiceSearcher;
    // iECGSocket is an RSocket in the Symbian version
    private BTSocket iECGSocket;

    private Vector<BluetoothDevice> deviceList;
    private int currentPos;
    private BluetoothDevice selectedDevice;
    private boolean deviceSearchCompleted;

    // Gestione puntatori al file
    private int iSizeBlock;
    private int iIndex;

    private boolean	iIsLastMessage;

    // contains the value of battery charge (this device doesn't have this information)
    private int iBattery;

    // Contiene tutti i dati ricevuti
    private byte[] iEcgBuffer;
    private int iIndexEcgBuffer;

    // iID identificativo del messaggio atteso
    private int iID;

    // iSize dimensione in bytes dei dati attesi
    private int iSize;

    // iType tipologia di messaggio atteso
    private byte iType;

    // buffer needed for read on connection to server
    private byte[] iStartMsg; //Contiene il primo ottetto dell'header
    private byte[] iCrc; //Contiene il Crc del body
    private byte[] iTempHeader; //Contiene il secondo, terzo e quarto ottetto dell'header
    private byte[] iHeader; //Contiene l'intero header
    private byte[] iBody; //Contiene il body
    private byte[] iBodyFirstMsg; //Contiene il body del primo msg

    // iMessageToSend a copy of the message to send
    private byte[] iMessageToSend;

    // Dati per lo ricezione del file
    private int iBaseAddress;
    private int iCurrentAddress;
    private int iFileTotalSize;

    private boolean dataFound;
    private boolean operationDeleted;
    private boolean serverOpenFailed;

    // Controlla il numero di tentativi di ritrasmissione di
    // un pacchetto errato
    private int iRetry;

    private boolean iFirstPacket;
    private Timer timer;

    private static final String TAG = "EcgProtocol";

    public static boolean needPairing(UserDevice userDevice) {
        return false;
    }

    public static boolean needConfig(UserDevice userDevice) {
        return false;
    }

    public EcgProtocol(DeviceListener listener, UserDevice ud) {
        super(listener, ud);

        deviceSearchCompleted = false;
        dataFound = false;

        iID = 0;
        iSize = 0;
        iType = 0x00;
        iSizeBlock = KMaximumMessageLengthECG;
        iFileTotalSize = 0;
        iIndex = 0;
        iIsLastMessage = false;
        iFirstPacket = true;
        iRetry = KMaxRetry;
        iIndexEcgBuffer = 0;

    	/* Buffer utilizzati per la trasmissione e ricezione dati. */
        iEcgBuffer = null;
        iStartMsg = new byte[1];
        iCrc = new byte[2];
        iTempHeader = new byte[3];
        iHeader = new byte[4];
        iBody = new byte[KMaximumMessageLengthECG];
        iBodyFirstMsg = new byte[KFirstMessageLengthECG];

        // there isn't battery information
        iBattery = -1;

        operationDeleted = false;
        serverOpenFailed = false;
        iServiceSearcher = new BTSearcher();
        iECGSocket = BTSocket.getBTSocket();
    }

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
        if (iCmdCode == TCmd.ECmdConnByUser && iBTSearchListener != null)
            iServiceSearcher.addBTSearcherEventListener(iBTSearchListener);
        iServiceSearcher.addBTSearcherEventListener(this);
        iServiceSearcher.startSearchDevices();
        return true;
    }

    @Override
    public void abortOperation() {
        Log.d(TAG, "abortOperation");
        stop();
    }

    @Override
    public void selectDevice(BluetoothDevice bd){
        Log.d(TAG, "selectDevice: iBtDevAddr="+bd.getAddress());
        iServiceSearcher.stopSearchDevices();
        selectedDevice = bd;
        iBtDevAddr = selectedDevice.getAddress();
        iState = TState.EGettingService;
        runBTSearcher();
    }


    // methods of BTSearchEventListener interface

    @Override
    public void deviceDiscovered(Vector<BluetoothDevice> devList) {
        deviceList = devList;
        // we recall runBTSearcher, because every time we find a device, we have
        // to check if it is the device we want
        runBTSearcher();
    }

    @Override
    public void deviceSearchCompleted() {
        deviceSearchCompleted = true;
        currentPos = 0;
    }


    private void connectToServer() throws IOException {
        // this function is called when we are in EGettingService state and
        // we are going to EGettingConnection state
        Log.i(TAG, "ECG: connectToServer");
        // iCGBloodPressureSocket is an RSocket in the Symbian version
        iECGSocket.addBTSocketEventListener(this);
        iECGSocket.connect(selectedDevice);
    }

    private void disconnectProtocolError() {
        operationDeleted = true;
        iECGSocket.removeBTSocketEventListener(this);
        iECGSocket.close();
        reset();
        String msg = ResourceManager.getResource().getString("ECommunicationError");
        deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR,msg);
    }

    public void stop() {
        iServiceSearcher.stopSearchDevices();
        iServiceSearcher.clearBTSearcherEventListener();
        iServiceSearcher.close();
        iECGSocket.close();
        iECGSocket.removeBTSocketEventListener(this);

        if (iState == TState.EDisconnectingPairing) {
            runBTSocket();
        } else if (iState == TState.EDisconnectingOK) {
            makeResultData();
        }
        reset();
    }

    public void reset() {
        Log.i(TAG, "-----------------ECG reset");
        if (timer!=null) {
            timer.cancel();
        }
        // we free all buffer, descriptor and array
        deviceSearchCompleted = false;
        dataFound = false;

        iID = 0;
        iSize = 0;
        iType = 0x00;
        iSizeBlock = KMaximumMessageLengthECG;
        iFileTotalSize = 0;
        iIndex = 0;
        iIsLastMessage = false;
        iFirstPacket = true;
        iRetry = KMaxRetry;
        iIndexEcgBuffer = 0;
    	/* Buffer utilizzati per la trasmissione e ricezione dati. */
        iEcgBuffer = null;
        iStartMsg = new byte[1];
        iCrc = new byte[2];
        iTempHeader = new byte[3];
        iHeader = new byte[4];
        iBody = new byte[KMaximumMessageLengthECG];
        iBodyFirstMsg = new byte[KFirstMessageLengthECG];
        // there isn't battery information
        iBattery = -1;
        operationDeleted = false;
        serverOpenFailed = false;
        // this class object must return to the initial state
        iState = TState.EWaitingToGetDevice;
    }


    // methods of BTSocketEventListener interface

    @Override
    public void errorThrown(int type, String description) {
        Log.e(TAG, "ECG writeErrorThrown");
        Log.e(TAG, "ECG writeErrorThrown " + type + ": " + description);
        String msg;
        switch (type) {
            case 0: //thread interrupted
                reset();
                msg = ResourceManager.getResource().getString("ECommunicationError");
                deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR,msg);
                break;
            case 1: //bluetooth open error
                if (iState == TState.EConnected) {
                    // if we don't receive any message from the blood pressure at this state
                    // means that we have to do the pairing
                    iState = TState.EDisconnecting;
                    operationDeleted = true;
                    serverOpenFailed = true;
                    runBTSocket();
                }
                reset();
                msg = ResourceManager.getResource().getString("EBtDeviceConnError");
                deviceListener.notifyError(DeviceListener.CONNECTION_ERROR,msg);
                break;
            case 2: //bluetooth read error
            case 3: //bluetooth write error
                iState = TState.EDisconnecting;
                operationDeleted = true;
                runBTSocket();
                reset();
                msg = ResourceManager.getResource().getString("ECommunicationError");
                deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR,msg);
                break;
            case 4: //bluetooth close error
                reset();
                msg = ResourceManager.getResource().getString("ECommunicationError");
                deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR,msg);
                break;
        }
    }

    @Override
    public void openDone() {
        runBTSocket();
    }

    @Override
    public void readDone() {
        runBTSocket();
    }

    @Override
    public void writeDone() {
        runBTSocket();
    }


    // methods for parsing data

    /**
     * Get from the first message the BaseAddress and the FileTotalSize.
     */
    private void decodeBuffer()
    {
        Log.i(TAG, "ECG decodeBuffer");
        //I primi 4 bytes del messaggio sono l'indirizzo base del file
        for (int i=3; i>=0; i--)
        {
            iBaseAddress = iBaseAddress << 8;
            iBaseAddress = iBaseAddress | ((iBodyFirstMsg[i] & 0x000000FF));
        }

        //I seguenti 4 bytes rappresentano la dimensione totale del file
        for (int i=7; i>3; i--)
        {
            iFileTotalSize = iFileTotalSize << 8;
            iFileTotalSize = iFileTotalSize | ((iBodyFirstMsg[i] & 0x000000FF));
            Log.i(TAG, "iFileTotalSize " +iFileTotalSize);
            Log.i(TAG, "iBodyFirstMsg[i] " +((iBodyFirstMsg[i] & 0x000000FF)));
        }

        if ( iEcgBuffer != null )
        {
            iEcgBuffer = null;
        }
        iEcgBuffer =  new byte[iFileTotalSize];
    }

    /**
     * Make the body of message to send.
     */
    private void fillBuffer(byte[] buffer, int choice)
    {
        switch ( choice )
        {
            case 0:
                for ( int i=0; i<buffer.length; i++ )
                { buffer[i] = 0x00;	}
                break;
            case 1:
                int tempData;
                tempData = iCurrentAddress;
                for ( int i=0; i<4; i++ )
                {
                    buffer[i] = (byte) (tempData & 0xFF);
                    tempData = tempData >> 8;
                }
                tempData = iSizeBlock;
                for (int i=4; i<8; i++)
                {
                    buffer[i] = (byte) (tempData & 0xFF);
                    tempData = tempData >> 8;
                }
                break;
        }
    }

    /**
     * Make the CRC of a buffer.
     */
    private byte[] computeCrc(byte[] buffer, int size)
    {
        int CrcLo = 0xFF;
        int CrcHi = 0xFF;
        int bWork;
        int bTemp;

        byte[] crc = new byte[2];

        int i = 0;

        //while( (size--) > 0 )
        while( !(i == size) )
        {
            bWork =	(CrcHi ^ buffer[i]) & 0x000000ff;
            //Log.i(TAG, "CRC " +Integer.toString((bWork & 0xFF),16));
            CrcHi =	bWork & 0x000000ff;
            //Log.i(TAG, "CRC " +Integer.toString((CrcHi & 0xFF),16));
            bWork = (bWork>>4) & 0x000000ff;
            //Log.i(TAG, "CRC " +Integer.toString((bWork & 0xFF),16));
            bWork =	(bWork ^ CrcHi) & 0x000000ff;
            //Log.i(TAG, "CRC " +Integer.toString((bWork & 0xFF),16));
            CrcHi =	CrcLo & 0x000000ff;
            //Log.i(TAG, "CRC " +Integer.toString((CrcHi & 0xFF),16));
            CrcLo =	bWork & 0x000000ff;
            //Log.i(TAG, "CRC " +Integer.toString((CrcLo & 0xFF),16));
            bWork = (((((bWork<<8))|bWork)<<4)>>8) & 0x000000ff;
            //Log.i(TAG, "CRC " +Integer.toString((bWork & 0xFF),16));
            bTemp = bWork & 0x000000ff;
            //Log.i(TAG, "CRC " +Integer.toString((bTemp & 0xFF),16));
            bWork = (((((bWork<<8))|bWork)<<1)>>8) & 0x000000ff;
            //Log.i(TAG, "CRC " +Integer.toString((bWork & 0xFF),16));
            bWork = (bWork & 0x1f) & 0x000000ff;
            //Log.i(TAG, "CRC " +Integer.toString((bWork & 0xFF),16));
            CrcHi = (CrcHi ^ bWork) & 0x000000ff;
            //Log.i(TAG, "CRC " +Integer.toString((CrcHi & 0xFF),16));
            bWork = (bTemp & 0xf0) & 0x000000ff;
            //Log.i(TAG, "CRC " +Integer.toString((bWork & 0xFF),16));
            CrcHi = (CrcHi ^ bWork) & 0x000000ff;
            //Log.i(TAG, "CRC " +Integer.toString((CrcHi & 0xFF),16));
            bTemp = (((((bTemp<<8))|bTemp)<<1)>>8) & 0x000000ff;
            //Log.i(TAG, "CRC " +Integer.toString((bTemp & 0xFF),16));
            bTemp = (bTemp & 0xe0) & 0x000000ff;
            //Log.i(TAG, "CRC " +Integer.toString((bTemp & 0xFF),16));
            CrcLo = (CrcLo ^ bTemp) & 0x000000ff;
            //Log.i(TAG, "CRC " +Integer.toString((CrcLo & 0xFF),16));

            //Log.i(TAG, "------------");

            i++;
        }
        crc[0] = (byte)(CrcLo&0x000000ff);
        crc[1] = (byte)(CrcHi&0x000000ff);

        return crc;
    }


    // methods for socket

    /**
     * Read the data from server.
     * @param choice type of reading
     */
    private void readData(int choice)
    {
        switch ( choice )
        {
            case 1:
                //iStartMsg.Zero();
                iStartMsg = new byte[1];
                iECGSocket.read( iStartMsg );
                break;
            case 2:
                //iCrc.Zero();
                iCrc = new byte[2];
                iECGSocket.read( iCrc );
                break;
            case 3:
                iTempHeader = new byte[3];
                iECGSocket.read( iTempHeader );
                break;
            case 4:
                if (iID == 0)
                {
                    Log.i(TAG, "ECG: ReadData ID=0");
                    //Se è il primo messaggio che legge si aspetta un body di 8 bytes
                    //iBodyFirstMsg.Zero();

                    iBodyFirstMsg = new byte[KFirstMessageLengthECG];
                    iECGSocket.read( iBodyFirstMsg );
                } else if (iIsLastMessage) {
                    //Se l'ultimo pacchetto da ricevere ha dimensione < KMaximumMessageLengthECG
                    //deve passare alla read una porzione di buffer della stessa dimensione
                    //del pacchetto atteso
                    iBody = new byte[iSizeBlock];
                    iECGSocket.read( iBody );
                } else {
                    //iBody.Zero();
                    iBody = new byte[KMaximumMessageLengthECG];
                    iECGSocket.read( iBody );
                }
                break;
        }
    }

    /**
     * Send ACK.
     */
    private void sendData(byte type, byte[] body)
    {
        byte[] header = new byte[4];
        byte[] crc;
        int nMessageLength;

        //Prepara l'header del messaggio da inviare
        header[0] = TTypeMessage.getVal( TTypeMessage.START_MESSAGE );
        header[1] = (byte)iID;
        header[2] = type;
        header[3] = (byte)( header[0] ^ header[1] ^ header[2] );

        //Calcola il CRC sui dati da inviare
        crc = computeCrc( body, body.length );

        //Calcola la lunghezza del messaggio
        nMessageLength = header.length + body.length + crc.length;

        //Instanzia il buffer che deve essere inviato
        if ( iMessageToSend != null )
        {
            iMessageToSend = null;
        }
        iMessageToSend = new byte[nMessageLength];

        System.arraycopy(header,0,iMessageToSend,0,header.length);
        System.arraycopy(body,0,iMessageToSend,header.length,body.length);
        System.arraycopy(crc,0,iMessageToSend,header.length+body.length,crc.length);

        waiting();
        iECGSocket.write( iMessageToSend );
    }


    // methods for checking data received

    /**
     * Check if the data received is startOperation message
     */
    private boolean isStartMsg()
    {
        byte[] strStartMsg = new byte[1];
        strStartMsg[0] = TTypeMessage.getVal( TTypeMessage.START_MESSAGE );
        //Log.i(TAG, "ECG: rec: " +iStartMsg[0]);
        //Log.i(TAG, "ECG: att: " +strStartMsg[0]);
        //return iStartMsg.equals(strStartMsg);
        return (iStartMsg[0] == strStartMsg[0]);
    }

    /**
     * Check the CRC of header
     */
    private boolean checkCRCHeader()
    {
        Log.i(TAG, "ECG: checkCRCHeader");
        // iHeader[0]: contiene lo Start of Header
        // iHeader[1]: contiene il contatore di pacchetto
        // iHeader[2]: contiene il tipo di pacchetto
        // iHeader[3]: contiene il crc dell'Header
        byte testCRC = (byte)(iHeader[0] ^ iHeader[1] ^ iHeader[2]);

        return (testCRC == iHeader[3]);
    }

    /**
     * Check the type of message
     */
    private boolean checkType()
    {
        Log.i(TAG, "ECG: checkType");
        return ( (iHeader[2] == iType) && (iHeader[1] == iID) );
    }

    /**
     * Check the CRC of message's body
     */
    private boolean checkCRCBody()
    {
        Log.i(TAG, "ECG: checkCRCBody");
        if ( iIsLastMessage )
        {
            byte[] crcReceived;
            crcReceived = computeCrc(iBody, iBody.length);
            return ((crcReceived[0]==iCrc[0]) && (crcReceived[1]==iCrc[1]));
        } else {// if ( ConvertToTInt(iLenRead) == 2 )
            byte[] crcReceived;
            //Se è il primo messaggio che riceve, deve controllare iBodyFirstMsg
            if (iID == 0)
            {
                crcReceived = computeCrc(iBodyFirstMsg, iBodyFirstMsg.length);
            } else {
                crcReceived = computeCrc(iBody, iBody.length);
            }

            Log.i(TAG, "ECG: checkCRCBody iBody" +crcReceived[0]);
            Log.i(TAG, "ECG: checkCRCBody iBody" +crcReceived[1]);
            Log.i(TAG, "ECG: checkCRCBody iBody" +iCrc[0]);
            Log.i(TAG, "ECG: checkCRCBody iBody" +iCrc[1]);

            return ((crcReceived[0]==iCrc[0]) && (crcReceived[1]==iCrc[1]));
        }
    }

    /**
     * Aggiorna il risultato della misura e lo notifica alla GUI
     */
    private void makeResultData()
    {
        // we make the timestamp
        int year, month, day, hour, minute, second;
        GregorianCalendar calendar = new GregorianCalendar();
        year = calendar.get(Calendar.YEAR);
        // MONTH begin from 0 to 11, so we need add 1 to use it in the timestamp
        month = calendar.get(Calendar.MONTH) + 1;
        day = calendar.get(Calendar.DAY_OF_MONTH);
        hour = calendar.get(Calendar.HOUR_OF_DAY);
        minute = calendar.get(Calendar.MINUTE);
        second = calendar.get(Calendar.SECOND);

        String ecgFileName = "ecg" + "-" + year + month + day + "-" + hour + minute + second + ".scp";
        HashMap<String,String> tmpVal = new HashMap<>();
        tmpVal.put(GWConst.EGwCode_0G, ecgFileName);  // filename
        if (iBattery > 0)
            tmpVal.put(GWConst.EGwCode_BATTERY, Integer.toString(iBattery)); // livello batteria
        ByteBuffer buf = ByteBuffer.wrap(iEcgBuffer, 128, iEcgBuffer.length -128);
        byte[] iEcgBufferTemp = new byte[iEcgBuffer.length -128];
        buf.get(iEcgBufferTemp);

        Measure m = getMeasure();
        m.setFile(iEcgBufferTemp);
        m.setFileType(XmlManager.ECG_FILE_TYPE);
        m.setMeasures(tmpVal);
        deviceListener.showMeasurementResults(m);
    }

    private void runBTSearcher(){
        switch (iState){
            case EGettingDevice:
                // we have found a device (not necessarily the device that we need)
                // in this case the application is the master into bluetooth communication:
                // when the master has bluetooth off can't communicate with the slave, so
                // the framework asks user to activate bluetooth. We arrive here when the
                // user has activated and a device is found; but the device scheduler don't
                // still know that bluetooth is active and so we must advice it with setting
                // a new state of bluetooth
                //deviceListener.BTActivated();
                // the automatic search, find all available addresses and when we arrive
                // here we must check if the found address is the same of the searched
                // (in the case of search by name we must check if the device of found
                // address has the same name of the searched name)
                switch (iCmdCode) {
                    case ECmdConnByAddr:
                        String tmpBtDevAddr;
                        tmpBtDevAddr = deviceList.elementAt(currentPos).getAddress();
                        if (tmpBtDevAddr.equals(iBtDevAddr)) {
                            // we pass the position of the selected device into the devices vector
                            selectDevice(deviceList.elementAt(currentPos));
                        } else {
                            // the address is different, so we must wait that the searcher
                            // find another device
                            if (deviceSearchCompleted) {
                                deviceSearchCompleted = false;
                                // the search is completed and we have all the devices into
                                // the vector
                                if (currentPos <= deviceList.size() - 1) {
                                    currentPos++;
                                } else {
                                    // is the last found device and there isn't the one we
                                    // wanted: we restart the device search
                                    iServiceSearcher.startSearchDevices();
                                }
                            } else {
                                // some devices lack into the vector because the search is
                                // not completed
                                if (currentPos <= deviceList.size() - 1) {
                                    // we increment the current position only if there are
                                    // other elements into the vector (otherwise we remain
                                    // on the same position waiting the finding of new devices)
                                    currentPos++;
                                }
                            }
                        }
                        break;
                    case ECmdConnByUser:
                        // the selection done by user is managed in the ui class which
                        // implements BTSearcherEventListener interface, so here arrive
                        // when the selection is already done
                        deviceListener.notifyWaitToUi(ResourceManager.getResource().getString("KConnectingDev"));
                        break;
                }
                break;

            case EGettingService:
                iState = TState.EConnected;
                try {
                    connectToServer();
                } catch (IOException e) {
                    String msg = ResourceManager.getResource().getString("EBtDeviceConnError");
                    deviceListener.notifyError(DeviceListener.CONNECTION_ERROR,msg);
                }
                break;
        }
    }

    private void runBTSocket() {

        final int KTimeOut = 3 /*sec*/ * 1000;
        TimerExpired timerExpired;

        switch(iState) {
            case EConnected:
                Log.i(TAG, "ECG: runBTSocket EConnected");
                deviceListener.setBtMAC(iBtDevAddr);
                if(operationType == OperationType.Pair){
                    iState = TState.EDisconnectingPairing;
                    try {
                        Thread.sleep(250);
                        stop();
                    } catch (InterruptedException e) {
                        String msg = ResourceManager.getResource().getString("EBtDeviceDisconnError");
                        deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR,msg);
                    }
                }  else {
                    if(iCmdCode.equals(DeviceHandler.TCmd.ECmdConnByUser)){
                        Log.i(TAG, "iBTAddress: " + iBtDevAddr);
                        deviceListener.setBtMAC(iBtDevAddr);
                    }
                    deviceListener.notifyToUi(ResourceManager.getResource().getString("KConnSendMeas"));
                    iState = TState.EWaitingStartMessage;
                    //All'inizio mi attendo un messaggio t_Copy
                    //di lunghezza 8 e identificativo = 0.
                    iID = 0;
                    iSize = 8;
                    iType = TTypeMessage.getVal(TTypeMessage.kT_COPY);
                    //Legge un byte per volta per trovare
                    //il primo ottetto di un messaggio
                    readData(1);
                }
                break;

            case EWaitingStartMessage:
                //Log.i(TAG, "ECG: runBTSocket EWaitingStartMessage");

                if ( isStartMsg() ) {
                    Log.i(TAG, "ECG: runBTSocket EWaitingStartMessage trovato");
                    deviceListener.notifyToUi(ResourceManager.getResource().getString("KMeasuring"));
                    //Ha trovato il primo ottetto
                    //Legge l'Header
                    if (dataFound) {
                        Log.i(TAG, "dataFound");
                        //Se è una ritrasmissione blocca il timer
                        //e si mette in attesa di un nuovo file
                        if (timer!=null) {
                            timer.cancel();
                        }

                        //annulla l'operazione resetta i dati e richiede lo startMessage
                        //dataFound = false;
                        reset();
                        iID = 0;
                        iSize = 8;
                        iType = TTypeMessage.getVal(TTypeMessage.kT_COPY);
                        iIsLastMessage = false;
                        iIndex = 0;
                        iSizeBlock = KMaximumMessageLengthECG;

                        if (timer!=null) {
                            timer.cancel();
                        }
                        timerExpired = new TimerExpired();
                        timer = new Timer();
                        timer.schedule(timerExpired,KTimeOut);

                        iState = TState.EWaitingStartMessage;
                        readData(1);
    				/*byte tempHeader = iStartMsg[0];
    				reset();
    				iStartMsg[0] = tempHeader;*/
                    } else {
                        Log.i(TAG, "iFirstPacket = false");
                        iFirstPacket = false;
                        iState = TState.EWaitingHeader;
                        readData(3);
                    }
                } else {
                    //Controlla se è in attesa del primo pacchetto
                    //Entro 3 secondi deve riceverlo
                     if ( iFirstPacket ) {
                        iFirstPacket = false;

                        if (timer!=null) {
                            timer.cancel();
                        }
                        timerExpired = new TimerExpired();
                        timer = new Timer();
                        timer.schedule(timerExpired,KTimeOut);
                    }

                    readData(1); //Continua a leggere
                }
                break;

            case EWaitingHeader:
                Log.i(TAG, "EWaitingHeader");
                //Header ricevuto
                iHeader = new byte[4];
                iHeader[0] = iStartMsg[0];
                System.arraycopy(iTempHeader,0,iHeader,1,3);

                if ( checkCRCHeader() ) { //controlla CRC dell'header
                    if ( checkType() ) { //controlla se iType e iID sono quelli attesi
                        if (iIsLastMessage)	{
                            if (iType == TTypeMessage.getVal(TTypeMessage.kT_DATA_END_TX)) {
                                Log.i(TAG, "EWAITINGHEADER LAST");
                                //Si prepara ad una nuova trasmissione
                                iID = 0;
                                iSize = 8;
                                iType = TTypeMessage.getVal(TTypeMessage.kT_COPY);
                                iIsLastMessage = false;
                                iIndex = 0;
                                iIndexEcgBuffer = 0;
                                //iFileTotalSize = 0;
                                iSizeBlock = KMaximumMessageLengthECG;
                                iRetry = KMaxRetry;

                                //Segnala che un file è stato ricevuto
                                dataFound = true;

                                if (timer!=null) {
                                    timer.cancel();
                                }
                                timerExpired = new TimerExpired();
                                timer = new Timer();
                                timer.schedule(timerExpired,KTimeOut);

                                iState = TState.EWaitingStartMessage;
                                readData(1);
                            } else {
                                //E' in attesa dell'ultimo pacchetto
                                //prima di ricevere kT_DATA_END_TX
                                iState = TState.EWaitingBody;
                                readData(4);
                            }
                        } else {
                            //Non è l'ultimo msg quindi legge il body
                            iState = TState.EWaitingBody;
                            readData(4);
                        }
                    } else { //Type failure
                        //Si è verificato un errore
                        //attendo una ritrasmissione dei pacchetti
                        --iRetry;

                        if ( iRetry > 0 ) {
                            reset();

                            iID = 0;
                            iSize = 8;
                            iType = TTypeMessage.getVal(TTypeMessage.kT_COPY);
                            iIsLastMessage = false;
                            iIndex = 0;
                            iSizeBlock = KMaximumMessageLengthECG;

                            if (timer!=null) {
                                timer.cancel();
                            }
                            timerExpired = new TimerExpired();
                            timer = new Timer();
                            timer.schedule(timerExpired,KTimeOut);

                            iState = TState.EWaitingStartMessage;
                            readData(1);
                        } else {
                            operationDeleted = true;
                            iECGSocket.removeBTSocketEventListener(this);
                            iECGSocket.close();
                            reset();
                            String msg = ResourceManager.getResource().getString("ECommunicationError");
                            deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR,msg);
                        }
                    }
                } else {//CRC failure
                    //Si è verificato un errore
                    //attendo una ritrasmissione dei pacchetti
                    --iRetry;

                    if ( iRetry > 0 ) {
                        reset();

                        iID = 0;
                        iSize = 8;
                        iType = TTypeMessage.getVal(TTypeMessage.kT_COPY);
                        iIsLastMessage = false;
                        iIndex = 0;
                        iSizeBlock = KMaximumMessageLengthECG;

                        if (timer!=null) {
                            timer.cancel();
                        }
                        timerExpired = new TimerExpired();
                        timer = new Timer();
                        timer.schedule(timerExpired,KTimeOut);

                        iState = TState.EWaitingStartMessage;
                        readData(1);
                    } else {
                        operationDeleted = true;
                        iECGSocket.removeBTSocketEventListener(this);
                        iECGSocket.close();
                        reset();
                        String msg = ResourceManager.getResource().getString("ECommunicationError");
                        deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR,msg);
                  }
                }
                break;

            case EWaitingBody:
                //Legge il CRC del body
                Log.i(TAG, "ECG: EWaitingBody");
                iState = TState.ECheckMessage;
                readData(2);
                break;

            case ECheckMessage:
                //Ha ricevuto il CRC lo controlla
                Log.i(TAG, "ECG: ECheckMessage");
                if ( checkCRCBody() ) {
                    Log.i(TAG, "ECG: ECheckMessage OK 1");
                    //I dati ricevuti sono corretti
                    //Ricava i dati necessari per la ricezione del file
                    //Operazione che viene svolta solo alla ricezione del
                    //primo messaggio

                    //Riinizializza il numero di tentativi di ritrasmissione
                    iRetry = KMaxRetry;
                    Log.i(TAG, "ECG: ECheckMessage OK 2");

                    if (iID == 0) {
                        //Viene bloccato il timer
                        Log.i(TAG, "ECG: ECheckMessage OK 3.1");
                        if (timer!=null){
                            timer.cancel();
                        }
                        iFileTotalSize = 0;
                        iBaseAddress = 0;
                        decodeBuffer();
                    } else {
                        //I dati ricevuti vengono bufferizzati
                        try {
                            Log.i(TAG, "ECG: ECheckMessage OK 3.2");
                            Log.i(TAG, "ECG: ECheckMessage OK 3.2 " +iEcgBuffer.length);
                            Log.i(TAG, "ECG: ECheckMessage OK 3.2 " +iIndexEcgBuffer);
                            System.arraycopy(iBody,0,iEcgBuffer,iIndexEcgBuffer,iBody.length);
                            iIndexEcgBuffer = iIndexEcgBuffer + iBody.length;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    //Manda un ACK
                    iState = TState.ESendingData;
                    byte[] bodyAck = new byte[8];

                    iID++;
                    iCurrentAddress = iBaseAddress + iIndex; //Sposta il puntatore al file alla prossima posizione da leggere

                    if ( iIndex + KMaximumMessageLengthECG >= iFileTotalSize ) {
                        //E' l'ultimo blocco che deve ricevere e se l'ha già ricevuto azzera iSizeBlock
                        iSizeBlock = iFileTotalSize - iIndex;
                    }

                    Log.i(TAG, "iSizeBlock = "+iSizeBlock);
                    Log.i(TAG, "iIndex = "+iIndex);

                    if ( iSizeBlock > 0 ) {
                        fillBuffer(bodyAck,1);

                        sendData(TTypeMessage.getVal(TTypeMessage.kT_DATA_ACK), bodyAck);

                        iIndex += iSizeBlock;
                    } else {
                        Log.i(TAG, "Invio DATA_END");
                        iIsLastMessage = true;
                        fillBuffer(bodyAck,0);
                        sendData(TTypeMessage.getVal(TTypeMessage.kT_DATA_END), bodyAck);
                    }
                } else {
                    Log.i(TAG, "ECG: ECheckMessage NOK");
                    //Si è verificato un errore
                    //attendo una ritrasmissione dei pacchetti
                    --iRetry;

                    if ( iRetry > 0 ) {
                        reset();

                        iID = 0;
                        iSize = 8;
                        iType = TTypeMessage.getVal(TTypeMessage.kT_COPY);
                        iIsLastMessage = false;
                        iIndex = 0;
                        iSizeBlock = KMaximumMessageLengthECG;

                        if (timer!=null) {
                            timer.cancel();
                        }
                        timerExpired = new TimerExpired();
                        timer = new Timer();
                        timer.schedule(timerExpired,KTimeOut);

                        iState = TState.EWaitingStartMessage;
                        readData(1);
                    } else {
                        operationDeleted = true;
                        iECGSocket.removeBTSocketEventListener(this);
                        iECGSocket.close();
                        reset();
                        String msg = ResourceManager.getResource().getString("ECommunicationError");
                        deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR,msg);
                    }
                }
                break;

            case ESendingData:
                Log.i(TAG, "ECG: ESendingData");
                //Controlla se è stato inviato l'ultimo messaggio DATA_END
                if ( iIsLastMessage ) {
                    //Setta i parametri che si attende
                    //per la correttezza del prossimo messaggio
                    //Si mette in attesa dell'ultimo messaggio
                    iSize = 8;
                    iType = TTypeMessage.getVal(TTypeMessage.kT_DATA_END_TX);
                    iState = TState.EWaitingStartMessage;
                    readData(1);
                } else {
                    //E' stato inviato un ACK. Setta i parametri che si attende
                    //per la correttezza del prossimo messaggio
                    //Si mette in attesa del prossimo messaggio
                    iSize = iSizeBlock;
                    iType = TTypeMessage.getVal(TTypeMessage.kT_DATA_BLK);
                    iState = TState.EWaitingStartMessage;

                    readData(1);

                    //Controlla se è l'ultimo pacchetto
                    if (iSize < KMaximumMessageLengthECG) {
                        iIsLastMessage = true;
                    }
                }
                break;

            case EDisconnectingOK:
                Log.i(TAG, "ECG: runBTSocket EDisconnectingOK");
                break;

            case EDisconnectingPairing:
                Log.i(TAG, "ECG: runBTSocket EDisconnectingPairing");
                //Pairing eseguito con successo. Salva il BT MAC
                iECGSocket.removeBTSocketEventListener(this);
                deviceListener.configReady(ResourceManager.getResource().getString("KPairingMsgDone"));
                deviceListener.setBtMAC(iBtDevAddr);
                currentPos = 0;
                break;

            case EDisconnecting:
                Log.i(TAG, "EDisconnecting in runBTSocket");
                if (operationDeleted) {
                    //Chiusura su procedura di errore
                    iECGSocket.removeBTSocketEventListener(this);
                    iECGSocket.close();
                    iState = TState.EWaitingToGetDevice;
                }
                break;
        }
    }

    private void waiting(){
        long t0, t1;
        t0 = System.currentTimeMillis();

        do {
            t1 = System.currentTimeMillis();
        } while ((t1-t0)< 200);
    }

    private class TimerExpired extends TimerTask {
        @Override
        public void run(){
            Log.i(TAG, "ECG: timer scaduto");
            if ( iEcgBuffer == null ) {
                //Si disconnette
                disconnectProtocolError();
            } else {
                //Nel buffer è presente un Ecg
                //Chiude la connessione

                //Controlla se il file è stato letto completamente
                //altrimenti chiude la connessione segnalando che non ci sono dati
                if (dataFound) {
                    iState = TState.EDisconnectingOK;
                    try {
                        stop();
                    } catch (Exception e) {
                        String msg = ResourceManager.getResource().getString("EBtDeviceDisconnError");
                        deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR, msg);
                    }
                } else {
                    iState = TState.EDisconnecting;
                    operationDeleted = true;
                    String msg = ResourceManager.getResource().getString("ECommunicationError");
                    deviceListener.notifyError(DeviceListener.COMMUNICATION_ERROR,msg);
                    runBTSocket();
                    reset();
                }
            }
        }
    }
}
