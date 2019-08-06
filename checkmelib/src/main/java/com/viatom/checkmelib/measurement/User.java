package com.viatom.checkmelib.measurement;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import com.viatom.checkmelib.utils.LogUtils;


public class User{
	
	// User information
	private UserInfo userInfo;
	
	public User() {
		userInfo = new UserInfo();
	}
	
	public User(UserInfo userInfo) {
		super();
		this.userInfo = userInfo;
	}
	
	public UserInfo getUserInfo() {
		return userInfo;
	}

	public static ArrayList<User> getUserList(byte[] buf) {
		if (buf == null || buf.length % MeasurementConstant.USER_ITEM_LENGTH != 0) {
			LogUtils.d("user buff length err!");
			return null;
		}
		int itemNum = buf.length / MeasurementConstant.USER_ITEM_LENGTH;
		ArrayList<User> userList = new ArrayList<User>();
		for (int i = 0; i < itemNum; i++) {
			byte[] tempBuf = new byte[MeasurementConstant.USER_ITEM_LENGTH];
			System.arraycopy(buf, i * MeasurementConstant.USER_ITEM_LENGTH, tempBuf, 0, MeasurementConstant.USER_ITEM_LENGTH);
			userList.add(new User(new User.UserInfo(tempBuf)));
		}
		return userList;
	}

	public static class UserInfo{
		
		private byte id, ico, gender;
		private char[] name = new char[16];
		private Date birthDate;
		private int weight, height;
		
		public UserInfo() {
			id = 1;
			ico = 1;
		}
		
		public UserInfo(byte[] buf){
			if (buf == null || buf.length != MeasurementConstant.USER_ITEM_LENGTH) {
				LogUtils.d("user buf length err");
				return;
			}
			id = buf[0];
			for (int i = 0; i < name.length; i++)
				name[i] = (char)buf[i + 1];
			ico = buf[17];
			gender = buf[18];
			weight = (buf[23]&0xFF)+((buf[24]&0xFF)<<8);
			height = ((buf[25]&0xFF)+((buf[26]&0xFF)<<8))/10;
			
			Calendar calendar = new GregorianCalendar((buf[19] & 0xFF) 
					+ (((buf[20] & 0xFF)<<8)), (buf[21] & 0xFF) - 1
					, buf[22] & 0xFF);
			birthDate = calendar.getTime();
		}
		
		public byte getID() {
			return id;
		}
		public byte getICO() {
			return ico;
		}
		public byte getGender() {
			return gender;
		}
		public int getWeight() {
			return weight;
		}
		public int getHeight() {
			return height;
		}
		public Date getBirthDate() {
			return birthDate;
		}

		public String getName() {
			if (name == null) {
				return "";
			}
			return String.valueOf(name);
		}
	}
}
