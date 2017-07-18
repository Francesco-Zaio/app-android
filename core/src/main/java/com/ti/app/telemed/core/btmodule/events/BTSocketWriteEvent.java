package com.ti.app.telemed.core.btmodule.events;

import java.util.EventObject;

import com.ti.app.telemed.core.btmodule.BTSocketWrite;


public class BTSocketWriteEvent extends EventObject {
	public BTSocketWriteEvent(BTSocketWrite source) {
		super(source);
	}

}
