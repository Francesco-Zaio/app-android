package com.ti.app.mydoctor.gui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
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

public class ShowUtilitySettings extends ActionBarActivity {
	private static final String TAG = "ShowUtilitySettings";
	
	//Action bar
	private ActionBar customActionBar;
	private GWTextView titleTV;
	
	//Elementi che compongono la GUI
	private EditText hostEt;
	private TextView hostSendTitle;
	private EditText hostSendValue;
	private EditText portEt;
	private Spinner protocolSpinner;
	private EditText quizEt;
	private Button okButton;
	private Button cancelButton;
			
	private String[] portDefaultArray = new String[] {"443", "80"};
	
	private ArrayAdapter<String> protocolSpinnerAdapter;
	private String[] protocolArray = new String[] {"https", "http"};
	
	//Dialog
	public static final int ERROR_DIALOG = 0;
	public static final int CONNECTING_CONFIRM_DIALOG = 1;
	//public static final int SENDING_CONFIRM_DIALOG = 2;
	public static final int ALERT_DIALOG = 3;
	
	private Bundle dataBundle;
	private static final String ID = "ID";
	private static final String TITLE = "TITLE";
	private static final String MESSAGE = "MESSAGE";
	
	private ResourceManager rManager;
	private ServerCertificateManager scManager;
	
	//Serve per controllare il listener su protocolSpinner
	//in modo che non venga eseguito sulla onCreate
	private boolean isProtocolSpinnerTouched = false;
	
	private User activeUser;
		
	@Override
	public void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.show_connection_settings_layout);
		
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
		titleTV.setText(this.getResources().getString(R.string.connectionSettings));
		customActionBar.setCustomView(titleView);
		
		//L'icona dell'App diventa tasto per tornare nella Home
		customActionBar.setHomeButtonEnabled(true);
		customActionBar.setDisplayHomeAsUpEnabled(true);
		/*************************************************/
		
		//Ottengo il riferimento degli elementi che compongono la GUI
		hostEt = (EditText) findViewById(R.id.host_et);
		hostSendTitle = (TextView) findViewById(R.id.host_send_title_tv);
		hostSendValue = (EditText) findViewById(R.id.host_send_value_tv);
		hostSendValue.setEnabled(false);
		portEt = (EditText) findViewById(R.id.port_et);
		
		protocolSpinner = (Spinner) findViewById(R.id.protocol_spinner);
		protocolSpinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, protocolArray);
		protocolSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		protocolSpinner.setAdapter(protocolSpinnerAdapter);
				
		quizEt = (EditText) findViewById(R.id.quiz_et);
		
		
		activeUser = DbManager.getDbManager().getActiveUser();
		if( activeUser==null || activeUser.getId().equalsIgnoreCase( GWConst.DEFAULT_USER_ID )) {
			hostSendTitle.setVisibility(View.GONE);
			hostSendValue.setVisibility(View.GONE);
		}
		
		okButton = (Button) findViewById(R.id.confirm_button);
		cancelButton = (Button) findViewById(R.id.cancel_button);
		//Imposto il listener per i click sui button
		okButton.setOnClickListener(connection_button_click_listener);
		cancelButton.setOnClickListener(connection_button_click_listener);
		
		populateActivity();
				
		rManager = ResourceManager.getResource();
		
		scManager = ServerCertificateManager.getScMananger();
		scManager.setHandler(scManagerHandler);		
	}
	
	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		
		/*protocolSpinner.setOnTouchListener(new View.OnTouchListener() {			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				isProtocolSpinnerTouched = true;
				return true;
			}
		});
		
		protocolSpinner.setOnItemSelectedListener( new AdapterView.OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
						
				if(isProtocolSpinnerTouched) {
					Toast.makeText(parent.getContext(), 
							"OnItemSelectedListener : " + parent.getItemAtPosition(pos).toString(),
							Toast.LENGTH_SHORT).show();
					
					portEt.setText(portDefaultArray[pos]);
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				// Do nothing				
			}
		});	*/
	}

	/**
	 * Metodo che permette di popolare gli elementi che compongono la GUI con le informazioni lette da db
	 */
	private void populateActivity() {
		DbManager dbManager = DbManager.getDbManager();
		try {
			ServerConf sc = dbManager.getServerConf();
						
			hostEt.setText(sc.getIp());
			if( activeUser!=null && !activeUser.getId().equalsIgnoreCase( GWConst.DEFAULT_USER_ID )) {
				hostSendValue.setText( activeUser.getIp() );
			}
			portEt.setText(sc.getPort());
					
			String protocol = sc.getProtocol();
			for(int i = 0; i < protocolArray.length; i++) {
				if(protocolArray[i].equals(protocol))
					protocolSpinner.setSelection(i);
			}		
			
			protocolSpinner.setOnTouchListener(new View.OnTouchListener() {			
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					isProtocolSpinnerTouched = true;
					return false;
				}
			});
			
			protocolSpinner.setOnItemSelectedListener( new AdapterView.OnItemSelectedListener() {

				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
							
					if(isProtocolSpinnerTouched) {
						portEt.setText(portDefaultArray[pos]);
					}
				}

				@Override
				public void onNothingSelected(AdapterView<?> arg0) {
					// Do nothing				
				}
			});	
			
			quizEt.setText(Util.getRegistryValue(Util.KEY_URL_QUIZ, Util.URL_QUIZ_DEFAULT));
			
		} catch (DbException e) {
			showDialog(ShowUtilitySettings.ERROR_DIALOG);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		
		switch(item.getItemId()) {
		case android.R.id.home: //Ritorna alla Home quando si clicca sull'icona della App
			//Creo l'oggetto Intent necessario per tale operazione
			Intent intent = new Intent();
			//Associo il risultato dell'operazione all'intent
			setResult(RESULT_CANCELED, intent);
			//L'activity ha terminato la sua funzione e viene chiusa
			//passando il controllo all'activity che l'ha chiamata
			finish();
            return true;  
		case R.id.reset_settings:
			prepareBundle(R.id.reset_settings, rManager.getString("showSettingsRevertToDefaultTitle"), rManager.getString("showSettingsRevertToDefaultMessage"));
			showDialog(CONNECTING_CONFIRM_DIALOG);					
			return true;
            
            default:
            	return super.onOptionsItemSelected(item);
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		//MenuInflater inflater = getSupportMenuInflater();
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.show_settings_menu, menu);		
				
		return true;
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
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
		case CONNECTING_CONFIRM_DIALOG:
			builder.setTitle(dataBundle.getString(TITLE));
			builder.setMessage(dataBundle.getString(MESSAGE));
			builder.setPositiveButton(R.string.okButton, connecting_confirm_dialog_click_listener);
			builder.setNegativeButton(R.string.cancelButton, connecting_confirm_dialog_click_listener);
			break;		
		case ALERT_DIALOG:
			builder.setTitle(dataBundle.getString(TITLE));
			builder.setMessage(dataBundle.getString(MESSAGE));
			builder.setNeutralButton(R.string.okButton, alert_dialog_click_listener);
			break;
		}
		
		return builder.create();
	}
	
	private void prepareBundle(int id, String title, String message) {
		dataBundle = new Bundle();
		dataBundle.putInt(ID, id);
		dataBundle.putString(TITLE, title);
		dataBundle.putString(MESSAGE, message);
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
	
	/**
	 * Listener per i click sui button che compongono l'activity
	 */
	private View.OnClickListener connection_button_click_listener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			//Identifico quale button è stato selezionato
			switch(v.getId()) {
			case R.id.confirm_button:
				//Aggiorno il contenuto del db in base agli input dell'utente
				DbManager dbManager = DbManager.getDbManager();
				try {
										
					ServerConf sc = new ServerConf();
					sc.setIp(hostEt.getText().toString());
					sc.setPort(portEt.getText().toString());
					sc.setTarget(dbManager.getDefaultServerConf().getTargetDef());
					sc.setProtocol(protocolArray[protocolSpinner.getSelectedItemPosition()]);
					Util.setRegistryValue(Util.KEY_URL_QUIZ, quizEt.getText().toString());

					MyDoctorApp.getConfigurationManager().updateConfiguration(sc);
					
					finish();
				} catch (DbException e) {
					e.printStackTrace();
					Log.e(TAG, "ERROR on button_click_listener: " + e.getMessage());
					showDialog(ShowUtilitySettings.ERROR_DIALOG);
				}
				
				break;
			case R.id.cancel_button:
				finish();
				break;
			}
		}
	};
	
	/**
	 * Listener per la gestione degli eventi sulla dialog CONFIRM_DIALOG per il ripristino dei dati di connessione
	 */
	private DialogInterface.OnClickListener connecting_confirm_dialog_click_listener = new DialogInterface.OnClickListener() {
		
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
			removeDialog(CONNECTING_CONFIRM_DIALOG);
		}
	};
	
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
	
	private final Handler scManagerHandler = new Handler() {
		
		@Override
		public void handleMessage(Message msg) {
			switch(msg.what) {
			case ServerCertificateManager.DELETE_FAILURE:
				Log.i(TAG, "scManagerHandler: errore nella cancellazione dei certificati");
				prepareBundle(0, rManager.getString("warningTitle"), rManager.getString("deleteServerCertFailure"));
				showDialog(ShowUtilitySettings.ALERT_DIALOG);
				break;
			case ServerCertificateManager.DELETE_SUCCESS:
				Log.i(TAG, "scManagerHandler: certificati cancellati con successo");
				prepareBundle(0, rManager.getString("warningTitle"), rManager.getString("deleteServerCertSuccess"));
				showDialog(ShowUtilitySettings.ALERT_DIALOG);
				break;
			}
		}
		
	};

	/**
	 * Interfaccia OnPageChangedListener
	 */
	/*@Override
	public void onPageChanged() {
		mPageSelectedIndex = this.getSupportActionBar().getSelectedNavigationIndex();
		//Bisogna cambiare il menu, quindi lo invalida per richiamare onCreateOptionsMenu
		supportInvalidateOptionsMenu();	
	}*/
}
