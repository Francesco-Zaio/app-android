package com.ti.app.telemed.core.btmodule;

import java.util.EventListener;
import java.util.Vector;

import android.bluetooth.BluetoothDevice;

public interface BTSearcherEventListener extends EventListener {
    public void deviceDiscovered(BTSearcherEvent evt, Vector<BluetoothDevice> devList);
    public void deviceSearchCompleted(BTSearcherEvent evt);
	public void deviceSelected(BTSearcherEvent evt);
}
