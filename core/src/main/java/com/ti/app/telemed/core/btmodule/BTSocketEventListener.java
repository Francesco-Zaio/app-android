package com.ti.app.telemed.core.btmodule;

import java.util.EventListener;

public interface BTSocketEventListener extends EventListener {
	void openDone();
	void readDone();
	void writeDone();
	void errorThrown(int type, String description);
}
