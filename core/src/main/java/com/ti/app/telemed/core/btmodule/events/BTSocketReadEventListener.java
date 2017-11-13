package com.ti.app.telemed.core.btmodule.events;

import java.util.EventListener;

public interface BTSocketReadEventListener extends EventListener {
	void readOpenDone();
	void readDone();
	void readErrorThrown(int type, String description);
}
