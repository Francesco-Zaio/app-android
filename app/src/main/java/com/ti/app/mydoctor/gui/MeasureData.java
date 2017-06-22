package com.ti.app.mydoctor.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Vector;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.os.Bundle;

import com.ti.app.mydoctor.R;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.MeasureDetail;
import com.ti.app.telemed.core.xmlmodule.XmlManager;
import com.ti.app.mydoctor.exceptions.XmlException;
import com.ti.app.mydoctor.gui.listadapter.MeasureDataAdapter;
import com.ti.app.mydoctor.gui.listadapter.MeasureDataAdapter.LayoutType;
import com.ti.app.mydoctor.util.GWConst;
import com.ti.app.mydoctor.util.Util;

public class MeasureData extends ListActivity {
	
	//Dialog
	private static final int ERROR_DIALOG = 0;
	
	private static final String KEY_MEASURE_LBL = "measure_lbl";
	private static final String KEY_MEASURE_LBL_READ = "measure_lbl_read";
	private static final String KEY_MEASURE_LBL_THEORIC = "measure_lbl_theoric";
	private static final String KEY_MEASURE_VALUE = "measure_value";
	private static final String KEY_MEASURE_VALUE_READ = "measure_value_read";
	private static final String KEY_MEASURE_VALUE_THEORIC = "measure_value_theoric";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			Measure measure = (Measure)extras.get(GWConst.SELECTED_MEASURE);
			
			String title = Util.getString(R.string.measure_detail_title) + " " + ResourceManager.getResource().getString("measureType." + measure.getMeasureType());
			setTitle(title);
			
			try {
				(XmlManager.getXmlManager()).parse(measure.getXml());
				Vector<Object> measureList = (XmlManager.getXmlManager()).getParsedData();

				List<HashMap<String, String>> fillMaps = new ArrayList<HashMap<String, String>>();
				if (measure.getMeasureType().equals(GWConst.KMsrSpir)) {
					String[] from = new String[] { KEY_MEASURE_LBL_READ, KEY_MEASURE_LBL_THEORIC, KEY_MEASURE_VALUE_READ, KEY_MEASURE_VALUE_THEORIC };
					int[] to = new int[] { R.id.measure_lbl_read, R.id.measure_lbl_theoric, R.id.measure_value_read, R.id.measure_value_theoric };
					
					if (measureList.size() == 6) {
						
						for (int i = 0; i < measureList.size(); i++) {
							HashMap<String, String> map = new HashMap<String, String>();
							String s = ((MeasureDetail) (measureList.get(i))).getName();
							StringTokenizer st = new StringTokenizer(s, "-");
							 
							map.put(KEY_MEASURE_LBL_READ, st.nextToken().trim());
							map.put(KEY_MEASURE_LBL_THEORIC, "");
							
							String readValue = ((MeasureDetail) (measureList.get(i))).getValue() + " " + ((MeasureDetail) (measureList.get(i))).getUnit();
							//String theoricValue = ((MeasureDetail) (measureList.get(i+1))).getValue() + " " + ((MeasureDetail) (measureList.get(i+1))).getUnit();
							map.put(KEY_MEASURE_VALUE_READ, readValue);
							map.put(KEY_MEASURE_VALUE_THEORIC, "");
	
							fillMaps.add(map);
						}
						
						setListAdapter(new MeasureDataAdapter(this, fillMaps, R.layout.measure_detail_item_3,
								from, to, LayoutType.TABLE_ROW));
						
					}
					else {
					
						for (int i = 0; i < measureList.size(); i+=2) {
							HashMap<String, String> map = new HashMap<String, String>();
							String s = ((MeasureDetail) (measureList.get(i))).getName();
							StringTokenizer st = new StringTokenizer(s, "-");
							 
							map.put(KEY_MEASURE_LBL_READ, st.nextToken().trim());
							map.put(KEY_MEASURE_LBL_THEORIC, st.nextToken().trim());
							
							String readValue = ((MeasureDetail) (measureList.get(i))).getValue() + " " + ((MeasureDetail) (measureList.get(i))).getUnit();
							String theoricValue = ((MeasureDetail) (measureList.get(i+1))).getValue() + " " + ((MeasureDetail) (measureList.get(i+1))).getUnit();
							map.put(KEY_MEASURE_VALUE_READ, readValue);
							map.put(KEY_MEASURE_VALUE_THEORIC, theoricValue);
	
							fillMaps.add(map);
						}
						
						setListAdapter(new MeasureDataAdapter(this, fillMaps, R.layout.measure_detail_item_2,
								from, to, LayoutType.TABLE_ROW));
					}
					
					
				}
				else {
					String[] from = new String[] { KEY_MEASURE_LBL, KEY_MEASURE_VALUE };
					int[] to = new int[] { R.id.measure_lbl, R.id.measure_value };
					
					for (int i = 0; i < measureList.size(); i++) {
						HashMap<String, String> map = new HashMap<String, String>();
						map.put(KEY_MEASURE_LBL, ((MeasureDetail) (measureList.get(i))).getName());
						map.put(KEY_MEASURE_VALUE, ((MeasureDetail) (measureList.get(i))).getValue() + " " + ((MeasureDetail) (measureList.get(i))).getUnit());

						fillMaps.add(map);
					}
					
					setListAdapter(new MeasureDataAdapter(this, fillMaps, R.layout.measure_detail_item,
							from, to, LayoutType.NORMAL));
				}
				
			} catch (XmlException e) {
				showDialog(ERROR_DIALOG);
			}			
		}

	}
	
	@Override
	public Dialog onCreateDialog(int id) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		switch (id) {
		case ERROR_DIALOG:
			builder.setTitle(ResourceManager.getResource().getString("warningTitle"));
			builder.setMessage(ResourceManager.getResource().getString("errorDbRead"));
			builder.setNeutralButton("Ok", error_dialog_click_listener);
			break;
		}
		
		return builder.create();
	}
	
	/**
	 * Listener per i click sulla dialog ERROR_DIALOG
	 */
	private DialogInterface.OnClickListener error_dialog_click_listener = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			switch(which) {
			case DialogInterface.BUTTON_NEUTRAL:
				removeDialog(ERROR_DIALOG);
				finish();
			}
		}
	};
}