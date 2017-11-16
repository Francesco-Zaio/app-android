package com.ti.app.telemed.core.btmodule;

import java.util.EventListener;
import java.util.Vector;

import android.bluetooth.BluetoothDevice;

public interface BTSearcherEventListener extends EventListener {
    void deviceDiscovered(Vector<BluetoothDevice> devList);
    void deviceSearchCompleted();
}
