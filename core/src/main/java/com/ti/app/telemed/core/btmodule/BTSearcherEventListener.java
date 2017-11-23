package com.ti.app.telemed.core.btmodule;

import java.util.EventListener;
import java.util.Vector;

import android.bluetooth.BluetoothDevice;

/**
 * Interfaccia che implementa le callback invocate durante il discovery Bluetooth.
 */
public interface BTSearcherEventListener extends EventListener {
    /**
     * Metodo che viene invocato quando viene trovato un nuovo device Bluetooth.
     * @param devList Lista dei device trovati.
     */
    void deviceDiscovered(Vector<BluetoothDevice> devList);

    /**
     * Metodo che viene invocato per notificare la fine dell'operazione di discovery.
     */
    void deviceSearchCompleted();
}
