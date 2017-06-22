package com.ti.app.telemed.core.webmodule.webmanagerevents;

import java.util.EventObject;

import com.ti.app.telemed.core.webmodule.WebManager;


public class WebManagerResultEvent extends EventObject {
	private int resultCode;
	
	public WebManagerResultEvent(WebManager source) {
		super(source);
	}

	public int getResultCode() {
		return resultCode;
	}

	public void setResultCode(int resultCode) {
		this.resultCode = resultCode;
	}
	
}
