package com.ti.app.mydoctor.gui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.AppResourceManager;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.exceptions.DbException;
import com.ti.app.telemed.core.util.GWConst;


public class AboutScreen extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.splashscreen);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		//super.onCreateOptionsMenu(menu);

		//1. verifico che l'utente attivo sia quello default
		DbManager dbM = DbManager.getDbManager();
		User loggedUser = dbM.getCurrentUser();
		//2. verifico se è l'utente di default
		if( loggedUser != null ) {
			if( loggedUser.getId().equalsIgnoreCase( GWConst.DEFAULT_USER_ID ) ) {
				//utente di default, abilita menu
				MenuInflater inflater = getMenuInflater();
				inflater.inflate(R.menu.show_advanced_settings_menu, menu);
			}
		} else {
			//Non ci sono utenti attivi, quindi carica l'utente di default
			try {
				loggedUser = dbM.getUser( GWConst.DEFAULT_USER_ID );

				if( loggedUser == null ) {
					//3. se l'utente di default non esiste lo crea
					dbM.createDefaultUser();
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
	    	builder.setTitle(AppResourceManager.getResource().getString("MainGUI.configWarningTitle"));
			builder.setMessage(AppResourceManager.getResource().getString("MainGUI.configWarningMsg"));
			builder.setPositiveButton(AppResourceManager.getResource().getString("EGwnurseOk"),
					new DialogInterface.OnClickListener(){
						public void onClick(DialogInterface arg0,
								int arg1) {
							Intent intent = new Intent(AboutScreen.this, ShowUtilitySettings.class);
							startActivity(intent);
						}
			});
			builder.show();
			break;
		}

		return true;
	}

	@Override
	public void onBackPressed() {
		finish();
	}
}
