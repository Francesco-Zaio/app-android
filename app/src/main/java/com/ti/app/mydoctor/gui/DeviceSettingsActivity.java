package com.ti.app.mydoctor.gui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.MyDoctorApp;
import com.ti.app.mydoctor.util.AppConst;
import com.ti.app.mydoctor.AppResourceManager;
import com.ti.app.mydoctor.util.AppUtil;
import com.ti.app.telemed.core.common.Patient;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.common.UserPatient;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.exceptions.DbException;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.mydoctor.devicemodule.DeviceManager;
import com.ti.app.mydoctor.gui.customview.ActionBarListActivity;
import com.ti.app.mydoctor.gui.customview.GWTextView;
import com.ti.app.mydoctor.gui.listadapter.DeviceListAdapter;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DeviceSettingsActivity extends ActionBarListActivity {
	private static final String TAG = "DeviceSettingsActivity";
	
	// Intent request codes
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_SCAN_DEVICES = 2;

    //Dialog
    private static ProgressDialog progressDialog;
    private static AlertDialog alertDialog;
    	
	//etichette per l'adapter
	private static final String KEY_ICON = "icon";
	private static final String KEY_LABEL = "label";
	private static final String KEY_MODEL = "model";
	
	//Gestione lista misure
	private List<String> measureList;
	private HashMap<String, UserDevice> deviceMap;
	private HashMap<String, List<UserDevice>> measureModelsMap;
	private List<HashMap<String, String>> fillMaps;
	
	private String selectedMeasureType;
	private int selectedMeasurePosition;
	
	//For showSelectModelDialog
	private int selectedModelPosition = -1;
	
	//List adapter
	private DeviceListAdapter listAdapter;
	
	//Device Manager
	private DeviceManager deviceManager;
	private DeviceManagerMessageHandler deviceManagerHandler = new DeviceManagerMessageHandler(this);

    //Controlla se l'operazione di pairing è stata avviata
	private boolean isPairing;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.device_list);
		
		//Flag per mantenere attivo lo schermo finchè l'activity è in primo piano
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		//Inizializza l'ActionBAr
		ActionBar actionBar = this.getSupportActionBar();
		if (actionBar != null) {
			//Setta il gradiente di sfondo della action bar
            Drawable cd = ResourcesCompat.getDrawable(getResources(), R.drawable.action_bar_background_color, null);
			actionBar.setBackgroundDrawable(cd);
			actionBar.setDisplayShowCustomEnabled(true);
			actionBar.setDisplayShowTitleEnabled(false);
			//Setta l'icon
			actionBar.setIcon(R.drawable.icon_action_bar);
			//L'icona dell'App diventa tasto per tornare nella Home
			actionBar.setHomeButtonEnabled(true);
			actionBar.setDisplayHomeAsUpEnabled(true);
			//Settare il font e il titolo della Activity
			LayoutInflater inflator = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View titleView = inflator.inflate(R.layout.actionbar_title, null);
            GWTextView titleTV = (GWTextView)titleView.findViewById(R.id.actionbar_title_label);
			titleTV.setText(R.string.app_name);
			actionBar.setCustomView(titleView);
		} else {
            Log.e(TAG, "ActionBar is NULL!!");
        }

		deviceManager = MyDoctorApp.getDeviceManager();
		deviceManager.setHandler(deviceManagerHandler);	
		
		try {
			setupDeviceList();
		} catch (DbException e) {
			e.printStackTrace();
		} 
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
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
				
		if(deviceManager.isOperationRunning()){
			super.onCreateContextMenu(menu, v, menuInfo);
			return;
		}
		
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		selectedMeasureType = measureList.get(info.position);
		selectedMeasurePosition = info.position;
		
		try {
			initDeviceMap();
			UserDevice pd = deviceMap.get(selectedMeasureType);
			
			MenuInflater inflater = getMenuInflater();
			
			inflater.inflate(R.menu.context_menu_device_settings_advanced, menu);
			menu.setHeaderTitle(AppResourceManager.getResource().getString("measureType." + selectedMeasureType));
			menu.setHeaderIcon(AppUtil.getSmallIconId(selectedMeasureType));
			
			if (pd.getBtAddress() == null){
				menu.setGroupVisible(R.id.pair_group, false);
			}
			
			if (pd.getDevice().getModel().equalsIgnoreCase(GWConst.KCAMERA)) {
				menu.setGroupVisible(R.id.pair_group, false);
				menu.setGroupVisible(R.id.select_model_group, false);				
			}
		} catch (DbException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		getListView().setSelection(info.position);

        UserDevice ud = deviceMap.get(selectedMeasureType);
        if(ud.isActive()){
            deviceManager.setCurrentDevice(ud);
        } else {
            deviceManager.setCurrentDevice(null);
        }
    	
	    switch (item.getItemId()) {
		    case R.id.pair:			    	
	    		doScan();		    	
		    	return true;
		    case R.id.select_model:
		    	showSelectModelDialog();
		    	return true;
		    default:
		        return super.onOptionsItemSelected(item);
	    }
	}
	
	//Inizializza mappa dei dispositivi
	private void initDeviceMap() throws DbException {
		deviceMap = new HashMap<>();
		List<UserDevice> dList = DbManager.getDbManager().getCurrentUserDevices();		
		for (UserDevice pDevice : dList) {
			UserDevice tmpDev = deviceMap.get(pDevice.getMeasure());
			if(tmpDev == null || pDevice.isActive()){
				deviceMap.put(pDevice.getMeasure(), pDevice);				
			}				
		}		
	}
	
	//Inizializza per un dato dispositivo la mappa dei modelli
	private void initMeasureModelsMap(String userId) throws DbException {
		measureModelsMap = new HashMap<>();
		for (String measure : measureList) {
			List<UserDevice> modelList = DbManager.getDbManager().getModelsForMeasure(measure, userId);
			measureModelsMap.put(measure, modelList);			
		}		
	}
	
	//Inizializza il singolo elemento per fillMaps
	private HashMap<String, String> setFieldsMap(String measureType) {
		HashMap<String, String> map = new HashMap<>();
		map.put(KEY_ICON, "" + AppUtil.getIconId(measureType));
		//map.put(KEY_LABEL, setupFeedback(AppResourceManager.getResource().getString("measureType." + measureType)));
		map.put(KEY_LABEL, AppResourceManager.getResource().getString("measureType." + measureType));
		UserDevice pd = deviceMap.get(measureType);
		if(pd.isActive()){
			map.put(KEY_MODEL, pd.getDevice().getDescription());
		} else {
			map.put(KEY_MODEL, getString(R.string.selectDevice));
		}
		return map;
	}	
	
	private void setupDeviceList() throws DbException {		
		User defaultUser = DbManager.getDbManager().getUser( GWConst.DEFAULT_USER_ID );
		if(defaultUser != null){
			//L'utente corrente diventa utente attivo
			defaultUser.setActive(true);
			
			//Modifica il DB
			DbManager.getDbManager().setCurrentUser(defaultUser);
			
			deviceManager.setCurrentUser(defaultUser);

            measureList = DbManager.getDbManager().getCfgMeasureTypes();
			initDeviceMap();
			initMeasureModelsMap(defaultUser.getId());

            List<UserPatient> patientList = DbManager.getDbManager().getUserPatients(defaultUser.getId());
			if( patientList.size() == 1 ) {
				//L'utente default ha un solo paziente, se stesso
				Patient p = DbManager.getDbManager().getPatientData(patientList.get(0).getIdPatient()); 
				UserManager.getUserManager().setCurrentPatient(p);
			}
						
			setupListView();					
			registerForContextMenu(getListView());			
		}
	}	
	
	//Inizializza il layout della view
	private void setupListView() {
		// create the grid item mapping
		String[] from = new String[] { KEY_ICON, KEY_LABEL, KEY_MODEL };
		int[] to = new int[] { R.id.icon, R.id.label, R.id.model };				
		
		fillMaps = new ArrayList<>();
		for (String measureType : measureList) {			
			HashMap<String, String> map = setFieldsMap(measureType);
			fillMaps.add(map);		
		}
		listAdapter = new DeviceListAdapter(this, fillMaps, R.layout.device_list_item, from, to, false, false);
		setListAdapter(listAdapter);

		ListView lv = getListView();
		lv.setDivider(null); //rimuove la linea di bordo
		lv.setCacheColorHint(Color.TRANSPARENT); //il background della lista non cambia colore durante lo scroll
		lv.setTextFilterEnabled(false);
		lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				Log.i(TAG, "position: "+position);
				Log.i(TAG, "parent.getItemAtPosition: "+parent.getItemAtPosition(position));
								
				try {
					initDeviceMap();
				} catch (DbException e) {
                    e.printStackTrace();
				}
				
				if(!deviceManager.isOperationRunning() || measureList.get(position).equalsIgnoreCase(GWConst.KMsrAritm)){
					selectedMeasureType = measureList.get(position);
					selectedMeasurePosition = position;		
					
					if(!deviceMap.get(selectedMeasureType).isActive()){
						showSelectModelDialog();
					} else if ( !deviceMap.get(selectedMeasureType).getDevice().getModel().equals(GWConst.KMirSpirometerSNA)) {
						UserDevice ud = deviceMap.get(selectedMeasureType);
						if(ud.isActive()){
							deviceManager.setCurrentDevice(ud);
						} else {
							deviceManager.setCurrentDevice(null);
						}
						
						doScan();
					}							
								
				} else {
					Log.i(TAG, "operation running: click ignored");	
					Toast.makeText(getApplicationContext(), AppResourceManager.getResource().getString("KOperationRunning"), Toast.LENGTH_LONG).show();
				}
			}
		});
	}
	
	//Ridisegna la lista
	private void refreshList() {
		HashMap<String, String> map = setFieldsMap(measureList.get(selectedMeasurePosition));
		fillMaps.remove(selectedMeasurePosition);
		fillMaps.add(selectedMeasurePosition, map);
		listAdapter.notifyDataSetChanged();
	}
	
	//Dialog per selezione modello
	private void showSelectModelDialog() {
		final List<UserDevice> userDevices = measureModelsMap.get(selectedMeasureType);
        final SparseIntArray mapPosition = new SparseIntArray(userDevices.size());

 		List<String> nal = new ArrayList<>();

 		int deviceSelectedIndex = -1;
 		 		 		
 		int p = 0;
 		for (int i = 0; i < userDevices.size(); i++) {
			//Aggiunge l'elemento alla lista di scelta
			nal.add(userDevices.get(i).getDevice().getDescription());
			mapPosition.put(p, i);
			p++;
			
			//Individua il dispositivo scelto in precedenza
			if( userDevices.get(i).isActive() ) {
				deviceSelectedIndex = i;
			}
		} 		
 		
		final CharSequence[] items = new CharSequence[nal.size()];
		for (int i = 0; i < items.length; i++) {
			items[i] = nal.get(i);
		}
		
		selectedModelPosition = -1;
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);

		builder.setTitle(R.string.select_model);
		builder.setIcon(AppUtil.getSmallIconId(selectedMeasureType));
		
		builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				
				if (selectedModelPosition != -1) {
					UserDevice selectedUserDevice = userDevices.get(selectedModelPosition);	
			    	
			    	for (int i = 0; i < userDevices.size(); i++) {
			    		userDevices.get(i).setActive(false);
					}
			    	selectedUserDevice.setActive(true);
			    	deviceMap.put(selectedMeasureType, selectedUserDevice);
			    	//DbManager.getDbManager().updateUserDeviceModel(selectedMeasureType, selectedUserDevice.getDevice().getId());
			    	
			    	try {
			    		DbManager.getDbManager().updateUserDeviceModel(selectedMeasureType, selectedUserDevice.getDevice().getId());
					} catch (DbException e) {
                        e.printStackTrace();
					}
			    	
			    	refreshList(); 
			    	
					dialog.dismiss();
				}
			}
		});
		
		builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		
		builder.setSingleChoiceItems(items, deviceSelectedIndex, new DialogInterface.OnClickListener() {
		    public void onClick(DialogInterface dialog, int item) {	    	
		    	
		    	selectedModelPosition = mapPosition.get(item);
		    	((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);		    	
		    }
		});
		
		final AlertDialog selectModelDialog = builder.create();
		selectModelDialog.show();
		if( deviceSelectedIndex == -1 ){
			Button b = selectModelDialog.getButton(DialogInterface.BUTTON_POSITIVE);
			b.setEnabled(false);
		}
	}
	
	//Gestisce l'avvio della scansione BT
	private void doScan() {
		// If BT is not on, request that it be enabled.
		// startScan() will then be called during onActivityResult
		if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
			isPairing = true;
			requestEnableBT();
		} else {
			startScan();
		}
	}
	
	//Viene chiesto all'utente di abilitare il BT sullo smartphone
	private void requestEnableBT() {
		Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
	}
	
	//Scansione dispositivi BT
	private void startScan() {	
		UserDevice ud = deviceMap.get(selectedMeasureType);
		UserDevice tmpUd = (UserDevice) ud.clone();
		tmpUd.setBtAddress(null);
		deviceManager.setCurrentDevice(tmpUd);
		
		if (!AppUtil.isC40(deviceManager.getCurrentDevice().getDevice())
                && !AppUtil.isCamera(deviceManager.getCurrentDevice().getDevice())) {
			// Launch the DeviceScanActivity to see devices and do scan
			Intent serverIntent = new Intent(this, DeviceScanActivity.class);
			//startActivity(serverIntent);
			startActivityForResult(serverIntent, REQUEST_SCAN_DEVICES);	
		} else {
            deviceManager.startDiscovery(null);
		}
	}
	
	//Termina operazione di scansione
	private void stopDeviceOperation(int position) {
		deviceManager.stopDeviceOperation(position);
		if(position < 0){
			closeProgressDialog();			
		}
		
		refreshList();
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);	

		Log.d(TAG, "onActivityResult requestCode=" + requestCode + " resultCode=" + resultCode);
		
		switch(requestCode) {
    	
    	case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
            	//Avvia la scansione
            	if ( isPairing )
    				startScan();           	        	
            } else {
            	// User did not enable Bluetooth or an error occured
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, R.string.bt_not_enabled, Toast.LENGTH_SHORT).show();
            }    
            break;
    	
    	case REQUEST_SCAN_DEVICES:
    		if (resultCode == Activity.RESULT_OK){
    			int position = data.getExtras().getInt(DeviceScanActivity.SELECTED_DEVICE_POSITION);
    			stopDeviceOperation(position);
    		} else {
    			stopDeviceOperation(-2); 
    		}
    		break;
    	}
	}

    private static class DeviceManagerMessageHandler extends Handler {

        private final WeakReference<DeviceSettingsActivity> mActivity;

        DeviceManagerMessageHandler(DeviceSettingsActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

    	@Override
		public void handleMessage(Message msg) {
            DeviceSettingsActivity activity = mActivity.get();
            if (activity == null)
                return;

        	Bundle dataBundle = msg.getData();        	
        	
        	Log.d(TAG, "DeviceManagerMessageHandler d=" + dataBundle);
        	
            switch (msg.what) {
            case DeviceManager.MESSAGE_STATE:   
            	dataBundle.putBoolean(AppConst.IS_MEASURE, true);
            	if( progressDialog == null )
                    activity.createProgressDialog(dataBundle);
                //getActivity().showDialog(PROGRESS_DIALOG);
                break;
            case DeviceManager.MESSAGE_STATE_WAIT:
            	dataBundle.putBoolean(AppConst.IS_MEASURE, true);
            	if( progressDialog == null )
                    activity.createProgressDialog(dataBundle);
            	//getActivity().showDialog(PROGRESS_DIALOG);
                break;
            case DeviceManager.CONFIG_READY:

                activity.refreshList();

                activity.closeProgressDialog();
                activity.createAlertDialog(dataBundle);
	            break;
            }
        }
    }

	/**
     * PROGRESS DIALOG
     * 
     */
    
    private void createProgressDialog(Bundle data) {
		progressDialog = new ProgressDialog( this );
		
		String msg = data.getString( AppConst.MESSAGE );
		Log.d(TAG, "createProgressDialog msg=" + msg);
		
		progressDialog.setIndeterminate(true);
		progressDialog.setCancelable(false);
		progressDialog.setMessage(data.getString(AppConst.MESSAGE));
		progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, AppResourceManager.getResource().getString("EGwnurseCancel"),  new ProgressDialogClickListener());
		
		progressDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {			
			@Override
			public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {				
				return true;
			}
		});
		
		progressDialog.show();
    	//return progressDialog;
	}
    
    private void closeProgressDialog() {
		if(progressDialog!= null){
			progressDialog.dismiss();
			progressDialog = null;
		}
	}
    
    private class ProgressDialogClickListener implements DialogInterface.OnClickListener {			
		public void onClick(DialogInterface dialog, int which) {
			closeProgressDialog();
			stopDeviceOperation(-1);			
		}		
	}
    
    /**
     * ALERT DIALOG
     */
    private void createAlertDialog(Bundle data) {
    	String msg = data.getString(AppConst.MESSAGE);
    	alertDialog = createAlert(msg, null);
    	alertDialog.show();
	}	
    
    private AlertDialog createAlert(String msg, String title) {
    	
    	Log.d(TAG, "createAlert() msg=" + msg);
    	
    	AlertDialog.Builder builder = new AlertDialog.Builder( this );
		builder.setMessage(msg);
		builder.setPositiveButton("Ok", new AlertDialogClickListener());                       
		builder.setTitle(title);
		return builder.create();
	}
    
    private void closeAlertDialog() {
    	if(alertDialog!= null && alertDialog.isShowing()){
    		alertDialog.dismiss();
    		alertDialog = null;
		}
    }
    
    private class AlertDialogClickListener implements DialogInterface.OnClickListener {			
		public void onClick(DialogInterface dialog, int which) {
			if(progressDialog != null && progressDialog.isShowing()){
				closeProgressDialog();
			}
			
			closeAlertDialog();    	   	
		}		
	}
    
    @Override
    protected void onDestroy() {
    	Log.d(TAG, "onDestroy");

    	UserManager.getUserManager().setCurrentPatient(null);
    	UserManager.getUserManager().reset();
    	    	
    	super.onDestroy();
    }
	
}
