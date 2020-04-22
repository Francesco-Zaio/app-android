package com.ti.app.mydoctor.gui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.AppResourceManager;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.usermodule.UserManager;


public class AboutScreen extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.splashscreen);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		User loggedUser = UserManager.getUserManager().getCurrentUser();
		// verifico se è l'utente di default
		if( loggedUser == null || loggedUser.isDefaultUser()) {
			//utente di default, abilita menu
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.show_advanced_settings_menu, menu);
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);
		if(item.getItemId() == R.id.advance_options_settings) {
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
		}
		return true;
	}

	@Override
	public void onBackPressed() {
		finish();
	}
}
