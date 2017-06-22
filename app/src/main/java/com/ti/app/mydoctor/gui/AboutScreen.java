package com.ti.app.mydoctor.gui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.core.MyDoctorApp;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.common.ServerConf;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.exceptions.DbException;
import com.ti.app.mydoctor.util.GWConst;
import com.ti.app.mydoctor.util.Util;

public class AboutScreen extends AppCompatActivity {

	private ImageView splashIV;
	private TextView demoVersionText;

	private boolean isDemoVersion = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		//requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.splashscreen);

		splashIV = (ImageView)findViewById(R.id.splash_image_view);
		splashIV.setOnLongClickListener(new View.OnLongClickListener() {

			@Override
			public boolean onLongClick(View v) {

				if (demoVersionText.getVisibility() == View.VISIBLE) {
					demoVersionText.setVisibility(View.GONE);

					isDemoVersion = false;
					Util.setRegistryValue(Util.KEY_DEMO_VERSION_SETTING, false);

					restoreConfigurationData();
				}
				else {
					setDemoVersion();

					isDemoVersion = true;
					Util.setRegistryValue(Util.KEY_DEMO_VERSION_SETTING, true);
				}

				return true;
			}
		});

		demoVersionText = (TextView)findViewById(R.id.demo_version_text_view);

		isDemoVersion = Util.getRegistryValue(Util.KEY_DEMO_VERSION_SETTING, false);
		if( isDemoVersion )
			setDemoVersion();
		
		
		/*ImageView splashImg = (ImageView) findViewById( R.id.splash_image_view );
		splashImg.setOnLongClickListener( new View.OnLongClickListener() {
			
			@Override
			public boolean onLongClick(View v) {
				showAdvanceSettings();
				return true;
			}
		});*/
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		//super.onCreateOptionsMenu(menu);

		//1. verifico che l'utente attivo sia quello default
		DbManager dbM = DbManager.getDbManager();
		User defaultUser = dbM.getActiveUser();
		//2. verifico se è l'utente di default
		if( defaultUser != null ) {
			if( defaultUser.getId().equalsIgnoreCase( GWConst.DEFAULT_USER_ID ) ) {
				//utente di default, abilita menu
				MenuInflater inflater = getMenuInflater();
				inflater.inflate(R.menu.show_advanced_settings_menu, menu);
			}
		} else {
			//Non ci sono utenti attivi, quindi carica l'utente di default
			try {
				defaultUser = dbM.getUser( GWConst.DEFAULT_USER_ID );

				if( defaultUser == null ) {
					//3. se l'utente di default non esiste lo crea
					defaultUser = dbM.createDefaultUser();
				}

				MenuInflater inflater = getMenuInflater();
				inflater.inflate(R.menu.show_advanced_settings_menu, menu);
			} catch (DbException e) {
				e.printStackTrace();
			}
		}

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);

		switch(item.getItemId()) {

		case R.id.advance_options_settings:

			AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    	builder.setTitle(ResourceManager.getResource().getString("MainGUI.configWarningTitle"));
			builder.setMessage(ResourceManager.getResource().getString("MainGUI.configWarningMsg"));
			builder.setPositiveButton(ResourceManager.getResource().getString("EGwnurseOk"),
					new DialogInterface.OnClickListener(){
						public void onClick(DialogInterface arg0,
								int arg1) {
							//Intent intent = new Intent(AboutScreen.this, ShowUtilitySettingsTabs.class);
							//intent.putExtra("TYPE_SETTINGS", "CONNECTION");

							Intent intent = new Intent(AboutScreen.this, ShowUtilitySettings.class);
							startActivity(intent);
						}
			});
			builder.show();
			break;
		}

		return true;
	}

	protected void showAdvanceSettings() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	builder.setTitle(ResourceManager.getResource().getString("MainGUI.configWarningTitle"));
		builder.setMessage(ResourceManager.getResource().getString("MainGUI.configWarningMsg"));
		builder.setPositiveButton(ResourceManager.getResource().getString("EGwnurseOk"),
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface arg0,
										int arg1) {
						Intent intent = new Intent(AboutScreen.this, ShowUtilitySettings.class);
						startActivity(intent);
					}
				});
		builder.show();
	}

	@Override
	public void onBackPressed() {
		finish();
	}

	private void setDemoVersion() {
		String versionStr = getApplicationContext().getString(R.string.app_name_demo_version_label);

		demoVersionText.setText(versionStr);
		demoVersionText.setVisibility(View.VISIBLE);

		//Modifica impostazioni di connessione
		ServerConf sc = new ServerConf();
		sc.setIp("127.0.0.1");
		sc.setPort("9080");
		sc.setTarget("service/M2MConfigure.wh");
		sc.setProtocol("http");

		try {
			MyDoctorApp.getConfigurationManager().updateConfiguration(sc);
		} catch (DbException e) {
			e.printStackTrace();
		}
	}

	private void restoreConfigurationData() {
		DbManager dbManager = DbManager.getDbManager();
		try {
			dbManager.resetServerConf();
			ServerConf defaultSC = dbManager.getDefaultServerConf();
			defaultSC.setIp(defaultSC.getIpDef());
			defaultSC.setPort(defaultSC.getPortDef());
			defaultSC.setProtocol(defaultSC.getProtocolDef());
			defaultSC.setTarget(defaultSC.getTargetDef());

			MyDoctorApp.getConfigurationManager().updateConfiguration(defaultSC);
		} catch(DbException e) {
			e.printStackTrace();
		}
	}

}
