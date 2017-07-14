package com.ti.app.telemed.core.common;

public class Device implements Cloneable {
	private Integer id;
	private String measure;
	private String model;
	private String description;
    private boolean needCfg;

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

	public void setNeedCfg(boolean needCfg) {
        this.needCfg = needCfg;
    }

    public boolean needCfg() {
        return needCfg;
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
            newDevice.setNeedCfg(this.needCfg);
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
		return d.getMeasure() != null && d.getModel() != null &&
                d.getMeasure().equalsIgnoreCase(measure)
                && d.getModel().equalsIgnoreCase(model);
	}
}
