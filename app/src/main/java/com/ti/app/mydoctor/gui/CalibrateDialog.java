package com.ti.app.mydoctor.gui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.ti.app.mydoctor.R;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.mydoctor.util.GWConst;
import com.ti.app.mydoctor.util.Util;

public class CalibrateDialog extends Dialog implements OnClickListener {
	
	private EditText calibrateEditText;
	private String currentValue;
	CalibrateDialogListener listener;

	public CalibrateDialog(Context context, CalibrateDialogListener listener) {
		super(context);
		this.listener = listener;
		setContentView(R.layout.calibrate_dialog);
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setTitle(R.string.calibrateTitle);		
		TextView tempLbl = (TextView) findViewById(R.id.calibrateLbl);
		tempLbl.setText(R.string.calibrateLbl);
		
		Button okButton = (Button) findViewById(R.id.okButton);
		Button cancelButton = (Button) findViewById(R.id.cancelButton);

		okButton.setOnClickListener(this);
		cancelButton.setOnClickListener(this);
		
		calibrateEditText = (EditText)findViewById(R.id.calibrate);		
		//calibrateEditText.setText(Util.getRegistryValue(Util.KEY_BTGT_CALIBRATE_VALUE + "_" + DbManager.getDbManager().getActiveUser().getId()));
		calibrateEditText.setText(Util.getRegistryValue(Util.KEY_BTGT_CALIBRATE_VALUE/* + "_" + DbManager.getDbManager().getActiveUser().getId()*/));
		currentValue = calibrateEditText.getText().toString();
	}

	@Override
	public void onClick(View v) {		
		switch (v.getId()) {
			case R.id.okButton:								
				if (isValueInRange(calibrateEditText.getText().toString())) {
					//Util.setRegistryValue(Util.KEY_BTGT_CALIBRATE_VALUE + "_" + DbManager.getDbManager().getActiveUser().getId(), calibrateEditText.getText().toString());
					Util.setRegistryValue(Util.KEY_BTGT_CALIBRATE_VALUE/* + "_" + DbManager.getDbManager().getActiveUser().getId()*/, calibrateEditText.getText().toString());
					dismiss();
					listener.onCalibrationConfirmed();
				} else {
					calibrateEditText.setText(currentValue);
				}
			break;
			case R.id.cancelButton:
				cancel();
			break;
		}
	}

	private boolean isValueInRange(String calValue) {
		boolean ret = false;
		if(!Util.isEmptyString(calValue)){
			int cal = Integer.valueOf(calValue);
			ret = (GWConst.MIN_STRIP_CODE <= cal) && (cal <= GWConst.MAX_STRIP_CODE);
		}
		return ret;
	}

}
