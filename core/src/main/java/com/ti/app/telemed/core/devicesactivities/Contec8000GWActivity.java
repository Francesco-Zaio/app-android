package com.ti.app.telemed.core.devicesactivities;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.ti.app.telemed.core.R;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.btmodule.DeviceHandler;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.util.Util;

import serial.jni.BluetoothConnect;
import serial.jni.DataUtils;
import serial.jni.GLView;
import serial.jni.NativeCallBack;

import com.ti.app.telemed.core.btdevices.Contec8000GW;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

public class Contec8000GWActivity extends Activity {
	private static final String TAG = "Contec8000GWActivity";

    private String iBTAddress;
    private GLView glView;
	private DataUtils data;
	private String fileName;
    private String filePath;
    private Measure measure;
    private int result = Contec8000GW.RESULT_ABORT;

    private Button buttonStart;
    private Button buttonAbort;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        //Remove title bar
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        //Remove notification bar
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.glsurfaceview);

        filePath = Util.getMeasuresDir().getAbsolutePath() + "/";
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
        Date curDate = new Date(System.currentTimeMillis());
        fileName = "8000GW-" + formatter.format(curDate);

		DisplayMetrics dm = new DisplayMetrics();
		// Get the window properties
		getWindowManager().getDefaultDisplay().getMetrics(dm);
		int width = dm.widthPixels;// width
		int height = dm.heightPixels;// height
		Log.e("Activity WxH", width + "x" + height);
		Log.e("Density", "" + dm.densityDpi);

		buttonStart = (Button) this.findViewById(R.id.btn07);
		buttonAbort = (Button) this.findViewById(R.id.btn08);
        buttonStart.setText(ResourceManager.getResource().getString("startButton"));
		buttonStart.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
                buttonAbort.setEnabled(true);
                buttonStart.setEnabled(false);
                data.saveCase(filePath, fileName, 10);// The storage file parameters are the path, file name, and storage seconds
			}
		});
        buttonStart.setEnabled(false);
        buttonAbort.setText(ResourceManager.getResource().getString("cancelButton"));
		buttonAbort.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
                buttonStart.setEnabled(true);
                buttonAbort.setEnabled(false);
				data.cancelCase();// Cancel the saved file
                Toast.makeText(Contec8000GWActivity.this, ResourceManager.getResource().getString("KAbortMeasure"),Toast.LENGTH_LONG).show();
			}
		});
        buttonAbort.setEnabled(false);

		// The data object contains all ECG acquisition related operations
		// GlView is responsible for displaying
		// Bluetooth collection
		Intent para = getIntent();
		if (para != null) {
            iBTAddress = para.getExtras().getString(DeviceHandler.BT_ADDRESS);
            measure = (Measure) para.getExtras().getSerializable(Contec8000GW.MEASURE_OBJECT);
			data = new DataUtils(this, iBTAddress, mHandler);
		}

		// Presentation file collection
		// data = new DataUtils(Environment.getExternalStorageDirectory().getPath()+"/demo.ecg");
		// USB 8000G Device support
		// mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		// data = new DataUtils(mUsbManager, mHandler);
		// The following is necessary for the glView operation, please do not change
		glView = (GLView) this.findViewById(R.id.GLWave);
		glView.setBackground(Color.TRANSPARENT, Color.rgb(111, 110, 110));
		glView.setGather(data);
		glView.setMsg(mHandler);
		glView.setZOrderOnTop(true);
        glView.setRendererColor(0.5f, 0.5f, 0.5f, 0);
        glView.getHolder().setFormat(PixelFormat.TRANSLUCENT);

		textHR = (TextView) this.findViewById(R.id.textHR);
        progressBar = (ProgressBar) this.findViewById(R.id.progressBar);
        progressBar.setMax(100);
	}

	@Override
	protected void onDestroy() {
        Log.d(TAG, "onDestroy");

        Intent intent = new Intent();
        intent.setAction(Contec8000GW.CONTEC8000GW_BROADCAST_EVENT);
        intent.putExtra(Contec8000GW.RESULT, result);
        if (result == Contec8000GW.RESULT_OK)
            intent.putExtra(Contec8000GW.MEASURE_OBJECT, measure);
        sendBroadcast(intent);
		super.onDestroy();

        data.gatherEnd();
        final File folder = Util.getMeasuresDir();
        final File[] files = folder.listFiles( new FilenameFilter() {
            @Override
            public boolean accept( final File dir, final String name ) {
                return name.matches( fileName + ".*" );
            }
        } );
        for ( final File file : files ) {
            file.delete();
        }
	}

	@Override
	protected void onPause() {
		super.onPause();
        Log.d(TAG, "onPause");
		glView.onPause();
		data.gatherEnd();
	}

	@Override
	protected void onResume() {
		super.onResume();
        Log.d(TAG, "onResume");
		glView.onResume();
		data.gatherStart(new nativeMsg());
	}

    @Override
    public void onBackPressed() {
        Log.i(TAG, "Premuto il tasto back");
        glView.onPause();
        data.gatherEnd();
        finish();
    }

    private void makeResultData() {
        int ret = data.ecgDataToAECG(
                filePath + "/" + fileName + ".c8k",
                filePath + fileName + ".xml");
        if (Util.zipFile( filePath + fileName + ".xml", filePath + fileName + ".zip" )) {
            try {
                File file = new File(filePath + fileName + ".zip");
                byte[] fileData = new byte[(int) file.length()];
                DataInputStream dis = new DataInputStream(new FileInputStream(file));
                dis.readFully(fileData);
                dis.close();
                result = Contec8000GW.RESULT_OK;

                String ecgFileName = fileName + ".scp";
                HashMap<String,String> tmpVal = new HashMap<>();
                tmpVal.put(GWConst.EGwCode_0G, ecgFileName);  // filename
                measure.setMeasures(tmpVal);
                measure.setFile(fileData);
                measure.setFileType(XmlManager.ECG_FILE_TYPE);
                measure.setFailed(false);
                measure.setBtAddress(iBTAddress);
                finish();
            } catch (IOException e) {
                result = Contec8000GW.RESULT_ERROR;
                finish();
            }
        }
    }

	private TextView textHR;
    private ProgressBar progressBar;
	private static final int MESSAGE_UPDATE_HR = 0;
    private static final int MESSAGE_UPDATE_PROGRESS = 1;
    private static final int MESSAGE_END_MEASURE = 2;
    private static final int MESSAGE_WAWE_COLOR = 3;

	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {

			switch (msg.what) {
                case MESSAGE_UPDATE_HR:
                    textHR.setText(msg.obj.toString() + "bpm");
                    break;
                case MESSAGE_UPDATE_PROGRESS:
                    progressBar.setProgress((int)msg.obj);
                    break;
                case MESSAGE_WAWE_COLOR:
                    buttonStart.setEnabled((boolean)(msg.obj));
                    break;
                case MESSAGE_END_MEASURE:
                    makeResultData();
                    break;
                case BluetoothConnect.MESSAGE_CONNECT_SUCCESS:
                    Log.d(TAG, "MESSAGE_CONNECT_SUCCESS");
                    result = Contec8000GW.RESULT_ERROR;
                    break;
                case BluetoothConnect.MESSAGE_CONNECT_INTERRUPTED:
                    Log.d(TAG, "MESSAGE_CONNECT_INTERRUPTED");
                    result = Contec8000GW.RESULT_ERROR;
                    finish();
                    break;
                case BluetoothConnect.MESSAGE_CONNECT_FAILED:
                    Log.d(TAG, "MESSAGE_CONNECT_FAILED");
                    result = Contec8000GW.RESULT_ERROR;
                    finish();
                    break;
                default:
                    Log.d(TAG, Integer.toString(msg.what));
                    break;
			}
		}
	};

	private class nativeMsg extends NativeCallBack {

		@Override
		public void callHRMsg(short hr) {// Heart rate
			Log.d(TAG, "callHRMsg - " + Integer.toString(hr));
			mHandler.obtainMessage(MESSAGE_UPDATE_HR, hr).sendToTarget();
		}

		@Override
		public void callLeadOffMsg(String flagOff) {// Lead off
			Log.d(TAG, "callLeadOffMsg - " + flagOff);
		}

		@Override
		public void callProgressMsg(short progress) {// Percentage of file storage progress%
			Log.d(TAG, "callProgressMsg - " + progress);
            mHandler.obtainMessage(MESSAGE_UPDATE_PROGRESS, (int)progress).sendToTarget();
		}

		@Override
		public void callCaseStateMsg(short state) {
            Log.e(TAG, "callCaseStateMsg " + Integer.toString(state));
			if (state == 0) {
				Log.d(TAG, "Save start");// Start storing files
			} else {
				Log.d(TAG, "Save end");// The storage is complete
                mHandler.obtainMessage(MESSAGE_END_MEASURE).sendToTarget();
			}
		}

		@Override
		public void callHBSMsg(short hbs) {// Heart rate hbs = 1 means heartbeat
            Log.d(TAG, "callHBSMsg  - " + "Sound " + hbs);
		}

		@Override
		public void callBatteryMsg(short per) {// Collection box power
            Log.d(TAG, "callBatteryMsg  - " + "Battery " + per);
		}

		@Override
		public void callCountDownMsg(short per) {// Remaining storage time
            Log.d(TAG, "callCountDownMsg  - " + per + "%");
		}

		@Override
		public void callWaveColorMsg(boolean flag) {
            Log.d(TAG, "callWaveColorMsg  - " + "flag " + flag);
            mHandler.obtainMessage(MESSAGE_WAWE_COLOR,flag).sendToTarget();
			if (flag) {
				// After the waveform stabilizes the color turns green
				glView.setRendererColor(1.0f, 1.0f, 1.0f, 0);
				// The following operations can be done automatically to save the file
				// data.saveCase(Environment.getExternalStorageDirectory() + "/", fileName, 20);// The storage file parameters are the path, file name, and storage seconds
			} else
                glView.setRendererColor(0.5f, 0.5f, 0.5f, 0);
		}
	}
}
