package com.ti.app.mydoctor.gui;

import java.util.ArrayList;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.util.AppConst;
import com.ti.app.mydoctor.AppResourceManager;
import com.ti.app.telemed.core.util.GWConst;

public class ManualTemperatureDialog extends Dialog implements OnClickListener {

	private ManualMeasureDialogListener listener;

	public ManualTemperatureDialog(Context context, ManualMeasureDialogListener listener) {
		super(context);
		this.listener = listener;
		setContentView(R.layout.manual_temperature_dialog);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setTitle(AppResourceManager.getResource().getString("ManualTemperatureDialog.title"));
		TextView tempLbl = (TextView) findViewById(R.id.temperatureLbl);
		tempLbl.setText(AppResourceManager.getResource().getString("ManualTemperatureDialog.temperatureLabel"));
		
		Button okButton = (Button) findViewById(R.id.okButton);
		Button cancelButton = (Button) findViewById(R.id.cancelButton);

		okButton.setOnClickListener(this);
		cancelButton.setOnClickListener(this);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.okButton:
			EditText tempView = (EditText) findViewById(R.id.temperature);
			String value = tempView.getText().toString();
			if(isValidValue(value)){
				ArrayList<String> values = new ArrayList<String>();					
				values.add(""+value);		
				listener.onOkClick(GWConst.KMsrTemp, values, -1);
				dismiss();
			} else {
				tempView.setError("Errore");
			}
			
			break;
		case R.id.cancelButton:			
			listener.onCancelClick();
			cancel();
			break;
		}
	}
	
	private boolean isValidValue(String value) {
		TextView validationView = (TextView) findViewById(R.id.temperatureValidationErr);
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
        			validationView.setText(AppResourceManager.getResource().getString("ManualTemperatureDialog.validationMsg"));
        		}
        	} catch(NumberFormatException e){
        		ret = false;
        		validationView.setText(AppResourceManager.getResource().getString("ManualTemperatureDialog.validationFormatMsg"));
        	}
        }
        return ret;
	}

}
