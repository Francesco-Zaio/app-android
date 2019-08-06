package com.viatom.checkmelib.measurement;

import com.viatom.checkmelib.utils.LogUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * ECG item, every one contains an ECGInnerItem
 * @author zouhao
 */
public class ECGItem implements Serializable,CommonItem{
	
	private static final long serialVersionUID = -2885920788431684715L;
	
	// Origin data read from files
	private byte[] dataBuf;
	// Measuring date
	private Date date;
	// Whether contains voice
	private byte voiceFlag;
	// Measuring mode
	private byte measuringMode;
	// Image result, smile or cry
	private byte imgResult;
	// The internal item, contains ECG data
	private ECGInnerItem innerItem;
	// Whether the internal item has been downloaded
	boolean downloaded;

	public ECGItem(byte[] buf){
		if(buf.length != MeasurementConstant.ECGLIST_ITEM_LENGTH)
			return;
		dataBuf = buf;
		
		Calendar calendar = new GregorianCalendar((buf[0] & 0xFF)
				+ ((buf[1] & 0xFF) << 8)
		, (buf[2] & 0xFF) - 1, buf[3] & 0xFF,
		buf[4] & 0xFF, buf[5] & 0xFF, buf[6] & 0xFF);
		date = calendar.getTime();
		measuringMode = ((byte) (buf[7] & 0xFF));
		imgResult = buf[8];
		voiceFlag = buf[9];
	}

	public static ArrayList<ECGItem> getEcgItemList(byte[] buf) {
		if (buf == null || buf.length % MeasurementConstant.ECGLIST_ITEM_LENGTH != 0) {
			LogUtils.d("ecg item buff length err!");
			return null;
		}

		int itemNum = buf.length / MeasurementConstant.ECGLIST_ITEM_LENGTH;
		ArrayList<ECGItem> ecgItems = new ArrayList<ECGItem>();
		for (int i = 0; i < itemNum; i++) {
			byte[] tempBuf = new byte[MeasurementConstant.ECGLIST_ITEM_LENGTH];
			System.arraycopy(buf, i * MeasurementConstant.ECGLIST_ITEM_LENGTH, tempBuf, 0, MeasurementConstant.ECGLIST_ITEM_LENGTH);
			ecgItems.add(new ECGItem(tempBuf));
		}
		return ecgItems;
	}
	
	public Date getDate() {
		return date;
	}

	public byte getMeasuringMode() {
		return measuringMode;
	}

	public byte getVoiceFlag() {
		return voiceFlag;
	}
	
	public byte getImgResult() {
		return imgResult;
	}


	public ECGInnerItem getInnerItem() {
		return innerItem;
	}

	public boolean isDownloaded() {
		return downloaded;
	}

	public byte[] getDataBuf() {
		return dataBuf;
	}
	
	public void setDownloaded(boolean downloadState) {
		this.downloaded = downloadState;
	}

	public void setInnerItem(ECGInnerItem innerItem) {
		this.innerItem = innerItem;
	}
	
}
