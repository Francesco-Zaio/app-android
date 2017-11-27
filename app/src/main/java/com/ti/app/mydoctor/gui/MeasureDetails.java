package com.ti.app.mydoctor.gui;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Vector;

import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.util.AppConst;
import com.ti.app.mydoctor.AppResourceManager;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.MeasureDetail;
import com.ti.app.telemed.core.common.Patient;
import com.ti.app.mydoctor.gui.listadapter.MeasureDetailsListAdapter;
import com.ti.app.mydoctor.util.AppUtil;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.util.Util;

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
			Measure currentMeasure = (Measure)extras.get(ShowMeasure.MEASURE_KEY);
			if (currentMeasure == null)
				return;

			//Ricava il paziente relativo a questa misura
			Patient p = UserManager.getUserManager().getPatientData(currentMeasure.getIdPatient());
			String patientName = p.getSurname() + " " + p.getName();
			
			//Setta l'icona
			ImageView measureIcon = (ImageView)findViewById(R.id.measureIcon);
			measureIcon.setImageResource(AppUtil.getIconId(currentMeasure.getMeasureType()));
						
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
			Date d = Util.parseTimestamp(currentMeasure.getTimestamp());
			String date = getDate(d);
			String hour = getHour(d);
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

			separator = new String[measureList.size()];
			item = new String[measureList.size()];

			for(int i = 0; i < measureList.size(); i++) {
				separator[i] = measureList.get(i).getName();
				item[i] = measureList.get(i).getValue() + " " + measureList.get(i).getUnit();
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
	 * Metodo che restituisce la data in forma dd MMM yyyy
	 * @param data  variabile di tipo {@code Date} che contiene la data da convertire
	 * @return variabile di tipo {@code String} che contiene la stringa convertita
	 */
	private String getDate(Date data) {
		if (data == null) return "";
		SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
		return sdf.format(data);
	}
	
	/**
	 * Metodo che restituisce l'orario in forma HH:mm"
	 * @param data variabile di tipo {@code Date} che contiene la data da convertire in ora
	 * @return variabile di tipo {@code String} che contiene la stringa convertita
	 */
	private String getHour(Date data) {
		if (data == null) return "";
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
		return sdf.format(data);
	}
}
