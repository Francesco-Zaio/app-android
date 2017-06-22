package com.ti.app.mydoctor.core.btmodule.events;

import java.util.EventObject;

import com.ti.app.mydoctor.core.btmodule.BTSocketRead;


public class BTSocketReadEvent extends EventObject {
	public BTSocketReadEvent(BTSocketRead source) {
		super(source);
	}
}
