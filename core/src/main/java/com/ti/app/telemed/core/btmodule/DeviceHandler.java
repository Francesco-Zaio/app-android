package com.ti.app.telemed.core.btmodule;

import com.ti.app.telemed.core.btmodule.events.BTSearcherEventListener;
import com.ti.app.telemed.core.common.UserDevice;

public interface DeviceHandler {

	String BT_ADDRESS = "BT_ADDRESS";
    String MEASURE_OBJECT = "MEASURE_OBJECT";

	enum TCmd {
		ECmdConnByUser,
		ECmdConnByAddr
	}

	enum OperationType {
		Measure,
		Pair,
		Config
	}

	// NB !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    // DeviceHandler Classes MUST IMPLEMENT also the following two static methods
    // (Not added to the Interface because static interface methods are not supported in Java 7)
    /*
    static boolean needPairing(UserDevice userDevice);
    static boolean needConfig(UserDevice userDevice);
    */

    void start(OperationType ot, UserDevice ud, BTSearcherEventListener btSearchListener);
	void stopDeviceOperation(int selected);
    void confirmDialog();
    void cancelDialog();
}
