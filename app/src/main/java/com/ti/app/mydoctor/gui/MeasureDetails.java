package com.ti.app.mydoctor.gui;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.Vector;

import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.util.AppConst;
import com.ti.app.mydoctor.AppResourceManager;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.MeasureDetail;
import com.ti.app.telemed.core.common.Patient;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.mydoctor.gui.listadapter.MeasureDetailsListAdapter;
import com.ti.app.mydoctor.util.Util;
import com.ti.app.telemed.core.util.GWConst;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
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

	private Bundle deleteBundle = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.measure_details_list);
		
		//Legge i dati extras
		Bundle extras = getIntent().getExtras();
		
		if (extras != null) {
			Measure currentMeasure = (Measure)extras.get(ShowMeasure.SELECTED_MEASURE_KEY);
			if (currentMeasure == null)
				return;

			//Ricava il paziente relativo a questa misura
			Patient p = DbManager.getDbManager().getPatientData(currentMeasure.getIdPatient());
			String patientName = p.getSurname() + " " + p.getName();
			
			//Setta l'icona
			ImageView measureIcon = (ImageView)findViewById(R.id.measureIcon);
			measureIcon.setImageResource(Util.getIconId(currentMeasure.getMeasureType()));
						
			//Setta la stringa tipo misura
			final String title = AppResourceManager.getResource().getString("measureType." + currentMeasure.getMeasureType());
			TextView measureType = (TextView)findViewById(R.id.measureLabel);
			measureType.setText(title);
			
			//Setta la stringa modello dispositivo
			TextView deviceModelTV = (TextView)findViewById(R.id.deviceModelLabel);
			deviceModelTV.setText(currentMeasure.getDeviceDesc());
						
			//Setta il nome del paziente
			TextView patientNameTV = (TextView)findViewById(R.id.patientNameLabel);
			patientNameTV.setText(patientName);
			
			//Setta la data
			String date = getDate(currentMeasure.getTimestamp());
			String hour = getHour(currentMeasure.getTimestamp());
			TextView dateText = (TextView)findViewById(R.id.dataValue);
			dateText.setText(date + " " + hour);

			
			//Setta il listener per il buttone cancellazione misura
			ImageButton buttonCancel = (ImageButton)findViewById(R.id.imageButtonCancel);
			buttonCancel.setOnClickListener(new View.OnClickListener() {
				
				@Override
				public void onClick(View v) {
					//Crea la dialog per la conferma della cancellazione della misura		
					deleteBundle = new Bundle();
					deleteBundle.putString(AppConst.TITLE, AppResourceManager.getResource().getString("warningTitle"));
					deleteBundle.putString(AppConst.MESSAGE, AppResourceManager.getResource().getString("showMeasureDialogDeleteQuestion1") + " " + title + "?");
		    		showDialog(DELETE_CONFIRM_DIALOG);
				}
			});

            Vector<MeasureDetail> measureList = MeasureDetail.getMeasureDetails(currentMeasure, true);

            String[] separator;
            String[] item;

            if (currentMeasure.getMeasureType().equals(GWConst.KMsrSpir)) {
                if (measureList.size() == 6) {
                    separator = new String[measureList.size()];
                    item = new String[measureList.size()];

                    //Misura semplice di spirometria
                    for(int i = 0; i < measureList.size(); i++) {
                        String label = measureList.get(i).getName();
                        StringTokenizer st = new StringTokenizer(label, "-");

                        separator[i] = st.nextToken().trim();
                        item[i] = measureList.get(i).getValue() + " " + measureList.get(i).getUnit();
                    }
                } else {
                    //Misura comparativa di spirometria
                    int index = 0;

                    separator = new String[measureList.size() / 2];
                    item = new String[measureList.size() / 2];

                    for(int i = 0; i < measureList.size(); i+=2) {

                        String readValue = measureList.get(i).getValue() + " " + measureList.get(i).getUnit();
                        String theoricValue = measureList.get(i+1).getValue() + " " + measureList.get(i+1).getUnit();

                        separator[index] = measureList.get(i).getName();
                        item[index] = readValue + " - " + theoricValue;

                        index++;
                    }
                }
            } else {
                separator = new String[measureList.size()];
                item = new String[measureList.size()];

                for(int i = 0; i < measureList.size(); i++) {
                    separator[i] = measureList.get(i).getName();
                    item[i] = measureList.get(i).getValue() + " " + measureList.get(i).getUnit();
                }
            }

            MeasureDetailsListAdapter adapter = new MeasureDetailsListAdapter(this);
            for (int i = 0; i < separator.length; i++) {
                adapter.addSeparatorItem(separator[i]);
                adapter.addItem(item[i]);
            }
            setListAdapter(adapter);
		}
		
		
	}

	@Override
	public Dialog onCreateDialog(int id) {
		Context ctx = this;
        ctx.setTheme(R.style.Theme_MyDoctorAtHome_Light);
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
		
		switch (id) {
		case ERROR_DIALOG:
			builder.setTitle(AppResourceManager.getResource().getString("warningTitle"));
			builder.setMessage(AppResourceManager.getResource().getString("errorDbRead"));
			builder.setNeutralButton("Ok", error_dialog_click_listener);
			break;
		case DELETE_CONFIRM_DIALOG:
			builder.setTitle(deleteBundle.getString(AppConst.TITLE));
			builder.setMessage(deleteBundle.getString(AppConst.MESSAGE));
			builder.setPositiveButton(AppResourceManager.getResource().getString("confirmButton"), delete_confirm_dialog_click_listener);
			builder.setNegativeButton(AppResourceManager.getResource().getString("cancelButton"), delete_confirm_dialog_click_listener);
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
}
