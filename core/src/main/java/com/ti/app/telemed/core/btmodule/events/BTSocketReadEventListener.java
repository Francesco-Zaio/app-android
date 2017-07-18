package com.ti.app.telemed.core.btmodule.events;

import java.util.EventListener;

public interface BTSocketReadEventListener extends EventListener {
	public void openDone(BTSocketReadEvent evt);
	public void readDone(BTSocketReadEvent evt);
	public void errorThrown(BTSocketReadEvent evt, int type, String description);
}
