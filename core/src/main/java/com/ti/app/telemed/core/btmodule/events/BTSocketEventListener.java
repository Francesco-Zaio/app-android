package com.ti.app.telemed.core.btmodule.events;

import java.util.EventListener;

public interface BTSocketEventListener extends EventListener {
	public void openDone(BTSocketEvent evt);
	public void readDone(BTSocketEvent evt);
	public void writeDone(BTSocketEvent evt);
	public void errorThrown(BTSocketEvent evt, int type, String description);
}
