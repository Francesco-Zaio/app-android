package com.ti.app.telemed.core.common;

public class Device implements Cloneable {
	private Integer id;
	private String measure;
	private String model;
	private String description;
    private DevType devType;
	private String className;

	public enum DevType {
		NONE(0), BT(1), APP(2);

		private final int value;
		private DevType(int value) {
			this.value = value;
		}
		public int getValue() {
			return value;
		}

		public static DevType fromInteger(int x) {
			switch(x) {
				case 0:
					return NONE;
				case 1:
					return BT;
				case 2:
					return APP;
			}
			return null;
		}
	}
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

	public void setDevType(DevType devType) {
        this.devType = devType;
    }

    public DevType getDevType() {
        return devType;
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
            newDevice.setDevType(this.devType);
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
