package com.ti.app.telemed.core.common;

public class UserDevice implements Cloneable {
	private Integer id;
	private String idUser = "";
	private String measure = "";
	private String btAddress = "";
	private boolean active;
	private Device device;

	public UserDevice() {
		active = false;
	}

	public Integer getId() {
		return id;
	}
	public void setId(Integer id) {
		this.id = id;
	}

	public String getIdUser() {
		return idUser;
	}
	public void setIdUser(String idUser) {
		this.idUser = idUser;
	}

	public String getMeasure() {
		return measure;
	}
	public void setMeasure(String measure) {
		this.measure = measure;
	}

	public String getBtAddress() {
		return btAddress;
	}
	public void setBtAddress(String btAddress) {
		this.btAddress = btAddress;
	}

	public boolean isActive() {
		return active;
	}
	public void setActive(boolean active) {
		this.active = active;
	}

	public Device getDevice() {
		return device;
	}
	public void setDevice(Device device) {
		this.device = device;
	}

	@Override
	public Object clone() {
		try {
			super.clone();
			UserDevice newDevice = new UserDevice();
			newDevice.setId(this.id);
			newDevice.setBtAddress(this.btAddress);
			newDevice.setMeasure(this.measure);
			newDevice.setIdUser(this.idUser);
			newDevice.setDevice(this.device);
			return newDevice;
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public String toString() {
		return device.getDescription();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof UserDevice && equalsMeasureModel(obj);
	}

	public boolean equalsMeasureModel(Object obj) {
		if (obj instanceof UserDevice) {
			UserDevice pd = (UserDevice) obj;
			Device d = pd.getDevice();
				return d.getMeasure() != null
						&& d.getModel() != null
						&& pd.getIdUser() != null
						&& d.getMeasure().equalsIgnoreCase(measure)
						&& d.getModel().equalsIgnoreCase(device.getModel())
						&& pd.getIdUser().equalsIgnoreCase(idUser);
		} else {
			return false;
		}
	}
}
