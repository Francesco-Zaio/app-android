package com.ti.app.mydoctor.gui;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.Vector;

import com.ti.app.mydoctor.R;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.MeasureDetail;
import com.ti.app.telemed.core.common.Patient;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.xmlmodule.XmlManager;
import com.ti.app.telemed.core.exceptions.XmlException;
import com.ti.app.mydoctor.gui.listadapter.MeasureDetailsListAdapter;
import com.ti.app.mydoctor.util.GWConst;
import com.ti.app.mydoctor.util.Util;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class MeasureDetails extends ListActivity {
	//Dialog
	private static final int ERROR_DIALOG = 0;
	private static final int DELETE_CONFIRM_DIALOG = 1;
	private static final int MEASURE_SEND_DIALOG = 2;
	
	private Bundle deleteBundle = null;
	private Bundle sendBundle = null;

	private MeasureDetailsListAdapter mAdapter;
	
	private ImageButton mButtonSend;
	private ImageButton mButtonCancel;
	
	private Measure mCurrentMeasure = null;
	private String mPatientName;
	private String mDeviceModel;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.measure_details_list);
		
		//Legge i dati extras
		Bundle extras = getIntent().getExtras();
		
		if (extras != null) {
			mCurrentMeasure = (Measure)extras.get(GWConst.SELECTED_MEASURE);
			
			//Ricava il paziente relativo a questa misura
			Patient p = DbManager.getDbManager().getPatientData(mCurrentMeasure.getIdPatient());
			mPatientName = p.getSurname() + " " + p.getName();
			
			//Ricava il modello del dispositivo
			mDeviceModel = mCurrentMeasure.getDeviceDesc();
			
			//Setta l'icona
			ImageView measureIcon = (ImageView)findViewById(R.id.measureIcon);
			measureIcon.setImageResource(Util.getIconId(mCurrentMeasure.getMeasureType()));
						
			//Setta la stringa tipo misura
			final String title = ResourceManager.getResource().getString("measureType." + mCurrentMeasure.getMeasureType());
			TextView measureType = (TextView)findViewById(R.id.measureLabel);
			measureType.setText(title);
			
			//Setta la stringa modello dispositivo
			TextView deviceModelTV = (TextView)findViewById(R.id.deviceModelLabel);
			deviceModelTV.setText(mDeviceModel);
						
			//Setta il nome del paziente
			TextView patientNameTV = (TextView)findViewById(R.id.patientNameLabel);
			patientNameTV.setText(mPatientName);
			
			//Setta la data
			String date = getDate(mCurrentMeasure.getTimestamp());
			String hour = getHour(mCurrentMeasure.getTimestamp());
			TextView dateText = (TextView)findViewById(R.id.dataValue);
			dateText.setText(date + " " + hour);
			
			//abilita/disabilita il button invia misura
			if(mCurrentMeasure.getSent()) {
				ImageButton buttonSend = (ImageButton)findViewById(R.id.imageButtonSend);
				buttonSend.setEnabled(false);
				buttonSend.setVisibility(View.GONE);
			}
			
			//Setta i listener per i button
			mButtonSend = (ImageButton)findViewById(R.id.imageButtonSend);
			mButtonSend.setOnClickListener(new View.OnClickListener() {
				
				@Override
				public void onClick(View v) {
					sendBundle = new Bundle();
					sendBundle.putString(GWConst.TITLE, ResourceManager.getResource().getString("measureType." + mCurrentMeasure.getMeasureType()));
					if(mCurrentMeasure.getMeasureType().equals("OS"))
						sendBundle.putString(GWConst.MESSAGE, getBodyOxy(mCurrentMeasure.getXml()));
					else if(mCurrentMeasure.getMeasureType().equals("SP"))
						sendBundle.putString(GWConst.MESSAGE, getBodySP(mCurrentMeasure.getXml()));
					else
						sendBundle.putString(GWConst.MESSAGE, getBody(mCurrentMeasure.getXml()));
							
					showDialog(MEASURE_SEND_DIALOG);
				}
			});
			
			mButtonCancel = (ImageButton)findViewById(R.id.imageButtonCancel);
			mButtonCancel.setOnClickListener(new View.OnClickListener() {
				
				@Override
				public void onClick(View v) {
					//Crea la dialog per la conferma della cancellazione della misura		
					deleteBundle = new Bundle();
					deleteBundle.putString(GWConst.TITLE, ResourceManager.getResource().getString("warningTitle"));
					deleteBundle.putString(GWConst.MESSAGE, ResourceManager.getResource().getString("showMeasureDialogDeleteQuestion1") + " " + title + "?");		    	
		    		showDialog(DELETE_CONFIRM_DIALOG);
				}
			});
			
			try {
				(XmlManager.getXmlManager()).parse(mCurrentMeasure.getXml());
				Vector<Object> measureList = (XmlManager.getXmlManager()).getParsedData();

				/*String[] separator = new String[measureList.size()];
				String[] item = new String[measureList.size()];*/
				
				String[] separator;
				String[] item;
				
				if (mCurrentMeasure.getMeasureType().equals(GWConst.KMsrSpir)) {
					if (measureList.size() == 6) {
						separator = new String[measureList.size()];
						item = new String[measureList.size()];
						
						//Misura semplice di spirometria
						for(int i = 0; i < measureList.size(); i++) {
							String label = ((MeasureDetail) (measureList.get(i))).getName();
							StringTokenizer st = new StringTokenizer(label, "-");
							
							separator[i] = st.nextToken().trim();
							item[i] = ((MeasureDetail) (measureList.get(i))).getValue() + " " + ((MeasureDetail) (measureList.get(i))).getUnit();
						}						
					} else {
						//Misura comparativa di spirometria
						int index = 0;
						
						separator = new String[measureList.size() / 2];
						item = new String[measureList.size() / 2];
						
						for(int i = 0; i < measureList.size(); i+=2) {
							
							String readValue = ((MeasureDetail) (measureList.get(i))).getValue() + " " + ((MeasureDetail) (measureList.get(i))).getUnit();
							String theoricValue = ((MeasureDetail) (measureList.get(i+1))).getValue() + " " + ((MeasureDetail) (measureList.get(i+1))).getUnit();
							
							separator[index] = ((MeasureDetail) (measureList.get(i))).getName();
							item[index] = readValue + " - " + theoricValue;
							
							index++;
						}
					}
				} else {
					separator = new String[measureList.size()];
					item = new String[measureList.size()];
					
					for(int i = 0; i < measureList.size(); i++) {
						separator[i] = ((MeasureDetail) (measureList.get(i))).getName();
						item[i] = ((MeasureDetail) (measureList.get(i))).getValue() + " " + ((MeasureDetail) (measureList.get(i))).getUnit();
					}					
				}				
				
				mAdapter = new MeasureDetailsListAdapter(this);
		        for (int i = 0; i < separator.length; i++) {
					mAdapter.addSeparatorItem(separator[i]);
		            mAdapter.addItem(item[i]);
		        }
		        setListAdapter(mAdapter);
				
			} catch(XmlException e) {
				showDialog(ERROR_DIALOG);
			}
			
		}
		
		
	}

	@Override
	public Dialog onCreateDialog(int id) {
		Context ctx = this;
        ctx.setTheme(R.style.Theme_MyDoctorAtHome_Light);
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        
		/*AlertDialog.Builder builder = new AlertDialog.Builder(this);
		LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);*/
		
		switch (id) {
		case ERROR_DIALOG:
			builder.setTitle(ResourceManager.getResource().getString("warningTitle"));
			builder.setMessage(ResourceManager.getResource().getString("errorDbRead"));
			builder.setNeutralButton("Ok", error_dialog_click_listener);
			break;
		case DELETE_CONFIRM_DIALOG:
			builder.setTitle(deleteBundle.getString(GWConst.TITLE));
			builder.setMessage(deleteBundle.getString(GWConst.MESSAGE));
			builder.setPositiveButton(ResourceManager.getResource().getString("confirmButton"), delete_confirm_dialog_click_listener);
			builder.setNegativeButton(ResourceManager.getResource().getString("cancelButton"), delete_confirm_dialog_click_listener);
			break;
		case MEASURE_SEND_DIALOG:
			builder.setTitle(sendBundle.getString(GWConst.TITLE));
			
			String measureStr = sendBundle.getString(GWConst.MESSAGE);
			
			String txt = ResourceManager.getResource().getString("MeasureResultDialog.sendMeasureMsg");
			Patient patient = DbManager.getDbManager().getPatientData(mCurrentMeasure.getIdPatient());
			String questionStr = String.format(txt, patient.getName(), patient.getSurname());
			
			builder.setMessage(measureStr + "\n" + questionStr);
			
			/*Button b = new Button(ctx);
			b.setText(R.string.changePatientQuestion);
			builder.setView(b);*/

			/*View show_measure_dialog_view = inflater.inflate(R.layout.show_measure_custom_dialog, null);
			TextView measureTV = (TextView) show_measure_dialog_view.findViewById(R.id.measure_dialog_tv);
			TextView questionTV = (TextView) show_measure_dialog_view.findViewById(R.id.question_dialog_tv);
			
			((Button) show_measure_dialog_view.findViewById(R.id.measure_dialog_button)).setVisibility(View.GONE);
			
			measureTV.setText(sendBundle.getString(GWConst.MESSAGE));
			String txt = ResourceManager.getResource().getString("MeasureResultDialog.sendMeasureMsg");
			
			Patient patient = DbManager.getDbManager().getPatientData(mCurrentMeasure.getIdPatient());
			String msg = String.format(txt, patient.getName(), patient.getSurname());
			questionTV.setText(msg);

			builder.setView(show_measure_dialog_view);*/
			builder.setPositiveButton(ResourceManager.getResource().getString("MeasureResultDialog.sendBtn"), measure_send_dialog_click_listener);
			builder.setNegativeButton(ResourceManager.getResource().getString("MeasureResultDialog.cancelBtn"), measure_send_dialog_click_listener);			
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
	
	/**
	 * Listener per i click sulla dialog DELETE_CONFIRM_DIALOG
	 */
	private DialogInterface.OnClickListener delete_confirm_dialog_click_listener = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			removeDialog(DELETE_CONFIRM_DIALOG);
			
			switch (which) {
			case DialogInterface.BUTTON_POSITIVE:
				//Viene eliminata la singola misura
				Intent returnIntent = new Intent();
				String result = "delete";
				returnIntent.putExtra("result",result);
				setResult(RESULT_OK, returnIntent);     
				finish();
				break;
			case DialogInterface.BUTTON_NEGATIVE:
				Toast.makeText(MeasureDetails.this, "Misura non eliminata", Toast.LENGTH_LONG).show();				
				break;
			}			
		}
	};
	
	/**
	 * Listener per la selezione degli elementi che compongono la dialog MEASURE_SEND_DIALOG
	 */
	private DialogInterface.OnClickListener measure_send_dialog_click_listener = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			removeDialog(MEASURE_SEND_DIALOG);
			
			switch(which) {
			case DialogInterface.BUTTON_POSITIVE:
				Intent returnIntent = new Intent();
				String result = "send";
				returnIntent.putExtra("result",result);
				setResult(RESULT_OK, returnIntent);     
				finish();
				break;
			case DialogInterface.BUTTON_NEGATIVE:				
				break;
			}
		}
	};
	
	/**
	 * Metodo che restituisce la data in forma dd MMMMM yyyy
	 * @param timestamp  variabile di tipo {@code String} che contiene la stringa da convertire
	 * @return variabile di tipo {@code String} che contiene la stringa convertita
	 */
	private String getDate(String timestamp) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
		try {
			Date data = sdf.parse(timestamp);
			sdf = new SimpleDateFormat("dd MMM yyyy");
			return sdf.format(data);
		} catch (ParseException e) {
			e.printStackTrace();
			showDialog(ERROR_DIALOG);
			return null;
		}
	}
	
	/**
	 * Metodo che restituisce l'orario in forma HH:mm"
	 * @param timestamp variabile di tipo {@code String} che contiene la stringa da convertire in ora
	 * @return variabile di tipo {@code String} che contiene la stringa convertita
	 */
	private String getHour(String timestamp) {	
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
		try {
			Date data = sdf.parse(timestamp);
			sdf = new SimpleDateFormat("HH:mm:ss");
			return sdf.format(data);
		} catch (ParseException e) {
			e.printStackTrace();
			showDialog(ERROR_DIALOG);
			return null;
		}
	}
	
	/**
	 * Versione customizzata per misura di OSSIMETRIA
	 * Metodo che permette di costruire il testo della dialog con i dettagli relativi alla misura
	 * @param xml variabile di tipo {@code String} che contiene l'xml della misura
	 * @return variabile di tipo {@code String} che contiene il messaggio da visualizzare sulla dialog
	 */
	private String getBodyOxy(String xml) {
		StringBuilder builder = new StringBuilder();
		try {
			(XmlManager.getXmlManager()).parse(xml);
			Vector<Object> measureList = (XmlManager.getXmlManager()).getParsedData();
			
			for (int i = 0; i < measureList.size(); i++) {
				String name = ((MeasureDetail) (measureList.get(i))).getName();
				if(name.equals(ResourceManager.getResource().getString("EGwnurseMeasureOxyMed")) || name.equals(ResourceManager.getResource().getString("EGwnurseMeasureOxyFreqMed"))) {
					String value = ((MeasureDetail) (measureList.get(i))).getValue();
					String unit = ((MeasureDetail) (measureList.get(i))).getUnit();
					builder.append(name + " " + value + " " + unit + "\n");
				}
			}
		} catch (XmlException e) {
			e.printStackTrace();
			showDialog(ERROR_DIALOG);
		}
		return builder.toString();
	}
	
	/**
	 * Versione customizzata per misura di SPIROMETRIA
	 * Metodo che permette di costruire il testo della dialog con i dettagli relativi alla misura
	 * @param xml variabile di tipo {@code String} che contiene l'xml della misura
	 * @return variabile di tipo {@code String} che contiene il messaggio da visualizzare sulla dialog
	 */
	private String getBodySP(String xml) {
		StringBuilder builder = new StringBuilder();
		try {
			(XmlManager.getXmlManager()).parse(xml);
			Vector<Object> measureList = (XmlManager.getXmlManager()).getParsedData();
			
			for (int i = 0; i < measureList.size(); i++) {
				String name = ((MeasureDetail) (measureList.get(i))).getName();
				ResourceManager rMananger = ResourceManager.getResource();
				if(name.equals(rMananger.getString("EGwnurseMeasureSpiroPEF")) || name.equals(rMananger.getString("EGwnurseMeasureSpiroFEV1")) || name.equals(rMananger.getString("EGwnurseMeasureSpiroFVC")) || name.equals(rMananger.getString("EGwnurseMeasureSpiroFEV1perc")) || name.equals(rMananger.getString("EGwnurseMeasureSpiroFET"))) {
					String value = ((MeasureDetail) (measureList.get(i))).getValue();
					String unit = ((MeasureDetail) (measureList.get(i))).getUnit();
					builder.append(name + " " + value + " " + unit + "\n");
				}			
			}
			
		} catch (XmlException e) {
			e.printStackTrace();
			showDialog(ERROR_DIALOG);
		}
		
		return builder.toString();
	}
	
	/**
	 * Metodo che permette di costruire il testo della dialog con i dettagli relativi alla misura
	 * @param xml variabile di tipo {@code String} che contiene l'xml della misura
	 * @return variabile di tipo {@code String} che contiene il messaggio da visualizzare sulla dialog
	 */
	private String getBody(String xml) {
		StringBuilder builder = new StringBuilder();
		try {
			(XmlManager.getXmlManager()).parse(xml);
			Vector<Object> measureList = (XmlManager.getXmlManager()).getParsedData();
			
			for (int i = 0; i < measureList.size(); i++) {
				String name = ((MeasureDetail) (measureList.get(i))).getName();
				String value = ((MeasureDetail) (measureList.get(i))).getValue();
				String unit = ((MeasureDetail) (measureList.get(i))).getUnit();
				builder.append(name + " " + value + " " + unit + "\n"); 
			}
		} catch (XmlException e) {
			e.printStackTrace();
			showDialog(ERROR_DIALOG);
		}
		
		return builder.toString();
	}
	
	private String getDeviceModel(String xmlString) {
		String fieldToSearchStart = "<Property name=\"MODEL\" value=\"";
		String fieldToSearchEnd = "\"/>";
		
		String tmp = xmlString.substring(xmlString.indexOf(fieldToSearchStart)
										 + fieldToSearchStart.length());
		
		return tmp.substring(0, tmp.indexOf(fieldToSearchEnd));
	}
}
