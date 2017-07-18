package com.ti.app.telemed.core.btmodule.events;

import java.util.EventObject;

import com.ti.app.telemed.core.btmodule.BTSocketRead;


public class BTSocketReadEvent extends EventObject {
	public BTSocketReadEvent(BTSocketRead source) {
		super(source);
	}
}
