package com.ti.app.telemed.core.webmodule.webmanagerevents;

import java.util.EventObject;

import com.ti.app.telemed.core.webmodule.WebManager;
import com.ti.app.telemed.core.xmlmodule.XmlManager;


public class WebManagerSendingResultEvent extends EventObject {
	private XmlManager.XmlErrorCode _resultCode;

	public WebManagerSendingResultEvent(WebManager source) {
		super(source);
		_resultCode = XmlManager.XmlErrorCode.COMMAND_SUCCESSFULLY_EXEC;
	}
	public WebManagerSendingResultEvent(WebManager source, XmlManager.XmlErrorCode resultCode) {
		super(source);
		_resultCode = resultCode;
	}
	public XmlManager.XmlErrorCode resultCode () {
		return _resultCode;
	}
}
