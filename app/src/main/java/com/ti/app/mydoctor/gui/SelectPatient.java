package com.ti.app.mydoctor.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.core.view.MenuItemCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;

import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.AppResourceManager;
import com.ti.app.telemed.core.common.Patient;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.mydoctor.gui.alphabeticalindex.IndexableListView;
import com.ti.app.mydoctor.gui.customview.GWTextView;
import com.ti.app.mydoctor.gui.adapter.PatientListAdapter;

public class SelectPatient extends AppCompatActivity implements SearchView.OnQueryTextListener {

	public static final String PATIENT = "PATIENT";
	
	private static final String TAG = "SelectPatient";
	
	private static final int ERROR_DIALOG = 0;
	
	private static final String KEY_CF = "cf";
	private static final String KEY_PATIENT = "patient";
	private static final String KEY_PATIENT_NAME = "patient_name";
	private static final String KEY_PATIENT_SURNAME = "patient_surname";
	private static final String KEY_ID = "id";

	private List<Patient> patientList;
	private List<HashMap<String, String>> fillMaps;
	private PatientListAdapter patientListAdapter;
	
	private boolean hasCurrentPatient;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.select_patient_layout);

		//Flag per mantenere attivo lo schermo finch?? l'activity ?? in primo piano
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // create the grid item mapping
        String[] from = new String[]{KEY_CF, KEY_PATIENT_SURNAME, KEY_PATIENT_NAME};
        int[] to = new int[]{R.id.cf, R.id.patient_surname, R.id.patient_name};

        fillMaps = new ArrayList<>();
        patientList = UserManager.getUserManager().getCurrentUser().getPatients();

        //Inizializza l'ActionBAr
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
            //Setta il gradiente di sfondo della action bar
            Drawable cd = this.getResources().getDrawable(R.drawable.action_bar_background_color);
            actionBar.setBackgroundDrawable(cd);

            actionBar.setDisplayShowCustomEnabled(true);
            actionBar.setDisplayShowTitleEnabled(false);

            //Setta l'icon
            actionBar.setIcon(R.drawable.icon_action_bar);

            //Settare il font e il titolo della Activity
            LayoutInflater inflator = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View titleView = inflator.inflate(R.layout.actionbar_title, null);
            GWTextView titleTV = (GWTextView) titleView.findViewById(R.id.actionbar_title_label);
            actionBar.setCustomView(titleView);

            //L'icona dell'App diventa tasto per tornare nella Home
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);

            //Nome dell'utente da usare come title dell'activity
            CharSequence mCurrentUserName = UserManager.getUserManager().getCurrentUser().getName() + "\n" + UserManager.getUserManager().getCurrentUser().getSurname();
            titleTV.setText(mCurrentUserName);
        }

		Collections.sort(patientList, new Comparator<Patient>() {
			@Override
			public int compare(Patient p1, Patient p2) {
				int ret = p1.getSurname().compareToIgnoreCase(p2.getSurname());
				if(ret == 0) {
					ret = p1.getName().compareToIgnoreCase(p2.getName());
				}
				return ret;
			}
		});
		
		final Patient currentPatient = UserManager.getUserManager().getCurrentPatient();
		hasCurrentPatient = false;
		for (Patient p : patientList) {
			if(currentPatient == null || !currentPatient.getId().equals(p.getId())) {
				HashMap<String, String> map = new HashMap<>();
				String cfValue = p.getCf();
				if (cfValue == null || cfValue.equalsIgnoreCase("null"))
					cfValue = "";
				map.put(KEY_CF, "[" + cfValue + "]");
				map.put(KEY_ID, p.getId());
				map.put(KEY_PATIENT, p.getName() + " " + p.getSurname());
				map.put(KEY_PATIENT_SURNAME, p.getSurname());
				map.put(KEY_PATIENT_NAME, p.getName());
				fillMaps.add(map);
			}
			else {
				hasCurrentPatient = true;
			}
		}
		if (hasCurrentPatient)
		    patientList.remove(currentPatient);

        IndexableListView lv = findViewById(R.id.patient_list);
		lv.setFastScrollEnabled(true);
		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				HashMap<String, String> map = fillMaps.get(position);
				String patientId = map.get(KEY_ID);
				Patient patient = null;
				for (Patient p : patientList)
					if (p.getId().equals(patientId)) {
						patient = p;
						break;
					}
                Intent result = new Intent();
                result.putExtra(PATIENT, patient);
                setResult(RESULT_OK, result);
                finish();
			}
		});
		patientListAdapter = new PatientListAdapter(this, fillMaps, R.layout.patient_item, from, to);
		lv.setAdapter(patientListAdapter);
	}	
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater menuInflater = getMenuInflater();
		menuInflater.inflate(R.menu.patient_list_menu, menu);
		return true;
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		createSearchView(menu);
		return true;
	}

	private void createSearchView(Menu menu) {
        SearchView mSearchView = (SearchView) MenuItemCompat.getActionView(menu.findItem(R.id.patient_list_action_bar_menu_search));
		mSearchView.setQueryHint(getResources().getString(R.string.searchPatients));
        mSearchView.setOnQueryTextListener(this);		
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case android.R.id.home: //Ritorna alla Home quando si clicca sull'icona della App
            finish();
            return true;  
            
		default:
            return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public Dialog onCreateDialog(int id) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		//Identifico la dialog che deve essere visualizzata
		switch(id) {
		case ERROR_DIALOG:
			builder.setTitle(AppResourceManager.getResource().getString("warningTitle"));
			builder.setMessage(AppResourceManager.getResource().getString("errorDb"));
			builder.setNeutralButton(R.string.okButton, error_dialog_click_listener);
			break;
		}
		
		return builder.create();
	}
	
	/**
	 * Listener per la gestione degli eventi sulla dialog ERROR_DIALOG
	 */
	private DialogInterface.OnClickListener error_dialog_click_listener = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			//Identifico quale elemento ?? stato premuto
			switch(which) {
			case DialogInterface.BUTTON_NEUTRAL:
				removeDialog(ERROR_DIALOG);
				finish();
				break;
			}
		}
	};

	@Override
	public boolean onQueryTextSubmit(String query) {
		return false;
	}

	@Override
	public boolean onQueryTextChange(String newText) {
	    if (newText==null || newText.isEmpty())
	        newText="";

		fillMaps.clear();
		for (Patient p : patientList) {
			if (p.getSurname().toLowerCase(Locale.ROOT).contains(newText.toLowerCase(Locale.ROOT))) {
				HashMap<String, String> map = new HashMap<>();
				
				String cfValue = p.getCf();
				if (cfValue == null || cfValue.equalsIgnoreCase("null"))
					cfValue = "";	
				
				map.put(KEY_CF, "[" + cfValue + "]");
				map.put(KEY_ID, p.getId());
				map.put(KEY_PATIENT, p.getName() + " " + p.getSurname());
				map.put(KEY_PATIENT_SURNAME, p.getSurname());
				map.put(KEY_PATIENT_NAME, p.getName());
				fillMaps.add(map);
			} else if (p.getName().toLowerCase(Locale.ROOT).contains(newText.toLowerCase(Locale.ROOT))) {
				HashMap<String, String> map = new HashMap<>();
				
				String cfValue = p.getCf();
				if (cfValue == null || cfValue.equalsIgnoreCase("null"))
					cfValue = "";	
				
				map.put(KEY_CF, "[" + cfValue + "]");
				map.put(KEY_ID, p.getId());
				map.put(KEY_PATIENT, p.getName() + " " + p.getSurname());
				map.put(KEY_PATIENT_SURNAME, p.getSurname());
				map.put(KEY_PATIENT_NAME, p.getName());
				fillMaps.add(map);
			} else if( p.getCf().regionMatches(true, 0, newText, 0, newText.length()) ) {
				HashMap<String, String> map = new HashMap<>();
				
				String cfValue = p.getCf();
				if (cfValue == null || cfValue.equalsIgnoreCase("null"))
					cfValue = "";	
				
				map.put(KEY_CF, "[" + cfValue + "]");
				map.put(KEY_ID, p.getId());
				map.put(KEY_PATIENT, p.getName() + " " + p.getSurname());
				map.put(KEY_PATIENT_SURNAME, p.getSurname());
				map.put(KEY_PATIENT_NAME, p.getName());
				fillMaps.add(map);
			}
		}
		patientListAdapter.notifyDataSetChanged();
		return true;
	}	
}
