package com.ti.app.mydoctor.core.btmodule.events;

import java.util.EventListener;
import java.util.Vector;

import android.bluetooth.BluetoothDevice;

public interface BTSearcherEventListener extends EventListener {
    void deviceDiscovered(BTSearcherEvent evt, Vector<BluetoothDevice> devList);
    void deviceSearchCompleted(BTSearcherEvent evt);
	void deviceSelected(BTSearcherEvent evt);
}
