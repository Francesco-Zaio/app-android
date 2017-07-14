package com.ti.app.mydoctor.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.SearchView;
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
import com.ti.app.mydoctor.util.AppConst;
import com.ti.app.mydoctor.AppResourceManager;
import com.ti.app.telemed.core.common.Patient;
import com.ti.app.telemed.core.common.UserPatient;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.mydoctor.gui.alphabeticalindex.IndexableListView;
import com.ti.app.mydoctor.gui.customview.ActionBarListActivity;
import com.ti.app.mydoctor.gui.customview.GWTextView;
import com.ti.app.mydoctor.gui.listadapter.PatientListAdapter;

public class SelectPatient extends ActionBarListActivity implements SearchView.OnQueryTextListener {

	public static final String USER_ID = "USER_ID";
	public static final String PATIENT = "PATIENT";
	public static final String PATIENT_ID = "PATIENT_ID";
	
	private static final String TAG = "SelectPatient";
	
	private static final int ERROR_DIALOG = 0;
	
	private static final String KEY_CF = "cf";
	private static final String KEY_PATIENT = "patient";
	private static final String KEY_PATIENT_NAME = "patient_name";
	private static final String KEY_PATIENT_SURNAME = "patient_surname";
	private static final String KEY_ID = "id";

	//Elementi che compongono la GUI
	private GWTextView titleTV;
	private List<UserPatient> patientList;
	private List<Patient> patientNameList;
		
	private List<HashMap<String, String>> fillMaps;
	private PatientListAdapter patientListAdapter;
	
	private boolean hasCurrentPatient;
	
	//SEARCH VIEW
	private SearchView mSearchView;
	
	//Nome dell'utente da usare come title dell'activity
	private CharSequence mCurrentUserName = "TEST";
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.select_patient_layout);

		//Flag per mantenere attivo lo schermo finchè l'activity è in primo piano
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
				
		/**
		 * ACTION BAR
		 */
		//Inizializza l'ActionBAr
		ActionBar actionBar = this.getSupportActionBar();
		//Setta il gradiente di sfondo della action bar
		Drawable cd = this.getResources().getDrawable(R.drawable.action_bar_background_color);
		actionBar.setBackgroundDrawable(cd);
				
		actionBar.setDisplayShowCustomEnabled(true);
		actionBar.setDisplayShowTitleEnabled(false);
		
		//Setta l'icon
		actionBar.setIcon(R.drawable.icon_action_bar);

		//Settare il font e il titolo della Activity
		LayoutInflater inflator = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View titleView = inflator.inflate(R.layout.actionbar_title, null);
		titleTV = (GWTextView)titleView.findViewById(R.id.actionbar_title_label);
		actionBar.setCustomView(titleView);
		
		//L'icona dell'App diventa tasto per tornare nella Home
		actionBar.setHomeButtonEnabled(true);
		actionBar.setDisplayHomeAsUpEnabled(true);
		/*************************************************/
		
		// create the grid item mapping
		//String[] from = new String[] { KEY_CF, KEY_PATIENT };
		//int[] to = new int[] { R.id.cf, R.id.patient };
		String[] from = new String[] { KEY_CF, KEY_PATIENT_SURNAME, KEY_PATIENT_NAME };
		int[] to = new int[] { R.id.cf, R.id.patient_surname, R.id.patient_name };

		fillMaps = new ArrayList<>();

		String userId = getIntent().getExtras().getString(USER_ID);
		patientList = DbManager.getDbManager().getUserPatients(userId);
		
		//ricava il nome dell'utente attivo per la title dell'ActionBar
		mCurrentUserName = UserManager.getUserManager().getCurrentUser().getName() + "\n" + UserManager.getUserManager().getCurrentUser().getSurname();
		titleTV.setText(mCurrentUserName);
		
		
		//Ordinamento della lista
		patientNameList = new ArrayList<Patient>();		
		for (UserPatient up : patientList) {
			Patient p;

			p = DbManager.getDbManager().getPatientData(up.getIdPatient());
			patientNameList.add(p);
		}
		Collections.sort(patientNameList, new Comparator() {

			@Override
			public int compare(Object arg0, Object arg1) {
				// TODO Auto-generated method stub
				Patient p1 = (Patient)arg0;
				Patient p2 = (Patient)arg1;
				
				int ret = p1.getSurname().compareToIgnoreCase(p2.getSurname());
				
				if(ret == 0) {
					ret = p1.getName().compareToIgnoreCase(p2.getName());
				}
				
				return ret;
			}
			
		});
		
		final Patient currentPatient = UserManager.getUserManager().getCurrentPatient();
		hasCurrentPatient = false;
		for (Patient p : patientNameList) {
			if(currentPatient == null || !currentPatient.getId().equals(p.getId())) {
				HashMap<String, String> map = new HashMap<String, String>();
				
				String cfValue = p.getCf();
				if (cfValue == null || cfValue.equalsIgnoreCase("null"))
					cfValue = "";	
				
				map.put(KEY_CF, "[" + cfValue + "]");
				map.put(KEY_ID, "[" + p.getId() + "]");
				map.put(KEY_PATIENT, p.getName() + " " + p.getSurname());
				map.put(KEY_PATIENT_SURNAME, p.getSurname());
				map.put(KEY_PATIENT_NAME, p.getName());
				fillMaps.add(map);
			}
			else
				hasCurrentPatient = true;
		}
		

		IndexableListView lv = (IndexableListView)getListView();
		lv.setFastScrollEnabled(true);
		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				if(hasCurrentPatient && position == fillMaps.size()) {
					Log.i(TAG, "Selezionato paziente corrente. Non faccio nulla");
				}
				else {
					HashMap<String, String> map = fillMaps.get(position);
					Log.i(TAG, "Nome paziente: " + map.get(KEY_PATIENT));
					Intent result = new Intent();
					String idPatient = map.get(KEY_ID);
					idPatient = idPatient.replace("[", "");
					idPatient = idPatient.replace("]", "");
					result.putExtra(PATIENT_ID, idPatient);
					result.putExtra(PATIENT, map.get(KEY_PATIENT));
					setResult(RESULT_OK, result);
					finish();
				}
			}
		});
		
		patientListAdapter = new PatientListAdapter(this, fillMaps, R.layout.patient_item, from, to);
		setListAdapter(patientListAdapter);
	}	
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		
		//MenuInflater menuInflater = getSupportMenuInflater();
		MenuInflater menuInflater = getMenuInflater();
		menuInflater.inflate(R.menu.patient_list_menu, menu);
		
		//SEARCH VIEW
		//createSearchView(menu);
		
		return true;
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		
		super.onPrepareOptionsMenu(menu);
		createSearchView(menu);
		return true;
	}
	

	private void createSearchView(Menu menu) {
		//mSearchView = (SearchView) menu.findItem(R.id.patient_list_action_bar_menu_search).getActionView();
		mSearchView = (SearchView) MenuItemCompat.getActionView(menu.findItem(R.id.patient_list_action_bar_menu_search));
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
			//Identifico quale elemento è stato premuto
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
		newText = (newText.length()==0) ? "" : newText;
		
		//Ciclare nella lista pazienti per trovare newText e aggiornare la lista fillMaps
		fillMaps.clear();
		
		for (Patient p : patientNameList) {
			
			if( p.getSurname().regionMatches(true, 0, newText, 0, newText.length()) ) {
				//Controlla cognome
				HashMap<String, String> map = new HashMap<String, String>();
				
				String cfValue = p.getCf();
				if (cfValue == null || cfValue.equalsIgnoreCase("null"))
					cfValue = "";	
				
				map.put(KEY_CF, "[" + cfValue + "]");
				map.put(KEY_ID, "[" + p.getId() + "]");
				map.put(KEY_PATIENT, p.getName() + " " + p.getSurname());
				map.put(KEY_PATIENT_SURNAME, p.getSurname());
				map.put(KEY_PATIENT_NAME, p.getName());
				fillMaps.add(map);
			} else if( p.getName().regionMatches(true, 0, newText, 0, newText.length()) ) {
				//Controlla nome
				HashMap<String, String> map = new HashMap<String, String>();
				
				String cfValue = p.getCf();
				if (cfValue == null || cfValue.equalsIgnoreCase("null"))
					cfValue = "";	
				
				map.put(KEY_CF, "[" + cfValue + "]");
				map.put(KEY_ID, "[" + p.getId() + "]");
				map.put(KEY_PATIENT, p.getName() + " " + p.getSurname());
				map.put(KEY_PATIENT_SURNAME, p.getSurname());
				map.put(KEY_PATIENT_NAME, p.getName());
				fillMaps.add(map);
			} else if( p.getCf().regionMatches(true, 0, newText, 0, newText.length()) ) {
				//Controlla CF
				HashMap<String, String> map = new HashMap<String, String>();
				
				String cfValue = p.getCf();
				if (cfValue == null || cfValue.equalsIgnoreCase("null"))
					cfValue = "";	
				
				map.put(KEY_CF, "[" + cfValue + "]");
				map.put(KEY_ID, "[" + p.getId() + "]");
				map.put(KEY_PATIENT, p.getName() + " " + p.getSurname());
				map.put(KEY_PATIENT_SURNAME, p.getSurname());
				map.put(KEY_PATIENT_NAME, p.getName());
				fillMaps.add(map);
			}
			
			
			/*if(p.getSurname().toLowerCase().contains(newText)) {
				HashMap<String, String> map = new HashMap<String, String>();
				
				String cfValue = p.getCf();
				if (cfValue == null || cfValue.equalsIgnoreCase("null"))
					cfValue = "";	
				
				map.put(KEY_CF, "[" + cfValue + "]");
				map.put(KEY_ID, "[" + p.getId() + "]");
				map.put(KEY_PATIENT, p.getName() + " " + p.getSurname());
				map.put(KEY_PATIENT_SURNAME, p.getSurname());
				map.put(KEY_PATIENT_NAME, p.getName());
				fillMaps.add(map);
			}*/			
		}
		
		patientListAdapter.notifyDataSetChanged();
		
		return true;
	}	
}
