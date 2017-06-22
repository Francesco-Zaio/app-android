package com.ti.app.telemed.core.common;

import com.ti.app.telemed.core.ResourceManager;

import java.util.Map;
import java.util.Vector;

public class MeasureDetail {
	private String name; 
	private String value;
	private String unit;

	static public Vector<MeasureDetail> getMeasureDetails(Measure m){
		Vector<MeasureDetail> ret = new Vector<>();
		for (Map.Entry<String, String> entry : m.getMeasures().entrySet())
		{
			if (!ResourceManager.getResource().getString("MeasureName_" + entry.getKey()).isEmpty()) {
				MeasureDetail md = new MeasureDetail();
				md.setName(ResourceManager.getResource().getString("MeasureName_" + entry.getKey()));
				md.setValue(entry.getValue());
				md.setUnit(ResourceManager.getResource().getString("MeasureUnit_" + entry.getKey()));
				ret.add(md);
			}
		}
		return ret;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public void setValue(String value) {
		this.value = value;
	}
	
	public void setUnit(String unit) {
		this.unit = unit;
	}
	
	public String getName() {
		return name;
	}
	
	public String getValue() {
		return value;
	}
	
	public String getUnit() {
		return unit;
	}
}
