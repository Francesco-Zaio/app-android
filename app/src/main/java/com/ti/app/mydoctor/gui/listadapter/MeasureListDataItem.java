package com.ti.app.mydoctor.gui.listadapter;

public class MeasureListDataItem {
	public String icon;
	public String data;
	public String hours;
	
	public boolean sended;
  
	@Override
    public String toString() {
        return data;
     }
}
