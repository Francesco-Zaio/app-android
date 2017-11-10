package com.ti.app.telemed.core.btmodule;


import java.util.Vector;

import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.btmodule.events.BTSearcherEvent;
import com.ti.app.telemed.core.btmodule.events.BTSearcherEventListener;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

public class BTSearcher {

    private static final String TAG = "BTSearcher";

    private Vector<BTSearcherEventListener> btSearcherEventListeners = new Vector<>();

    // Type of search the scheduler requires
    private DeviceHandler.TCmd searchType;

    private int selectedDevice;

    private Vector<BluetoothDevice> foundDevices = null;

    private BluetoothAdapter mBtAdapter;

    private BroadcastReceiver mReceiver = null;

    public BTSearcher() {
        selectedDevice = -1;
        foundDevices = new Vector<>();

        // Get the local Bluetooth adapter
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    private void registerReceiver() {
        if (mReceiver == null) {
            mReceiver = new MyBroadcastReceiver();
            // Register for broadcasts when a device is discovered
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            MyApp.getContext().registerReceiver(mReceiver, filter);

            // Register for broadcasts when discovery has finished
            filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            MyApp.getContext().registerReceiver(mReceiver, filter);
        }
    }

    private void unregisterReceiver() {
        if (mReceiver != null) {
            try {
                MyApp.getContext().unregisterReceiver(mReceiver);
            } catch (Exception e) {
                Log.w(TAG, e.getMessage());
            }
            mReceiver = null;
        }
    }

    public void setSearchType(DeviceHandler.TCmd sType) {
        searchType = sType;
    }

    public BluetoothDevice getCurrBTDevice() {
        return foundDevices.elementAt(selectedDevice);
    }

    private Runnable startSearchRunnable = new Runnable() {

        @Override
        public void run() {
            reset();
            Log.i(TAG, "Start searching devices");

            registerReceiver();

            // If we're already discovering, stop it
            if (mBtAdapter.isDiscovering()) {
                mBtAdapter.cancelDiscovery();
            }

            // Request discover from BluetoothAdapter
            mBtAdapter.startDiscovery();
        }
    };

    public void startSearchDevices() {
        Thread t = new Thread(startSearchRunnable);
        t.start();
    }

    public void stopSearchDevices(int selected) {
        Log.i(TAG, "stopSearchDevices: selected = " + selected);
        unregisterReceiver();
        if (mBtAdapter.isDiscovering())
        mBtAdapter.cancelDiscovery();
        if (selected >= 0) {
            selectedDevice = selected;
            // in all case
            fireDeviceSelected();
        }
    }

    /**
     * Advise that the device search is completed
     */
    public void deviceSearchCompleted() {
        fireDeviceSearchCompleted();
        unregisterReceiver();
        if (searchType == DeviceHandler.TCmd.ECmdConnByAddr && selectedDevice == -1)
            startSearchDevices();

    }

    public void close() {
        reset();
    }

    private void reset() {
        selectedDevice = -1;
        if (!foundDevices.isEmpty()) {
            foundDevices.removeAllElements();
        }
    }

    // methods to add/remove event listeners

    public synchronized void clearBTSearcherEventListener() {
        btSearcherEventListeners.clear();
    }

    public synchronized void addBTSearcherEventListener(BTSearcherEventListener listener) {
        if (listener == null || btSearcherEventListeners.contains(listener)) {
            return;
        }
        btSearcherEventListeners.addElement(listener);
    }

    public synchronized void removeBTSearcherEventListener(BTSearcherEventListener listener) {
        if (listener != null)
            btSearcherEventListeners.removeElement(listener);
    }

    // methods to trigger events

    private Vector<BTSearcherEventListener> getBTSearcherEventListeners(){
        // we work on a copy of the vector, so if change we don't have problem
        Vector<BTSearcherEventListener> copy;
        synchronized (this) {
            copy = (Vector<BTSearcherEventListener>) btSearcherEventListeners.clone();
        }
        return copy;
    }

    private void fireDeviceSelected() {
        BTSearcherEvent event = new BTSearcherEvent(this);
        for (BTSearcherEventListener listener : getBTSearcherEventListeners()) {
            listener.deviceSelected(event);
        }
    }

    private void fireDeviceDiscovered(Vector<BluetoothDevice> devList) {
        BTSearcherEvent event = new BTSearcherEvent(this);
        for (BTSearcherEventListener listener : getBTSearcherEventListeners()) {
            listener.deviceDiscovered(event, devList);
        }
    }

    private void fireDeviceSearchCompleted() {
        BTSearcherEvent event = new BTSearcherEvent(this);
        for (BTSearcherEventListener listener : getBTSearcherEventListeners()) {
            listener.deviceSearchCompleted(event);
        }
    }

    public void deviceDiscovered(BluetoothDevice btDevice) {
        if(btDevice.getName()!= null){
            Log.i(TAG, "Found BluetoothDevice : " + btDevice.getName() + " - " + btDevice.getAddress());
            int i;
            for (i=0; i<foundDevices.size(); i++)
                if (foundDevices.elementAt(i).getAddress().equals(btDevice.getAddress()))
                    break;
            if (i>=foundDevices.size()){
                foundDevices.addElement(btDevice);
                fireDeviceDiscovered(foundDevices);
            }
        } else {
            Log.i(TAG, "Found BluetoothDevice with NULL name : " + btDevice.getAddress());
        }
    }

    private class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                deviceDiscovered(device);
                // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.i(TAG, "Devices search complete");
                if (foundDevices.isEmpty()) {
                    // we have not found any bt devices in the range
                    Log.i(TAG, "There aren't devices in the range");
                }
                deviceSearchCompleted();
            }
        }
    }
}
