package com.ti.app.mydoctor.gui;

import java.util.ArrayList;

public interface ManualMeasureDialogListener {
	void onOkClick(String measure, ArrayList<String> values, int battery);
	void onCancelClick();
} 
