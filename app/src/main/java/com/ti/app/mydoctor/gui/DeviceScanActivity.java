package com.ti.app.mydoctor.gui;

import java.util.Vector;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.MyDoctorApp;
import com.ti.app.telemed.core.btmodule.BTSearcherEventListener;

public class DeviceScanActivity extends Activity implements BTSearcherEventListener {
    // Debugging
    private static final String TAG = "DeviceScanActivity";

    // Member fields
    private ArrayAdapter<String> mNewDevicesArrayAdapter;
    private Vector<BluetoothDevice> devList;

	public static final String SELECTED_DEVICE = "SELECTED_DEVICE";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup the window
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.device_scan_list);

        // Initialize array adapter
        mNewDevicesArrayAdapter = new MyArrayAdapter(this, R.layout.device_name);
        devList = new Vector<>();

        // Find and set up the ListView for newly discovered devices
        ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
        newDevicesListView.setAdapter(mNewDevicesArrayAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListener);                       
        
        startDiscovery();
    }

    @Override
    public void onBackPressed() {
    	super.onBackPressed();
    	Log.i(TAG, "onBackPressed --> setResult(RESULT_CANCELED)");
    	setResult(RESULT_CANCELED);
    }
    
	private void startDiscovery() {
		// Indicate scanning in the title
        setProgressBarIndeterminateVisibility(true);
        setTitle(R.string.scanning);        
        MyDoctorApp.getDeviceOperations().startDiscovery(this);
	}

    // The on-click listener for all devices in the ListViews
    private OnItemClickListener mDeviceClickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int position, long arg3) {             	      
            String info = ((TextView) v).getText().toString();    
            Log.i(TAG, "Riga selezionata: "+position + " - " + info);
            if(info.equalsIgnoreCase(getResources().getString(R.string.none_paired))
            		|| info.equalsIgnoreCase(getResources().getString(R.string.none_found))){ 
            	setResult(RESULT_CANCELED);            	
            } else {
            	Intent res = new Intent();
				res.putExtra(SELECTED_DEVICE, devList.get(position));
            	setResult(RESULT_OK, res);
            }          
            finish();
        }		
    };

	@Override
	public void deviceDiscovered(Vector<BluetoothDevice> devList) {
		BluetoothDevice dev = devList.lastElement();
		if(dev.getName()!= null)
			mNewDevicesArrayAdapter.add(dev.getName());
		else if (dev.getAddress() != null)
			mNewDevicesArrayAdapter.add(dev.getAddress());
        else
            return;
        this.devList.add(dev);
	}

	@Override
	public void deviceSearchCompleted() {
		setProgressBarIndeterminateVisibility(false);
        setTitle(R.string.select_device);
        if (mNewDevicesArrayAdapter.getCount() == 0) {
            String noDevices = getResources().getText(R.string.none_found).toString();
            mNewDevicesArrayAdapter.add(noDevices);
        }        
	}
	
	private class MyArrayAdapter extends ArrayAdapter<String> {

		private int[] colors = new int[] { Color.argb(30, 242, 242, 242),
				Color.argb(30, 0, 0, 0) };

		public MyArrayAdapter(Context context, int resource) {
			super(context, resource);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = super.getView(position, convertView, parent);
			int colorPos = position % colors.length;
			view.setBackgroundColor(colors[colorPos]);
			return view;
		}
	}

}
