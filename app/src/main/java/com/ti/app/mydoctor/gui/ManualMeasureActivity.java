package com.ti.app.mydoctor.gui;

import android.app.Activity;
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
import android.widget.ImageView;
import android.widget.TextView;

import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.util.AppConst;
import com.ti.app.mydoctor.AppResourceManager;
import com.ti.app.mydoctor.gui.customview.CustomKeyboardListener;
import com.ti.app.mydoctor.gui.customview.GWTextView;
import com.ti.app.mydoctor.gui.customview.CustomKeyboard;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.measuremodule.MeasureManager;
import com.ti.app.telemed.core.util.GWConst;

public class ManualMeasureActivity extends AppCompatActivity implements CustomKeyboardListener {

	static final String MEASURE_TYPE = "MEASURE_TYPE";
	static final String VALUE_TYPE = "VALUE_TYPE";
	static final String VALUE = "VALUE";
	static final String MEASURE_OBJECT = "MEASURE_OBJECT";

	static final int TEMPERATURE = 1;
	static final int PRESS_SIST = 2;
	static final int PRESS_DIAST = 3;
	static final int PRESS_BPM = 4;
	static final int OXY = 5;
	static final int OXY_BPM = 6;

	//Elementi che compongono la GUI
	private EditText etMeasureValue;

	private String measureType = "";
	private int valueType = -1;
	private Measure measure = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Bundle b = getIntent().getExtras();
		if (b != null) {
			measureType = b.getString(MEASURE_TYPE);
			valueType = b.getInt(VALUE_TYPE);
			measure = (Measure)b.get(ManualMeasureActivity.MEASURE_OBJECT);
		}

		setContentView(R.layout.manual_measure_activity);
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
		ImageView iv = findViewById(R.id.imageView1);

		GWTextView titleTV = (GWTextView)titleView.findViewById(R.id.actionbar_title_label);
        switch (measureType) {
			case GWConst.KMsrTemp:
				titleTV.setText(AppResourceManager.getResource().getString("ManualMeasureDialog.temperatureTitle"));
				iv.setImageResource(R.drawable.temperatura_icon);
				break;
			case GWConst.KMsrPres:
				titleTV.setText(AppResourceManager.getResource().getString("ManualMeasureDialog.pressureTitle"));
				iv.setImageResource(R.drawable.pressione_icon);
				break;
			case GWConst.KMsrOss:
				titleTV.setText(AppResourceManager.getResource().getString("ManualMeasureDialog.oxymetryTitle"));
				iv.setImageResource(R.drawable.ossimetria_icon);
				break;

		}
		actionBar.setCustomView(titleView);
		
		//L'icona dell'App diventa tasto per tornare nella Home
		actionBar.setHomeButtonEnabled(true);
		actionBar.setDisplayHomeAsUpEnabled(true);
				
		etMeasureValue =(EditText)findViewById(R.id.measure_edit_text);
		Typeface type = Typeface.createFromAsset(getAssets(),"fonts/dsdigi.ttf"); 
		etMeasureValue.setTypeface(type);

		TextView tempInfoLbl = (TextView) findViewById(R.id.manual_measure_info_label);
		TextView degreesLabel = (TextView) findViewById(R.id.measure_unit_label);
		CustomKeyboard mCustomKeyboard = null;
		switch (valueType) {
			case TEMPERATURE:
				tempInfoLbl.setText(AppResourceManager.getResource().getString("ManualMeasureDialog.temperatureLabel"));
				degreesLabel.setText(AppResourceManager.getResource().getString("EGwUnitTemperature"));
				mCustomKeyboard = new CustomKeyboard(this, this, R.id.keyboardview, R.xml.keyboard_layout_float);
				break;
			case PRESS_SIST:
				tempInfoLbl.setText(AppResourceManager.getResource().getString("ManualMeasureDialog.pressMaxLabel"));
				degreesLabel.setText(AppResourceManager.getResource().getString("EGwnurseUnitPressure"));
				mCustomKeyboard = new CustomKeyboard(this, this, R.id.keyboardview, R.xml.keyboard_layout_integer );
				break;
			case PRESS_DIAST:
				tempInfoLbl.setText(AppResourceManager.getResource().getString("ManualMeasureDialog.pressMinLabel"));
				degreesLabel.setText(AppResourceManager.getResource().getString("EGwnurseUnitPressure"));
				mCustomKeyboard = new CustomKeyboard(this, this, R.id.keyboardview, R.xml.keyboard_layout_integer );
				break;
			case OXY:
				tempInfoLbl.setText(AppResourceManager.getResource().getString("ManualMeasureDialog.oxymetryLabel"));
				degreesLabel.setText(AppResourceManager.getResource().getString("EGwnurseUnitOxy"));
				mCustomKeyboard = new CustomKeyboard(this, this, R.id.keyboardview, R.xml.keyboard_layout_integer );
				break;
			case OXY_BPM:
			case PRESS_BPM:
				tempInfoLbl.setText(AppResourceManager.getResource().getString("ManualMeasureDialog.bpmLabel"));
				degreesLabel.setText(AppResourceManager.getResource().getString("EGwnurseUnitHeartRate"));
				mCustomKeyboard = new CustomKeyboard(this, this, R.id.keyboardview, R.xml.keyboard_layout_integer );
				break;
		}
		degreesLabel.setTypeface(type);
		
		//KEYBOARD
		if (mCustomKeyboard != null)
			mCustomKeyboard.registerEditText(R.id.measure_edit_text);
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
		etMeasureValue.setText("");
		//listener.onCancelClick();
		Intent intent = new Intent();
		setResult( RESULT_CANCELED, intent );
		finish();
    }

	public void send() {
		String value = etMeasureValue.getText().toString();
		if (!isValidValue(value)) {
			etMeasureValue.setError("Errore");
			return;
		}

		if (measure == null)
			measure = MeasureManager.getMeasureManager().getManualMeasure(measureType,false);

		switch (valueType) {
			case TEMPERATURE:
				measure.getMeasures().put(GWConst.EGwCode_0R, value);
				break;
			case PRESS_SIST:
				measure.getMeasures().put(GWConst.EGwCode_04, value);
				break;
			case PRESS_DIAST:
				measure.getMeasures().put(GWConst.EGwCode_03, value);
				break;
			case OXY:
				measure.getMeasures().put(GWConst.EGwCode_07, value);
				break;
			case OXY_BPM:
				measure.getMeasures().put(GWConst.EGwCode_0F, value);
				break;
			case PRESS_BPM:
				measure.getMeasures().put(GWConst.EGwCode_06, value);
				break;
		}
		Intent intent = new Intent();
		intent.putExtra(MEASURE_OBJECT, measure);
		setResult(Activity.RESULT_OK, intent);
		finish();
	}
	
	public void cancel() {
		etMeasureValue.setText("");
		Intent intent = new Intent();
		setResult( Activity.RESULT_CANCELED, intent );
		finish();
	}
	
	public void clear() {
		etMeasureValue.setError(null);
	}
	
	private boolean isValidValue(String value) {
		if(value == null || value.isEmpty())
			return false;

		String tmp = value;
		if(tmp.indexOf(AppConst.COMMA)!= -1){
			tmp = tmp.replace(AppConst.COMMA, AppConst.DOT);
		}
		int v;
		float fvalue;
		switch (valueType) {
			case TEMPERATURE:
				try{
					fvalue = Float.parseFloat(tmp);
					if(fvalue < AppConst.MIN_TEMPERATURE || fvalue > AppConst.MAX_TEMPERATURE)
						return false;
				} catch(NumberFormatException e){
					return false;
				}
				break;
			case PRESS_SIST:
				v = Integer.parseInt(tmp);
				if(v < AppConst.MIN_PRESS_SIST || v > AppConst.MAX_PRESS_SIST)
					return false;
				break;
			case PRESS_DIAST:
				v = Integer.parseInt(tmp);
				if(v < AppConst.MIN_PRESS_DIAST || v > AppConst.MAX_PRESS_DIAST)
					return false;
				break;
			case OXY:
				v = Integer.parseInt(tmp);
				if(v < AppConst.MIN_OXY || v > AppConst.MAX_OXY)
					return false;
				break;
			case OXY_BPM:
			case PRESS_BPM:
				v = Integer.parseInt(tmp);
				if(v < AppConst.MIN_BPM || v > AppConst.MAX_BPM)
					return false;
				break;
		}
        return true;
	}
}
