package com.ti.app.mydoctor.gui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.MyDoctorApp;
import com.ti.app.mydoctor.AppResourceManager;
import com.ti.app.mydoctor.util.AppUtil;
import com.ti.app.telemed.core.common.ServerConf;
import com.ti.app.mydoctor.gui.customview.GWTextView;
import com.ti.app.telemed.core.util.Util;

public class ShowUtilitySettings extends AppCompatActivity {
	//Elementi che compongono la GUI
	private EditText hostEt;
	private EditText portEt;
	private EditText quizEt;
	private Switch demoRocheSw;
	private Switch btAddrResetSw;

	//Dialog
	private static final int ERROR_DIALOG = 0;
    private static final int CONNECTING_CONFIRM_DIALOG = 1;
	
	private Bundle dataBundle;
	private static final String ID = "ID";
	private static final String TITLE = "TITLE";
	private static final String MESSAGE = "MESSAGE";
	
	private AppResourceManager rManager;
		
	@Override
	public void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.show_connection_settings_layout);
		
		//La tastiera viene aperta solo quando viene selezionata una edittext
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		//Flag per mantenere attivo lo schermo finchè l'activity è in primo piano
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		//Inizializza l'ActionBAr
        ActionBar customActionBar = this.getSupportActionBar();
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
        GWTextView titleTV = (GWTextView)titleView.findViewById(R.id.actionbar_title_label);
		titleTV.setText(this.getResources().getString(R.string.connectionSettings));
		customActionBar.setCustomView(titleView);
		
		//L'icona dell'App diventa tasto per tornare nella Home
		customActionBar.setHomeButtonEnabled(true);
		customActionBar.setDisplayHomeAsUpEnabled(true);

		//Ottengo il riferimento degli elementi che compongono la GUI
		hostEt = (EditText) findViewById(R.id.host_et);
		portEt = (EditText) findViewById(R.id.port_et);
		quizEt = (EditText) findViewById(R.id.quiz_et);
        demoRocheSw = (Switch) findViewById(R.id.demo_roche_sw);
		btAddrResetSw = (Switch) findViewById(R.id.reset_bt_addr_sw);
        btAddrResetSw.setChecked(false);

        Button okButton = (Button) findViewById(R.id.confirm_button);
        Button cancelButton = (Button) findViewById(R.id.cancel_button);
		//Imposto il listener per i click sui button
		okButton.setOnClickListener(connection_button_click_listener);
		cancelButton.setOnClickListener(connection_button_click_listener);
		
		populateActivity();
				
		rManager = AppResourceManager.getResource();
	}

	/**
	 * Metodo che permette di popolare gli elementi che compongono la GUI con le informazioni lette da db
	 */
	private void populateActivity() {
        ServerConf sc = MyDoctorApp.getConfigurationManager().getConfiguration();
		try {
			hostEt.setText(sc.getIp());
			portEt.setText(sc.getPort());
			quizEt.setText(AppUtil.getRegistryValue(AppUtil.KEY_URL_QUIZ, AppUtil.URL_QUIZ_DEFAULT));
            demoRocheSw.setChecked(Util.isDemoRocheMode());
        } catch (Exception e) {
			showDialog(ShowUtilitySettings.ERROR_DIALOG);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		
		switch(item.getItemId()) {
			case android.R.id.home: //Ritorna alla Home quando si clicca sull'icona della App
				Intent intent = new Intent();
				setResult(RESULT_CANCELED, intent);
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
			builder.setTitle(AppResourceManager.getResource().getString("warningTitle"));
			builder.setMessage(AppResourceManager.getResource().getString("errorDb"));
			builder.setNeutralButton(R.string.okButton, error_dialog_click_listener);
			break;
		case CONNECTING_CONFIRM_DIALOG:
			builder.setTitle(dataBundle.getString(TITLE));
			builder.setMessage(dataBundle.getString(MESSAGE));
			builder.setPositiveButton(R.string.okButton, reset_configuration_dialog_click_listener);
			builder.setNegativeButton(R.string.cancelButton, reset_configuration_dialog_click_listener);
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
				AppUtil.setRegistryValue(AppUtil.KEY_URL_QUIZ, quizEt.getText().toString());
				//Aggiorno il contenuto del db in base agli input dell'utente
				ServerConf sc = MyDoctorApp.getConfigurationManager().getConfiguration();
				sc.setIp(hostEt.getText().toString());
				sc.setPort(portEt.getText().toString());
				MyDoctorApp.getConfigurationManager().updateConfiguration(sc);
				Util.setDemoRocheMode(demoRocheSw.isChecked());
				if (btAddrResetSw.isChecked())
					Util.resetBTAddresses();
				finish();
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
	private DialogInterface.OnClickListener reset_configuration_dialog_click_listener = new DialogInterface.OnClickListener() {
		@Override
		public void onClick(DialogInterface dialog, int which) {
			//Identifico quale elemento è stato premuto
			switch(which) {
			case DialogInterface.BUTTON_POSITIVE:
				if(dataBundle.getInt(ID) == R.id.reset_settings) {
                    MyDoctorApp.getConfigurationManager().resetConfiguration();
                    Toast.makeText(getApplicationContext(), AppResourceManager.getResource().getString("showSettingsOperationSuccessfull"), Toast.LENGTH_LONG).show();
                    finish();
				}
				break;
			case DialogInterface.BUTTON_NEGATIVE:
				Toast.makeText(getApplicationContext(), AppResourceManager.getResource().getString("showSettingsOperationCancelled"), Toast.LENGTH_LONG).show();
				break;
			}
			removeDialog(CONNECTING_CONFIRM_DIALOG);
		}
	};
}
