package com.ti.app.telemed.core.btmodule;

import com.ti.app.telemed.core.btmodule.events.BTSearcherEventListener;

public interface DeviceHandler {

	String BT_ADDRESS = "BT_ADDRESS";
    String MEASURE_OBJECT = "MEASURE_OBJECT";

	enum TCmd {
		ECmdConnByUser,
		ECmdConnByAddr
	}

	void start(String btAddr, boolean pairingMode);
	void start(BTSearcherEventListener listener, boolean pairingMode);
	void confirmDialog();
	void cancelDialog();
	void stop();
	void stopDeviceOperation(int selected);
	void reset();
}
