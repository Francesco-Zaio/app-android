package com.ti.app.mydoctor.util;

import com.ti.app.mydoctor.R;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class DialogManager {

	public static void showChoice(Context context, String title, String message,
			String okText, String koText,
			DialogInterface.OnClickListener okClickListener,
			DialogInterface.OnClickListener koClickListener
			) {
		
		AlertDialog.Builder builder = new Builder(context); 
		builder.setTitle(title);
		builder.setMessage(message);
		builder.setCancelable(false);
		builder.setPositiveButton(okText, okClickListener);
		builder.setNegativeButton(koText, koClickListener);
		
		builder.create().show();
	}


	public static void showToastMessage(Context context, String messageToast) {
    	
		Toast t = new Toast(context);
		t.setDuration(Toast.LENGTH_LONG);
		LinearLayout ll = new LinearLayout(context);
		TextView tv = new TextView(context);
		tv.setText(messageToast);
		tv.setBackgroundColor(0xff9d1515);
		tv.setTextColor(Color.WHITE);
		tv.setTextSize(20);
		tv.setPadding(10, 10, 10, 10);
		MarginLayoutParams mlp = new MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		mlp.setMargins(10, 10, 10, 10);
		ll.addView(tv, mlp);
		
		t.setView(ll);
		t.show();
	}
    
	public static void showSimpleToastMessage(Context context, String messageToast) {
    	
		Toast t = new Toast(context);
		t.setDuration(Toast.LENGTH_LONG);
		LinearLayout ll = new LinearLayout(context);
		TextView tv = new TextView(context);
		tv.setText(messageToast);
		tv.setBackgroundColor(Color.BLACK);
		tv.setBackgroundResource(R.drawable.border);
		tv.setTextColor(Color.WHITE);
		
		tv.setTextSize(20);
		tv.setPadding(10, 10, 10, 10);
		MarginLayoutParams mlp = new MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		mlp.setMargins(10, 10, 10, 10);
		ll.addView(tv, mlp);
		
		t.setView(ll);
		t.show();
	}
}
