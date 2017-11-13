package com.ti.app.telemed.core.btmodule.events;

import java.util.EventListener;

public interface BTSocketWriteEventListener extends EventListener {
	void writeOpenDone();
	void writeDone();
	void writeErrorThrown(int type, String description);
}
