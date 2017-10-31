package com.ti.app.telemed.core.common;

public class Device implements Cloneable {
	private Integer id;
	private String measure;
	private String model;
	private String description;
    private boolean isBTDevice;
	private String className;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getMeasure() {
		return measure;
	}

	public void setMeasure(String measure) {
		this.measure = measure;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public void setIsBTDevice(boolean isBTDevice) {
        this.isBTDevice = isBTDevice;
    }

    public boolean isBTDevice() {
        return isBTDevice;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

	@Override
	public Object clone() {
		try {
			super.clone();
			Device newDevice = new Device();
			newDevice.setId(this.id);
			newDevice.setModel(this.model);
			newDevice.setDescription(this.description);
			newDevice.setMeasure(this.measure);
            newDevice.setIsBTDevice(this.isBTDevice);
			return newDevice;
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public String toString() {
		return description;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof Device && equalsMeasureModel((Device)obj);
	}

	private boolean equalsMeasureModel(Device d){
		return d != null && d.getMeasure() != null && d.getModel() != null &&
                d.getMeasure().equalsIgnoreCase(measure)
                && d.getModel().equalsIgnoreCase(model);
	}
}
