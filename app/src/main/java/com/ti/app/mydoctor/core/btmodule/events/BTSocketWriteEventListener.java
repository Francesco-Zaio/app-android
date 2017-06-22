package com.ti.app.mydoctor.core.btmodule.events;

import java.util.EventListener;

public interface BTSocketWriteEventListener extends EventListener {
	void openDone(BTSocketWriteEvent evt);
	void writeDone(BTSocketWriteEvent evt);
	void errorThrown(BTSocketWriteEvent evt, int type, String description);
}
