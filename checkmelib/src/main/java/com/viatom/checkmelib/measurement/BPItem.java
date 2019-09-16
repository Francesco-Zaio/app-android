package com.viatom.checkmelib.measurement;

import com.viatom.checkmelib.utils.LogUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;


/**
 * Temperature item, used for thermometer measurement
 * @author zouhao
 */
public class BPItem implements CommonItem{

	// Origin data read from files
	private byte[] dataBuf;
	// Measuring date
	private Date date;
	// Systolic blood pressure
	private int systolic;
	// Distolic blood pressure
	private short diastolic;
	// Pulse Rate value
	private int pr;

	public BPItem(byte[] buf){
		
		if(buf.length!=MeasurementConstant.BP_ITEM_LENGHT){
			LogUtils.d("BP buf length error");
			return;
		}
		dataBuf = buf;
		Calendar calendar = new GregorianCalendar((buf[0] & 0xFF)
				+ ((buf[1] & 0xFF) << 8), (buf[2] & 0xFF) - 1, buf[3] & 0xFF,
				buf[4] & 0xFF, buf[5] & 0xFF, buf[6] & 0xFF);
		date = calendar.getTime();
		diastolic = buf[9];
		systolic = (buf[7] & 0xFF) + ((buf[8] & 0xFF) << 8);
		pr = buf[10];
	}

	public static ArrayList<BPItem> getBpItemList(byte[] buf) {
		if (buf == null || buf.length % MeasurementConstant.BP_ITEM_LENGHT != 0) {
			LogUtils.d("BP item buff length err!");
			return null;
		}

		int itemNum = buf.length / MeasurementConstant.BP_ITEM_LENGHT;
		ArrayList<BPItem> tempItems = new ArrayList<>();
		for (int i = 0; i < itemNum; i++) {
			byte[] tempBuf = new byte[MeasurementConstant.BP_ITEM_LENGHT];
			System.arraycopy(buf, i * MeasurementConstant.BP_ITEM_LENGHT, tempBuf, 0, MeasurementConstant.BP_ITEM_LENGHT);
			tempItems.add(new BPItem(tempBuf));
		}
		return tempItems;
	}

	public Date getDate() {
		return date;
	}
	public int getSystolic() {
		return systolic;
	}
	public int getDiastolic() {
		return diastolic;
	}
	public int getPulseRate() {
		return pr;
	}
	
	public byte[] getDataBuf() {
		return dataBuf;
	}
	
	@Override
	public boolean isDownloaded() {
		return true;
	}
}
