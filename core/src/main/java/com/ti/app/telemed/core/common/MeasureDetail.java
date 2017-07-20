package com.ti.app.telemed.core.common;

import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.util.GWConst;
import java.util.Vector;

public class MeasureDetail {

    // elenco ordinato delle misure da visualizzare per ogni gruppo misura (visualizzazione breve/dettagliata)
    private static final String[] OS_Short = {GWConst.EGwCode_07,GWConst.EGwCode_0F};
    private static final String[] OS_Detail = {GWConst.EGwCode_07,GWConst.EGwCode_1B,GWConst.EGwCode_1D,GWConst.EGwCode_1F,GWConst.EGwCode_0F,GWConst.EGwCode_1A,GWConst.EGwCode_1C,GWConst.EGwCode_1E,GWConst.EGwCode_1G,GWConst.EGwCode_BATTERY};
    private static final String[] PR_Short = {GWConst.EGwCode_03,GWConst.EGwCode_04};
    private static final String[] PR_Detail = {GWConst.EGwCode_03,GWConst.EGwCode_04,GWConst.EGwCode_05,GWConst.EGwCode_06,GWConst.EGwCode_BATTERY};
    private static final String[] PS_Short = {GWConst.EGwCode_01};
    private static final String[] PS_Detail = {GWConst.EGwCode_01,GWConst.EGwCode_F1,GWConst.EGwCode_F2,GWConst.EGwCode_BATTERY};
    private static final String[] TC_Short = {GWConst.EGwCode_0R};
    private static final String[] TC_Detail = {GWConst.EGwCode_0R,GWConst.EGwCode_0U,GWConst.EGwCode_BATTERY};
    private static final String[] PT_Short = {GWConst.EGwCode_0Z,GWConst.EGwCode_0X,GWConst.EGwCode_0V};
    private static final String[] PT_Detail = {GWConst.EGwCode_0Z,GWConst.EGwCode_0X,GWConst.EGwCode_0V,GWConst.EGwCode_BATTERY};
    // utilizzato per gruppi misura che non prevedono la visualizzazione delle misure (es.ECG)
    private static final String[] NoShow = {};

	private String name; 
	private String value;
	private String unit;

	static public Vector<MeasureDetail> getMeasureDetails(Measure m, boolean detailed){

    String[] toShow;
		switch (m.getMeasureType()) {
			case GWConst.KMsrOss:
			    if (detailed)
			        toShow = OS_Detail;
                else
                    toShow = OS_Short;
                break;
			case GWConst.KMsrPres:
                if (detailed)
                    toShow = PR_Detail;
                else
                    toShow = PR_Short;
                break;
            case GWConst.KMsrPeso:
                if (detailed)
                    toShow = PS_Detail;
                else
                    toShow = PS_Short;
                break;
            case GWConst.KMsrTemp:
                if (detailed)
                    toShow = TC_Detail;
                else
                    toShow = TC_Short;
                break;
            case GWConst.KMsrProtr:
                if (detailed)
                    toShow = PT_Detail;
                else
                    toShow = PT_Short;
                break;
            default:
                toShow = NoShow;
		}

        Vector<MeasureDetail> ret = new Vector<>();
		String val;
		for (String key : toShow) {
            if ((val = m.getMeasures().get(key)) != null) {
                MeasureDetail md = new MeasureDetail();
                md.setName(ResourceManager.getResource().getString("MeasureName_" + key));
                md.setValue(val);
                md.setUnit(ResourceManager.getResource().getString("MeasureUnit_" + key));
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
