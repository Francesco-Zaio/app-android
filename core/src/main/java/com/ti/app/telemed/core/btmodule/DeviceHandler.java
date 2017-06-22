package com.ti.app.telemed.core.btmodule;

public interface DeviceHandler {

	enum TCmd {
		ECmdConnByUser,
		ECmdConnByAddr
	}

	void start(String btAddr);
	void start(BTSearcherEventListener listener);
	void stop();
	void stopDeviceOperation(int selected);
	void reset();
}
