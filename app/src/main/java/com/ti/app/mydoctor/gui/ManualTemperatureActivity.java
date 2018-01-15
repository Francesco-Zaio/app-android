package com.ti.app.mydoctor.gui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.util.AppConst;
import com.ti.app.mydoctor.AppResourceManager;
import com.ti.app.mydoctor.gui.customview.CustomKeyboardListener;
import com.ti.app.mydoctor.gui.customview.GWTextView;
import com.ti.app.mydoctor.gui.customview.CustomKeyboard;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.measuremodule.MeasureManager;

public class ManualTemperatureActivity extends AppCompatActivity implements CustomKeyboardListener {

	static final String TEMPERATURE_MEASURE = "TEMPERATURE_MEASURE";

	//Elementi che compongono la GUI
	private EditText etTemperature;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.manual_temperature_activity);
		
		//Flag per mantenere attivo lo schermo finchè l'activity è in primo piano
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

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
        GWTextView titleTV = (GWTextView)titleView.findViewById(R.id.actionbar_title_label);
		titleTV.setText(AppResourceManager.getResource().getString("ManualTemperatureDialog.title"));
		actionBar.setCustomView(titleView);
		
		//L'icona dell'App diventa tasto per tornare nella Home
		actionBar.setHomeButtonEnabled(true);
		actionBar.setDisplayHomeAsUpEnabled(true);
				
		TextView tempInfoLbl = (TextView) findViewById(R.id.manual_temperature_info_label);
		tempInfoLbl.setText(AppResourceManager.getResource().getString("ManualTemperatureDialog.temperatureLabel"));
		
		etTemperature =(EditText)findViewById(R.id.temperature_edit_text);
		Typeface type = Typeface.createFromAsset(getAssets(),"fonts/dsdigi.ttf"); 
		etTemperature.setTypeface(type);		
		
		TextView degreesLabel = (TextView) findViewById(R.id.temperature_degrees_label);
		degreesLabel.setText(AppResourceManager.getResource().getString("EGwUnitTemperature"));
		degreesLabel.setTypeface(type);	
		
		//KEYBOARD
        CustomKeyboard mCustomKeyboard= new CustomKeyboard(this, this, R.id.keyboardview, R.xml.keyboard_layout );
        mCustomKeyboard.registerEditText(R.id.temperature_edit_text);      
		
		//etTemperature.findFocus();
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
		etTemperature.setText("");		
		//listener.onCancelClick();
		Intent intent = new Intent();
		setResult( RESULT_CANCELED, intent );
		finish();
    }

	public void send() {
		String value = etTemperature.getText().toString();
		if(isValidValue(value)){
			value = value.replace(",",".");
			Double temp = Double.parseDouble(value);
			Measure m = MeasureManager.getMeasureManager().getManualTemperature(temp,false);
			Intent intent = new Intent();
			intent.putExtra(TEMPERATURE_MEASURE, m);
			setResult(RESULT_OK, intent);
			finish();
		} else {
			etTemperature.setError("Errore");
		}		
	}
	
	public void cancel() {
		//Toast.makeText(this, "Annulla", Toast.LENGTH_SHORT).show();
		etTemperature.setText("");		
		//listener.onCancelClick();
		Intent intent = new Intent();
		setResult( RESULT_CANCELED, intent );
		finish();
	}
	
	public void clear() {
		etTemperature.setError(null);
	}
	
	private boolean isValidValue(String value) {
		boolean ret = true;     
		
		if(value == null || value.length() == 0){
        	ret = false;
        } else {
        	try{        		
        		String tmp = value;
				if(tmp.indexOf(AppConst.COMMA)!= -1){
					tmp = tmp.replace(AppConst.COMMA, AppConst.DOT);
				}				
				float fvalue = Float.parseFloat(tmp);
        		if(fvalue < AppConst.MIN_TEMPERATURE || fvalue > AppConst.MAX_TEMPERATURE){
        			ret = false;        			
        		}
        	} catch(NumberFormatException e){
        		ret = false;        		
        	}
        }
        return ret;
	}
	
}
