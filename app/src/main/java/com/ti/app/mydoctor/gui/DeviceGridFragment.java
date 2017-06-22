package com.ti.app.mydoctor.gui;

import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.gui.customview.GWTextView;

import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.GridView;
import android.widget.LinearLayout;

public class DeviceGridFragment extends Fragment {
	
	//Elementi del Layout
	View fragmentView;
	private LinearLayout currentPatientLL; 	//Layout per il nome del paziente selezionato
	private GWTextView patientNameTV;		//TextView per il nome del paziente selezionato
	private LinearLayout linlaHeaderProgress; //Spinner per attesa gestione dispositivi
	
	private DeviceListFragmentListener deviceListFragmentListener; 
	
	public DeviceGridFragment() {
		// Empty constructor required for fragment subclasses
	}
	
	public void setDeviceListFragmentListener(DeviceListFragmentListener listener) {
		this.deviceListFragmentListener = listener;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
				
		fragmentView = inflater.inflate(R.layout.device_grid, container, false);
				
		currentPatientLL = (LinearLayout) fragmentView.findViewById(R.id.current_patient_relative_layout);
		
		if(patientNameTV == null){
			patientNameTV = (GWTextView)fragmentView.findViewById(R.id.patient_name_label);				
		}        
		
		//Ricava il lineaLayout relativo al progress spinner
        linlaHeaderProgress = (LinearLayout) fragmentView.findViewById(R.id.linlaHeaderProgress);
        
        return fragmentView;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);		
		if (deviceListFragmentListener != null)
		deviceListFragmentListener.onListFragmentCreated();
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		// TODO Auto-generated method stub
		super.onConfigurationChanged(newConfig);		
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
	
	public GridView getGridView() {
		return (GridView) fragmentView.findViewById(R.id.grid_view);
	}
	
	public LinearLayout getProgressSpinnerLayout() {
		return linlaHeaderProgress;
	}
}
