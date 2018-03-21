package com.ti.app.telemed.core.btdevices;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.ParcelUuid;
import android.util.Log;

import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.BTSearcherEventListener;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.btmodule.DeviceListener;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.xmlmodule.XmlManager;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.util.Util;

import static com.ti.app.telemed.core.btmodule.DeviceListener.CONNECTION_ERROR;

public class IEMECG extends DeviceHandler {

    private static final String TAG = "IEMECG";

    private BluetoothDevice ecgDevice;
	private int iBattery;

	private final static UUID SERIAL_PORT_UUID = UUID.fromString("00001234-0000-1000-8000-00805F9B34FB");
    private final static UUID CLIENT_UUID1 = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    private final static UUID CLIENT_UUID2 = UUID.fromString("00001103-0000-1000-8000-00805f9b34fb");
    private final static UUID CLIENT_UUID3 = UUID.fromString("00000000-0000-1000-8000-00805f9b34fb");

	private static final String NAME = "IEMReceiver";
	
	private BluetoothAdapter btAdapter;
    private BluetoothServerSocket mmServerSocket = null;
    private BluetoothSocket mmSocket = null;

	private void sleep(long m) {
		try {
			Thread.sleep(m);
		} catch (Exception e) {
		    // TODO
		}
	}
	
	private static int toInt(byte[] bytes, int offset) {
	  int ret = 0;
	  for (int i=0; i<4 && i+offset<bytes.length; i++) {
	    ret <<= 8;
	    ret |= (int)bytes[i] & 0xFF;
	  }
	  return ret;
	}
	
	private class ECGRecord {
		private long dateSeconds;
		private Date date;
		private byte durationPre;
		private byte durationPost;
		private byte type;
		private byte[] data;
		
		long getDateSeconds() {
			return dateSeconds;
		}
		void setDateSeconds(long dateSeconds) {
			this.dateSeconds = dateSeconds;
		}
		Date getDate() {
			return date;
		}
		public void setDate(Date date) {
			this.date = date;
		}
		byte getDurationPre() {
			return durationPre;
		}
		void setDurationPre(byte durationPre) {
			this.durationPre = durationPre;
		}
		byte getDurationPost() {
			return durationPost;
		}
		void setDurationPost(byte durationPost) {
			this.durationPost = durationPost;
		}
		int getDurationTotal() {
			return durationPost+durationPre;
		}		
		byte getType() {
			return type;
		}
		void setType(byte type) {
			this.type = type;
		}
		byte[] getData() {
			return data;
		}
		void setData(byte[] data) {
			this.data = data;
		}
	}
	
	private List<ECGRecord> ecgsList = null;
    
	public IEMECG(DeviceListener listener, UserDevice ud) {
	    super(listener,ud);
		iBattery = -1;
	}

    // methods of DeviceHandler interface
    @Override
    public boolean startOperation(OperationType ot, BTSearcherEventListener btSearchListener) {
        if (!startInit(ot, btSearchListener))
            return false;

        if (ot != OperationType.Measure)
            return false;

        Log.d(TAG, "start() iState=" + iState);
        iState = TState.EGettingDevice;
		deviceListener.notifyToUi(ResourceManager.getResource().getString("KSearchingDev"));

        new Thread(new Runnable() {

            @Override
            public void run() {
                boolean connected = false;
                if (btAdapter == null)
                    btAdapter = BluetoothAdapter.getDefaultAdapter();
                try {

					Log.d(TAG, "Connecting as Server....");
                    mmServerSocket = btAdapter.listenUsingRfcommWithServiceRecord(NAME, SERIAL_PORT_UUID);
                    Log.d(TAG, "ECG-Server: Waiting for incoming connections...");
                    mmSocket = mmServerSocket.accept();

                    ecgDevice = mmSocket.getRemoteDevice();
                    Log.d(TAG, "ECG-Server: Socket accepted");

                    connected = true;
                    //deviceListener.setBtMAC(ecgDevice.getAddress());
                    deviceListener.notifyWaitToUi(ResourceManager.getResource().getString("KMeasuring"));
                    DataInputStream din = new DataInputStream(mmSocket.getInputStream());
                    final DataOutputStream don = new DataOutputStream(mmSocket.getOutputStream());

                    sleep(1000);
                    byte[] buffer = new byte[9];
                    din.readFully(buffer);
                    Log.d(TAG, "RCV: " + Util.toHexString(buffer));

                    sleep(500);

                    // GET NUMBER OF MEASURE
                    don.write(generatePacketCommand3());
                    don.flush();
                    buffer = new byte[1];
                    din.readFully(buffer);
                    Log.d(TAG, "RCV: " + Util.toHexString(buffer));

                    buffer = new byte[25];
                    din.readFully(buffer);
                    Log.d(TAG, "RCV: " + Util.toHexString(buffer));

                    int numberOfMeasure = buffer[3];
                    Log.d(TAG, "numberOfMeasure=" + numberOfMeasure);

                    if (numberOfMeasure > 0) {
                        // GET ECG INFO PACKET N.
                        don.write(generatePacketCommand6());
                        don.flush();

                        buffer = new byte[1];
                        din.readFully(buffer);
                        Log.d(TAG, "RCV: " + Util.toHexString(buffer));

                        ecgsList = new ArrayList<>();

                        for (byte i = 0; i < numberOfMeasure; i++) {
                            buffer = new byte[25];
                            din.readFully(buffer);
                            Log.d(TAG, "RCV: " + Util.toHexString(buffer));
                            int date = toInt(new byte[] { buffer[5], buffer[4], buffer[3], buffer[2] }, 0);

                            Log.d(TAG, "date=" + formatDate(date));
                            Log.d(TAG, "type=" + buffer[6]);
                            Log.d(TAG, "pre=" + buffer[7]);
                            Log.d(TAG, "post=" + buffer[8]);

                            ECGRecord record = new ECGRecord();
                            record.setDateSeconds(date);
                            record.setDate(extractDate(date));

                            record.setDurationPre(buffer[7]);
                            record.setDurationPost(buffer[8]);

                            record.setType(buffer[9]);
                            ecgsList.add(record);
                        }

                        for (byte i = 1; i <= numberOfMeasure; i++) {

                            //deviceListener.notifyWaitToUi(ResourceManager.getResource().getString("KMeasuring") + " (" + i + "/" + numberOfMeasure + ")");

                            sleep(250);

                            // GET ECG N.
                            don.write(generatePacketECG(i));
                            don.flush();

                            buffer = new byte[2];
                            din.readFully(buffer);
                            Log.d(TAG, "ECG-RCV: " + Util.toHexString(buffer));

                            int seconds = ecgsList.get(i-1).getDurationTotal();
                            Log.d(TAG, "ECG-seconds=" + seconds);

                            buffer = new byte[(200*seconds-1)*3+4+4];
                            din.readFully(buffer);
                            //Log.d(TAG, "ECG-RCV: " + Util.toHexString(buffer));
                            int date = toInt(new byte[] { buffer[3], buffer[2], buffer[1], buffer[0] }, 0);
                            Log.d(TAG, "ECG-date=" + date);
                            Log.d(TAG, "ECG-date=" + formatDate(date));

                            //buffer = new byte[(200*seconds-1)*3+4];
                            //din.readFully(buffer);
                            Log.d(TAG, "ECG-RCV data= " + Util.toHexString(buffer, 10));

                            ecgsList.get(i-1).setData(buffer);
                        }

                        // DELETE ALL
                        don.write(generatePacketCommand225());
                        don.flush();
                        buffer = new byte[1];
                        din.readFully(buffer);
                        Log.d(TAG, "RCV: " + Util.toHexString(buffer));

                        // SET DATE
                        don.write(generatePacketCommand41());
                        don.flush();
                        buffer = new byte[1];
                        din.readFully(buffer);
                        Log.d(TAG, "RCV: " + Util.toHexString(buffer));

                        // GET BATTERY
                        don.write(generatePacketCommand51());
                        don.flush();
                        buffer = new byte[1];
                        din.readFully(buffer);
                        Log.d(TAG, "RCV: " + Util.toHexString(buffer));

                        buffer = new byte[25];
                        din.readFully(buffer);
                        Log.d(TAG, "RCV: " + Util.toHexString(buffer));
                        int battery = toInt(new byte[] { buffer[2], buffer[3] }, 0);
                        Log.d(TAG, "RCV battery(mV)=" + battery);
                        iBattery = battery*100/1500;
                        Log.d(TAG, "RCV battery(%)=" + iBattery);
                    }
                    Log.d(TAG, "ECG-done.");

                    // Closing the sockets also closes input and output Stream
                    closeConnection();
                    Log.d(TAG, "ECG-Server: Closed");

                    makeResultData();
                } catch (Exception e) {
                    Log.e(TAG, "ECG-Server: listenUsingRfcommWithServiceRecord: " + e);
					if (connected) {
						deviceListener.notifyError(CONNECTION_ERROR, ResourceManager.getResource().getString(
								"ECommunicationError"));
						closeConnection();
					}
                }
            }

        }).start();
        return true;
    }

    @Override
    public void stopOperation() {
        Log.d(TAG, "stopOperation");
        stop();
    }

    @Override
    public void selectDevice(BluetoothDevice bd){
        Log.d(TAG, "unsupported!");
    }

    @Override
    public void cancelDialog() {
    }
    @Override
    public void confirmDialog() {
    }


    public void stop() {
        Log.d(TAG, "stop() iState=" + iState);
        closeConnection();
    }

    private void closeConnection() {
        if (mmSocket != null) {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG,e.getMessage());
            }
            mmSocket = null;
            Log.d(TAG, "Socket connection closed");
        }

        if(mmServerSocket != null){
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG,e.getMessage());
            }
            mmServerSocket = null;
            Log.d(TAG, "Server Socket closed (Service deregistered)");
        }
    }

    private byte[] generatePacketECG(byte numOfEcg) {

		Log.d(TAG, "generatePacket CMD=4 (Read ECG Data) num=" + numOfEcg);
		
		byte[] packet = new byte[25];
		
		for (int i = 0; i < packet.length; i++) {
			packet[i] = 0;
		}
		
		packet[0] = 2;
		packet[1] = 4;
		packet[2] = numOfEcg;
		
		packet[24] = 3;
		packet[23] = checkSum(packet);
				
		return packet;
	}
	
	private byte[] generatePacketCommand6() {

		Log.d(TAG, "generatePacket CMD=6 (2Read only measurement items)");
		
		byte[] packet = new byte[25];
		
		for (int i = 0; i < packet.length; i++) {
			packet[i] = 0;
		}
		
		packet[0] = 2;
		packet[1] = 6;
		packet[2] = 1;
		
		packet[24] = 3;
		packet[23] = checkSum(packet);
				
		return packet;
	}
	
	private byte[] generatePacketCommand41() {
		Log.d(TAG, "generatePacket CMD=41 (Set Time/Date)");
		byte[] packet = new byte[25];
		for (int i = 0; i < packet.length; i++) {
			packet[i] = 0;
		}
		/*
			4.7.1 Set time/date 
			PC sends <41> or <141> with 
			1. data byte = hour 
			2. data byte = minute 
			3. data byte = second 
			4. data byte = day 
			5. data byte = month 
			6. data byte = year -2000 
		 */
		packet[0] = 2;
		packet[1] = 41;
		int year, month, day, hour, minute, second;
		GregorianCalendar calendar = new GregorianCalendar();
		year = calendar.get(Calendar.YEAR);
		month = calendar.get(Calendar.MONTH) + 1;
		day = calendar.get(Calendar.DAY_OF_MONTH);
		hour = calendar.get(Calendar.HOUR_OF_DAY);
		minute = calendar.get(Calendar.MINUTE);
		second = calendar.get(Calendar.SECOND);
		packet[2] = (byte)hour;
		packet[3] = (byte)minute;
		packet[4] = (byte)second;
		packet[5] = (byte)day;
		packet[6] = (byte)month;
		packet[7] = (byte)(year-2000);
		packet[24] = 3;
		packet[23] = checkSum(packet);
		return packet;
	}
	
	private byte[] generatePacketCommand225() {

		Log.d(TAG, "generatePacket CMD=225 (Erase All)");
		
		byte[] packet = new byte[25];
		
		for (int i = 0; i < packet.length; i++) {
			packet[i] = 0;
		}
		
		packet[0] = 2;
		packet[1] = (byte)225;
		
		packet[24] = 3;
		packet[23] = checkSum(packet);
				
		return packet;
	}
	
	private byte[] generatePacketCommand51() {

		Log.d(TAG, "generatePacket CMD=51 (Battery Voltage)");
		
		byte[] packet = new byte[25];
		
		for (int i = 0; i < packet.length; i++) {
			packet[i] = 0;
		}
		
		packet[0] = 2;
		packet[1] = 51;
		
		packet[24] = 3;
		packet[23] = checkSum(packet);
				
		return packet;
	}
	
	private byte[] generatePacketCommand3() {

		Log.d(TAG, "generatePacket CMD=3 (Read number of measurements)");
		
		byte[] packet = new byte[25];
		
		for (int i = 0; i < packet.length; i++) {
			packet[i] = 0;
		}
		
		packet[0] = 2;
		packet[1] = 3;
		
		packet[24] = 3;
		packet[23] = checkSum(packet);
				
		return packet;
	}

	private String formatDate(long date) {
		SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ITALIAN);
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		return sdf.format(extractDate(date));
	}
	
	private Date extractDate(long date) {
		
		date = date * 1000 + 1230768000000L;
						
		return new Date(date);
	}

	private byte checkSum(byte[] buffer) {
		// ======================================================
		// relevant bytes : 2. - 23.
        int BLOCK_SIZE = 25;
		byte check; // checksum
		check = 0;
		for (int i = 1; i < (BLOCK_SIZE - 2); i++)
		    check += buffer[i];
		return check;
	}

	private void makeResultData() {

		ArrayList<Measure> mesureList = new ArrayList<>();
		
		if (ecgsList == null || ecgsList.size()==0) {
		    deviceListener.notifyError(DeviceListener.NO_MEASURES_FOUND, ResourceManager.getResource().getString(
                    "ENoMeasuresFound"));
            return;
        }
		
		for (int i = 0; i < ecgsList.size(); i++) {
            // we make the timestamp
			int year, month, day, hour, minute, second;
			GregorianCalendar calendar = new GregorianCalendar();
			calendar.setTime(ecgsList.get(i).getDate());
			calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
			
			year = calendar.get(Calendar.YEAR);
			// MONTH begin from 0 to 11, so we need add 1 to use it in the
			// timestamp
			month = calendar.get(Calendar.MONTH) + 1;
			day = calendar.get(Calendar.DAY_OF_MONTH);
			hour = calendar.get(Calendar.HOUR_OF_DAY);
			minute = calendar.get(Calendar.MINUTE);
			second = calendar.get(Calendar.SECOND);

            String ecgFileName = "ecg" + "-" + year + month + day + "-" + hour + minute
                    + second + ".iem";
            Log.d(TAG,"ecgFileName="+ecgFileName);
            HashMap<String,String> tmpVal = new HashMap<>();
            tmpVal.put(GWConst.EGwCode_0I, ecgFileName);  // filename
            if (iBattery > 0)
                tmpVal.put(GWConst.EGwCode_BATTERY, Integer.toString(iBattery)); // livello batteria
            if (ecgsList.get(i).getType() == ((byte)101) || ecgsList.get(i).getType() == ((byte)100))
                tmpVal.put(GWConst.EGwCode_2A, "L"); // Tipo E o L
            else
                tmpVal.put(GWConst.EGwCode_2A, "E"); // Tipo E o L
            tmpVal.put(GWConst.EGwCode_2B, Byte.toString(ecgsList.get(i).getDurationPre()));
            tmpVal.put(GWConst.EGwCode_2C, Byte.toString(ecgsList.get(i).getDurationPost()));

            Measure m = getMeasure();
            m.setBtAddress(ecgDevice.getAddress());
            m.setFile(ecgsList.get(i).getData());
            m.setFileType(XmlManager.ECG_FILE_TYPE);
            m.setMeasures(tmpVal);

            mesureList.add(m);

            deviceListener.showMeasurementResults(m);
		}

        deviceListener.showMeasurementResults(mesureList.get(mesureList.size()-1));
	}
}