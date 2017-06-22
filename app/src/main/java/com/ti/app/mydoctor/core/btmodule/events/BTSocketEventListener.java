package com.ti.app.mydoctor.core.btmodule.events;

import java.util.EventListener;

public interface BTSocketEventListener extends EventListener {
	void openDone(BTSocketEvent evt);
	void readDone(BTSocketEvent evt);
	void writeDone(BTSocketEvent evt);
	void errorThrown(BTSocketEvent evt, int type, String description);
}
