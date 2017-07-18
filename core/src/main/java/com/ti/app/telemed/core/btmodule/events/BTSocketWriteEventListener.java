package com.ti.app.telemed.core.btmodule.events;

import java.util.EventListener;

public interface BTSocketWriteEventListener extends EventListener {
	public void openDone(BTSocketWriteEvent evt);
	public void writeDone(BTSocketWriteEvent evt);
	public void errorThrown(BTSocketWriteEvent evt, int type, String description);
}
