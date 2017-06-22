package com.ti.app.mydoctor.gui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.core.MyDoctorApp;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.common.ServerConf;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.scmodule.ServerCertificateManager;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.exceptions.DbException;
import com.ti.app.mydoctor.gui.customview.GWTextView;
import com.ti.app.mydoctor.util.GWConst;
import com.ti.app.mydoctor.util.Util;

public class ShowSettings extends ActionBarActivity {
	
	private static final String TAG = "ShowSettings";
	
	//Dialog
	private static final int ERROR_DIALOG = 0;
	private static final int CONFIRM_DIALOG = 1;
	private static final int ALERT_DIALOG = 2;

	//Action bar
	private ActionBar customActionBar;
	private GWTextView titleTV;
		
	//Elementi che compongono la GUI
	private EditText hostEt;
	private EditText portEt;
	private Spinner protocolSpinner;
	private Button okButton;
	private Button cancelButton;
	
	private ArrayAdapter<String> protocolSpinnerAdapter;
	private ArrayAdapter<String> apnSpinnerAdapter;

	private String[] protocolArray = new String[] {"https", "http"};
	private String[] apnArray = new String[] {};
	
	private ResourceManager rManager;
	private ServerCertificateManager scManager;
	
	private Bundle dataBundle;
	private static final String ID = "ID";
	private static final String TITLE = "TITLE";
	private static final String MESSAGE = "MESSAGE";
	
	private LinearLayout autoSendRL;
	
	private CheckBox autoLoginCB;
	private CheckBox autoUpdateCB;
	private CheckBox autoSendCB;
	private EditText arTimeoutET;
	
	//Indica quali impostazioni devono essere visualizzate
	private String type;

	private TextView autoUpdateTV;

	private CheckBox arTimeoutCB;

	private EditText quizEt;

	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		//requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
		//La tastiera viene aperta solo quando viene selezionata una edittext
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		//Flag per mantenere attivo lo schermo finchè l'activity è in primo piano
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		
		/**
		 * ACTION BAR
		 */
		//Inizializza l'ActionBAr
		customActionBar = this.getSupportActionBar();
		//Setta il gradiente di sfondo della action bar
		Drawable cd = this.getResources().getDrawable(R.drawable.action_bar_background_color);
		customActionBar.setBackgroundDrawable(cd);
				
		customActionBar.setDisplayShowCustomEnabled(true);
		customActionBar.setDisplayShowTitleEnabled(false);
		
		//Setta l'icon
		customActionBar.setIcon(R.drawable.icon_action_bar);

		//Settare il font e il titolo della Activity
		LayoutInflater inflator = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View titleView = inflator.inflate(R.layout.actionbar_title, null);
		titleTV = (GWTextView)titleView.findViewById(R.id.actionbar_title_label);
		customActionBar.setCustomView(titleView);
		
		//L'icona dell'App diventa tasto per tornare nella Home
		customActionBar.setHomeButtonEnabled(true);
		customActionBar.setDisplayHomeAsUpEnabled(true);
		/*************************************************/
		
		
		Bundle data = getIntent().getExtras();
		type = data.getString("TYPE_SETTINGS");
		Log.i(TAG, "Impostazioni da visualizzare: " + type);
		
		if(type.equals("ZEPHYR")) {
			setContentView(R.layout.show_zephyr_settings_layout);
			setTitle(titleView, getString(R.string.config));
		}
		else {
			if(type.equals("STM")) {
				setContentView(R.layout.show_stm_settings_layout);
				setTitle(titleView, getString(R.string.advance_options));
			}
			else {
				if(type.equals("USER")) {
					setContentView(R.layout.show_user_settings_layout);
					setTitle(titleView, getString(R.string.userSettings));
				}
				else {
					setContentView(R.layout.show_connection_settings_layout);
					setTitle(titleView, getString(R.string.connectionSettings));
				}
			}
		}
		
		if(type.equals("ZEPHYR")) {	
			arTimeoutET = (EditText) findViewById(R.id.ARtimeoutEV);
			
			String arTimeout = Util.getRegistryValue(Util.KEY_ZEPHYR_TIMEOUT_VALUE);
			if(Util.isEmptyString(arTimeout)){
				arTimeout = String.valueOf(GWConst.ZEPHYR_TIMEOUT_DEFAULT);
			}
			arTimeoutET.setText(arTimeout);
			
			String loop = Util.getRegistryValue(Util.KEY_ZEPHYR_LOOP_VALUE);
			arTimeoutCB = (CheckBox) findViewById(R.id.ARtimeoutCB);
			
			if (loop.length() == 0) {
				arTimeoutCB.setChecked(false);
				arTimeoutET.setEnabled(true);
			} 
			else {
				arTimeoutCB.setChecked(true);
				arTimeoutET.setEnabled(false);
			}
			
			arTimeoutCB.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					if (isChecked) {
						arTimeoutET.setEnabled(false);
					} 
					else {
						arTimeoutET.setEnabled(true);
					}
				}
			});
				
			okButton = (Button) findViewById(R.id.confirm_button);
			cancelButton = (Button) findViewById(R.id.cancel_button);
			//Imposto il listener per i click sui button
			okButton.setOnClickListener(zephyr_button_click_listener);
			cancelButton.setOnClickListener(zephyr_button_click_listener);
		}
		else {
			if(type.equals("STM")) {	
				arTimeoutET = (EditText) findViewById(R.id.ARtimeoutEV);
				
				String arTimeout = Util.getRegistryValue(Util.KEY_AR_TIMEOUT_VALUE);
				if(Util.isEmptyString(arTimeout)){
					arTimeout = String.valueOf(GWConst.AR_TIMEOUT_DEFAULT);
				}
				arTimeoutET.setText(arTimeout);
				
				okButton = (Button) findViewById(R.id.confirm_button);
				cancelButton = (Button) findViewById(R.id.cancel_button);
				//Imposto il listener per i click sui button
				okButton.setOnClickListener(stm_button_click_listener);
				cancelButton.setOnClickListener(stm_button_click_listener);
			}
			else {
				if(type.equals("USER")) {			
					autoSendRL = (LinearLayout) findViewById(R.id.autoSendRL);
					autoLoginCB = (CheckBox) findViewById(R.id.autoLoginCB);
					autoSendCB = (CheckBox) findViewById(R.id.autoSendCB);
					autoUpdateCB = (CheckBox) findViewById(R.id.autoUpdateCB);
					autoUpdateTV = (TextView) findViewById(R.id.autoUpdateTV);
					
					User currentUser = UserManager.getUserManager().getCurrentUser();
					if(!currentUser.getIsPatient())
						autoSendRL.setVisibility(View.GONE);
					
					autoLoginCB.setChecked(DbManager.getDbManager().getAutoLoginStatus(currentUser.getId()));
					autoSendCB.setChecked(DbManager.getDbManager().getAutoSendStatus(currentUser.getId()));
									
					autoUpdateTV.setText(ResourceManager.getResource().getString("autoUpdateSetting"));
					boolean autoUpdateValue = Util.getRegistryValue(Util.KEY_AUTO_UPDATE + "_" + UserManager.getUserManager().getCurrentUser().getId(), true);
					autoUpdateCB.setChecked(autoUpdateValue);
					autoUpdateCB.setEnabled(autoLoginCB.isChecked());
					autoUpdateTV.setEnabled(autoLoginCB.isChecked());
					
					autoLoginCB.setOnCheckedChangeListener(new OnCheckedChangeListener() {
						
						@Override
						public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
							autoUpdateCB.setEnabled(isChecked);
							autoUpdateTV.setEnabled(isChecked);
						}
					});
					
					
					okButton = (Button) findViewById(R.id.confirm_button);
					cancelButton = (Button) findViewById(R.id.cancel_button);
					//Imposto il listener per i click sui button
					okButton.setOnClickListener(user_button_click_listener);
					cancelButton.setOnClickListener(user_button_click_listener);
				}
				else {			
					//Ottengo il riferimento degli elementi che compongono la GUI
					hostEt = (EditText) findViewById(R.id.host_et);
					portEt = (EditText) findViewById(R.id.port_et);
					protocolSpinner = (Spinner) findViewById(R.id.protocol_spinner);
					quizEt = (EditText) findViewById(R.id.quiz_et);
					
					protocolSpinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, protocolArray);
					protocolSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
					protocolSpinner.setAdapter(protocolSpinnerAdapter);
					apnArray = ApnManager.getApnMananger().getApnArray();

					apnSpinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, apnArray);
					apnSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
					
					okButton = (Button) findViewById(R.id.confirm_button);
					cancelButton = (Button) findViewById(R.id.cancel_button);
					//Imposto il listener per i click sui button
					okButton.setOnClickListener(connection_button_click_listener);
					cancelButton.setOnClickListener(connection_button_click_listener);
					
					populateActivity();
				}
			}
		}
		
		rManager = ResourceManager.getResource();
		
		scManager = ServerCertificateManager.getScMananger();
		scManager.setHandler(scManagerHandler);
	}
	
	private void setTitle(View titleView, String title) {
		
		if (titleTV == null) {
			//getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_title);
			//titleTV = (GWTextView) titleView.findViewById(R.id.title);
			titleTV = (GWTextView)titleView.findViewById(R.id.actionbar_title_label);
		}
		
		titleTV.setText(title);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		//super.onCreateOptionsMenu(menu);
		
		if ( type.equals("USER") ) {
			/*MenuInflater inflater = getSupportMenuInflater();
			inflater.inflate(R.menu.show_settings_user_menu, menu);*/
			
			//mActionBarMenu = menu;
		} else {
			//MenuInflater inflater = getSupportMenuInflater();
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.show_settings_menu, menu);
			
			//mActionBarMenu = menu;
		}
		return true;
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		// Opening submenu in action bar on Hardware menu button click
		/*if (!type.equals("USER")) {
			if(event.getAction() == KeyEvent.ACTION_UP){
			    switch(keyCode) {
			    case KeyEvent.KEYCODE_MENU:
	
			    	mActionBarMenu.performIdentifierAction(R.id.mi_action_bar_menu_overflow, 0);
	
			        return true;  
			    }
			}
		}*/
		return super.onKeyUp(keyCode, event);
	}
	
	@Override
	public Dialog onCreateDialog(int id) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		//Identifico la dialog che deve essere visualizzata
		switch(id) {
		case ERROR_DIALOG:
			builder.setTitle(ResourceManager.getResource().getString("warningTitle"));
			builder.setMessage(ResourceManager.getResource().getString("errorDb"));
			builder.setNeutralButton(R.string.okButton, error_dialog_click_listener);
			break;
		case CONFIRM_DIALOG:
			builder.setTitle(dataBundle.getString(TITLE));
			builder.setMessage(dataBundle.getString(MESSAGE));
			builder.setPositiveButton(R.string.okButton, confirm_dialog_click_listener);
			builder.setNegativeButton(R.string.cancelButton, confirm_dialog_click_listener);
			break;
		case ALERT_DIALOG:
			builder.setTitle(dataBundle.getString(TITLE));
			builder.setMessage(dataBundle.getString(MESSAGE));
			builder.setNeutralButton(R.string.okButton, alert_dialog_click_listener);
			break;
		}
		
		return builder.create();
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);

		switch(item.getItemId()) {
		case android.R.id.home: //Ritorna alla Home quando si clicca sull'icona della App
            finish();
            return true; 
		case R.id.reset_settings:
			prepareBundle(R.id.reset_settings, rManager.getString("showSettingsRevertToDefaultTitle"), rManager.getString("showConnectingSettingsRevertToDefaultMessage"));
			showDialog(CONFIRM_DIALOG);
			break;
		/*case R.id.advance_options_settings:
			//TODO
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    	builder.setTitle(ResourceManager.getResource().getString("MainGUI.configWarningTitle"));
			builder.setMessage(ResourceManager.getResource().getString("MainGUI.configWarningMsg"));
			builder.setPositiveButton(ResourceManager.getResource().getString("EGwnurseOk"), 
					new DialogInterface.OnClickListener(){
						public void onClick(DialogInterface arg0,
								int arg1) {		
							Intent intent = new Intent(ShowSettings.this, ShowUtilitySettings.class);
							intent.putExtra("TYPE_SETTINGS", "USER");
					    	startActivity(intent);
						}
			});
			builder.show();	
			break;*/
		}
		
		return true;
	}
	
	private final Handler scManagerHandler = new Handler() {
		
		@Override
		public void handleMessage(Message msg) {
			switch(msg.what) {
			case ServerCertificateManager.DELETE_FAILURE:
				Log.i(TAG, "scManagerHandler: errore nella cancellazione dei certificati");
				prepareBundle(0, rManager.getString("warningTitle"), rManager.getString("deleteServerCertFailure"));
				showDialog(ALERT_DIALOG);
				break;
			case ServerCertificateManager.DELETE_SUCCESS:
				Log.i(TAG, "scManagerHandler: certificati cancellati con successo");
				prepareBundle(0, rManager.getString("warningTitle"), rManager.getString("deleteServerCertSuccess"));
				showDialog(ALERT_DIALOG);
				break;
			}
		}
		
	};
	
	private void prepareBundle(int id, String title, String message) {
		dataBundle = new Bundle();
		dataBundle.putInt(ID, id);
		dataBundle.putString(TITLE, title);
		dataBundle.putString(MESSAGE, message);
	}
	
	/**
	 * Metodo che permette di popolare gli elementi che compongono la GUI con le informazioni lette da db
	 */
	private void populateActivity() {
		DbManager dbManager = DbManager.getDbManager();
		try {
			ServerConf sc = dbManager.getServerConf();
			
			hostEt.setText(sc.getIp());
			portEt.setText(sc.getPort());
			quizEt.setText(Util.getRegistryValue(Util.KEY_URL_QUIZ, Util.URL_QUIZ_DEFAULT));
			
			String protocol = sc.getProtocol();
			for(int i = 0; i < protocolArray.length; i++) {
				if(protocolArray[i].equals(protocol))
					protocolSpinner.setSelection(i);
			}
			
		} catch (DbException e) {
			showDialog(ERROR_DIALOG);
		}
	}
	
	/**
	 * Listener per la gestione degli eventi sulla dialog ALERT_DIALOG
	 */
	private DialogInterface.OnClickListener alert_dialog_click_listener = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			//Identifico quale elemento è stato premuto
			switch(which) {
			case DialogInterface.BUTTON_NEUTRAL:
				removeDialog(ALERT_DIALOG);
			}
		}
	};
	
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
	
	/**
	 * Listener per la gestione degli eventi sulla dialog CONFIRM_DIALOG
	 */
	private DialogInterface.OnClickListener confirm_dialog_click_listener = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			//Identifico quale elemento è stato premuto
			switch(which) {
			case DialogInterface.BUTTON_POSITIVE:
				if(dataBundle.getInt(ID) == R.id.reset_settings) {
					DbManager dbManager = DbManager.getDbManager();
					try {
						dbManager.resetServerConf();
						ServerConf defaultSC = dbManager.getDefaultServerConf();
						defaultSC.setIp(defaultSC.getIpDef());
						defaultSC.setPort(defaultSC.getPortDef());
						defaultSC.setProtocol(defaultSC.getProtocolDef());
						defaultSC.setTarget(defaultSC.getTargetDef());
						
						MyDoctorApp.getConfigurationManager().updateConfiguration(defaultSC);
						finish();
						Toast.makeText(getApplicationContext(), ResourceManager.getResource().getString("showSettingsOperationSuccessfull"), Toast.LENGTH_LONG).show();
					} catch(DbException e) {
						showDialog(ERROR_DIALOG);
					}
				}
				else {
					scManager.deleteAllServerCerts(UserManager.getUserManager().getCurrentUser().getId());
				}
				break;
			case DialogInterface.BUTTON_NEGATIVE:
				Toast.makeText(getApplicationContext(), ResourceManager.getResource().getString("showSettingsOperationCancelled"), Toast.LENGTH_LONG).show();
				break;
			}
			removeDialog(CONFIRM_DIALOG);
		}
	};
	
	/**
	 * Listener per i click sui button che compongono l'activity
	 */
	private View.OnClickListener connection_button_click_listener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			//Identifico quale button è stato selezionato
			switch(v.getId()) {
			case R.id.confirm_button:
				
				Util.setRegistryValue(Util.KEY_URL_QUIZ, quizEt.getText().toString());
				
				//Aggiorno il contenuto del db in base agli input dell'utente
				DbManager dbManager = DbManager.getDbManager();
				ServerConf sc = new ServerConf();
				sc.setIp(hostEt.getText().toString());
				sc.setPort(portEt.getText().toString());
				sc.setProtocol(protocolArray[protocolSpinner.getSelectedItemPosition()]);
				try {
					MyDoctorApp.getConfigurationManager().updateConfiguration(sc);
					finish();
				} catch (DbException e) {
					e.printStackTrace();
					Log.e(TAG, "ERROR on button_click_listener: " + e.getMessage());
					showDialog(ERROR_DIALOG);
				}
				
				break;
			case R.id.cancel_button:
				finish();
				break;
			}
		}
	};
	
	/**
	 * Listener per i click sui button che compongono l'activity
	 */
	private View.OnClickListener user_button_click_listener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			//Identifico quale button è stato selezionato
			switch(v.getId()) {
			case R.id.confirm_button:
				try {					
					DbManager.getDbManager().saveAutoLoginStatus(UserManager.getUserManager().getCurrentUser().getId(), autoLoginCB.isChecked());
					DbManager.getDbManager().saveAutoSendStatus(UserManager.getUserManager().getCurrentUser().getId(), autoSendCB.isChecked());
					
					Util.setRegistryValue(Util.KEY_AUTO_UPDATE + "_" + UserManager.getUserManager().getCurrentUser().getId(), autoUpdateCB.isChecked());
					
					finish();
				} catch (NumberFormatException e) {
					showError();
				}
				break;
			case R.id.cancel_button:
				finish();
				break;
			}
		}
	};
	
	/**
	 * Listener per i click sui button che compongono l'activity
	 */
	private View.OnClickListener zephyr_button_click_listener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			//Identifico quale button è stato selezionato
			switch(v.getId()) {
			case R.id.confirm_button:
				try {
					byte arTimeout = Byte.valueOf(arTimeoutET.getText().toString());
					if(arTimeout >= GWConst.ZEPHYR_TIMEOUT_MIN && arTimeout <= GWConst.ZEPHYR_TIMEOUT_MAX){						
						Util.setRegistryValue(Util.KEY_ZEPHYR_TIMEOUT_VALUE, String.valueOf(arTimeout));
						
						if (arTimeoutCB.isChecked())
							Util.setRegistryValue(Util.KEY_ZEPHYR_LOOP_VALUE, Util.KEY_ZEPHYR_LOOP_VALUE);
						else
							Util.setRegistryValue(Util.KEY_ZEPHYR_LOOP_VALUE, "");
						
						finish();				
					} else {
						showError();
					}
				} catch (NumberFormatException e) {
					showError();
				}
				break;
			case R.id.cancel_button:
				finish();
				break;
			}
		}
	};
	
	private View.OnClickListener stm_button_click_listener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			//Identifico quale button è stato selezionato
			switch(v.getId()) {
			case R.id.confirm_button:
				try {
					byte arTimeout = Byte.valueOf(arTimeoutET.getText().toString());
					if(arTimeout >= GWConst.AR_TIMEOUT_MIN && arTimeout <= GWConst.AR_TIMEOUT_MAX){						
						Util.setRegistryValue(Util.KEY_AR_TIMEOUT_VALUE, String.valueOf(arTimeout));				
						finish();				
					} else {
						showError();
					}
				} catch (NumberFormatException e) {
					showError();
				}
				break;
			case R.id.cancel_button:
				finish();
				break;
			}
		}
	};
	
	private void showError(){
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	builder.setTitle(ResourceManager.getResource().getString("ShowSettings.userSettings.errTitle"));
		builder.setMessage(ResourceManager.getResource().getString("ShowSettings.userSettings.errMsg"));
		builder.setPositiveButton(ResourceManager.getResource().getString("EGwnurseOk"), null);
		builder.show();	
	}
}
