package com.ti.app.mydoctor.gui;

import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.gui.customview.GWTextView;

import android.os.Bundle;
import androidx.fragment.app.ListFragment;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

public class DeviceListFragment extends ListFragment {
	
	//Elementi del Layout
	private LinearLayout currentPatientLL; 	//Layout per il nome del paziente selezionato
	private GWTextView patientNameTV;		//TextView per il nome del paziente selezionato
	//Spinner per attesa gestione dispositivi
  	private LinearLayout linlaHeaderProgress;
	
	private DeviceListFragmentListener deviceListFragmentListener; 
	
	public DeviceListFragment() {
		// Empty constructor required for fragment subclasses
	}
	
	public void setDeviceListFragmentListener(DeviceListFragmentListener listener) {
		this.deviceListFragmentListener = listener;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		
		
		View rootView = inflater.inflate(R.layout.device_list, container, false);
		
		currentPatientLL = (LinearLayout) rootView.findViewById(R.id.current_patient_relative_layout);
		
		if(patientNameTV == null){
			patientNameTV = (GWTextView)rootView.findViewById(R.id.patient_name_label);				
		}        
		
		//Ricava il lineaLayout relativo al progress spinner
        linlaHeaderProgress = (LinearLayout) rootView.findViewById(R.id.linlaHeaderProgress);
        return rootView;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		
		if (deviceListFragmentListener != null)
			deviceListFragmentListener.onListFragmentCreated();
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		return super.onContextItemSelected(item);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
	}
	
	public LinearLayout getPatientLayout() {
		return currentPatientLL;
	}
	
	public GWTextView getPatientLabel() {
		return patientNameTV;
	}	
	
	public LinearLayout getProgressSpinnerLayout() {
		return linlaHeaderProgress;
	}
}
