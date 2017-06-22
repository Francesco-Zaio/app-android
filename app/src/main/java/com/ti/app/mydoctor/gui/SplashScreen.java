package com.ti.app.mydoctor.gui;

import com.ti.app.mydoctor.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

public class SplashScreen extends Activity {
	
	private Handler handler = new Handler();

	@Override
	protected void onCreate(Bundle savedInstanceState) {		
		super.onCreate(savedInstanceState);
				
		setContentView(R.layout.splashscreen);

		handler.postDelayed(new Runnable() {
			
			@Override
			public void run() {
				startActivity(new Intent(getApplicationContext(), DeviceList.class));
			}
		}, 2000);
		
	}
	
	@Override
	protected void onPause() {		
		super.onPause();
		finish();
	}
	
	@Override
	public void onBackPressed() { }
}
