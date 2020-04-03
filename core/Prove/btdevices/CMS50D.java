package com.ti.app.telemed.core.btdevices;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.BTSearcher;
import com.ti.app.telemed.core.btmodule.BTSearcherEventListener;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.UUID;
import java.util.Vector;

import com.contec.cms50dj_jar.DeviceCommand;
import com.contec.cms50dj_jar.DeviceData50DJ_Jar;
import com.contec.cms50dj_jar.DeviceDataPedometerJar;
import com.contec.cms50dj_jar.DevicePackManager;

public class CMS50D extends DeviceHandler implements BTSearcherEventListener {

    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	private BTSearcher iServiceSearcher;

	private DeviceCommunicationThread commThread = null;

    private Vector<BluetoothDevice> deviceList = new Vector<>();
    private BluetoothDevice selectedDevice;

    private double iSpO2Med,iHRMed;
    private int	iSpO2Min,iSpO2Max;
    private int	iHRMin,iHRMax;

    private int	iEventSpO289; // + 20 sec < 89 %
    private int	iEventSpO289Count;
    private int	iEventBradi; // HR<40
    private int	iEventTachi; // HR>120

    private int	iT90; //tempo SpO2<90%
    private int	iT89; //tempo SpO2<89%
    private int	iT88; //tempo SpO2<88%
    private int	iT87; //tempo SpO2<87%
    private int	iT40; //tempo HR<40 bpm
    private int	iT120; //tempo HR>120 bpm

    private int	iAnalysisTime;

    private Vector<OxyElem> oxyQueue;

    private byte[] oxyStream;

	private static final String TAG = "CMS50D";

	private static final int NUMBER_OF_MEASUREMENTS = 300; // = 5 min
	private static final int BASE_OXY_STREAM_LENGTH = 189; //



	public CMS50D(DeviceListener listener, UserDevice ud) {
        super(listener, ud);

        iServiceSearcher = new BTSearcher();
        initVar();
	}
	
	private void initVar() {
		oxyQueue = new Vector<>(NUMBER_OF_MEASUREMENTS);
		
		ByteBuffer tmpStream = ByteBuffer.allocate(BASE_OXY_STREAM_LENGTH);
		tmpStream.put(67, (byte)0x0A); //STEP_OXY
		tmpStream.put(68, (byte)0x04);
		tmpStream.put(69, (byte)0x20); //FL_TEST
		oxyStream = tmpStream.array();
		
	    iSpO2Min = 1024;
	    iSpO2Max = 0; 
	    iSpO2Med = 0.0;
		iHRMin = 1024;
		iHRMax = 0;
		iHRMed = 0.0;
		iEventSpO289 = iEventSpO289Count = 0; 
		iEventBradi = iEventTachi = 0; 
		iT90 = iT89 = iT88 = iT87 = 0; 
		iT40 = iT120 = 0;
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

        deviceList.clear();
        iServiceSearcher.clearBTSearcherEventListener();
        iServiceSearcher.addBTSearcherEventListener(this);
        if (iCmdCode == TCmd.ECmdConnByUser && iBTSearchListener != null)
            iServiceSearcher.addBTSearcherEventListener(iBTSearchListener);
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
        Log.d(TAG, "selectDevice: addr=" + bd.getAddress());
        iServiceSearcher.stopSearchDevices();
        iBtDevAddr = bd.getAddress();
        iBtDevAddr = selectedDevice.getAddress();

        if (commThread != null) {
            commThread.cancel();
            commThread = null;
        }
        commThread = new DeviceCommunicationThread(bd);
        commThread.run();
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
                int i;
                for (i = 0; i < deviceList.size(); i++)
                    if (deviceList.elementAt(i).getAddress().equals(d.getAddress()))
                        break;
                if (i >= deviceList.size()) {
                    deviceList.addElement(d);
                    if (iBTSearchListener != null)
                        iBTSearchListener.deviceDiscovered(deviceList);
                }
                break;
        }
    }

    @Override
    public void deviceSearchCompleted() {
        if (iCmdCode == TCmd.ECmdConnByUser && iBTSearchListener != null) {
            Log.d(TAG, "BT scan completed");
            iBTSearchListener.deviceSearchCompleted();
            stop();
        } else if (iCmdCode == TCmd.ECmdConnByAddr) {
            Log.d(TAG, "Restarting BT Scan...");
            iServiceSearcher.startSearchDevices();
        }
    }

    private void reset() {
        iState = TState.EWaitingToGetDevice;
        initVar();
    }

    private void stop() {
        iServiceSearcher.stopSearchDevices();
        iServiceSearcher.clearBTSearcherEventListener();
        iServiceSearcher.close();
        if (commThread != null) {
            commThread.cancel();
            commThread = null;
        }

        if (iState == TState.EDisconnectingPairing) {
            // TODO
        } else if (iState == TState.EDisconnectingOK) {
            makeResultData();
        }
        reset();
    }

    private class DeviceCommunicationThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private boolean stop;

        DeviceCommunicationThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.d(TAG,e.toString());
            }
            mmSocket = tmp;
        }

        public void run() {
            if (mmSocket == null) {
                deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
                CMS50D.this.stop();
                return;
            }
            // Make a connection to the BluetoothSocket
            try {
                // Blocking call. Will only return on a successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                Log.e(TAG, "DeviceCommunicationThread connecton error:", e);
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                deviceListener.notifyError(DeviceListener.CONNECTION_ERROR, ResourceManager.getResource().getString("EBtDeviceConnError"));
                CMS50D.this.stop();
                return;
            }
            while (!stop) {
                // TODO
            }
        }

        public void cancel() {
            try {
                stop = true;
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }








	private void addNewSample(int aSpO2, int aHR) {
		
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
			if (iEventSpO289Count == 20) 
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
		
		oxyQueue.add(new OxyElem(aSpO2,aHR));
	}
	
	private void calcFinalData() {

		int hrTot=0, spO2Tot=0, sampleCount;
		OxyElem elem;	
		
		sampleCount = oxyQueue.size();
		
		//iOxyStream = iOxyStream->ReAlloc(BASE_OXY_STREAM_LENGTH + SampleCount*2);	
		ByteBuffer tmpStream = ByteBuffer.allocate(BASE_OXY_STREAM_LENGTH + sampleCount*2);
		tmpStream.put(oxyStream);
		oxyStream = tmpStream.array();						
		
		// Sample num
		oxyStream[187] = (byte)((sampleCount>>8) & 0xFF);
		oxyStream[188] = (byte)((sampleCount) & 0xFF);
			
		for (int i=0; i<oxyQueue.size(); i++) {
			elem = oxyQueue.get(i);
			
			hrTot += elem.getIFreq();
			spO2Tot += elem.getISat();

			oxyStream[BASE_OXY_STREAM_LENGTH + (i*2)] =	(byte)(elem.getISat() & 0xFF);		//SpO2
			oxyStream[BASE_OXY_STREAM_LENGTH + (i*2) + 1] = (byte)(elem.getIFreq() & 0xFF);	//HR
		}
		
		iAnalysisTime = sampleCount; // tempo in secondi
		
		iSpO2Med = ((double)spO2Tot/(double)sampleCount);
		iHRMed = ((double)hrTot/(double)sampleCount);
		
	}
	
	private void makeResultData() {
			
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
        String durata = Integer.toString(iAnalysisTime);
        //String durata = String.format(Locale.ITALY,"%02d%02d%02d", tDurata.getHh(), tDurata.getMm(), tDurata.getSs());

        //Recording Time & Analysis Time
        oxyStream[84] = (byte)tDurata.getHh();
        oxyStream[85] = (byte)tDurata.getMm();
        oxyStream[86] = (byte)tDurata.getSs();
        oxyStream[87] = (byte)tDurata.getHh();
        oxyStream[88] = (byte)tDurata.getMm();
        oxyStream[89] = (byte)tDurata.getSs();

        //LIVELLO BATTERIA
        int batt;
        if (iLowBattery)
            batt = 20;
        else
            batt = 100;

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

        String oxyFileName = "oxy-"+ year + month + day + "-" + hour + minute + second +".oxy";

        // Creo un istanza di Misura del tipo PR
        HashMap<String,String> tmpVal = new HashMap<>();
        tmpVal.put(GWConst.EGwCode_07, df.format(iSpO2Med).replace ('.', ','));  // O2 Med
        tmpVal.put(GWConst.EGwCode_1B, df.format(iSpO2Min).replace ('.', ','));  // O2 Min
        tmpVal.put(GWConst.EGwCode_1D, df.format(iSpO2Max).replace ('.', ','));  // O2 Max
        tmpVal.put(GWConst.EGwCode_1F, "0");
        tmpVal.put(GWConst.EGwCode_0F, df.format(iHRMed).replace ('.', ','));  // HR Med
        tmpVal.put(GWConst.EGwCode_1A, df.format(iHRMin).replace ('.', ','));  // HR Min
        tmpVal.put(GWConst.EGwCode_1C, df.format(iHRMax).replace ('.', ','));  // HR Max
        tmpVal.put(GWConst.EGwCode_1E, "0");
        tmpVal.put(GWConst.EGwCode_1G, durata);
        tmpVal.put(GWConst.EGwCode_1H, oxyFileName);  // filename
        tmpVal.put(GWConst.EGwCode_BATTERY, Integer.toString(batt)); // livello batteria

        Measure m = getMeasure();
        m.setFile(oxyStream);
        m.setFileType(XmlManager.MIR_OXY_FILE_TYPE);
        m.setMeasures(tmpVal);
        deviceListener.showMeasurementResults(m);
	}
	
	private Time calcTime(int aSec) {
		int hh = aSec/3600;
		int mm = (aSec%3600)/60;
		int ss = (aSec%3600)%60;
		return new Time(hh, mm, ss);
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
		
		public Time(int hh, int mm, int ss) {
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
}
