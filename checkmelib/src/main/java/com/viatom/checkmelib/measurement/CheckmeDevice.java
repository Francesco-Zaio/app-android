package com.viatom.checkmelib.measurement;

import org.json.JSONException;
import org.json.JSONObject;

import com.viatom.checkmelib.utils.LogUtils;


public class CheckmeDevice {

	private static final String MODE_HOSPITAL = "MODE_HOSPITAL";
	private static final String MODE_HOME = "MODE_HOME";
	private static final String MODE_MULTI = "MODE_MULTI";

	private String region = "";
	private String model = "";
	private String hardware = "";
	private String software = "0";
	private int language = 0;
	private String curLanguage = "";
	private String sn = "";
	private String spcpVer = "";
	private String fileVer = "";
	private String name = "";
	private String mode = "";
	private boolean available = false;

	public CheckmeDevice(JSONObject jsonObject) throws JSONException{
		LogUtils.d("Device Info: " + jsonObject.toString());
		region = jsonObject.getString("Region");
		model = jsonObject.getString("Model");
		hardware = jsonObject.getString("HardwareVer");
		int temVersion = jsonObject.getInt("SoftwareVer");
		software = temVersion / 10000 + "." + (temVersion%10000 / 100) + "." + (temVersion%100);
		language = jsonObject.getInt("LanguageVer");
		try {
			curLanguage = jsonObject.getString("CurLanguage");
			mode = jsonObject.getString("Application");
			if (mode == null || mode.equals("")) {
				mode = MODE_HOME;
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
		spcpVer = jsonObject.getString("SPCPVer");
		fileVer = jsonObject.getString("FileVer");
		sn = jsonObject.getString("SN");
	}

	public CheckmeDevice(String region, String model, String hardware,
						 String software, int language, String curLanguage) {
		super();
		this.region = region;
		this.model = model;
		this.hardware = hardware;
		this.software = software;
		this.language = language;
		this.curLanguage = curLanguage;
	}

	public CheckmeDevice(String name, boolean available) {
		super();
		this.name = name;
		this.available = available;
	}

	public static CheckmeDevice decodeCheckmeDevice(String checkmeInfo) {
		if (checkmeInfo==null || checkmeInfo.length()==0) {
			return null;
		}
		LogUtils.d(checkmeInfo);
		CheckmeDevice checkmeDevice;
		try {
			checkmeDevice = new CheckmeDevice(new JSONObject(checkmeInfo));
		} catch (JSONException e) {
			e.printStackTrace();
			LogUtils.d("解析数据异常");
			return null;
		}
		return checkmeDevice;
	}

	public String getRegion() {
		return region;
	}

	public String getModel() {
		return model;
	}

	public String getHardware() {
		return hardware;
	}

	public String getSoftware() {
		return software;
	}

	public int getLanguage() {
		return language;
	}

	public String getCurLanguage() {
		return curLanguage;
	}

	public String getSn() {
		return sn;
	}

	public String getSpcpVer() {
		return spcpVer;
	}

	public String getFileVer() {
		return fileVer;
	}

	public String getName() {
		return name;
	}

	public boolean isAvailable() {
		return available;
	}

	public void setAvailable(boolean available) {
		this.available = available;
	}

	public String getMode() {
		return mode;
	}

}
