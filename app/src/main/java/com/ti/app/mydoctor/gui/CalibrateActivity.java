package com.ti.app.mydoctor.gui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.util.AppConst;
import com.ti.app.mydoctor.gui.customview.CustomKeyboard;
import com.ti.app.mydoctor.gui.customview.CustomKeyboardListener;
import com.ti.app.mydoctor.gui.customview.GWTextView;
import com.ti.app.mydoctor.util.Util;

public class CalibrateActivity extends ActionBarActivity implements CustomKeyboardListener {

	//Elementi che compongono la GUI
	private GWTextView titleTV;
	EditText calibrateEditText;
	private CustomKeyboard mCustomKeyboard;
		
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.calibrate_activity);
		
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
		titleTV.setText(R.string.calibrateTitle);
		actionBar.setCustomView(titleView);
		
		//L'icona dell'App diventa tasto per tornare nella Home
		actionBar.setHomeButtonEnabled(true);
		actionBar.setDisplayHomeAsUpEnabled(true);
		/*************************************************/		
		
		calibrateEditText =(EditText)findViewById(R.id.calibrate_edit_text);
		Typeface type = Typeface.createFromAsset(getAssets(),"fonts/dsdigi.ttf"); 
		calibrateEditText.setTypeface(type);
		
		//KEYBOARD
		mCustomKeyboard= new CustomKeyboard(this, this, R.id.keyboardview, R.xml.keyboard_layout );        
        mCustomKeyboard.registerEditText(R.id.calibrate_edit_text); 
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
            
		default:
            return super.onOptionsItemSelected(item);
		}
	}
	
	@Override 
	public void onBackPressed() { 
		calibrateEditText.setText("");
		
		Intent intent = new Intent();
		setResult( RESULT_CANCELED, intent );
		finish();
    }
	
	public void send() {
		String value = calibrateEditText.getText().toString();
		
		if( isValueInRange(value) ){
			//Util.setRegistryValue(Util.KEY_BTGT_CALIBRATE_VALUE + "_" + DbManager.getDbManager().getActiveUser().getId(), calibrateEditText.getText().toString());
			Util.setRegistryValue(Util.KEY_BTGT_CALIBRATE_VALUE/* + "_" + DbManager.getDbManager().getActiveUser().getId()*/, calibrateEditText.getText().toString());

			Intent intent = new Intent();			
			setResult(RESULT_OK, intent);
			
			finish();			
		} else {
			calibrateEditText.setError("Errore");
		}		
	}
	
	public void cancel() {
		calibrateEditText.setText("");
		
		Intent intent = new Intent();
		setResult( RESULT_CANCELED, intent );
		finish();
	}
	
	public void clear() {
		calibrateEditText.setError(null);
	}

	private boolean isValueInRange(String calValue) {
		boolean ret = false;
		if(!Util.isEmptyString(calValue)){
			int cal = Integer.valueOf(calValue);
			ret = (AppConst.MIN_STRIP_CODE <= cal) && (cal <= AppConst.MAX_STRIP_CODE);
		}
		return ret;
	}
}
