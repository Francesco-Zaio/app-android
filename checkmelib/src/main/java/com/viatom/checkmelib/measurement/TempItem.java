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
public class TempItem implements CommonItem{
	
	// Origin data read from files
	private byte[] dataBuf;
	// Measuring date
	private Date date;
	// Measuring mode, body or thing
	private byte measuringMode;
	// Measuring result, in Celsius
	private float result;
	// Image result, smile or cry
	private byte imgResult;
	
	public TempItem(byte[] buf){
		
		if(buf.length!=MeasurementConstant.TEMP_ITEM_LENGHT){
			LogUtils.d("Temp buf length error");
			return;
		}
		dataBuf = buf;
		Calendar calendar = new GregorianCalendar((buf[0] & 0xFF)
				+ ((buf[1] & 0xFF) << 8), (buf[2] & 0xFF) - 1, buf[3] & 0xFF,
				buf[4] & 0xFF, buf[5] & 0xFF, buf[6] & 0xFF);
		date = calendar.getTime();
		measuringMode = buf[7];
		result = (float) ((float) ((buf[8] & 0xFF) + ((buf[9] & 0xFF) << 8))) / 10;
		imgResult = buf[10];
	}

	public static ArrayList<TempItem> getTempItemList(byte[] buf) {
		ArrayList<TempItem> tempItems = new ArrayList<>();
		if (buf == null || buf.length % MeasurementConstant.TEMP_ITEM_LENGHT != 0) {
			LogUtils.d("temp item buff length err!");
			return tempItems;
		}

		int itemNum = buf.length / MeasurementConstant.TEMP_ITEM_LENGHT;
		for (int i = 0; i < itemNum; i++) {
			byte[] tempBuf = new byte[MeasurementConstant.TEMP_ITEM_LENGHT];
			System.arraycopy(buf, i * MeasurementConstant.TEMP_ITEM_LENGHT, tempBuf, 0, MeasurementConstant.TEMP_ITEM_LENGHT);
			tempItems.add(new TempItem(tempBuf));
		}
		return tempItems;
	}

	public Date getDate() {
		return date;
	}
	public byte getMeasuringMode() {
		return measuringMode;
	}
	public float getResult() {
		return result;
	}
	public byte getImgResult() {
		return imgResult;
	}
	
	public byte[] getDataBuf() {
		return dataBuf;
	}
	
	@Override
	public boolean isDownloaded() {
		return true;
	}
}
