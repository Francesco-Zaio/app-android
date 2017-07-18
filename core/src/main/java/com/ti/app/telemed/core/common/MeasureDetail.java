package com.ti.app.telemed.core.common;

import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.util.GWConst;
import java.util.Vector;

public class MeasureDetail {

    // elenco ordinato delle misure da visualizzare per ogni gruppo misura (visualizzazione breve/dettagliata)
    private static final String[] OssShort = {GWConst.EGwCode_07,GWConst.EGwCode_0F};
    private static final String[] OssDetail = {GWConst.EGwCode_07,GWConst.EGwCode_1B,GWConst.EGwCode_1D,GWConst.EGwCode_1F,GWConst.EGwCode_0F,GWConst.EGwCode_1A,GWConst.EGwCode_1C,GWConst.EGwCode_1E,GWConst.EGwCode_1G,GWConst.EGwCode_BATTERY};
    private static final String[] PresShort = {GWConst.EGwCode_03,GWConst.EGwCode_04};
    private static final String[] PresDetail = {GWConst.EGwCode_03,GWConst.EGwCode_04,GWConst.EGwCode_05,GWConst.EGwCode_06,GWConst.EGwCode_BATTERY};
    private static final String[] PesoShort = {GWConst.EGwCode_01};
    private static final String[] PesoDetail = {GWConst.EGwCode_01,GWConst.EGwCode_F1,GWConst.EGwCode_F2,GWConst.EGwCode_BATTERY};
    private static final String[] TempShort = {GWConst.EGwCode_0R};
    private static final String[] TempDetail = {GWConst.EGwCode_0R,GWConst.EGwCode_0U,GWConst.EGwCode_BATTERY};
    // utilizzato per gruppi misura che non prevedono la visualizzazione delle misure (es.ECG)
    private static final String[] NoShow = {};

	private String name; 
	private String value;
	private String unit;

	static public Vector<MeasureDetail> getMeasureDetails(Measure m, boolean detailed){

    /*
		String KMsrPres = "PR";
		String KMsrPeso = "PS";
		String KMsrGlic = "GL";
		String KMsrOss = "OS";
		String KMsrSpir = "SP";
		String KMsrEcg = "EC";
		String KMsrAritm = "AR";
		String KMsrTemp = "TC";
		String KMsrBodyFat = "MG";
		String KMsrProtr = "PT";
		String KMsr_Healt = "Q0";
		String KMsr_Sleep = "Q1";
		String KMsr_Pain = "Q2";
		String KMsr_Disch = "D0";
		String KMsr_Accep = "D1";
	*/

    String[] toShow;
		switch (m.getMeasureType()) {
			case GWConst.KMsrOss:
			    if (detailed)
			        toShow = OssDetail;
                else
                    toShow = OssShort;
                break;
			case GWConst.KMsrPres:
                if (detailed)
                    toShow = PresDetail;
                else
                    toShow = PresShort;
                break;
            case GWConst.KMsrPeso:
                if (detailed)
                    toShow = PesoDetail;
                else
                    toShow = PesoShort;
                break;
            case GWConst.KMsrTemp:
                if (detailed)
                    toShow = TempDetail;
                else
                    toShow = TempShort;
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
