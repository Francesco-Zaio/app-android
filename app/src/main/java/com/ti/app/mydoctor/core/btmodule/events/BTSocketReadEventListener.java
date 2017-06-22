package com.ti.app.mydoctor.core.btmodule.events;

import java.util.EventListener;

public interface BTSocketReadEventListener extends EventListener {
	void openDone(BTSocketReadEvent evt);
	void readDone(BTSocketReadEvent evt);
	void errorThrown(BTSocketReadEvent evt, int type, String description);
}
